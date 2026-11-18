package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import cn.lineai.core.kernel.error.FailureReporter;
import cn.lineai.core.kernel.error.FailureText;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.McpToolInfo;

/**
 * MCP client over the Streamable HTTP transport: session initialization, {@code tools/list}
 * discovery and {@code tools/call} execution.
 *
 * <p>Every endpoint is used through a negotiated session. The client posts {@code initialize}
 * with the newest protocol revision it supports, keeps the {@code Mcp-Session-Id} and the
 * revision the server answered with, then completes the handshake with
 * {@code notifications/initialized} before the first request. Later requests replay both values;
 * a server that reports the session as gone (HTTP 404, or a 400 naming the session) is
 * re-initialized once and the request is retried, which is what the transport requires after a
 * server restart or session expiry.</p>
 *
 * <p>Responses are accepted either as plain JSON or as an event stream, and {@code tools/list}
 * follows {@code nextCursor} so servers that page their catalogue are read completely. Endpoints
 * that answer no handshake are used through the stateless profile: the revision, client identity
 * and capabilities travel in per-request metadata instead of a session.</p>
 */
public final class McpClient {

    /** Protocol revisions offered during initialization, newest first. */
    private static final List<String> PROTOCOL_VERSIONS = Collections.unmodifiableList(
            Arrays.asList("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"));

    private static final String CLIENT_NAME = "LineCode";
    private static final String CLIENT_VERSION = "1.0";
    private static final String ERROR_PREFIX = "MCP error: ";
    private static final int MAX_TOOL_PAGES = 20;
    private static final int UNSUPPORTED_PROTOCOL = -32602;
    private static final int UNKNOWN_METHOD = -32601;

    /** Revision used for endpoints that answer no handshake, which carry state in metadata. */
    private static final String STATELESS_VERSION = "2026-07-28";

    private final McpTransport transport = new McpTransport();
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final FailureReporter failures;

    /** Creates a client whose failures are thrown but not recorded. */
    public McpClient() {
        this(FailureReporter.NONE);
    }

    /**
     * Creates a client that records every failed handshake, discovery or tool call.
     *
     * @param failures sink receiving one entry per failed operation; null disables recording
     */
    public McpClient(FailureReporter failures) {
        this.failures = failures == null ? FailureReporter.NONE : failures;
    }

    /**
     * Validates and normalises an MCP endpoint URL.
     *
     * <p>HTTPS is mandatory for public hosts. Plain HTTP is allowed for loopback and emulator
     * hosts and for private/LAN addresses ({@code 10/8}, {@code 127/8}, {@code 169.254/16},
     * {@code 172.16/12}, {@code 192.168/16}, IPv6 loopback, IPv6 ULA and IPv6 link-local), plus
     * {@code localhost} and {@code *.local} names. This keeps MCP traffic and credentials off
     * plaintext transport on public networks while local MCP servers remain reachable.</p>
     *
     * @param raw candidate URL
     * @return trimmed URL without trailing slashes
     * @throws IllegalArgumentException when the URL is not absolute HTTPS, or HTTP on a host
     *         that is not loopback or a private/LAN address
     */
    public static String normalizeUrl(String raw) {
        return McpEndpoint.normalize(raw);
    }

    /**
     * Extracts tool descriptors from a {@code tools/list} response.
     *
     * <p>Accepts the {@code result.tools} shape defined by the specification and a top-level
     * {@code tools} array used by gateways that unwrap the JSON-RPC envelope.</p>
     *
     * @param body raw response body, plain JSON or an event stream
     * @return tool descriptors with name, description and input schema JSON
     * @throws org.json.JSONException when the body is not JSON at all
     */
    public static List<McpToolInfo> parseTools(String body) {
        return tools(new JSONObject(jsonPayload(body)));
    }

    /**
     * Extracts human-readable text from a {@code tools/call} response.
     *
     * @param body raw response body
     * @return joined text blocks, the serialized result when no text blocks exist, or a prefixed
     *         message when the response carries a JSON-RPC error or a failed tool result
     * @throws org.json.JSONException when the body is not JSON at all
     */
    public static String toolResultText(String body) {
        return resultText(new JSONObject(jsonPayload(body)));
    }

