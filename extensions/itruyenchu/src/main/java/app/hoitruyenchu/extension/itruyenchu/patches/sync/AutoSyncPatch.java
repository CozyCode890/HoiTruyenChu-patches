package app.hoitruyenchu.extension.itruyenchu.patches.sync;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.facebook.react.bridge.ReadableArray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Automatic sync of the reading history ("Lịch sử đọc") with the iTruyenChu account.
 * <p>
 * How the stock app works (from the Hermes bundle, app 2.1.0):
 * <ul>
 *   <li>The reading history is a zustand store persisted in AsyncStorage under the key {@code read}:
 *       {@code {"state":{"recentReads":[{slug,title,categories,currentChapter,totalChapter,chapterTitle}]},"version":0}}.
 *       The reader only updates this local store.</li>
 *   <li>The login is persisted under {@code auth-storage}: {@code {"state":{"user":{..},"accessToken":".."}}}.</li>
 *   <li>The server only learns about a chapter when it is downloaded with the token, which the app does
 *       for chapter 51 and later. Chapters 1-50 come from a public URL without the token.
 *       That is why the history sometimes syncs and sometimes does not.</li>
 *   <li>A full sync only happens when pressing "Đồng bộ" in the history screen:
 *       {@code POST /users/sync-books {recentReads}} uploads the local list and the response
 *       ({@code data.recentReads}) replaces the local list.</li>
 * </ul>
 * This class does the same automatically, from the native side of AsyncStorage:
 * <ul>
 *   <li>Every write of {@code read} (chapter change) uploads the history a few seconds later.</li>
 *   <li>On app start the history is uploaded and the server answer is merged into the stored history
 *       before the app loads it, so history read on other devices shows up without pressing anything.</li>
 * </ul>
 * All public static methods are called from patched smali.
 */
@SuppressWarnings("unused")
public final class AutoSyncPatch {
    private static final String TAG = "HTC-Sync";

    private static final String API_URL = "https://api.tienphu.top";
    private static final String SYNC_PATH = "/users/sync-books";
    /** Returns the server history as {slug: chapter}, used when there is no local history to upload. */
    private static final String PULL_PATH = "/users/sync-books-new";

    private static final String KEY_READ = "read";
    private static final String KEY_AUTH = "auth-storage";
    private static final String DATABASE = "RKStorage";
    private static final String TABLE = "catalystLocalStorage";

    /** Same limit as the app (addRecentRead keeps 20 entries). */
    private static final int MAX_ITEMS = 20;
    private static final long UPLOAD_DELAY_MS = 3_000;
    /** How long loading the history may wait for the startup sync. */
    private static final long STARTUP_WAIT_MS = 3_500;
    private static final int STARTUP_TIMEOUT_MS = 3_000;
    private static final int TIMEOUT_MS = 15_000;

    private static final Object lock = new Object();
    private static Context appContext;
    private static Handler worker;

    /** Latest values persisted by the app. */
    private static volatile String token;
    private static volatile String readValue;
    /** recentReads last accepted by the server, to skip redundant uploads. */
    private static String lastUploaded;

    private static final CountDownLatch startupDone = new CountDownLatch(1);
    /** New value for {@code read} computed by the startup sync, written before the app loads it. */
    private static volatile String startupMerged;
    private static boolean historyLoadSeen;

    private static final Runnable uploadTask = AutoSyncPatch::upload;

