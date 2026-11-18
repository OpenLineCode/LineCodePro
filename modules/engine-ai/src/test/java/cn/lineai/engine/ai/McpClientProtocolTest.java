package cn.lineai.engine.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.McpToolInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Protocol conformance of {@link McpClient} against a server that enforces the Streamable HTTP
 * rules: initialization before any request, session and protocol-version headers afterwards,
 * acknowledgement of the completion notification, paged tool catalogues and event-stream replies.
 * The stateless profile and failure recording are covered as well.
 */
public class McpClientProtocolTest {

    private static final String SESSION = "sess-42";
    private static final String STATELESS = "2026-07-28";

    private final List<String> methods = new CopyOnWriteArrayList<>();
    private final List<String> sessions = new CopyOnWriteArrayList<>();
    private final List<String> versions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean statelessOnly = new AtomicBoolean();
    private final AtomicInteger restarts = new AtomicInteger();
    private final AtomicReference<String> newestSupported = new AtomicReference<>("");
    private HttpServer server;

    @Before public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    @After public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test public void completesHandshakeAndFollowsToolPagination() throws IOException {
        List<McpToolInfo> tools = new McpClient().listTools(url(), headers());

        assertEquals(Arrays.asList("initialize", "notifications/initialized",
                "tools/list", "tools/list"), methods);
        assertEquals(Arrays.asList("", SESSION, SESSION, SESSION), sessions);
        assertEquals(Arrays.asList("", "2025-11-25", "2025-11-25", "2025-11-25"), versions);
        assertEquals(2, tools.size());
        assertEquals("alpha", tools.get(0).name);
        assertEquals("beta", tools.get(1).name);
        assertTrue(tools.get(1).inputSchemaJson.contains("object"));
    }

    @Test public void readsEventStreamAndFlagsFailingToolResults() throws IOException {
        McpClient client = new McpClient();

        assertEquals("pong", client.callTool(url(), headers(), "ping", "{}"));
        assertTrue(client.callTool(url(), headers(), "boom", "{}").startsWith("MCP error"));
        assertEquals(1, Collections.frequency(methods, "initialize"));
    }

    @Test public void negotiatesOlderProtocolWhenNewestIsRefused() throws IOException {
        newestSupported.set("2025-06-18");

        List<McpToolInfo> tools = new McpClient().listTools(url(), headers());

        assertEquals(2, tools.size());
        assertEquals(2, Collections.frequency(methods, "initialize"));
        assertEquals("2025-06-18", versions.get(versions.size() - 1));
    }

    @Test public void reinitializesAfterServerDropsSession() throws IOException {
        McpClient client = new McpClient();

        assertEquals("pong", client.callTool(url(), headers(), "restart", "{}"));
        assertEquals(2, Collections.frequency(methods, "initialize"));
        assertEquals(2, Collections.frequency(methods, "notifications/initialized"));
    }

    @Test public void usesStatelessProfileWhenHandshakeIsRejected() throws IOException {
        statelessOnly.set(true);
        McpClient client = new McpClient();

        List<McpToolInfo> tools = client.listTools(url(), headers());
        assertEquals(2, tools.size());
        assertEquals("pong", client.callTool(url(), headers(), "ping", "{}"));

        assertEquals(1, Collections.frequency(methods, "initialize"));
        assertEquals(0, Collections.frequency(methods, "notifications/initialized"));
        assertEquals(Arrays.asList("", STATELESS, STATELESS, STATELESS), versions);
    }