    /**
     * Discovers the tools exposed by an MCP server.
     *
     * <p>Initializes the endpoint when no live session is cached and follows
     * {@code nextCursor} until the server stops paging.</p>
     *
     * @param url MCP endpoint URL, validated by {@link #normalizeUrl(String)}
     * @param headers authentication headers to send
     * @return discovered tools in server order
     * @throws IOException on transport failure, a JSON-RPC error or non-2xx status
     */
    public List<McpToolInfo> listTools(String url, List<HttpHeader> headers) throws IOException {
        String endpoint = normalizeUrl(url);
        try {
            return readTools(endpoint, headers);
        } catch (IOException error) {
            report(endpoint, "tools/list", error);
            throw error;
        }
    }

    /**
     * Invokes one MCP tool.
     *
     * @param url MCP endpoint URL, validated by {@link #normalizeUrl(String)}
     * @param headers authentication headers to send
     * @param toolName tool to invoke
     * @param argumentsJson JSON object of tool arguments; blank yields an empty object
     * @return result text as produced by {@link #toolResultText(String)}
     * @throws IOException on transport failure, a protocol error or malformed arguments
     */
    public String callTool(String url, List<HttpHeader> headers, String toolName,
                           String argumentsJson) throws IOException {
        String endpoint = normalizeUrl(url);
        try {
            JSONObject params = new JSONObject();
            params.put("name", toolName);
            params.put("arguments", arguments(argumentsJson));
            return resultText(exchange(endpoint, headers, "tools/call", params));
        } catch (IOException error) {
            report(endpoint, "tools/call " + toolName, error);
            throw error;
        }
    }