    private AutoSyncPatch() {
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks called from com.reactnativecommunity.asyncstorage.AsyncStorageModule

    /** End of the AsyncStorageModule constructor. */
    public static void onStorageCreated(Context context) {
        synchronized (lock) {
            if (worker != null || context == null) return;
            Context app = context.getApplicationContext();
            appContext = app != null ? app : context;
            HandlerThread thread = new HandlerThread("HTC-Sync");
            thread.start();
            worker = new Handler(thread.getLooper());
            worker.post(AutoSyncPatch::startupSync);
        }
    }

    /**
     * Start of AsyncStorageModule.multiGet(keys). The first time the app loads its reading history,
     * a task is queued on the module's serial executor in front of the read: it waits for the startup
     * sync and stores the merged history, so the app reads the merged value.
     */
    public static void beforeStorageGet(ReadableArray keys, Executor executor) {
        try {
            if (executor == null || !containsKey(keys, KEY_READ)) return;
            synchronized (lock) {
                if (historyLoadSeen) return;
                historyLoadSeen = true;
            }
            executor.execute(() -> {
                try {
                    if (!startupDone.await(STARTUP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "startup sync too slow, loading the local history");
                    }
                    String merged;
                    synchronized (lock) {
                        merged = startupMerged;
                        startupMerged = null;
                    }
                    if (merged != null && writeDb(KEY_READ, merged)) {
                        readValue = merged;
                        Log.i(TAG, "merged server history into the local history");
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "applying startup sync failed", t);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "beforeStorageGet failed", t);
        }
    }

    /** Start of AsyncStorageModule.multiSet([[key, value], ...]). */
    public static void onStorageSet(ReadableArray pairs) {
        try {
            if (pairs == null) return;
            boolean upload = false;
            for (int i = 0; i < pairs.size(); i++) {
                ReadableArray pair = pairs.getArray(i);
                if (pair == null || pair.size() < 2 || pair.isNull(0)) continue;
                String key = pair.getString(0);
                String value = pair.isNull(1) ? null : pair.getString(1);
                if (KEY_READ.equals(key)) {
                    readValue = value;
                    upload = true;
                } else if (KEY_AUTH.equals(key)) {
                    String newToken = tokenFrom(value);
                    if (newToken != null && !newToken.equals(token)) upload = true; // just logged in
                    token = newToken;
                }
            }
            if (upload) scheduleUpload();
        } catch (Throwable t) {
            Log.w(TAG, "onStorageSet failed", t);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sync

    private static void scheduleUpload() {
        Handler handler;
        synchronized (lock) {
            handler = worker;
        }
        if (handler == null) return;
        handler.removeCallbacks(uploadTask);
        handler.postDelayed(uploadTask, UPLOAD_DELAY_MS);
    }

    /** Runs on the worker thread, before the app loads its history. */
    private static void startupSync() {
        try {
            token = tokenFrom(readDb(KEY_AUTH));
            String stored = readDb(KEY_READ);
            readValue = stored;
            if (token == null) {
                Log.i(TAG, "not logged in, nothing to sync");
                return;
            }
            JSONArray local = recentReadsFrom(stored);
            JSONArray remote;
            if (local.length() > 0) {
                JSONObject body = new JSONObject().put("recentReads", local);
                JSONObject response = post(SYNC_PATH, body, STARTUP_TIMEOUT_MS);
                if (response == null) return;
                lastUploaded = local.toString();
                remote = itemsFrom(response.opt("recentReads"));
            } else {
                // Never upload an empty list. Ask for the server history instead (the profile sync button).
                JSONObject response = post(PULL_PATH, null, STARTUP_TIMEOUT_MS);
                if (response == null) return;
                remote = itemsFrom(response.opt("recentReads"));
            }
            if (remote == null || remote.length() == 0) return;

            JSONArray merged = merge(local, remote);
            if (merged.toString().equals(local.toString())) return;
            synchronized (lock) {
                startupMerged = withRecentReads(stored, merged);
            }
            Log.i(TAG, "startup sync: local=" + local.length() + " server=" + remote.length()
                    + " merged=" + merged.length());
        } catch (Throwable t) {
            Log.w(TAG, "startup sync failed", t);
        } finally {
            startupDone.countDown();
        }
    }

    /** Runs on the worker thread a few seconds after the history changed. */
    private static void upload() {
        try {
            String currentToken = token;
            if (currentToken == null) return;
            JSONArray local = recentReadsFrom(readValue);
            if (local.length() == 0) return;
            String json = local.toString();
            if (json.equals(lastUploaded)) return;
            JSONObject response = post(SYNC_PATH, new JSONObject().put("recentReads", local), TIMEOUT_MS);
            if (response != null) {
                lastUploaded = json;
                Log.i(TAG, "uploaded " + local.length() + " history entries");
            }
        } catch (Throwable t) {
            Log.w(TAG, "upload failed", t);
        }
    }

    /**
     * Union by slug. The entry with the higher chapter wins (keeping the local title etc. if the
     * server entry has none). Order: by timestamp if the server provides one, otherwise local
     * entries first (most recent on this device), then entries only known to the server.
     */
    static JSONArray merge(JSONArray local, JSONArray remote) throws Exception {
        LinkedHashMap<String, JSONObject> bySlug = new LinkedHashMap<>();
        for (int i = 0; i < local.length(); i++) {
            JSONObject item = local.optJSONObject(i);
            String slug = item == null ? null : item.optString("slug", null);
            if (slug != null && !bySlug.containsKey(slug)) bySlug.put(slug, item);
        }
        for (int i = 0; i < remote.length(); i++) {
            JSONObject item = remote.optJSONObject(i);
            String slug = item == null ? null : item.optString("slug", null);
            if (slug == null) continue;
            JSONObject mine = bySlug.get(slug);
            if (mine == null) {
                bySlug.put(slug, item);
            } else if (chapterOf(item) > chapterOf(mine)) {
                JSONObject combined = new JSONObject(item.toString());
                for (String field : new String[]{"title", "categories", "totalChapter"}) {
                    if (isEmpty(combined.opt(field)) && !isEmpty(mine.opt(field))) {
                        combined.put(field, mine.get(field));
                    }
                }
                bySlug.put(slug, combined);
            }
        }
        List<JSONObject> items = new ArrayList<>(bySlug.values());
        final String stamp = timestampField(items);
        if (stamp != null) {
            items.sort((a, b) -> Long.compare(timeOf(b.opt(stamp)), timeOf(a.opt(stamp))));
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < items.size() && i < MAX_ITEMS; i++) result.put(items.get(i));
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // JSON helpers

    private static String tokenFrom(String authValue) {
        try {
            if (authValue == null) return null;
            JSONObject state = new JSONObject(authValue).optJSONObject("state");
            if (state == null || state.isNull("accessToken")) return null;
            String value = state.optString("accessToken", "");
            return value.isEmpty() ? null : value;
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONArray recentReadsFrom(String readValue) {
        try {
            if (readValue == null) return new JSONArray();
            JSONObject state = new JSONObject(readValue).optJSONObject("state");
            JSONArray items = state == null ? null : state.optJSONArray("recentReads");
            return items != null ? items : new JSONArray();
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static String withRecentReads(String readValue, JSONArray items) throws Exception {
        JSONObject root;
        try {
            root = readValue != null ? new JSONObject(readValue) : new JSONObject();
        } catch (Throwable t) {
            root = new JSONObject();
        }
        JSONObject state = root.optJSONObject("state");
        if (state == null) {
            state = new JSONObject();
            root.put("state", state);
        }
        state.put("recentReads", items);
        if (!root.has("version")) root.put("version", 0);
        return root.toString();
    }

    /**
     * The server answers either a list of history entries (sync-books) or an object
     * {slug: chapter} (sync-books-new). The latter is converted like the profile screen does.
     */
    private static JSONArray itemsFrom(Object value) throws Exception {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (!(value instanceof JSONObject)) return null;
        JSONObject map = (JSONObject) value;
        JSONArray items = new JSONArray();
        Iterator<String> keys = map.keys();
        while (keys.hasNext()) {
            String slug = keys.next();
            Object chapter = map.get(slug);
            items.put(new JSONObject()
                    .put("slug", slug)
                    .put("totalChapter", chapter)
                    .put("title", "")
                    .put("categories", new JSONArray())
                    .put("currentChapter", chapter)
                    .put("chapterTitle", ""));
        }
        return items;
    }

    private static double chapterOf(JSONObject item) {
        Object value = item.opt("currentChapter");
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null ? 0 : Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null || value == JSONObject.NULL) return true;
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof JSONArray) return ((JSONArray) value).length() == 0;
        return false;
    }

    private static String timestampField(List<JSONObject> items) {
        for (String field : new String[]{"updatedAt", "lastReadAt", "readAt", "lastRead", "timestamp"}) {
            boolean all = !items.isEmpty();
            for (JSONObject item : items) {
                if (timeOf(item.opt(field)) <= 0) {
                    all = false;
                    break;
                }
            }
            if (all) return field;
        }
        return null;
    }

    private static long timeOf(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            String text = (String) value;
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
            }
            try {
                return java.time.Instant.parse(text).toEpochMilli();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static boolean containsKey(ReadableArray keys, String key) {
        if (keys == null) return false;
        for (int i = 0; i < keys.size(); i++) {
            if (!keys.isNull(i) && key.equals(keys.getString(i))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Network

    /** @return the JSON response, or null if the request failed. */
    private static JSONObject post(String path, JSONObject body, int timeoutMs) {
        String currentToken = token;
        if (currentToken == null) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Authorization", "Bearer " + currentToken);
            connection.setRequestProperty("Accept", "application/json");
            byte[] payload = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "POST " + path + " -> HTTP " + code);
                return null;
            }
            String text = readAll(connection.getInputStream());
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (Throwable t) {
            Log.w(TAG, "POST " + path + " failed: " + t);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // AsyncStorage database (RKStorage / catalystLocalStorage)

    private static SQLiteDatabase openDb() {
        Context context = appContext;
        if (context == null) return null;
        File file = context.getDatabasePath(DATABASE);
        if (!file.exists()) return null;
        return SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    private static String readDb(String key) {
        try {
            SQLiteDatabase db = openDb();
            if (db == null) return null;
            try (Cursor cursor = db.rawQuery("SELECT value FROM " + TABLE + " WHERE key = ?", new String[]{key})) {
                return cursor.moveToFirst() ? cursor.getString(0) : null;
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "reading " + key + " failed: " + t);
            return null;
        }
    }

    private static boolean writeDb(String key, String value) {
        try {
            SQLiteDatabase db = openDb();
            if (db == null) return false;
            try {
                db.execSQL("INSERT OR REPLACE INTO " + TABLE + " (key, value) VALUES (?, ?)", new Object[]{key, value});
                return true;
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "writing " + key + " failed: " + t);
            return false;
        }
    }
}
