package cn.lineai.engine.ai;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.engine.protocol.SseParser;

/**
 * One JSON-RPC exchange over the MCP Streamable HTTP transport.
 *
 * <p>The transport owns framing only: it posts a serialized message, carries the session and
 * protocol-version headers once a session exists, and returns the JSON-RPC response taken from
 * either a plain JSON body or an event stream. The event stream is consumed until the matching
 * response arrives instead of until the socket closes, so servers that keep the stream open do
 * not stall the caller.</p>
 *
 * <p>Server-initiated requests (sampling, roots, elicitation) are not answered here; they are
 * skipped while the response to the posted request is awaited.</p>
 */
final class McpTransport {

    /** Response header carrying the server-issued session identifier. */
    static final String SESSION_HEADER = "Mcp-Session-Id";

    /** Required request header identifying the protocol revision in use. */
    static final String VERSION_HEADER = "MCP-Protocol-Version";

    /** Request header naming the JSON-RPC method, required by the stateless transport profile. */
    static final String METHOD_HEADER = "Mcp-Method";

    /** Request header naming the invoked tool, required for {@code tools/call}. */
    static final String NAME_HEADER = "Mcp-Name";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int DETAIL_LIMIT = 300;

    /** A server reply: the JSON-RPC message and the session identifier it introduced. */
    static final class Reply {
        final JSONObject message;
        final String sessionId;

        /**
         * Creates a reply.
         *
         * @param message decoded JSON-RPC message; null when the server accepted a notification
         * @param sessionId value of the session header; null when absent
         */
        Reply(JSONObject message, String sessionId) {
            this.message = message;
            this.sessionId = sessionId == null ? "" : sessionId;
        }
    }

    /** Non-2xx reply, carrying the status so callers can detect an expired session. */
    static final class HttpStatus extends IOException {

        private static final long serialVersionUID = 1L;

        /** HTTP status code returned by the server. */
        final int status;

        /**
         * Creates the failure.
         *
         * @param status HTTP status code
         * @param detail human-readable detail built from the error body
         */
        HttpStatus(int status, String detail) {
            super(detail);
            this.status = status;
        }
    }

    /**
     * Posts one JSON-RPC message.
     *
     * @param endpoint normalised endpoint URL
     * @param headers user-configured headers such as credentials; may be null
     * @param session negotiated session, or null for the initializing request
     * @param payload serialized JSON-RPC message
     * @return the decoded reply
     * @throws IOException on transport failure or a non-2xx status
     */
    Reply post(String endpoint, List<HttpHeader> headers, McpSession session, JSONObject payload)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            if (headers != null) {
                for (HttpHeader header : headers) {
                    if (!header.name.isEmpty()) {
                        connection.setRequestProperty(header.name, header.value);
                    }
                }
            }
            // Protocol headers are applied last so a configured header cannot desynchronise
            // framing from the negotiated session.
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            String method = payload.optString("method", "");
            if (!method.isEmpty()) {
                connection.setRequestProperty(METHOD_HEADER, method);
                JSONObject params = payload.optJSONObject("params");
                String name = params == null ? "" : params.optString("name", "");
                if ("tools/call".equals(method) && !name.isEmpty()) {
                    connection.setRequestProperty(NAME_HEADER, name);
                }
            }
            if (session != null) {
                if (session.hasId()) {
                    connection.setRequestProperty(SESSION_HEADER, session.id);
                }
                if (session.hasProtocolVersion()) {
                    connection.setRequestProperty(VERSION_HEADER, session.protocolVersion);
                }
            }
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatus(status, describe(status, read(connection.getErrorStream())));
            }
            if (status == 202 || status == 204) {
                // Notifications are acknowledged without a body; reading would block on servers
                // that keep the exchange open after the acknowledgement.
                return new Reply(null, connection.getHeaderField(SESSION_HEADER));
            }
            return new Reply(message(connection), connection.getHeaderField(SESSION_HEADER));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Decodes the reply body, choosing the event-stream or the plain-JSON form by content type. */
    private static JSONObject message(HttpURLConnection connection) throws IOException {
        String type = connection.getContentType();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("text/event-stream")) {
            return streamed(connection.getInputStream());
        }
        String body = read(connection.getInputStream());
        if (body.trim().isEmpty()) {
            return null;
        }
        JSONObject message = parse(body);
        if (message == null) {
            throw new IOException("MCP response is not JSON: " + snippet(body));
        }
        return message;
    }

    /**
     * Reads an event stream until the response to the posted request arrives.
     *
     * <p>The read stops at the first message carrying {@code result} or {@code error}; progress
     * notifications and server-initiated requests that arrive before it are skipped.</p>
     */
    private static JSONObject streamed(InputStream stream) throws IOException {
        SseParser parser = new SseParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject message = response(parser.feed(line));
                if (message != null) {
                    return message;
                }
            }
        }
        return response(parser.finish());
    }

    /** Returns the first completed payload that is a JSON-RPC response. */
    private static JSONObject response(List<String> payloads) {
        for (String payload : payloads) {
            JSONObject message = parse(payload);
            if (message != null && (message.has("result") || message.has("error"))) {
                return message;
            }
        }
        return null;
    }

    /** Parses one payload, returning null for keep-alive comments and partial frames. */
    private static JSONObject parse(String payload) {
        try {
            return new JSONObject(payload);
        } catch (JSONException error) {
            return null;
        }
    }

    /** Builds a failure message that prefers the JSON-RPC error text over the raw body. */
    private static String describe(int status, String body) {
        String text = body == null ? "" : body.trim();
        String prefix = "MCP HTTP " + status;
        if (text.isEmpty()) {
            return prefix;
        }
        JSONObject parsed = parse(text);
        JSONObject error = parsed == null ? null : parsed.optJSONObject("error");
        if (error == null) {
            return prefix + ": " + snippet(text);
        }
        return prefix + ": " + snippet(error.optString("message", text));
    }

    /** Caps a body fragment so transport failures stay readable in a toast. */
    private static String snippet(String text) {
        return text.length() <= DETAIL_LIMIT ? text : text.substring(0, DETAIL_LIMIT);
    }

    /** Reads a UTF-8 response body; a null stream yields the empty string. */
    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                out.append(buffer, 0, count);
            }
        }
        return out.toString();
    }
}
