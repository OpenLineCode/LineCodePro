package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.data.api.ProviderStore;

/**
 * SQLite-backed provider endpoints, alphabetical by display name.
 *
 * <p>The provider row owns the protocol, base URL, credential and shared headers; model rows only
 * reference it. Headers use the shared newline/tab {@link HeaderCodec} encoding.</p>
 */
public final class SqliteProviderStore implements ProviderStore {

    private final LineCodeDatabase database;
    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param database shared database connection holder
     * @param clock clock used to timestamp saved providers
     */
    public SqliteProviderStore(LineCodeDatabase database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Creates the store with the system clock.
     *
     * @param database shared database connection holder
     */
    public SqliteProviderStore(LineCodeDatabase database) {
        this(database, Clock.system());
    }

    /**
     * Reads every provider, alphabetical by display name.
     *
     * @return all stored provider configurations
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public List<ProviderConfig> list() {
        List<ProviderConfig> result = new ArrayList<>();
        try (Cursor cursor = database.open().query("model_providers", null, null, null,
                null, null, "name ASC, id ASC")) {
            while (cursor.moveToNext()) result.add(read(cursor));
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "list providers", error);
        }
        return result;
    }

    /**
     * Looks up one provider by identifier.
     *
     * @param id provider identifier
     * @return the provider or null when the identifier is unknown
     * @throws DomainException with {@code ErrorCode.STORAGE} when the query fails
     */
    @Override
    public ProviderConfig find(String id) {
        try (Cursor cursor = database.open().query("model_providers", null, "id=?",
                new String[] {id}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "find provider", error);
        }
    }

    /**
     * Inserts or replaces one provider, preserving the creation time on update. An empty
     * identifier is generated; callers that need to reference the row must supply one.
     *
     * @param provider configuration to persist; must not be null
     * @throws DomainException with {@code ErrorCode.STORAGE} when the write fails
     */
    @Override
    public void save(ProviderConfig provider) {
        if (provider == null) throw new IllegalArgumentException("provider == null");
        long now = clock.nowMs();
        ProviderConfig saved = provider.toBuilder()
                .id(provider.id.isEmpty() ? Ids.prefixed("provider") : provider.id)
                .times(provider.createdAt <= 0 ? now : provider.createdAt, now)
                .build();
        ContentValues values = new ContentValues();
        values.put("id", saved.id);
        values.put("name", saved.name);
        values.put("protocol", saved.protocol.name());
        values.put("base_url", saved.baseUrl);
        values.put("api_key", saved.apiKey);
        values.put("headers", HeaderCodec.encode(saved.headers));
        values.put("created_at", saved.createdAt);
        values.put("updated_at", saved.updatedAt);
        try {
            database.open().insertWithOnConflict("model_providers", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "save provider", error);
        }
    }

    /**
     * Removes the provider row with the given identifier.
     *
     * @param id provider identifier; unknown identifiers are ignored
     */
    @Override
    public void delete(String id) {
        database.open().delete("model_providers", "id=?", new String[] {id});
    }

    /** Maps the current row onto a provider configuration. */
    private static ProviderConfig read(Cursor cursor) {
        return ProviderConfig.builder()
                .id(cursor.getString(cursor.getColumnIndexOrThrow("id")))
                .name(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                .protocol(protocol(cursor.getString(cursor.getColumnIndexOrThrow("protocol"))))
                .endpoint(cursor.getString(cursor.getColumnIndexOrThrow("base_url")),
                        cursor.getString(cursor.getColumnIndexOrThrow("api_key")))
                .headers(HeaderCodec.decode(
                        cursor.getString(cursor.getColumnIndexOrThrow("headers"))))
                .times(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                .build();
    }

    /** Parses the stored protocol, falling back to the OpenAI-compatible default. */
    static ModelConfig.Protocol protocol(String value) {
        try {
            return ModelConfig.Protocol.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            return ModelConfig.Protocol.OPENAI_COMPATIBLE;
        }
    }
}
