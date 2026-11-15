package cn.lineai.engine.ai;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import cn.lineai.core.model.ModelConfig;

/**
 * Human-readable, desensitized diagnostics for failed model HTTP requests.
 *
 * <p>A failure report carries the endpoint facts, every sent header, the request body, the
 * provider response body and the exception stack. Header values and obvious secret literals are
 * masked before formatting so the report can be logged or shown in the error log without leaking
 * credentials; every section is capped so one huge body cannot flood the log.</p>
 */
final class HttpDiagnostics {

    /** Maximum character count kept for each request or response body. */
    static final int BODY_LIMIT = 32 * 1024;

    /** Masked replacement for every secret header value or body literal. */
    static final String REDACTED = "<redacted>";

    /** Matches a bearer credential in a body or message. */
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/=\\-]{6,}");
    /** Matches an OpenAI-style secret key literal. */
    private static final Pattern SECRET_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{6,}");
    /** Matches a JSON member whose name marks the value as a credential. */
    private static final Pattern SECRET_JSON = Pattern.compile(
            "(?i)\"(api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|token"
                    + "|secret|password)\"\\s*:\\s*\"[^\"]*\"");

    /** Static utility; not instantiable. */
    private HttpDiagnostics() {
    }

    /** Immutable facts of one failed request, enough to describe where it went. */
    static final class Request {
        final ModelConfig.Protocol protocol;
        final String baseUrl;
        final String url;
        final String modelId;
        final String body;

        /**
         * Creates the request fact set.
         *
         * @param protocol wire protocol of the request
         * @param baseUrl provider base URL as configured
         * @param url resolved endpoint URL
         * @param modelId provider-side model identifier
         * @param body serialized request body, may be null or empty
         */
        Request(ModelConfig.Protocol protocol, String baseUrl, String url, String modelId,
                String body) {
            this.protocol = protocol;
            this.baseUrl = orEmpty(baseUrl);
            this.url = orEmpty(url);
            this.modelId = orEmpty(modelId);
            this.body = orEmpty(body);
        }
    }

    /**
     * Formats one failure report.
     *
     * @param request request facts, may be null
     * @param headers headers actually sent, may be null
     * @param status HTTP status line such as {@code HTTP 500}; empty when the failure was not
     *               a status rejection
     * @param responseBody provider response or error payload, may be null or empty
     * @param error causing failure; a synthetic exception describes a status rejection
     * @return multi-section human-readable report, never null
     */
    static String format(Request request, Map<String, String> headers, String status,
                         String responseBody, Throwable error) {
        StringBuilder report = new StringBuilder(1024);
        appendEndpoint(report, request, status);
        report.append("\nrequest headers:\n").append(headerLines(headers));
        report.append("\nrequest body:\n")
                .append(truncate(redactBody(request == null ? "" : request.body)));
        report.append("\nresponse body:\n").append(truncate(redactBody(responseBody)));
        report.append("\nstack:\n").append(stackTrace(error));
        return report.toString();
    }

    /** Appends the endpoint section; every line names the fact it carries. */
    private static void appendEndpoint(StringBuilder report, Request request, String status) {
        report.append("endpoint:");
        if (request == null) {
            report.append("\n  protocol: unknown\n  base url: unknown\n  url: unknown"
                    + "\n  model: unknown");
        } else {
            report.append("\n  protocol: ").append(request.protocol)
                    .append("\n  base url: ").append(request.baseUrl)
                    .append("\n  url: ").append(request.url)
                    .append("\n  model: ").append(request.modelId);
        }
        if (status != null && !status.trim().isEmpty()) {
            report.append("\n  status: ").append(status.trim());
        }
    }

    /**
     * Renders every sent header with its name visible and credential values masked.
     *
     * @param headers headers actually sent; null or empty yields a neutral marker
     * @return one indented line per header
     */
    static String headerLines(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "  (none)";
        }
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append("  ").append(header.getKey()).append(": ")
                    .append(isSecretHeader(header.getKey())
                            ? REDACTED : redactText(header.getValue()));
        }
        return lines.toString();
    }

    /**
     * Masks the credential headers while keeping their names readable.
     *
     * <p>Authorization, proxy authorization, API-key, cookie and set-cookie headers are always
     * masked; any other name containing token, secret, password or key is masked as well, except
     * harmless transport fields such as Content-Type that a later check would need to see.</p>
     *
     * @param name header name, matched case-insensitively
     * @return true when the value must be masked
     */
    static boolean isSecretHeader(String name) {
        String normalized = orEmpty(name).toLowerCase(Locale.ROOT);
        if ("content-type".equals(normalized) || "content-length".equals(normalized)) {
            return false;
        }
        if ("authorization".equals(normalized) || "proxy-authorization".equals(normalized)
                || "x-api-key".equals(normalized) || "api-key".equals(normalized)
                || "cookie".equals(normalized) || "set-cookie".equals(normalized)) {
            return true;
        }
        return normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("password") || normalized.contains("key");
    }

    /**
     * Redacts obvious secret literals from arbitrary text.
     *
     * @param value text to sanitize, null tolerated
     * @return text with bearer credentials, {@code sk-} keys and credential JSON values masked
     */
    static String redactText(String value) {
        String text = orEmpty(value);
        String outcome = BEARER.matcher(text).replaceAll("$1" + REDACTED);
        outcome = SECRET_KEY.matcher(outcome).replaceAll("sk-" + REDACTED);
        return SECRET_JSON.matcher(outcome)
                .replaceAll("\"$1\":\"" + REDACTED + "\"");
    }

    /** Redacts a request or response body; null becomes a neutral marker. */
    private static String redactBody(String body) {
        if (body == null || body.isEmpty()) {
            return "(empty)";
        }
        return redactText(body);
    }

    /**
     * Caps a body at {@link #BODY_LIMIT} characters.
     *
     * @param value text to cap, null tolerated
     * @return the original text, or its prefix plus a truncation marker
     */
    static String truncate(String value) {
        String text = orEmpty(value);
        if (text.length() <= BODY_LIMIT) {
            return text;
        }
        return text.substring(0, BODY_LIMIT) + "\n[truncated: " + (text.length() - BODY_LIMIT)
                + " characters omitted]";
    }

    /**
     * Renders the full stack trace of a failure.
     *
     * @param error causing failure, may be null
     * @return printable stack trace, or an empty string when there is none
     */
    static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter text = new StringWriter();
        try (PrintWriter writer = new PrintWriter(text)) {
            error.printStackTrace(writer);
        }
        return text.toString();
    }

    /** Null-safe trim. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
