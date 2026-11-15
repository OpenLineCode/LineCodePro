package cn.lineai.data.api;

/** Reads aggregated token usage from persisted assistant messages. */
public interface UsageStatsStore {

    /**
     * Loads the aggregated usage snapshot.
     *
     * @return totals plus the per-model breakdown; empty when no message carries token data
     */
    UsageStats load();
}
