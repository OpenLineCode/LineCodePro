package cn.lineai.data.sqlite;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.ProviderStore;
import cn.lineai.data.api.SettingsStore;

/**
 * Model store that resolves the shared provider endpoint and headers on every read.
 *
 * <p>A model row only owns model-specific settings; the protocol, base URL, credential and shared
 * headers come from its {@link ProviderConfig}. Local GGUF models bypass provider resolution and
 * keep their own fields. Legacy model rows whose provider row does not exist yet are returned
 * unchanged so old databases stay readable before {@link ProviderBackfill} runs; saving one through
 * this store creates the missing provider first.</p>
 */
public final class ProviderModelStore implements ModelStore {

    private final ModelStore raw;
    private final ProviderStore providers;

    /**
     * Creates the resolving wrapper.
     *
     * @param raw underlying model store that owns the rows
     * @param providers provider store holding the shared endpoint configurations
     */
    public ProviderModelStore(ModelStore raw, ProviderStore providers) {
        this.raw = raw;
        this.providers = providers;
    }

    /**
     * Reads every model with its provider resolution applied.
     *
     * @return all stored models, default first and then alphabetical by name
     */
    @Override
    public List<ModelConfig> list() {
        List<ModelConfig> result = new ArrayList<>();
        for (ModelConfig model : raw.list()) result.add(resolve(model));
        return result;
    }

    /**
     * Looks up one model with its provider resolution applied.
     *
     * @param id model identifier
     * @return the resolved model or null when the identifier is unknown
     */
    @Override
    public ModelConfig find(String id) {
        return resolve(raw.find(id));
    }

    /**
     * Returns the chat default with its provider resolution applied.
     *
     * @return the resolved default model or null when the table is empty
     */
    @Override
    public ModelConfig selected() {
        return resolve(raw.selected());
    }

    /**
     * Saves a model, creating an implicit provider first when a non-local model has none.
     *
     * <p>The resolved provider endpoint is also written into the model row so the raw store stays
     * usable without provider resolution. Model-level headers are not copied from the provider:
     * they stay model-specific so later provider header edits keep winning on the read path.</p>
     *
     * @param model configuration to persist; must not be null
     */
    @Override
    public void save(ModelConfig model) {
        if (model == null) throw new IllegalArgumentException("model == null");
        raw.save(resolvedForSave(model));
    }

    /**
     * Moves the default flag to one model.
     *
     * @param id model identifier; unknown identifiers leave no default selected
     */
    @Override
    public void select(String id) {
        raw.select(id);
    }

    /**
     * Deletes the given models and reassigns the default when needed.
     *
     * @param ids model identifiers to delete; null or empty leaves the store untouched
     */
    @Override
    public void delete(List<String> ids) {
        raw.delete(ids);
    }

    /**
     * Seeds the first model from the legacy single-model settings keys.
     *
     * @param settings settings store holding the legacy keys
     */
    @Override
    public void seedFromSettings(SettingsStore settings) {
        raw.seedFromSettings(settings);
    }

    /** Applies provider resolution to one model; null stays null and local models pass through. */
    private ModelConfig resolve(ModelConfig model) {
        if (model == null || model.protocol == ModelConfig.Protocol.LOCAL_GGUF) return model;
        ProviderConfig provider = providers.find(model.providerId);
        if (provider == null) return model;
        return model.toBuilder()
                .endpoint(provider.id, model.modelId, provider.baseUrl)
                .providerLabel(provider.name)
                .protocol(provider.protocol)
                .secret(provider.apiKey)
                .advanced(model.reasoningField, mergeHeaders(provider.headers, model.headers))
                .build();
    }

    /** Resolves the provider for a save, creating an implicit one when the model has none. */
    private ModelConfig resolvedForSave(ModelConfig model) {
        if (model.protocol == ModelConfig.Protocol.LOCAL_GGUF) return model;
        ProviderConfig provider = providers.find(model.providerId);
        if (provider == null) {
            provider = ProviderFactory.fromModel(model, Ids.prefixed("provider"));
            providers.save(provider);
        }
        return model.toBuilder()
                .endpoint(provider.id, model.modelId, provider.baseUrl)
                .providerLabel(provider.name)
                .protocol(provider.protocol)
                .secret(provider.apiKey)
                .build();
    }

    /**
     * Merges provider headers with model headers; the model wins when a name occurs in both.
     *
     * @param providerHeaders headers shared by every model of the provider
     * @param modelHeaders headers specific to one model
     * @return the merged list with provider order kept and model-only headers appended
     */
    static List<HttpHeader> mergeHeaders(List<HttpHeader> providerHeaders,
                                         List<HttpHeader> modelHeaders) {
        List<HttpHeader> merged = new ArrayList<>(providerHeaders);
        for (HttpHeader header : modelHeaders) {
            int existing = indexOfName(merged, header.name);
            if (existing >= 0) {
                merged.set(existing, header);
            } else {
                merged.add(header);
            }
        }
        return merged;
    }

    /** Returns the index of the header with the given name, or -1 when absent. */
    private static int indexOfName(List<HttpHeader> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).name.equals(name)) return i;
        }
        return -1;
    }
}
