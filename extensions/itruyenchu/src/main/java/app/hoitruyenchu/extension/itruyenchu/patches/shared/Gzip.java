package app.hoitruyenchu.extension.itruyenchu.patches.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/** The app's static files are often gzipped (chapters/{slug}.json.gz, previews). */
public final class Gzip {
    private Gzip() {
    }

    public static boolean isGzip(byte[] bytes) {
        return bytes != null && bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    /** @param partial the data is cut off: return what could be decompressed. */
    public static byte[] gunzip(byte[] bytes, boolean partial) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (EOFException e) {
            if (!partial) throw e;
        }
        return out.toByteArray();
    }
}
