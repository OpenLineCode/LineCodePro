package cn.lineai.data.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated token usage over every persisted assistant message.
 *
 * <p>Amounts are totals across the whole database; {@link #models} keeps the per-model split for
 * the statistics page. The cache hit rate is the weighted ratio cached/input, not the mean of the
 * per-model ratios.</p>
 */
public final class UsageStats {

    /** Total prompt tokens, including cached input. */
    public final long inputTokens;
    /** Total completion tokens. */
    public final long outputTokens;
    /** Total prompt tokens served from the provider cache. */
    public final long cachedTokens;
    /** Per-model breakdown ordered by total consumption, descending. */
    public final List<ModelUsage> models;

    /**
     * Creates a snapshot.
     * @param input total input tokens
     * @param output total output tokens
     * @param cached total cached input tokens
     * @param models per-model breakdown, null yields an empty list
     */
    public UsageStats(long input, long output, long cached, List<ModelUsage> models) {
        this.inputTokens = Math.max(0L, input);
        this.outputTokens = Math.max(0L, output);
        this.cachedTokens = Math.max(0L, cached);
        this.models = Collections.unmodifiableList(models == null
                ? new ArrayList<ModelUsage>() : new ArrayList<>(models));
    }

    /** Returns an empty snapshot. */
    public static UsageStats empty() {
        return new UsageStats(0L, 0L, 0L, null);
    }

    /** Total consumed tokens: input plus output. */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /** Weighted cache hit ratio in the range 0..1; zero when nothing was consumed. */
    public double cacheRate() {
        return inputTokens == 0L ? 0d : (double) cachedTokens / (double) inputTokens;
    }

    /** True when no assistant message carried token accounting. */
    public boolean isEmpty() {
        return inputTokens == 0L && outputTokens == 0L && cachedTokens == 0L;
    }

    /** One model's share of the aggregated usage. */
    public static final class ModelUsage {
        public final String modelId;
        public final String providerId;
        public final long inputTokens;
        public final long outputTokens;
        public final long cachedTokens;
        public final int requests;

        /**
         * Creates one model row.
         * @param model provider-side model identifier
         * @param provider provider identifier
         * @param input total input tokens
         * @param output total output tokens
         * @param cached total cached input tokens
         * @param requests number of assistant messages
         */
        public ModelUsage(String model, String provider, long input, long output, long cached,
                          int requests) {
            this.modelId = model == null ? "" : model;
            this.providerId = provider == null ? "" : provider;
            this.inputTokens = Math.max(0L, input);
            this.outputTokens = Math.max(0L, output);
            this.cachedTokens = Math.max(0L, cached);
            this.requests = Math.max(0, requests);
        }

        /** Total consumed tokens for this model. */
        public long totalTokens() {
            return inputTokens + outputTokens;
        }

        /** Weighted cache hit ratio in the range 0..1. */
        public double cacheRate() {
            return inputTokens == 0L ? 0d : (double) cachedTokens / (double) inputTokens;
        }
    }
}
