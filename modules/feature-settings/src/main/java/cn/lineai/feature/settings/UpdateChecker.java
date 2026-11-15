package cn.lineai.feature.settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Fetches and parses the latest GitHub release of the project.
 *
 * <p>The network call blocks, so callers run it on their I/O scheduler; the class starts no threads
 * of its own and never throws: every failure is reported as a null release. The parser is a
 * separate pure method so the payload handling is testable without a server.</p>
 */
public final class UpdateChecker {

    /** Public GitHub endpoint serving the latest published release. */
    public static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/LangLang03/LineCodePro/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "LineCodePro-Android";
    private static final String ACCEPT = "application/vnd.github+json";

    /** Not instantiable; the fetch and parser are static. */
    private UpdateChecker() {
    }

    /**
     * Performs a blocking fetch of the latest published release.
     *
     * @return the parsed release, or null when the request fails, no release exists or the payload
     *     cannot be parsed
     */
    public static ReleaseInfo latest() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", ACCEPT);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) return null;
            return parse(read(connection.getInputStream()));
        } catch (IOException error) {
            return null;
        } catch (RuntimeException error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Parses a GitHub {@code releases/latest} payload.
     *
     * <p>The parser requires both the tag and the release page address because the update action
     * cannot be offered without them; a malformed payload yields null.</p>
     *
     * @param json raw response body
     * @return the parsed release, or null when a required field is missing or the JSON is invalid
     */
    public static ReleaseInfo parse(String json) {
        if (json == null || json.isEmpty()) return null;
        String tag = stringField(json, "tag_name").trim();
        String url = stringField(json, "html_url").trim();
        if (tag.isEmpty() || url.isEmpty()) return null;
        return new ReleaseInfo(tag, stringField(json, "body"), url);
    }

    /**
     * Extracts one top-level JSON string field without depending on a JSON library.
     *
     * <p>The UI module may not import {@code org.json} (architecture guard), and the GitHub
     * payload is simple enough for a tolerant scanner. Escapes for quotes, slashes, control
     * characters and {@code U+XXXX} are decoded; a missing or non-string field yields an empty
     * string.</p>
     *
     * @param json response body; never null here
     * @param key field name to locate
     * @return decoded field value, or an empty string when absent
     */
    private static String stringField(String json, String key) {
        String needle = "\"" + key + "\"";
        int start = json.indexOf(needle);
        if (start < 0) return "";
        int colon = json.indexOf(':', start + needle.length());
        if (colon < 0) return "";
        int index = colon + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        if (index >= json.length() || json.charAt(index) != '"') return "";
        index++;
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        while (index < json.length()) {
            char value = json.charAt(index++);
            if (escaped) {
                if (value == 'u' && index + 4 <= json.length()) {
                    try {
                        out.append((char) Integer.parseInt(
                                json.substring(index, index + 4), 16));
                    } catch (NumberFormatException ignored) {
                        out.append('u');
                    }
                    index += 4;
                } else if (value == 'n') {
                    out.append('\n');
                } else if (value == 't') {
                    out.append('\t');
                } else if (value == 'r') {
                    out.append('\r');
                } else {
                    out.append(value);
                }
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '"') {
                break;
            } else {
                out.append(value);
            }
        }
        return out.toString();
    }

    /** Reads the whole response body as UTF-8 text with the caller owning the socket timeouts. */
    private static String read(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
            return result.toString();
        }
    }
}
