package cn.lineai.engine.ai;

import java.net.URI;
import java.util.Locale;

/**
 * Validation and normalisation of MCP endpoint URLs.
 *
 * <p>HTTPS is mandatory for public hosts. Plain HTTP is allowed for loopback and emulator hosts
 * and for private/LAN addresses ({@code 10/8}, {@code 127/8}, {@code 169.254/16},
 * {@code 172.16/12}, {@code 192.168/16}, IPv6 loopback, IPv6 ULA and IPv6 link-local), plus
 * {@code localhost} and {@code *.local} names. This keeps MCP traffic, session identifiers and
 * credentials off plaintext transport on public networks while local MCP servers stay
 * reachable.</p>
 */
final class McpEndpoint {

    private McpEndpoint() {
    }

    /**
     * Validates and normalises an endpoint URL.
     *
     * @param raw candidate URL
     * @return trimmed URL without trailing slashes
     * @throws IllegalArgumentException when the URL is not absolute HTTPS, or HTTP on a host
     *         that is not loopback or a private/LAN address
     */
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/") && value.length() > 8) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid MCP URL: " + raw);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            if (!"http".equals(scheme) || !isCleartextHostAllowed(host)) {
                throw new IllegalArgumentException(
                        "MCP URL must use https:// (http:// only for local or LAN addresses)");
            }
        }
        return value;
    }

    /**
     * Reduces an endpoint to the part that is safe to write into the failure log.
     *
     * <p>User info, query and fragment are dropped because MCP endpoints commonly carry API keys
     * in the query string; the remaining scheme, host and path identify the server without
     * leaking a credential.</p>
     *
     * @param url endpoint URL, normalised or raw
     * @return endpoint without credentials, query or fragment
     */
    static String display(String url) {
        String value = url == null ? "" : url.trim();
        int cut = value.length();
        int query = value.indexOf('?');
        if (query >= 0) {
            cut = query;
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        value = value.substring(0, cut);
        int scheme = value.indexOf("://");
        int at = value.indexOf('@');
        if (scheme >= 0 && at > scheme + 3) {
            value = value.substring(0, scheme + 3) + value.substring(at + 1);
        }
        return value;
    }

    /**
     * Tests whether a cleartext HTTP host is local to the device or the local network.
     *
     * <p>Only literal addresses and recognised local host names are accepted; no DNS resolution
     * happens here, so validation never blocks on the network and a public host name cannot be
     * smuggled in through a resolver.</p>
     *
     * @param host host parsed from the URI, bracketed for IPv6 literals; may be empty
     * @return true for loopback, private/LAN or link-local addresses and local host names
     */
    private static boolean isCleartextHostAllowed(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String value = host;
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if ("localhost".equals(value) || "localhost.localdomain".equals(value)) {
            return true;
        }
        if (value.endsWith(".local") && value.length() > ".local".length()) {
            return true;
        }
        if (value.indexOf(':') >= 0) {
            return isPrivateIpv6(value);
        }
        return isPrivateIpv4(value);
    }

    /** Tests whether a dotted-quad host is loopback, private RFC 1918 or IPv4 link-local. */
    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int index = 0; index < 4; index++) {
            try {
                octets[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException error) {
                return false;
            }
            if (octets[index] < 0 || octets[index] > 255) {
                return false;
            }
        }
        return octets[0] == 127
                || octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 169 && octets[1] == 254);
    }

    /** Tests whether an IPv6 literal is loopback, unique-local or link-local. */
    private static boolean isPrivateIpv6(String host) {
        String value = host;
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return true;
        }
        if (value.startsWith("fc") || value.startsWith("fd")) {
            return true;
        }
        String first = value.split(":", 2)[0];
        if (first.isEmpty()) {
            return false;
        }
        try {
            int hextet = Integer.parseInt(first, 16);
            return (hextet & 0xffc0) == 0xfe80;
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
