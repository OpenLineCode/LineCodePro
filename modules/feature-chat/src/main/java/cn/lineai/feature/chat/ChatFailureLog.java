package cn.lineai.feature.chat;

import cn.lineai.data.api.ErrorLogStore;

/**
 * Failure-log wiring for one chat controller.
 *
 * <p>The root chat and every sub agent session own a controller, but only the root is built by
 * the application graph. This holder keeps the log, the device summary and the label naming the
 * session, so a sub agent failure is recorded under the agent instead of the model alone and a
 * controller without a log stays silent rather than failing.</p>
 */
final class ChatFailureLog {

    private ErrorLogStore store;
    private String summary = "";
    private String label = "";

    /**
     * Attaches the store.
     *
     * @param value failure log to write to; null disables recording
     */
    void attach(ErrorLogStore value) {
        store = value;
    }

    /**
     * Sets the label prefixed to entry titles.
     *
     * @param value session label such as the agent name; null or blank restores the model title
     */
    void label(String value) {
        label = value == null ? "" : value.trim();
    }

    /**
     * Sets the device and version summary.
     *
     * @param value summary from the app shell; null becomes empty
     */
    void deviceSummary(String value) {
        summary = value == null ? "" : value.trim();
    }

    /** Returns the device and version summary, empty when none was supplied. */
    String deviceSummary() {
        return summary;
    }

    /**
     * Composes an entry title from the session label and the model name.
     *
     * @param modelName display name of the model that failed
     * @return the label followed by the model, or the model alone
     */
    String title(String modelName) {
        return label.isEmpty() ? modelName : label + " - " + modelName;
    }

    /**
     * Appends one entry when a store is attached.
     *
     * @param title short headline naming the failure
     * @param subtitle one-line cause
     * @param detail full diagnostic report
     */
    void record(String title, String subtitle, String detail) {
        if (store != null) {
            store.record(title, subtitle, detail);
        }
    }
}
