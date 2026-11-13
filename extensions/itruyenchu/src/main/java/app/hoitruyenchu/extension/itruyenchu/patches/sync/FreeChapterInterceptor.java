package app.hoitruyenchu.extension.itruyenchu.patches.sync;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Notices when the app downloads a free chapter ({@code STATIC_URL/free/{slug}/chuong-{n}.txt},
 * chapters 1-50), which the server never sees, and lets {@link AutoSyncPatch} record it.
 * The request itself is not changed.
 */
final class FreeChapterInterceptor implements Interceptor {
    private static final Pattern FREE_CHAPTER = Pattern.compile("^/free/([^/]+)/chuong-(\\d+)\\.txt$");

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        try {
            if ("GET".equals(request.method()) && response.isSuccessful()) {
                Matcher matcher = FREE_CHAPTER.matcher(request.url().encodedPath());
                if (matcher.matches()) {
                    AutoSyncPatch.onFreeChapterLoaded(matcher.group(1), Integer.parseInt(matcher.group(2)));
                }
            }
        } catch (Throwable ignored) {
            // Never break the app's request.
        }
        return response;
    }
}
