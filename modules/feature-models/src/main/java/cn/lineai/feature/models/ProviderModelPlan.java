package cn.lineai.feature.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;

/**
 * Plans the model-row writes produced by saving one provider.
 *
 * <p>The provider editor works on a list of drafts; this class turns them into the final model
 * configurations plus the identifiers that must be deleted. Existing configurations are matched
 * through the stored config they were loaded from, so a rename keeps every custom setting while a
 * removed row deletes exactly the model that was on screen. New rows are inferred from
 * {@link ModelInference}. Pure and deterministic, therefore directly unit-testable.</p>
 */
final class ProviderModelPlan {

    /** One editable row of the provider editor. */
    static final class Draft {
        final String modelId;
        final String displayName;
        final ModelConfig original;
        final boolean fetched;

        /**
         * Creates a draft row.
         *
         * @param modelId provider-side model identifier
         * @param displayName display name entered by the user, or blank for the inferred one
         * @param original stored configuration the row was loaded from, or null for a new row
         * @param fetched true when the row came from a catalog query
         */
        Draft(String modelId, String displayName, ModelConfig original, boolean fetched) {
            this.modelId = modelId == null ? "" : modelId.trim();
            this.displayName = displayName == null ? "" : displayName.trim();
            this.original = original;
            this.fetched = fetched;
        }
    }

    final List<ModelConfig> models;
    final List<String> deletedIds;

    /** Creates the immutable plan; construction is reserved for {@link #of}. */
    private ProviderModelPlan(List<ModelConfig> models, List<String> deletedIds) {
        this.models = models;
        this.deletedIds = deletedIds;
    }

    /**
     * Resolves the drafts of the provider editor into writes.
     *
     * @param provider saved provider configuration whose identity the models inherit
     * @param existing models currently stored for the provider, used to detect deletions
     * @param drafts editable rows in display order; blank model ids and duplicate ids are skipped
     * @return the model writes and the deleted identifiers
     */
    static ProviderModelPlan of(ProviderConfig provider, List<ModelConfig> existing,
                                List<Draft> drafts) {
        List<ModelConfig> saved = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        Set<String> modelIds = new HashSet<>();
        if (drafts != null) {
            for (Draft draft : drafts) {
                if (draft == null || draft.modelId.isEmpty()) continue;
                if (!modelIds.add(draft.modelId)) continue;
                saved.add(resolve(provider, draft));
                if (draft.original != null) keptIds.add(draft.original.id);
            }
        }
        if (existing != null) {
            for (ModelConfig model : existing) {
                if (model != null && !keptIds.contains(model.id)) deleted.add(model.id);
            }
        }
        return new ProviderModelPlan(saved, deleted);
    }

    /** Builds one model row, preserving an existing configuration or inferring a new one. */
    private static ModelConfig resolve(ProviderConfig provider, Draft draft) {
        String resolvedName = draft.displayName.isEmpty()
                ? ModelInference.displayName(draft.modelId) : draft.displayName;
        if (draft.original == null) {
            ModelConfig inferred = ModelInference.infer(provider.id, provider.name,
                    provider.protocol, provider.baseUrl, provider.apiKey, draft.modelId);
            return inferred.toBuilder().identity(inferred.id, resolvedName).build();
        }
        return draft.original.toBuilder()
                .identity(draft.original.id, resolvedName)
                .endpoint(provider.id, draft.modelId, provider.baseUrl)
                .providerLabel(provider.name)
                .protocol(provider.protocol)
                .secret(provider.apiKey)
                .advanced(draft.original.reasoningField,
                        withoutProviderHeaders(draft.original.headers, provider.headers))
                .build();
    }

    /**
     * Drops headers that already belong to the provider, so a re-save cannot duplicate the shared
     * header set; a model header keeps its own value when it overrides a provider header.
     */
    private static List<HttpHeader> withoutProviderHeaders(List<HttpHeader> modelHeaders,
                                                           List<HttpHeader> providerHeaders) {
        List<HttpHeader> result = new ArrayList<>();
        if (modelHeaders == null) return result;
        for (HttpHeader header : modelHeaders) {
            if (header == null) continue;
            if (contains(providerHeaders, header)) continue;
            result.add(header);
        }
        return result;
    }

    /** True when {@code headers} contains an equal header; null-safe. */
    private static boolean contains(List<HttpHeader> headers, HttpHeader target) {
        if (headers == null) return false;
        for (HttpHeader header : headers) {
            if (target.equals(header)) return true;
        }
        return false;
    }
}