    /** Reads every page of the tool catalogue; failures are reported by the caller. */
    private List<McpToolInfo> readTools(String endpoint, List<HttpHeader> headers)
            throws IOException {
        List<McpToolInfo> found = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < MAX_TOOL_PAGES; page++) {
            JSONObject params = new JSONObject();
            if (!cursor.isEmpty()) {
                params.put("cursor", cursor);
            }
            JSONObject message = exchange(endpoint, headers, "tools/list", params);
            String error = errorText(message);
            if (!error.isEmpty()) {
                throw new IOException(error);
            }
            found.addAll(tools(message));
            cursor = cursor(message);
            if (cursor.isEmpty()) {
                break;
            }
        }
        return found;
    }

    /**
     * Posts one request on the cached session, re-initializing once when the server dropped it.
     *
     * @param endpoint normalised endpoint URL
     * @param headers authentication headers to send
     * @param method JSON-RPC method name
     * @param params method parameters
     * @return the JSON-RPC message the server answered with; null for accepted notifications
     * @throws IOException on transport failure or a non-retryable status
     */
    private JSONObject exchange(String endpoint, List<HttpHeader> headers, String method,
                                JSONObject params) throws IOException {
        String key = key(endpoint, headers);
        McpSession session = session(key, endpoint, headers);
        try {
            return transport.post(endpoint, headers, session,
                    request(method, params, session)).message;
        } catch (McpTransport.HttpStatus error) {
            if (session.stateless || !expired(error)) {
                throw error;
            }
            sessions.remove(key);
            McpSession renewed = session(key, endpoint, headers);
            return transport.post(endpoint, headers, renewed,
                    request(method, params, renewed)).message;
        }
    }

    /** Returns the cached session for an endpoint, initializing it on first use. */
    private McpSession session(String key, String endpoint, List<HttpHeader> headers)
            throws IOException {
        McpSession cached = sessions.get(key);
        if (cached != null) {
            return cached;
        }
        McpSession created = handshake(endpoint, headers);
        sessions.put(key, created);
        return created;
    }

    /**
     * Negotiates a session by posting {@code initialize} and completing the handshake.
     *
     * <p>A server that does not know the offered revision answers with an error; the next
     * revision in {@link #PROTOCOL_VERSIONS} is then tried so older deployments stay usable. A
     * server that does not implement the handshake at all - an unknown-method error or HTTP 405
     * - is used through the stateless profile instead.</p>
     *
     * @param endpoint normalised endpoint URL
     * @param headers authentication headers to send
     * @return the negotiated session
     * @throws IOException when every offered revision is refused or the transport fails
     */
    private McpSession handshake(String endpoint, List<HttpHeader> headers) throws IOException {
        IOException failure = null;
        for (String version : PROTOCOL_VERSIONS) {
            JSONObject params = new JSONObject();
            params.put("protocolVersion", version);
            params.put("capabilities", new JSONObject());
            params.put("clientInfo", clientInfo());
            try {
                McpTransport.Reply reply = transport.post(endpoint, headers, null,
                        request("initialize", params, null));
                JSONObject result = reply.message == null
                        ? null : reply.message.optJSONObject("result");
                if (result == null) {
                    if (unknownMethod(reply.message)) {
                        // A server that does not implement initialize speaks the stateless
                        // profile: requests carry the revision in metadata instead.
                        return McpSession.stateless(STATELESS_VERSION);
                    }
                    if (unsupportedProtocol(reply.message)) {
                        failure = new IOException(
                                "MCP server rejected protocol version " + version);
                        continue;
                    }
                    String error = errorText(reply.message);
                    throw new IOException(error.isEmpty()
                            ? "MCP initialize returned no result" : error);
                }
                McpSession session = new McpSession(reply.sessionId,
                        result.optString("protocolVersion", version));
                confirm(endpoint, headers, session);
                return session;
            } catch (McpTransport.HttpStatus error) {
                if (error.status == 405 || error.status == 501) {
                    return McpSession.stateless(STATELESS_VERSION);
                }
                if (!retryableVersion(error.status)) {
                    throw error;
                }
                failure = error;
            }
        }
        throw failure == null ? new IOException("MCP initialize failed") : failure;
    }

    /** Sends {@code notifications/initialized}, which completes the handshake. */
    private void confirm(String endpoint, List<HttpHeader> headers, McpSession session)
            throws IOException {
        JSONObject notification = new JSONObject();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        try {
            transport.post(endpoint, headers, session, notification);
        } catch (McpTransport.HttpStatus error) {
            if (error.status >= 500) {
                throw error;
            }
            // A server that rejects the completion notification still owns a usable session;
            // the next request reports the real protocol failure if there is one.
        }
    }

    /**
     * Builds a JSON-RPC request with a session-unique identifier.
     *
     * @param method JSON-RPC method name
     * @param params method parameters; may be null
     * @param session session in effect, or null for the initializing request
     * @return the serialized request
     */
    private JSONObject request(String method, JSONObject params, McpSession session) {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", "linecode_" + sequence.incrementAndGet());
        request.put("method", method);
        if (params != null) {
            if (session != null && session.stateless) {
                params.put("_meta", metadata());
            }
            request.put("params", params);
        }
        return request;
    }

    /** Builds the per-request metadata the stateless profile carries instead of a handshake. */
    private static JSONObject metadata() {
        JSONObject meta = new JSONObject();
        meta.put("io.modelcontextprotocol/protocolVersion", STATELESS_VERSION);
        meta.put("io.modelcontextprotocol/clientCapabilities", new JSONObject());
        meta.put("io.modelcontextprotocol/clientInfo", clientInfo());
        return meta;
    }

    /** Builds the client identity block shared by the handshake and the metadata profile. */
    private static JSONObject clientInfo() {
        JSONObject info = new JSONObject();
        info.put("name", CLIENT_NAME);
        info.put("version", CLIENT_VERSION);
        return info;
    }

    /** Records one failed operation, naming the endpoint without its credentials. */
    private void report(String endpoint, String operation, IOException error) {
        failures.record(McpEndpoint.display(endpoint),
                operation + ": " + FailureText.message(error),
                FailureText.stack(error));
    }

    /** Builds the cache key from the endpoint and the configured headers. */
    private static String key(String endpoint, List<HttpHeader> headers) {
        StringBuilder key = new StringBuilder(endpoint);
        if (headers == null || headers.isEmpty()) {
            return key.toString();
        }
        List<String> parts = new ArrayList<>();
        for (HttpHeader header : headers) {
            parts.add(header.name.toLowerCase(Locale.ROOT) + ':' + header.value);
        }
        Collections.sort(parts);
        for (String part : parts) {
            key.append('\n').append(part);
        }
        return key.toString();
    }

    /** Tests whether a failure means the server no longer knows the session. */
    private static boolean expired(McpTransport.HttpStatus error) {
        if (error.status == 404) {
            return true;
        }
        String detail = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return error.status == 400 && detail.contains("session");
    }

    /** Tests whether a status means the protocol revision was refused. */
    private static boolean retryableVersion(int status) {
        return status == 400 || status == 406 || status == 415;
    }

    /** Tests whether a JSON-RPC error reports an unsupported protocol revision. */
    private static boolean unsupportedProtocol(JSONObject message) {
        return errorCode(message) == UNSUPPORTED_PROTOCOL;
    }

    /** Tests whether a JSON-RPC error reports the handshake as an unknown method. */
    private static boolean unknownMethod(JSONObject message) {
        return errorCode(message) == UNKNOWN_METHOD;
    }

    /** Returns the JSON-RPC error code of a reply, or zero when it carries no error. */
    private static int errorCode(JSONObject message) {
        JSONObject error = message == null ? null : message.optJSONObject("error");
        return error == null ? 0 : error.optInt("code", 0);
    }

    /** Extracts the tool array from a {@code tools/list} reply. */
    private static List<McpToolInfo> tools(JSONObject message) {
        List<McpToolInfo> result = new ArrayList<>();
        if (message == null) {
            return result;
        }
        JSONObject holder = message.optJSONObject("result");
        JSONArray tools = holder == null ? null : holder.optJSONArray("tools");
        if (tools == null) {
            tools = message.optJSONArray("tools");
        }
        if (tools == null) {
            return result;
        }
        for (int index = 0; index < tools.length(); index++) {
            JSONObject tool = tools.optJSONObject(index);
            if (tool == null) {
                continue;
            }
            String name = tool.optString("name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            JSONObject schema = tool.optJSONObject("inputSchema");
            result.add(new McpToolInfo(name, true, tool.optString("description", ""),
                    schema == null ? "{}" : schema.toString()));
        }
        return result;
    }

    /** Returns the next page token of a {@code tools/list} reply, empty when complete. */
    private static String cursor(JSONObject message) {
        JSONObject holder = message == null ? null : message.optJSONObject("result");
        return holder == null ? "" : holder.optString("nextCursor", "");
    }

    /** Extracts a JSON-RPC error as display text, empty when the reply carries none. */
    private static String errorText(JSONObject message) {
        JSONObject error = message == null ? null : message.optJSONObject("error");
        if (error == null) {
            return "";
        }
        return ERROR_PREFIX + error.optString("message", error.toString());
    }

    /** Turns a {@code tools/call} reply into text, flagging protocol and tool failures. */
    private static String resultText(JSONObject message) {
        String error = errorText(message);
        if (!error.isEmpty()) {
            return error;
        }
        JSONObject result = message == null ? null : message.optJSONObject("result");
        if (result == null) {
            return message == null ? "" : message.toString();
        }
        String text = contentText(result);
        return result.optBoolean("isError", false) ? ERROR_PREFIX + text : text;
    }

    /** Joins the text blocks of a result, falling back to the serialized result. */
    private static String contentText(JSONObject result) {
        JSONArray content = result.optJSONArray("content");
        if (content == null) {
            return result.toString();
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < content.length(); index++) {
            JSONObject item = content.optJSONObject(index);
            String value = item == null ? "" : item.optString("text", "");
            if (value.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(value);
        }
        return text.length() == 0 ? result.toString() : text.toString();
    }

    /** Parses tool arguments, rejecting anything that is not a JSON object. */
    private static JSONObject arguments(String argumentsJson) throws IOException {
        String text = argumentsJson == null || argumentsJson.trim().isEmpty()
                ? "{}" : argumentsJson;
        try {
            return new JSONObject(text);
        } catch (JSONException error) {
            throw new IOException("MCP tool arguments are not a JSON object", error);
        }
    }

    /** Extracts the JSON object from a body that may be plain JSON or an event stream. */
    private static String jsonPayload(String body) {
        String text = body == null ? "" : body.trim();
        if (text.startsWith("{") || text.startsWith("[")) {
            return text;
        }
        int start = text.lastIndexOf("data:");
        if (start >= 0) {
            String candidate = text.substring(start + "data:".length()).trim();
            int end = candidate.indexOf('\n');
            if (end >= 0) {
                candidate = candidate.substring(0, end).trim();
            }
            if (candidate.startsWith("{")) {
                return candidate;
            }
        }
        return text;
    }
}
