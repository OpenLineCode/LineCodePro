package cn.lineai.feature.chat;

import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;

/**
 * Aggregated token, cache and performance figures of one conversation.
 *
 * <p>Pure computation over the persisted transcript: every figure other than the estimated
 * context usage comes from the token and timing accounting stored on assistant messages. The
 * cache hit rate is the weighted cache/input ratio, and the throughput is the output tokens over
 * the total stream time, so repeated tool rounds count only once.</p>
 */
final class SessionStats {

    /** Estimated tokens currently in context, supplied by the chat controller. */
    final int usedTokens;
    /** Context window in tokens, at least one. */
    final int maxTokens;
    /** Summed prompt tokens of every assistant message. */
    final long inputTokens;
    /** Summed completion tokens of every assistant message. */
    final long outputTokens;
    /** Summed prompt tokens served from the provider cache. */
    final long cachedTokens;
    /** Number of assistant messages that reported a time to first token. */
    final int ttftSamples;
    /** Summed time to first token, in milliseconds. */
    final long ttftTotalMs;
    /** Summed model stream time, in milliseconds. */
    final long durationMs;
    /** Number of tool calls requested across the transcript. */
    final int toolCalls;
    /** Number of assistant messages, including tool rounds and compaction markers. */
    final int assistantTurns;

    /**
     * Aggregates the transcript.
     *
     * @param messages persisted transcript, may be null
     * @param usedTokens estimated context usage in tokens
     * @param maxTokens context window in tokens
     */
    SessionStats(List<Message> messages, int usedTokens, int maxTokens) {
        this.usedTokens = Math.max(0, usedTokens);
        this.maxTokens = Math.max(1, maxTokens);
        long input = 0L;
        long output = 0L;
        long cached = 0L;
        long ttftTotal = 0L;
        long duration = 0L;
        int ttftCount = 0;
        int calls = 0;
        int turns = 0;
        if (messages != null) {
            for (Message message : messages) {
                calls += message.toolCalls.size();
                if (message.role != Role.ASSISTANT) {
                    continue;
                }
                turns++;
                input += Math.max(0, message.inputTokens);
                output += Math.max(0, message.outputTokens);
                cached += Math.max(0, message.cachedTokens);
                if (message.ttftMs > 0) {
                    ttftCount++;
                    ttftTotal += message.ttftMs;
                }
                if (message.durationMs > 0) {
                    duration += message.durationMs;
                }
            }
        }
        this.inputTokens = input;
        this.outputTokens = output;
        this.cachedTokens = cached;
        this.ttftSamples = ttftCount;
        this.ttftTotalMs = ttftTotal;
        this.durationMs = duration;
        this.toolCalls = calls;
        this.assistantTurns = turns;
    }

    /** Context usage as a percentage clamped to 0..100. */
    int contextPercent() {
        return (int) Math.min(100L, usedTokens * 100L / maxTokens);
    }

    /** Total consumed tokens of the conversation: input plus output. */
    long totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * Weighted cache hit rate as a percentage of the total prompt tokens.
     *
     * @return a value such as {@code 42}; empty when no prompt tokens were reported
     */
    String cacheHitText() {
        if (inputTokens <= 0L) {
            return "";
        }
        return String.format(Locale.ROOT, "%d%%", Math.round(cachedTokens * 100d / inputTokens));
    }

    /**
     * Average time to first token over the messages that reported one.
     *
     * @return average in milliseconds, zero when no message reported a first-token time
     */
    int averageTtftMs() {
        return ttftSamples == 0 ? 0 : (int) Math.round((double) ttftTotalMs / ttftSamples);
    }

    /**
     * Output throughput over the measured model stream time.
     *
     * @return tokens per second, zero when no stream time was reported
     */
    double tps() {
        return durationMs <= 0L ? 0d : outputTokens / (durationMs / 1000d);
    }

    /**
     * Throughput rendered with one decimal for the statistics sheet.
     *
     * @return a value such as {@code 24.8}; empty when no stream time was reported
     */
    String tpsText() {
        return durationMs <= 0L ? "" : String.format(Locale.ROOT, "%.1f", tps());
    }

    /** True when any assistant message carried token accounting. */
    boolean hasTokenUsage() {
        return inputTokens > 0L || outputTokens > 0L || cachedTokens > 0L;
    }

}