    @Test public void recordsFailedConnectionsWithoutLeakingQuerySecrets() {
        List<String> recorded = new ArrayList<>();
        McpClient client = new McpClient((title, subtitle, detail) ->
                recorded.add(title + " | " + subtitle));
        try {
            client.listTools("http://127.0.0.1:1/mcp?token=secret", headers());
            fail("expected a transport failure");
        } catch (IOException expected) {
            assertEquals(1, recorded.size());
            assertFalse(recorded.get(0).contains("secret"));
            assertTrue(recorded.get(0).contains("tools/list"));
        }
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    private static List<HttpHeader> headers() {
        return new ArrayList<>();
    }

    private void handle(HttpExchange exchange) throws IOException {
        JSONObject request;
        try (InputStream in = exchange.getRequestBody()) {
            request = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        }
        String method = request.optString("method", "");
        String session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        methods.add(method);
        sessions.add(session == null ? "" : session);
        versions.add(version == null ? "" : version);
        dispatch(exchange, request, method, session == null ? "" : session, version == null
                ? "" : version);
    }

    private void dispatch(HttpExchange exchange, JSONObject request, String method, String session,
                          String version) throws IOException {
        if ("initialize".equals(method)) {
            if (statelessOnly.get()) {
                respond(exchange, 200, error(request, -32601, "Method not found"));
                return;
            }
            JSONObject params = request.optJSONObject("params");
            String requested = params == null ? "" : params.optString("protocolVersion", "");
            String supported = newestSupported.get();
            if (!supported.isEmpty() && requested.compareTo(supported) > 0) {
                respond(exchange, 400, error(request, -32602, "Unsupported protocol version"));
                return;
            }
            JSONObject result = new JSONObject();
            result.put("protocolVersion", requested);
            result.put("capabilities", new JSONObject());
            exchange.getResponseHeaders().add("Mcp-Session-Id", SESSION);
            respond(exchange, 200, reply(request, result));
            return;
        }
        if ("notifications/initialized".equals(method)) {
            initialized.set(true);
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        if (statelessOnly.get()) {
            if (!statelessRequest(exchange, request, method)) {
                respond(exchange, 200, error(request, -32600, "Missing protocol metadata"));
                return;
            }
        } else {
            if (!SESSION.equals(session)) {
                respond(exchange, 400, error(request, -32600, "Bad Request: Missing session ID"));
                return;
            }
            if (version.isEmpty()) {
                respond(exchange, 400,
                        error(request, -32600, "Missing MCP-Protocol-Version header"));
                return;
            }
            if (!initialized.get()) {
                respond(exchange, 400, error(request, -32600, "Session not initialized"));
                return;
            }
        }
        JSONObject params = request.optJSONObject("params");
        if ("tools/list".equals(method)) {
            listTools(exchange, request, params == null ? "" : params.optString("cursor", ""));
            return;
        }
        if ("tools/call".equals(method)) {
            callTool(exchange, request, params == null ? "" : params.optString("name", ""));
            return;
        }
        respond(exchange, 200, error(request, -32601, "Method not found"));
    }

    /** Checks the metadata and routing headers the stateless profile requires on every request. */
    private boolean statelessRequest(HttpExchange exchange, JSONObject request, String method) {
        String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        String routed = exchange.getRequestHeaders().getFirst("Mcp-Method");
        JSONObject params = request.optJSONObject("params");
        JSONObject meta = params == null ? null : params.optJSONObject("_meta");
        String declared = meta == null
                ? "" : meta.optString("io.modelcontextprotocol/protocolVersion", "");
        if (!STATELESS.equals(version) || !STATELESS.equals(declared) || !method.equals(routed)) {
            return false;
        }
        if ("tools/call".equals(method)) {
            String name = exchange.getRequestHeaders().getFirst("Mcp-Name");
            return name != null && !name.isEmpty();
        }
        return true;
    }

    private void listTools(HttpExchange exchange, JSONObject request, String cursor)
            throws IOException {
        JSONArray tools = new JSONArray();
        JSONObject result = new JSONObject();
        if (cursor.isEmpty()) {
            tools.put(tool("alpha", "First tool"));
            result.put("nextCursor", "page-2");
        } else {
            tools.put(tool("beta", "Second tool"));
        }
        result.put("tools", tools);
        respond(exchange, 200, reply(request, result));
    }

    private void callTool(HttpExchange exchange, JSONObject request, String name)
            throws IOException {
        if ("restart".equals(name) && restarts.getAndIncrement() == 0) {
            initialized.set(false);
            respond(exchange, 404, error(request, -32000, "Session terminated"));
            return;
        }
        if ("boom".equals(name)) {
            respond(exchange, 200, reply(request, failure("tool failed")));
            return;
        }
        stream(exchange, reply(request, text("pong")).toString());
    }

    private static JSONObject tool(String name, String description) {
        JSONObject tool = new JSONObject();
        tool.put("name", name);
        tool.put("description", description);
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", new JSONObject());
        tool.put("inputSchema", schema);
        return tool;
    }

    private static JSONObject text(String value) {
        return content(value, false);
    }

    private static JSONObject failure(String value) {
        return content(value, true);
    }

    private static JSONObject content(String value, boolean isError) {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", value);
        JSONArray blocks = new JSONArray();
        blocks.put(item);
        JSONObject result = new JSONObject();
        result.put("content", blocks);
        result.put("isError", isError);
        return result;
    }

    private static JSONObject reply(JSONObject request, JSONObject result) {
        JSONObject message = new JSONObject();
        message.put("jsonrpc", "2.0");
        message.put("id", request.opt("id"));
        message.put("result", result);
        return message;
    }

    private static JSONObject error(JSONObject request, int code, String detail) {
        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("message", detail);
        JSONObject message = new JSONObject();
        message.put("jsonrpc", "2.0");
        message.put("id", request.opt("id"));
        message.put("error", error);
        return message;
    }

    private static void respond(HttpExchange exchange, int status, JSONObject body)
            throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Emits a progress notification before the response so the reader must skip non-results. */
    private static void stream(HttpExchange exchange, String message) throws IOException {
        String progress = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}";
        byte[] bytes = ("event: message\ndata: " + progress + "\n\n"
                + "event: message\ndata: " + message + "\n\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
