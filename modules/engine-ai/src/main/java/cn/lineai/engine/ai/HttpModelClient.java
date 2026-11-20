package cn.lineai.engine.ai;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.EndpointUrl;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.protocol.AnthropicCodec;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.CodexCodec;
import cn.lineai.engine.protocol.OpenAiChatCodec;
import cn.lineai.engine.protocol.SseParser;
import cn.lineai.engine.protocol.StreamDecoder;
import cn.lineai.engine.protocol.StreamEvent;

/**
 * Blocking HTTP/SSE transport executed on the injected IO scheduler. The wire format is chosen by
 * the model protocol (OpenAI chat, Anthropic Messages, Codex Responses).
 *
 * <p>There is no retry: a failed request terminates the stream with a single error callback.
 * Cancellation disconnects the underlying connection so blocked reads fail promptly, and the
 * error is then reported as {@link ErrorCode#CANCELLED} rather than as a network failure.</p>
 */
public final class HttpModelClient implements ModelClient {

    /** Resolved request: endpoint URL, serialized body, headers and the response decoder. */
    private static final class Plan {
        final String url;
        final String body;
        final Map<String, String> headers;
        final StreamDecoder decoder;

        Plan(String url, String body, Map<String, String> headers,
             StreamDecoder decoder) {
            this.url = url;
            this.body = body;
            this.headers = headers;
            this.decoder = decoder;
        }
    }

    private final Scheduler scheduler;
    private final BooleanSupplier allowAnyHttp;

    /**
     * Creates a client over the given scheduler with plain HTTP limited to local and LAN hosts.
     *
     * @param scheduler scheduler that runs the blocking transport off the caller's thread
     */
    public HttpModelClient(Scheduler scheduler) {
        this(scheduler, null);
    }

    /**
     * Creates a client over the given scheduler.
     *
     * @param scheduler scheduler that runs the blocking transport off the caller's thread
     * @param allowAnyHttp live source of the security setting that permits plain HTTP to a
     *     public host; null keeps the default of local and LAN hosts only
     */
    public HttpModelClient(Scheduler scheduler, BooleanSupplier allowAnyHttp) {
        this.scheduler = scheduler;
        this.allowAnyHttp = allowAnyHttp == null ? () -> false : allowAnyHttp;
    }

