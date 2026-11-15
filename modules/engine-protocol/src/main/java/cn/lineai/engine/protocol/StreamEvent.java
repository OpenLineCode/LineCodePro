package cn.lineai.engine.protocol;

import java.util.Collections;
import java.util.List;

/**
 * One parsed streaming chunk. Immutable; empty strings mean "absent".
 *
 * <p>A single event may combine text, reasoning, usage and tool calls; codecs populate only the
 * fields carried by the corresponding wire event. {@link #isEmpty()} identifies events with no
 * content or accounting so dispatchers can skip them; a usage-only event is not empty because the
 * engine accounts tokens from it.</p>
 */
public final class StreamEvent {

    /** Incremental assistant text; empty when this event carries none. */
    public final String text;
    /** Incremental reasoning or summary text; empty when this event carries none. */
    public final String reasoning;
    /** Terminal stop reason ("stop", "tool_use" and similar); empty until the stream finishes. */
    public final String finishReason;
    /** Prompt token count reported by the provider; zero when unknown. */
    public final int promptTokens;
    /** Completion token count reported by the provider; zero when unknown. */
    public final int completionTokens;
    /** Prompt tokens served from the provider cache; zero when unknown or unreported. */
    public final int cachedTokens;
    /** Tool-call fragments decoded from this event; never {@code null}, possibly empty. */
    public final List<ToolCallDraft> calls;

    /** Creates an event; callers use the static factories instead. */
    private StreamEvent(String text, String reasoning, String finishReason, int promptTokens,
                        int completionTokens, int cachedTokens, List<ToolCallDraft> calls) {
        this.text = text;
        this.reasoning = reasoning;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.cachedTokens = cachedTokens;
        this.calls = calls == null ? Collections.<ToolCallDraft>emptyList() : calls;
    }

    /** Returns an event carrying only assistant text.
     *
     * @param value incremental text emitted by the model
     * @return text-only event
     */
    public static StreamEvent text(String value) {
        return new StreamEvent(value, "", "", 0, 0, 0, null);
    }

    /** Returns an event carrying only reasoning text.
     *
     * @param value incremental reasoning or summary text
     * @return reasoning-only event
     */
    public static StreamEvent reasoning(String value) {
        return new StreamEvent("", value, "", 0, 0, 0, null);
    }

    /** Returns an event combining text, reasoning, finish reason and usage.
     *
     * @param text incremental assistant text
     * @param reasoning incremental reasoning text
     * @param finishReason terminal stop reason, empty when the stream continues
     * @param promptTokens prompt token usage reported by the provider
     * @param completionTokens completion token usage reported by the provider
     * @return event without tool-call fragments or cached accounting
     */
    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens) {
        return of(text, reasoning, finishReason, promptTokens, completionTokens, 0);
    }

    /** Returns an event combining text, reasoning, finish reason and usage.
     *
     * @param text incremental assistant text
     * @param reasoning incremental reasoning text
     * @param finishReason terminal stop reason, empty when the stream continues
     * @param promptTokens prompt token usage reported by the provider
     * @param completionTokens completion token usage reported by the provider
     * @param cachedTokens prompt tokens served from the provider cache, zero when unreported
     * @return event without tool-call fragments
     */
    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens, int cachedTokens) {
        return new StreamEvent(text, reasoning, finishReason, promptTokens, completionTokens,
                cachedTokens, null);
    }

    /** Returns an event carrying only tool-call fragments.
     *
     * @param calls fragments decoded from this event
     * @return tool-call-only event
     */
    public static StreamEvent calls(List<ToolCallDraft> calls) {
        return new StreamEvent("", "", "", 0, 0, 0, calls);
    }

    /** Returns an event combining every field, including tool-call fragments.
     *
     * @param text incremental assistant text
     * @param reasoning incremental reasoning text
     * @param finishReason terminal stop reason, empty when the stream continues
     * @param promptTokens prompt token usage reported by the provider
     * @param completionTokens completion token usage reported by the provider
     * @param calls tool-call fragments decoded from this event
     * @return event carrying all supplied fields
     */
    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens,
                                 List<ToolCallDraft> calls) {
        return of(text, reasoning, finishReason, promptTokens, completionTokens, 0, calls);
    }

    /** Returns an event combining every field, including cached usage and tool-call fragments.
     *
     * @param text incremental assistant text
     * @param reasoning incremental reasoning text
     * @param finishReason terminal stop reason, empty when the stream continues
     * @param promptTokens prompt token usage reported by the provider
     * @param completionTokens completion token usage reported by the provider
     * @param cachedTokens prompt tokens served from the provider cache
     * @param calls tool-call fragments decoded from this event
     * @return event carrying all supplied fields
     */
    public static StreamEvent of(String text, String reasoning, String finishReason,
                                 int promptTokens, int completionTokens, int cachedTokens,
                                 List<ToolCallDraft> calls) {
        return new StreamEvent(text, reasoning, finishReason, promptTokens, completionTokens,
                cachedTokens, calls);
    }

    /**
     * Returns {@code true} when the event carries no text, reasoning, finish reason, calls or
     * token accounting; callers drop such keep-alive events.
     *
     * @return true when every content and accounting field is empty or zero
     */
    public boolean isEmpty() {
        return text.isEmpty() && reasoning.isEmpty() && finishReason.isEmpty() && calls.isEmpty()
                && promptTokens == 0 && completionTokens == 0 && cachedTokens == 0;
    }
}
