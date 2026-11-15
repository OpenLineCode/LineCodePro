package cn.lineai.engine.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.lineai.core.model.HttpHeader;

/**
 * Merges model-level HTTP headers into a request header map.
 *
 * <p>User-configured headers override provider defaults so a private gateway can adjust
 * authentication or routing headers. The transport-owned {@code Content-Type},
 * {@code Content-Length}, {@code Host} and {@code Connection} fields are never overridden:
 * {@link java.net.HttpURLConnection} must keep control of the message framing and connection
 * reuse, and a stale length would break the request.</p>
 */
final class RequestHeaders {

    /** Headers whose value belongs to the HTTP transport, matched case-insensitively. */
    private static final String[] PROTECTED = {
            "content-type", "content-length", "host", "connection"
    };

    /** Static utility; not instantiable. */
    private RequestHeaders() {
    }

    /**
     * Returns a copy of {@code base} with the model-level headers applied on top.
     *
     * @param base provider headers built by the caller; never mutated
     * @param extra model-level headers, may be null or empty
     * @return merged header map preserving the base order
     */
    static Map<String, String> merge(Map<String, String> base, List<HttpHeader> extra) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        if (extra == null) {
            return merged;
        }
        for (HttpHeader header : extra) {
            if (header == null || header.name.isEmpty() || isProtected(header.name)) {
                continue;
            }
            String existing = existingKey(merged, header.name);
            if (existing != null) {
                merged.remove(existing);
            }
            merged.put(header.name, header.value);
        }
        return merged;
    }

    /** True when the transport owns the header and user headers must not replace it. */
    private static boolean isProtected(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (String protectedName : PROTECTED) {
            if (protectedName.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the stored key that matches {@code name} case-insensitively, or null. */
    private static String existingKey(Map<String, String> map, String name) {
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }
}
