package cn.lineai.data.sqlite;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.ProviderStore;

/**
 * One-shot migration of legacy model endpoint fields into provider rows.
 *
 * <p>Before schema v16 every model carried its own protocol, base URL and credential. The backfill
 * groups the non-local models whose provider identifier has no provider row by protocol, base URL
 * and credential, creates one provider per group and rewrites the model rows to reference it. The
 * run is transactional and idempotent: providers that already exist are left alone, so replaying it
 * after a partial migration or on every start is safe.</p>
 */
public final class ProviderBackfill {

    private ProviderBackfill() {
    }

    /**
     * Creates the missing provider rows and rewrites the referencing model rows.
     *
     * @param database database holder used for the transactional model update
     * @param providers provider store receiving the new rows
     * @param rawModels underlying model store read for the legacy endpoint fields
     * @throws DomainException with {@code ErrorCode.STORAGE} when the update fails
     */
    public static void run(LineCodeDatabase database, ProviderStore providers, ModelStore rawModels) {
        if (database == null || providers == null || rawModels == null) {
            throw new IllegalArgumentException("backfill requires database, providers and models");
        }
        Map<String, List<ModelConfig>> groups = new LinkedHashMap<>();
        for (ModelConfig model : rawModels.list()) {
            if (model.protocol == ModelConfig.Protocol.LOCAL_GGUF) continue;
            if (!model.providerId.isEmpty() && providers.find(model.providerId) != null) continue;
            groups.computeIfAbsent(groupKey(model), key -> new ArrayList<>()).add(model);
        }
        if (groups.isEmpty()) return;
        SQLiteDatabase db = database.open();
        db.beginTransaction();
        try {
            java.util.Set<String> claimed = new java.util.HashSet<>();
            for (List<ModelConfig> group : groups.values()) {
                ModelConfig first = group.get(0);
                String id = first.providerId;
                if (id.isEmpty() || claimed.contains(id) || providers.find(id) != null) {
                    id = Ids.prefixed("provider");
                }
                while (claimed.contains(id) || providers.find(id) != null) {
                    id = Ids.prefixed("provider");
                }
                claimed.add(id);
                ProviderConfig provider = ProviderFactory.fromModel(representative(group), id);
                providers.save(provider);
                ContentValues values = new ContentValues();
                values.put("provider_id", provider.id);
                values.put("provider_label", provider.name);
                for (ModelConfig model : group) {
                    db.update("models", values, "id=?", new String[] {model.id});
                }
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException error) {
            throw new DomainException(ErrorCode.STORAGE, "backfill providers", error);
        } finally {
            db.endTransaction();
        }
    }

    /** Groups models sharing the legacy endpoint identity. */
    private static String groupKey(ModelConfig model) {
        return model.providerId + '\n' + model.protocol.name() + '\n' + model.baseUrl + '\n'
                + model.apiKey;
    }

    /** Picks the first model carrying a display label so the group keeps its user-visible name. */
    private static ModelConfig representative(List<ModelConfig> group) {
        for (ModelConfig model : group) {
            if (!model.providerLabel.trim().isEmpty()) return model;
        }
        return group.get(0);
    }
}