    /**
     * Starts a streaming completion on the IO scheduler.
     *
     * @param model model configuration selecting protocol, endpoint and credentials
     * @param request normalized completion request
     * @param listener callback sink; invoked on the IO thread
     * @return handle that disconnects the in-flight connection
     */
    @Override
    public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
        final Connection connection = new Connection();
        scheduler.io(() -> run(model, request, listener, connection));
        return connection;
    }

    /**
     * Executes the request and drives the listener to its terminal callback.
     *
     * <p>An unusable base URL fails immediately as {@link ErrorCode#INVALID_INPUT} with the
     * offending endpoint and the reason in the detail, because retrying or re-wording it as a
     * network failure hides the one thing the user has to change. HTTP 401/403 surface as
     * {@link ErrorCode#AUTH}; other non-2xx statuses and transport failures surface as
     * {@link ErrorCode#NETWORK}; a cancelled connection surfaces as {@link ErrorCode#CANCELLED}.
     * Decoder failures abort the stream after one error callback.</p>
     */
    private void run(ModelConfig model, ChatRequest request, Listener listener, Connection holder) {
        String baseUrl = EndpointUrl.normalize(model.baseUrl);
        EndpointUrl.Problem problem = EndpointUrl.problem(baseUrl, allowAnyHttp.getAsBoolean());
        if (problem != EndpointUrl.Problem.NONE) {
            listener.onError(new DomainException(ErrorCode.INVALID_INPUT,
                    EndpointUrl.describe(problem, baseUrl)));
            return;
        }
        Plan plan = plan(model, request, baseUrl);
        HttpDiagnostics.Request diagnostic = new HttpDiagnostics.Request(model.protocol,
                baseUrl, plan.url, model.modelId, plan.body);
        Map<String, String> sentHeaders = sentHeaders(plan.headers);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(plan.url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(180_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            for (Map.Entry<String, String> header : plan.headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            holder.attach(connection);
            byte[] body = plan.body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String errorPayload = readError(connection);
                String statusLine = "HTTP " + status;
                String detail = HttpDiagnostics.format(diagnostic, sentHeaders, statusLine,
                        errorPayload, new IllegalStateException(statusLine));
                listener.onError(new DomainException(status == 401 || status == 403
                        ? ErrorCode.AUTH : ErrorCode.NETWORK, detail));
                return;
            }
            if (!read(connection.getInputStream(), listener, holder, plan.decoder, diagnostic,
                    sentHeaders)) {
                return;
            }
            listener.onComplete();
        } catch (IOException error) {
            if (holder.isCancelled()) {
                listener.onError(new DomainException(ErrorCode.CANCELLED, "connection closed"));
            } else {
                listener.onError(new DomainException(ErrorCode.NETWORK,
                        HttpDiagnostics.format(diagnostic, sentHeaders, "", "", error), error));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Selects endpoint, headers, body and decoder for the model protocol. */
    private static Plan plan(ModelConfig model, ChatRequest request, String baseUrl) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/event-stream");
        String url;
        String body;
        StreamDecoder decoder;
        switch (model.protocol) {
            case ANTHROPIC_MESSAGES:
                headers.put("x-api-key", model.apiKey);
                headers.put("anthropic-version", "2023-06-01");
                url = AnthropicCodec.messagesEndpoint(baseUrl);
                body = AnthropicCodec.requestBody(request);
                decoder = AnthropicCodec.decoder();
                break;
            case CODEX_RESPONSES:
                headers.put("Authorization", "Bearer " + model.apiKey);
                headers.put("version", CodexCodec.PROTOCOL_VERSION);
                headers.put("originator", CodexCodec.ORIGINATOR);
                headers.put("User-Agent", CodexCodec.ORIGINATOR + "/" + CodexCodec.PROTOCOL_VERSION
                        + " (Android; LineCode)");
                url = CodexCodec.responsesEndpoint(baseUrl);
                body = CodexCodec.requestBody(request, baseUrl);
                decoder = CodexCodec.decoder();
                break;
            case OPENAI_COMPATIBLE:
            case LOCAL_GGUF:
            default:
                headers.put("Authorization", "Bearer " + model.apiKey);
                url = OpenAiChatCodec.endpoint(baseUrl);
                body = OpenAiChatCodec.requestBody(request);
                decoder = OpenAiChatCodec.decoder();
                break;
        }
        return new Plan(url, body, RequestHeaders.merge(headers, model.headers), decoder);
    }

    /** Returns the headers actually sent: the transport content type plus the planned headers. */
    private static Map<String, String> sentHeaders(Map<String, String> planned) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.putAll(planned);
        return headers;
    }

    /** Reads the provider's error payload; the status code alone hides the actual reason. */
    private static String readError(HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = stream) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0 && out.size() < 32768) {
                out.write(buffer, 0, Math.min(count, 32768 - out.size()));
            }
        } catch (IOException error) {
            return "";
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    /**
     * Feeds the response stream through the SSE parser and decoder.
     *
     * @return {@code false} when cancelled or when the decoder reported an error; the listener has
     *         received its terminal callback in both cases
     */
    private boolean read(InputStream input, Listener listener, Connection holder,
                         StreamDecoder decoder, HttpDiagnostics.Request diagnostic,
                         Map<String, String> sentHeaders)
            throws IOException {
        SseParser parser = new SseParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (holder.isCancelled()) {
                    return false;
                }
                if (!dispatch(parser.feed(line), decoder, listener, diagnostic, sentHeaders)) {
                    return false;
                }
            }
        }
        return dispatch(parser.finish(), decoder, listener, diagnostic, sentHeaders);
    }

    /**
     * Delivers decoded events to the listener; returns false after reporting a decoder failure.
     *
     * <p>The payload that failed to decode is included as the response body of the diagnostic
     * report, so an unexpected provider event can be reproduced from the error log.</p>
     */
    private static boolean dispatch(List<String> payloads, StreamDecoder decoder, Listener listener,
                                    HttpDiagnostics.Request diagnostic,
                                    Map<String, String> sentHeaders) {
        String current = "";
        try {
            for (String payload : payloads) {
                current = payload;
                StreamEvent event = decoder.decode(payload);
                if (event != null && !event.isEmpty()) listener.onEvent(event);
            }
            return true;
        } catch (IllegalStateException error) {
            listener.onError(new DomainException(ErrorCode.NETWORK,
                    HttpDiagnostics.format(diagnostic, sentHeaders, "", current, error), error));
            return false;
        }
    }

    /**
     * Reports whether a configured base URL can be turned into a request.
     *
     * <p>The gate is shared with the provider form through {@link EndpointUrl}, so an address
     * saved by the editor is never rejected later by the transport. A scheme-less value is
     * repaired first: HTTP for a local or LAN host, HTTPS otherwise.</p>
     *
     * @param baseUrl candidate endpoint base URL, may be null
     * @return true when the endpoint normalises into an absolute HTTP(S) URL that is safe to call
     */
    static boolean isAllowed(String baseUrl) {
        return isAllowed(baseUrl, false);
    }

    /**
     * Reports whether a configured base URL can be turned into a request under the given policy.
     *
     * @param baseUrl candidate endpoint base URL, may be null
     * @param allowAnyHttp true when plain HTTP to any host is permitted
     * @return true when the endpoint normalises into a usable URL
     */
    static boolean isAllowed(String baseUrl, boolean allowAnyHttp) {
        return EndpointUrl.problem(baseUrl, allowAnyHttp) == EndpointUrl.Problem.NONE;
    }

    /**
     * Cancellable handle bound to the in-flight {@link HttpURLConnection}.
     *
     * <p>Cancellation is safe at any point, including before a connection exists: the handle
     * records the request and disconnects as soon as one is attached.</p>
     */
    private static final class Connection implements Cancellable {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        /** Binds the connection and disconnects immediately when cancellation already happened. */
        void attach(HttpURLConnection value) {
            connection = value;
            if (cancelled) {
                value.disconnect();
            }
        }

        /** Marks the request cancelled and disconnects the bound connection. */
        @Override
        public void cancel() {
            cancelled = true;
            HttpURLConnection local = connection;
            if (local != null) {
                local.disconnect();
            }
        }

        /** Returns {@code true} once cancellation has been requested. */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
