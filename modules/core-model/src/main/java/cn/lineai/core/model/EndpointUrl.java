package cn.lineai.core.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validation and normalisation of model provider base URLs.
 *
 * <p>A configured endpoint is usable when it is an absolute HTTPS URL, or a plain HTTP URL on a
 * loopback, private/LAN or link-local host - the same cleartext rule the MCP client applies,
 * because self-hosted gateways and local inference servers are ordinary endpoints. A public host
 * over plain HTTP is accepted only when the user enabled the HTTP allowance in the security
 * settings; that switch is passed in by the caller rather than read here, so the class stays a
 * pure policy helper.</p>
 *
 * <p>Normalisation repairs the mistakes users actually make when pasting an endpoint: a missing
 * scheme, a trailing slash, and full-width punctuation produced by a CJK keyboard. Repair never
 * changes a scheme the user typed, so an explicit {@code http://} is validated rather than
 * silently upgraded.</p>
 */
public final class EndpointUrl {

    /** Outcome of validating one configured endpoint. */
    public enum Problem {
        /** The endpoint can be used for a request. */
        NONE,
        /** No endpoint was configured at all. */
        EMPTY,
        /** The value is not a parseable absolute URL, e.g. it contains a space. */
        MALFORMED,
        /** The scheme is neither {@code http} nor {@code https}. */
        UNSUPPORTED_SCHEME,
        /** Plain HTTP was configured for a public host without the HTTP allowance switched on. */
        CLEARTEXT
    }

    /** Static utility; not instantiable. */
    private EndpointUrl() {
    }

    /**
     * Returns the scheme, host and path of a configured endpoint, without the parts that may
     * carry a credential.
     *
     * <p>User info, query and fragment are dropped so the value can be written into a failure
     * report: gateways such as OpenRouter accept the key in the query string, and a leaked key in
     * an error log is worse than a slightly shorter endpoint.</p>
     *
     * @param raw configured endpoint, may be null
     * @return the endpoint without credentials, query or fragment; never null
     */
    public static String display(String raw) {
        String value = raw == null ? "" : raw.trim();
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
     * Repairs and trims a configured endpoint.
     *
     * <p>Full-width ASCII variants and zero-width characters are folded first, then a missing
     * scheme is filled in: {@code http://} for a local or LAN host, {@code https://} otherwise.
     * Trailing slashes are removed because every codec appends its own path; the scheme separator
     * itself is never stripped.</p>
     *
     * @param raw configured endpoint, may be null
     * @return the repaired endpoint, or an empty string when nothing was configured
     */
    public static String normalize(String raw) {
        String value = folded(raw);
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("//")) {
            value = "https:" + value;
        } else if (!hasScheme(value)) {
            String host = authorityHost(value);
            value = (isLocalHost(host) ? "http://" : "https://") + value;
        }
        while (value.endsWith("/") && value.length() > 8) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * Classifies a configured endpoint with the default cleartext policy.
     *
     * @param raw configured endpoint, may be null
     * @return the first blocking problem, or {@link Problem#NONE}
     */
    public static Problem problem(String raw) {
        return problem(raw, false);
    }

    /**
     * Classifies a configured endpoint.
     *
     * <p>Validation runs on the normalised value, so an endpoint the user typed without a scheme
     * is not reported as malformed; only a value that stays unusable after repair is rejected.
     * Plain HTTP passes for local and LAN hosts; a public host passes only when
     * {@code allowAnyHttp} is set, which is the security setting the caller reads.</p>
     *
     * @param raw configured endpoint, may be null
     * @param allowAnyHttp true when the user allowed plain HTTP to any host
     * @return the first blocking problem, or {@link Problem#NONE}
     */
    public static Problem problem(String raw, boolean allowAnyHttp) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            return Problem.EMPTY;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '\\') {
                return Problem.MALFORMED;
            }
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException error) {
            return Problem.MALFORMED;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return Problem.MALFORMED;
        }
        if ("https".equals(scheme)) {
            return Problem.NONE;
        }
        if (!"http".equals(scheme)) {
            return Problem.UNSUPPORTED_SCHEME;
        }
        if (allowAnyHttp || isLocalHost(host)) {
            return Problem.NONE;
        }
        return Problem.CLEARTEXT;
    }

    /**
     * Describes a blocking problem for a failure report.
     *
     * @param problem classification returned by {@link #problem(String)}
     * @param raw configured endpoint, printed without credentials
     * @return one technical line naming the problem and the offending endpoint
     */
    public static String describe(Problem problem, String raw) {
        String endpoint = display(raw);
        switch (problem) {
            case EMPTY:
                return "missing base URL: set the provider endpoint in Models";
            case MALFORMED:
                return "malformed base URL: " + endpoint;
            case UNSUPPORTED_SCHEME:
                return "base URL must start with http:// or https://: " + endpoint;
            case CLEARTEXT:
                return "plain http base URL is only allowed for local or LAN hosts: " + endpoint
                        + " (enable the HTTP allowance in Security settings to use it)";
            case NONE:
            default:
                return "unsupported endpoint: " + endpoint;
        }
    }

    /**
     * Tests whether a cleartext HTTP host is local to the device or the local network.
     *
     * <p>Only literal addresses and recognised local host names are accepted; no DNS resolution
     * happens here, so validation never blocks on the network and a public host name cannot be
     * smuggled in through a resolver. This is the same classification the MCP client applies.</p>
     *
     * @param host host as returned by {@link URI#getHost()}, bracketed for IPv6 literals
     * @return true for loopback, private/LAN or link-local addresses and local host names
     */
    public static boolean isLocalHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String value = unbracketed(host);
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

    /** Removes the brackets {@link URI#getHost()} keeps around an IPv6 literal. */
    private static String unbracketed(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Folds the characters a CJK keyboard inserts into a pasted URL.
     *
     * <p>Full-width ASCII is mapped back to its ASCII form, the ideographic space becomes a plain
     * space and zero-width characters are dropped, so a URL that looks right on screen is not
     * rejected as malformed.</p>
     *
     * @param raw configured endpoint, may be null
     * @return trimmed text with full-width and invisible characters folded
     */
    private static String folded(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder folded = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (ch == '\u3000') {
                ch = ' ';
            } else if (ch >= '\uff01' && ch <= '\uff5e') {
                ch = (char) (ch - 0xfee0);
            }
            if (ch == '\u200b' || ch == '\u200c' || ch == '\u200d' || ch == '\ufeff') {
                continue;
            }
            folded.append(ch);
        }
        return folded.toString().trim();
    }

    /**
     * Tests whether a value already starts with a URI scheme.
     *
     * <p>{@code host:port} would otherwise look like a scheme named after the host, so a colon
     * followed by a digit is treated as a port separator instead.</p>
     *
     * @param value trimmed endpoint
     * @return true when a scheme prefix precedes the first colon
     */
    private static boolean hasScheme(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        for (int index = 0; index < colon; index++) {
            char ch = value.charAt(index);
            boolean letter = Character.isLetter(ch);
            boolean trailing = Character.isDigit(ch) || ch == '+' || ch == '-' || ch == '.';
            if (!letter && !(index > 0 && trailing)) {
                return false;
            }
        }
        return colon + 1 >= value.length() || !Character.isDigit(value.charAt(colon + 1));
    }

    /**
     * Extracts the host of a scheme-less endpoint.
     *
     * @param value endpoint without a scheme, e.g. {@code 127.0.0.1:8080/v1}
     * @return the host part, or an empty string when none can be isolated
     */
    private static String authorityHost(String value) {
        int end = value.length();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '/' || ch == '?' || ch == '#') {
                end = index;
                break;
            }
        }
        String authority = value.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            return close > 0 ? authority.substring(0, close + 1) : authority;
        }
        int colon = authority.indexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
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
