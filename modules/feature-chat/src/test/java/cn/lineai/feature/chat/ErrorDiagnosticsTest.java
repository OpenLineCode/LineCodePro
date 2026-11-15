package cn.lineai.feature.chat;

import org.junit.Test;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.ModelConfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ErrorDiagnosticsTest {

    private static final String DEVICE =
            "Xiaomi 14 (Android 15, SDK 35), app 2.2.0 (code 220)";

    @Test
    public void reportPrefixesDeviceTimeConversationAndModel() {
        ModelConfig model = ModelConfig.builder().identity("cfg-1", "Claude Sonnet 5.5")
                .endpoint("anthropic", "claude-sonnet-5-5", "https://api.anthropic.com")
                .protocol(ModelConfig.Protocol.ANTHROPIC_MESSAGES)
                .build();
        DomainException error = new DomainException(ErrorCode.NETWORK,
                "endpoint:\n  protocol: ANTHROPIC_MESSAGES");

        String report = ErrorDiagnostics.format(DEVICE, 1_700_000_000_000L, "conv-9", model,
                error, "java.lang.IllegalStateException: boom\n\tat example");

        assertTrue(report.startsWith("device: " + DEVICE));
        assertTrue(report.contains("\ntime: 2023-11-14T22:13:20Z"));
        assertTrue(report.contains("\nconversation: conv-9"));
        assertTrue(report.contains("model: Claude Sonnet 5.5"));
        assertTrue(report.contains("config=cfg-1"));
        assertTrue(report.contains("model=claude-sonnet-5-5"));
        assertTrue(report.contains("protocol=ANTHROPIC_MESSAGES"));
        assertTrue(report.contains("error: NETWORK"));
        assertTrue(report.contains("endpoint:"));
        assertTrue(report.contains("\nstack:\n"));
        assertTrue(report.contains("IllegalStateException: boom"));
    }

    @Test
    public void reportHandlesMissingContext() {
        DomainException error = new DomainException(ErrorCode.UNKNOWN, "");

        String report = ErrorDiagnostics.format("", 0L, "", null, error, "");

        assertTrue(report.contains("device: unknown"));
        assertTrue(report.contains("conversation: (none)"));
        assertTrue(report.contains("model: unknown"));
        assertTrue(report.contains("error: UNKNOWN"));
        assertFalse(report.contains("stack:"));
    }
}
