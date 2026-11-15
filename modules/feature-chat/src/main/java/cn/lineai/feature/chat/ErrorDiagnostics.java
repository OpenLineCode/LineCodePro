package cn.lineai.feature.chat;

import java.time.Instant;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.model.ModelConfig;

/**
 * Builds the enriched diagnostic text recorded with a failed generation.
 *
 * <p>The report prefixes the device and version summary supplied by the app shell, then names the
 * timestamp, conversation, model and error before appending the exception stack. The assistant
 * ERROR row keeps the raw {@link ChatFailure#detail(DomainException)} text; this longer report is
 * written to the error log so a user can share it with the full context.</p>
 */
final class ErrorDiagnostics {

    /** Static utility; not instantiable. */
    private ErrorDiagnostics() {
    }

    /**
     * Formats one failure report.
     *
     * @param deviceSummary device/OS/app version summary from {@code ChatController}; may be empty
     * @param nowMs wall-clock time of the failure in epoch milliseconds
     * @param conversationId conversation being generated, may be empty
     * @param model model that failed, may be null
     * @param error failure reported by the engine or model client
     * @param stackTrace printable stack trace, may be empty
     * @return multi-line diagnostic report, never null
     */
    static String format(String deviceSummary, long nowMs, String conversationId,
                         ModelConfig model, DomainException error, String stackTrace) {
        StringBuilder report = new StringBuilder(512);
        report.append("device: ").append(orFallback(deviceSummary, "unknown"))
                .append("\ntime: ").append(Instant.ofEpochMilli(nowMs))
                .append("\nconversation: ").append(orFallback(conversationId, "(none)"))
                .append("\nmodel: ");
        if (model == null) {
            report.append("unknown");
        } else {
            report.append(model.displayName())
                    .append(" | config=").append(orFallback(model.id, "(none)"))
                    .append(" | model=").append(orFallback(model.modelId, "(none)"))
                    .append(" | protocol=").append(model.protocol);
        }
        report.append("\nerror: ").append(error.code().name()).append(" \u00b7 ")
                .append(ChatFailure.detail(error));
        String stack = orFallback(stackTrace, "");
        if (!stack.isEmpty()) {
            report.append("\nstack:\n").append(stack);
        }
        return report.toString();
    }

    /** Returns the trimmed value, or the fallback when the value is null or blank. */
    private static String orFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
