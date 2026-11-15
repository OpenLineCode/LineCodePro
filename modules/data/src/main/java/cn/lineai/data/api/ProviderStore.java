package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.ProviderConfig;

/**
 * Model provider endpoints plus their shared credentials and headers.
 *
 * <p>Implementations are synchronous; callers own the scheduler. A provider owns the endpoint,
 * protocol and credential, while model rows reference it by identifier.</p>
 */
public interface ProviderStore {

    /**
     * Lists the stored providers alphabetically by display name.
     *
     * @return every stored provider
     */
    List<ProviderConfig> list();

    /**
     * Looks up one provider by identifier.
     *
     * @param id provider identifier
     * @return the provider or null when the identifier is unknown
     */
    ProviderConfig find(String id);

    /**
     * Inserts or updates one provider.
     *
     * @param provider configuration to persist; must not be null
     */
    void save(ProviderConfig provider);

    /**
     * Deletes one provider row. Models that reference it are not touched; the caller removes or
     * reassigns them first.
     *
     * @param id provider identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
