package cn.lineai.core.kernel.error;

/**
 * Port for recording a handled failure so that it stays diagnosable.
 *
 * <p>Most failures are caught close to the user and reduced to a toast or a tool result, which
 * leaves no trace once the message disappears. Implementations append the report to the in-app
 * failure log. Reports are technical: the title names the operation, the subtitle carries the
 * one-line cause and the detail carries the stack.</p>
 *
 * <p>Never pass credentials, header values, tokens or file contents to this port. Endpoints are
 * logged through a redacted form so query-string secrets do not leak into the log.</p>
 */
public interface FailureReporter {

    /** Reporter that discards everything, used where no log is wired. */
    FailureReporter NONE = new FailureReporter() {
        @Override public void record(String title, String subtitle, String detail) {
            // Null object: keeps every call site free of null checks.
        }
    };

    /**
     * Records one failure.
     *
     * @param title short headline naming the operation that failed
     * @param subtitle one-line cause; may be empty
     * @param detail full diagnostic text, usually a stack trace; may be empty
     */
    void record(String title, String subtitle, String detail);
}
