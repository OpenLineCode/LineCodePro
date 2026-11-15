package cn.lineai.engine.ai;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.model.ModelConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpDiagnosticsTest {

    @Test
    public void masksCredentialHeadersAndKeepsTheirNames() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer sk-secret");
        headers.put("x-api-key", "sk-anthropic");
        headers.put("Cookie", "session=abc");
        headers.put("Set-Cookie", "session=abc");
        headers.put("X-Refresh-Token", "token-value");
        headers.put("Content-Type", "application/json");

        String lines = HttpDiagnostics.headerLines(headers);

        assertTrue(lines.contains("Authorization: <redacted>"));
        assertTrue(lines.contains("x-api-key: <redacted>"));
        assertTrue(lines.contains("Cookie: <redacted>"));
        assertTrue(lines.contains("Set-Cookie: <redacted>"));
        assertTrue(lines.contains("X-Refresh-Token: <redacted>"));
        assertTrue(lines.contains("Content-Type: application/json"));
        assertFalse(lines.contains("sk-secret"));
        assertFalse(lines.contains("session=abc"));
        assertFalse(lines.contains("token-value"));
    }

    @Test
    public void recognizesSecretHeaderNamesCaseInsensitively() {
        assertTrue(HttpDiagnostics.isSecretHeader("authorization"));
        assertTrue(HttpDiagnostics.isSecretHeader("Proxy-Authorization"));
        assertTrue(HttpDiagnostics.isSecretHeader("API-KEY"));
        assertTrue(HttpDiagnostics.isSecretHeader("X-Custom-Password"));
        assertTrue(HttpDiagnostics.isSecretHeader("X-Service-Secret"));
        assertTrue(HttpDiagnostics.isSecretHeader("X-Access-Token"));
        assertFalse(HttpDiagnostics.isSecretHeader("Content-Type"));
        assertFalse(HttpDiagnostics.isSecretHeader("content-length"));
        assertFalse(HttpDiagnostics.isSecretHeader("Accept"));
        assertFalse(HttpDiagnostics.isSecretHeader("anthropic-version"));
    }

    @Test
    public void redactsObviousSecretsInBodies() {
        String body = "{\"api_key\":\"sk-abcdef123456\",\"message\":\"Bearer abcdefghijklmn\"}";
        String redacted = HttpDiagnostics.redactText(body);
        assertFalse(redacted.contains("sk-abcdef123456"));
        assertFalse(redacted.contains("abcdefghijklmn"));
        assertTrue(redacted.contains("api_key"));
        assertTrue(redacted.contains("<redacted>"));
    }

    @Test
    public void truncatesBodiesAtTheCap() {
        StringBuilder large = new StringBuilder();
        for (int index = 0; index < HttpDiagnostics.BODY_LIMIT + 50; index++) {
            large.append('x');
        }
        String truncated = HttpDiagnostics.truncate(large.toString());
        assertTrue(truncated.length() > HttpDiagnostics.BODY_LIMIT);
        assertTrue(truncated.length() < large.length());
        assertTrue(truncated.contains("[truncated: 50 characters omitted]"));
        assertEquals("short", HttpDiagnostics.truncate("short"));
    }

    @Test
    public void formatsEveryDiagnosticSection() {
        HttpDiagnostics.Request request = new HttpDiagnostics.Request(
                ModelConfig.Protocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com",
                "https://api.anthropic.com/v1/messages", "claude-sonnet-5-5",
                "{\"model\":\"claude-sonnet-5-5\"}");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", "sk-ant-secret");
        IllegalArgumentException error = new IllegalArgumentException("boom");

        String report = HttpDiagnostics.format(request, headers, "HTTP 500", "{\"error\":\"bad\"}",
                error);

        assertTrue(report.contains("endpoint:"));
        assertTrue(report.contains("protocol: ANTHROPIC_MESSAGES"));
        assertTrue(report.contains("base url: https://api.anthropic.com"));
        assertTrue(report.contains("url: https://api.anthropic.com/v1/messages"));
        assertTrue(report.contains("model: claude-sonnet-5-5"));
        assertTrue(report.contains("status: HTTP 500"));
        assertTrue(report.contains("request headers:"));
        assertTrue(report.contains("x-api-key: <redacted>"));
        assertTrue(report.contains("request body:"));
        assertTrue(report.contains("response body:"));
        assertTrue(report.contains("{\"error\":\"bad\"}"));
        assertTrue(report.contains("stack:"));
        assertTrue(report.contains("IllegalArgumentException: boom"));
        assertFalse(report.contains("sk-ant-secret"));
    }

    @Test
    public void emptyBodiesAndMissingErrorsStayReadable() {
        String report = HttpDiagnostics.format(null, null, "", "", null);
        assertTrue(report.contains("protocol: unknown"));
        assertTrue(report.contains("request headers:\n  (none)"));
        assertTrue(report.contains("request body:\n(empty)"));
        assertTrue(report.contains("response body:\n(empty)"));
        assertTrue(report.contains("stack:"));
    }

    @Test
    public void stackTraceIsRenderedFromTheCausingException() {
        String stack = HttpDiagnostics.stackTrace(new IllegalStateException("caused"));
        assertTrue(stack.contains("IllegalStateException: caused"));
        assertTrue(stack.contains("at cn.lineai.engine.ai.HttpDiagnosticsTest"));
        assertEquals("", HttpDiagnostics.stackTrace(null));
    }
}
