package cn.lineai.ui.i18n;

import cn.lineai.core.kernel.error.ErrorCode;

/**
 * Maps a stable error code to one existing string resource.
 *
 * <p>Callers never render {@code Throwable.getMessage()}: the thrower keeps the technical detail
 * for logs, the UI shows one of these strings.</p>
 */
public final class ErrorTextMapper {

    /** Not instantiable; the mapping is a static registry lookup. */
    private ErrorTextMapper() {
    }

    /**
     * Maps one error code to the message the UI shows.
     *
     * @param code stable error code carried by the failure; null maps to the generic message
     * @return deferred lookup of the matching string resource
     */
    public static TextRef text(ErrorCode code) {
        if (code == null) {
            return TextRef.of(R.string.toast_ai_generate_failed);
        }
        switch (code) {
            case CANCELLED:
                return TextRef.of(R.string.chat_stop);
            case PROMPT_MISSING:
                return TextRef.of(R.string.model_retry_failed);
            case STORAGE:
                return TextRef.of(R.string.screen_storage_title);
            case INVALID_INPUT:
                // Inside a turn this code comes from the model endpoint gate; the generic
                // "generation failed" text would leave the user without the one field to fix.
                return TextRef.of(R.string.error_model_endpoint_invalid);
            case NETWORK:
            case TIMEOUT:
            case AUTH:
            case RATE_LIMIT:
            case NOT_FOUND:
            case TOOL_FAILED:
            case UNKNOWN:
            default:
                return TextRef.of(R.string.toast_ai_generate_failed);
        }
    }
}
