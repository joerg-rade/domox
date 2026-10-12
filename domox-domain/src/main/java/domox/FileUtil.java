package domox;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for reading files from the classpath resources.
 * <p>
 * Lives in the domain module because it depends only on standard Java APIs.
 * </p>
 */
public class FileUtil {

    public String readFileFromResources(String path) {
        final InputStream inputStream = getClass().getResourceAsStream("/" + path);
        if (inputStream == null) {
            throw new IllegalArgumentException("File not found in classpath: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            final StringBuilder sb = new StringBuilder();
            final char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }
}
