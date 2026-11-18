package cn.lineai.core.kernel.error;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Text builders for {@link FailureReporter} entries.
 *
 * <p>Pure Java so engine and tool modules can describe a failure without the Android logging
 * stack. Every method tolerates null and never throws: building a diagnostic must not replace
 * the failure being reported.</p>
 */
public final class FailureText {

    /** Upper bound for one stack trace, so a deep or repeated cause cannot flood the log. */
    private static final int STACK_LIMIT = 8_000;

    /** Static utility; not instantiable. */
    private FailureText() {
    }

    /**
     * Returns the exception message, or the class name when no message exists.
     *
     * @param error failure to describe; null yields the empty string
     * @return one-line description, never null
     */
    public static String message(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }

    /**
     * Returns a printable stack trace, including the causes.
     *
     * @param error failure to describe; null yields the empty string
     * @return stack trace capped at {@link #STACK_LIMIT} characters, never null
     */
    public static String stack(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter buffer = new StringWriter(512);
        try (PrintWriter writer = new PrintWriter(buffer)) {
            error.printStackTrace(writer);
        }
        String text = buffer.toString();
        return text.length() <= STACK_LIMIT ? text : text.substring(0, STACK_LIMIT);
    }
}
