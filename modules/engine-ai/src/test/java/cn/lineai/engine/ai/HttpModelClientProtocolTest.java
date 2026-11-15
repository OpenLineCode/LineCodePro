package cn.lineai.engine.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpModelClientProtocolTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> anthropicVersion = new AtomicReference<>();
    private final AtomicReference<String> originator = new AtomicReference<>();
    private final AtomicReference<String> modelHeader = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final List<String> failures = new ArrayList<>();
    private String sse;
    private int errorStatus = 200;
    private String errorBody = "";

    @Before public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange));
        server.start();
    }

    @After public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        path.set(exchange.getRequestURI().getPath());
        apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
        anthropicVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
        originator.set(exchange.getRequestHeaders().getFirst("originator"));
        modelHeader.set(exchange.getRequestHeaders().getFirst("X-Model"));
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        try (InputStream input = exchange.getRequestBody()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            body.set(output.toString(StandardCharsets.UTF_8.name()));
        }
        if (errorStatus != 200) {
            byte[] failure = errorBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(errorStatus, failure.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(failure);
            }
            return;
        }
        byte[] payload = sse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    @Test public void httpFailureKeepsTheProviderResponseBody() {
        errorStatus = 500;
        errorBody = "{\"error\":{\"message\":\"quota exceeded\"}}";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "k");
        assertEquals(1, result.failures.size());
        assertTrue(result.detail.contains("HTTP 500"));
        assertTrue(result.detail.contains("{\"error\":{\"message\":\"quota exceeded\"}}"));
    }

    @Test public void openAiProtocolPostsToChatCompletions() {
        sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "sk-openai");
        assertEquals("/v1/chat/completions", path.get());
        assertTrue(body.get().contains("\"messages\""));
        assertEquals("hi", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    @Test public void anthropicProtocolUsesMessagesEndpointAndHeaders() {
        sse = "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        Result result = stream(ModelConfig.Protocol.ANTHROPIC_MESSAGES, "sk-ant");
        assertEquals("/v1/messages", path.get());
        assertEquals("sk-ant", apiKeyHeader.get());
        assertEquals("2023-06-01", anthropicVersion.get());
        assertTrue(body.get().contains("\"system\":\"sys\""));
        assertTrue(body.get().contains("\"stream\":true"));
        assertEquals("hello", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    @Test public void anthropicThinkingDeltaGoesToReasoning() {
        sse = "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"think\"}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        Result result = stream(ModelConfig.Protocol.ANTHROPIC_MESSAGES, "k");
        assertEquals("think", result.reasoning.toString());
        assertTrue(result.text.length() == 0);
    }

    @Test public void codexProtocolUsesResponsesEndpoint() {
        sse = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hey\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}}\n\n";
        Result result = stream(ModelConfig.Protocol.CODEX_RESPONSES, "sk-codex");
        assertEquals("/v1/responses", path.get());
        assertEquals("codex_cli_rs", originator.get());
        assertTrue(body.get().contains("\"instructions\":\"sys\""));
        assertEquals("hey", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    @Test public void modelHeadersAreSentAndTransportHeadersAreProtected() {
        sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "k",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                java.util.Arrays.asList(new HttpHeader("X-Model", "model-value"),
                        new HttpHeader("content-type", "text/plain")));
        assertTrue(result.failures.isEmpty());
        assertEquals("model-value", modelHeader.get());
        assertEquals("application/json", contentType.get());
    }

    @Test public void statusFailureCarriesSanitizedDiagnostics() {
        errorStatus = 500;
        errorBody = "{\"error\":{\"message\":\"quota exceeded\"}}";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "sk-super-secret-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                java.util.Collections.singletonList(
                        new HttpHeader("X-Custom", "Bearer leaked-token")));
        assertEquals(1, result.failures.size());
        assertTrue(result.detail.contains("endpoint:"));
        assertTrue(result.detail.contains("protocol: OPENAI_COMPATIBLE"));
        assertTrue(result.detail.contains("request headers:"));
        assertTrue(result.detail.contains("Authorization: <redacted>"));
        assertTrue(result.detail.contains("X-Custom: Bearer <redacted>"));
        assertFalse(result.detail.contains("sk-super-secret-key"));
        assertFalse(result.detail.contains("leaked-token"));
        assertTrue(result.detail.contains("request body:"));
        assertTrue(result.detail.contains("\"model\":\"model\""));
        assertTrue(result.detail.contains("response body:"));
        assertTrue(result.detail.contains("quota exceeded"));
        assertTrue(result.detail.contains("HTTP 500"));
        assertTrue(result.detail.contains("stack:"));
    }

    @Test public void decoderFailureCarriesTheOffendingPayload() {
        sse = "data: not-json\n\n";
        Result result = stream(ModelConfig.Protocol.ANTHROPIC_MESSAGES, "k");
        assertEquals(1, result.failures.size());
        assertEquals("NETWORK", result.failures.get(0));
        assertTrue(result.detail.contains("response body:"));
        assertTrue(result.detail.contains("not-json"));
        assertTrue(result.detail.contains("stack:"));
    }

    @Test public void probeFailureCarriesSanitizedDiagnostics() {
        errorStatus = 401;
        errorBody = "{\"error\":{\"message\":\"invalid key\"}}";
        ModelConfig model = ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "http://127.0.0.1:" + server.getAddress().getPort()
                        + "/v1")
                .secret("sk-probe-secret")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .build();
        try {
            new ModelProbe().complete(model, "ping");
            fail("expected a DomainException");
        } catch (DomainException error) {
            assertEquals(cn.lineai.core.kernel.error.ErrorCode.NETWORK, error.code());
            assertTrue(error.detail().contains("request headers:"));
            assertTrue(error.detail().contains("Authorization: <redacted>"));
            assertFalse(error.detail().contains("sk-probe-secret"));
            assertTrue(error.detail().contains("request body:"));
            assertTrue(error.detail().contains("HTTP 401"));
            assertTrue(error.detail().contains("response body:"));
            assertTrue(error.detail().contains("invalid key"));
            assertTrue(error.detail().contains("stack:"));
        }
    }

    @Test public void visionFailureCarriesSanitizedDiagnostics() {
        errorStatus = 500;
        errorBody = "vision service down";
        ModelConfig model = ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "http://127.0.0.1:" + server.getAddress().getPort()
                        + "/v1")
                .secret("sk-vision-secret")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .build();
        try {
            new VisionClient().describe(model, "look", "/nonexistent/image.png");
            fail("expected an IOException");
        } catch (IOException error) {
            String message = error.getMessage();
            assertTrue(message.contains("request headers:"));
            assertTrue(message.contains("Authorization: <redacted>"));
            assertFalse(message.contains("sk-vision-secret"));
            assertTrue(message.contains("request body:"));
            assertTrue(message.contains("response body:"));
            assertTrue(message.contains("vision service down"));
            assertTrue(message.contains("stack:"));
        }
    }

    @Test public void imageGenerationFailureCarriesSanitizedDiagnostics() {
        errorStatus = 500;
        errorBody = "image service down";
        ModelConfig model = ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "http://127.0.0.1:" + server.getAddress().getPort()
                        + "/v1")
                .secret("sk-image-secret")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .build();
        try {
            new ImageGenerationClient().generate(model, "a cat");
            fail("expected an IOException");
        } catch (IOException error) {
            String message = error.getMessage();
            assertTrue(message.contains("request headers:"));
            assertTrue(message.contains("<redacted>"));
            assertFalse(message.contains("sk-image-secret"));
            assertTrue(message.contains("response body:"));
            assertTrue(message.contains("image service down"));
            assertTrue(message.contains("stack:"));
        }
    }

    @Test public void cleartextEndpointIsLimitedToExactLoopbackHosts() {
        assertTrue(HttpModelClient.isAllowed("http://localhost:8080/v1"));
        assertTrue(HttpModelClient.isAllowed("http://127.0.0.1/v1"));
        assertTrue(HttpModelClient.isAllowed("http://[::1]:8080/v1"));
        assertTrue(HttpModelClient.isAllowed("http://10.0.2.2:11434/v1"));
        assertTrue(HttpModelClient.isAllowed("https://api.example.com/v1"));
        assertFalse(HttpModelClient.isAllowed("http://localhost.example.com/v1"));
        assertFalse(HttpModelClient.isAllowed("http://127.0.0.1.example.com/v1"));
        assertFalse(HttpModelClient.isAllowed("http://10.0.2.2.example.com/v1"));
        assertFalse(HttpModelClient.isAllowed("http://192.168.1.10/v1"));
        assertFalse(HttpModelClient.isAllowed("ftp://localhost/v1"));
        assertFalse(HttpModelClient.isAllowed(""));
        assertFalse(HttpModelClient.isAllowed(null));
    }

    @Test public void cleartextLookalikeEndpointFailsBeforeConnecting() {
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "k",
                "http://localhost.example.com/v1");
        assertEquals(1, result.failures.size());
        assertEquals("INVALID_INPUT", result.failures.get(0));
        assertNull(path.get());
    }

    private Result stream(ModelConfig.Protocol protocol, String apiKey) {
        return stream(protocol, apiKey,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private Result stream(ModelConfig.Protocol protocol, String apiKey, String baseUrl) {
        return stream(protocol, apiKey, baseUrl, null);
    }

    private Result stream(ModelConfig.Protocol protocol, String apiKey, String baseUrl,
                          List<HttpHeader> headers) {
        ModelConfig.Builder builder = ModelConfig.builder()
                .identity("m", "M")
                .endpoint("p", "model", baseUrl)
                .secret(apiKey)
                .protocol(protocol);
        if (headers != null) {
            builder.advanced("", headers);
        }
        ModelConfig model = builder.build();
        ChatRequest request = ChatRequest.builder()
                .model("model")
                .system("sys")
                .messages(java.util.Collections.singletonList(
                        Message.builder().id("m1").conversationId("c").seq(1).role(Role.USER)
                                .addBlock(cn.lineai.core.model.ContentBlock.text("hello")).build()))
                .build();
        Result result = new Result();
        new HttpModelClient(Scheduler.immediate()).stream(model, request, new ModelClient.Listener() {
            public void onEvent(StreamEvent event) {
                result.text.append(event.text);
                result.reasoning.append(event.reasoning);
            }
            public void onComplete() { result.completed = true; }
            public void onError(DomainException error) {
                result.failures.add(error.code().name());
                result.detail = error.detail();
            }
        });
        return result;
    }

    private static final class Result {
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final List<String> failures = new ArrayList<>();
        String detail = "";
        boolean completed;
    }
}
