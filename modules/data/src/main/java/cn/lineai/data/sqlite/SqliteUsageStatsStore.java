package cn.lineai.data.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.UsageStats;
import cn.lineai.data.api.UsageStatsStore;

/**
 * SQLite-backed token usage aggregation over persisted assistant messages.
 *
 * <p>Only assistant rows carry token accounting; user and tool rows are ignored. The per-model
 * breakdown is grouped by the model and provider identifiers and ordered by total consumption so
 * the statistics page renders the heaviest model first.</p>
 */
public final class SqliteUsageStatsStore implements UsageStatsStore {

    private final LineCodeDatabase database;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     */
    public SqliteUsageStatsStore(LineCodeDatabase database) {
        this.database = database;
    }

    /**
     * Aggregates every assistant message into totals plus the per-model breakdown.
     *
     * @return the usage snapshot; empty when no assistant row carries token data
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public UsageStats load() {
        String sql = "SELECT model_id, provider_id, SUM(input_tokens), SUM(output_tokens),"
                + " SUM(cached_tokens), COUNT(*) FROM messages WHERE role=?"
                + " GROUP BY model_id, provider_id"
                + " ORDER BY SUM(input_tokens + output_tokens) DESC";
        long input = 0L;
        long output = 0L;
        long cached = 0L;
        List<UsageStats.ModelUsage> models = new ArrayList<>();
        try (Cursor cursor = database.open().rawQuery(sql,
                new String[] {Role.ASSISTANT.name()})) {
            while (cursor.moveToNext()) {
                UsageStats.ModelUsage usage = new UsageStats.ModelUsage(cursor.getString(0),
                        cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                        cursor.getLong(4), cursor.getInt(5));
                models.add(usage);
                input += usage.inputTokens;
                output += usage.outputTokens;
                cached += usage.cachedTokens;
            }
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "load usage statistics", error);
        }
        return new UsageStats(input, output, cached, models);
    }
}
