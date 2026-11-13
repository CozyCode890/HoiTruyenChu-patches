package app.hoitruyenchu.extension.itruyenchu.patches.media;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import okhttp3.OkHttpClient;

/**
 * The voice bar plays a silent 6 s mp3 with expo-audio only to get a media notification; the
 * media widget / lock screen therefore showed that file (00:00 / 00:06, paused) and its buttons
 * controlled the silent file instead of the reading.
 * <p>
 * The player given to the MediaSession of expo-audio's AudioControlsService is replaced with
 * {@link TtsSessionPlayer}, which shows the TTS reading: playing / paused, the position in the
 * chapter, and whose play / pause buttons pause and resume the reading.
 * All public static methods are called from patched smali or from TtsController.
 */
@SuppressWarnings("unused")
public final class TtsMediaSession {
    static final String TAG = "HTC-Media";

    private static final Object lock = new Object();
    private static final ArrayList<WeakReference<TtsSessionPlayer>> players = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private TtsMediaSession() {
    }

    /**
     * AudioControlsService.setActivePlayerInternal(): the player the MediaSession is built with.
     *
     * @param service the AudioControlsService (holds the title / artwork set by the app)
     */
    public static Player wrap(Object service, Player player) {
        if (player == null) return null;
        try {
            TtsSessionPlayer wrapped = new TtsSessionPlayer(service, player);
            synchronized (lock) {
                prune();
                players.add(new WeakReference<>(wrapped));
            }
            Log.i(TAG, "media session shows the TTS reading");
            return wrapped;
        } catch (Throwable t) {
            Log.e(TAG, "could not wrap the media session player", t);
            return player;
        }
    }

    /** AudioControlsService.postOrStartForegroundNotification(): title / artwork may have changed. */
    public static void onNotificationUpdate() {
        notifyChanged(0);
    }

    /** OkHttpClientProvider.createClientBuilder(): capture the chapter texts. */
    public static void onCreateClientBuilder(OkHttpClient.Builder builder) {
        try {
            if (builder != null) builder.addInterceptor(new ChapterTextInterceptor());
        } catch (Throwable t) {
            Log.e(TAG, "could not add interceptor", t);
        }
    }

    /** The reading state changed: refresh the widget (after delayMs). */
    public static void notifyChanged(long delayMs) {
        ArrayList<TtsSessionPlayer> targets = new ArrayList<>();
        synchronized (lock) {
            prune();
            for (WeakReference<TtsSessionPlayer> ref : players) {
                TtsSessionPlayer p = ref.get();
                if (p != null) targets.add(p);
            }
        }
        if (targets.isEmpty()) return;
        Runnable r = () -> {
            for (TtsSessionPlayer p : targets) p.refresh();
        };
        if (delayMs > 0) main.postDelayed(r, delayMs);
        else main.post(r);
    }

    private static void prune() {
        Iterator<WeakReference<TtsSessionPlayer>> it = players.iterator();
        while (it.hasNext()) {
            TtsSessionPlayer p = it.next().get();
            if (p == null || p.isReleased()) it.remove();
        }
    }
}
