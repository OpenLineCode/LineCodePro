package cn.lineai.feature.models;

import java.util.ArrayList;

import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.ReasoningLevel;

/**
 * Applies the automatic {@link ModelProfile} to model configurations.
 *
 * <p>Providers rarely publish context size or modality, so newly created rows start from the
 * family profile and the user overrides whatever is wrong on the model-detail page. The same
 * profile feeds "Run automatic detection again"; the screen only overwrites the fields the profile
 * owns, never the identifiers or the user-defined reasoning field. All methods are pure, which
 * keeps the inference rules unit-testable without a device.</p>
 */
final class ModelInference {

    /** Not instantiable; every operation is static. */
    private ModelInference() {
    }

    /**
     * Formats a model identifier for the display-name column, tolerating blank input.
     *
     * @param modelId provider-side model identifier; may be null
     * @return a readable display name, or an empty string for a blank identifier
     */
    static String displayName(String modelId) {
        return ModelProfile.displayName(modelId);
    }

    /**
     * Builds a brand-new model configuration from the family profile of {@code modelId}.
     *
     * @param providerId owning provider identifier
     * @param providerLabel provider display name
     * @param protocol wire protocol inherited from the provider
     * @param baseUrl provider base URL
     * @param apiKey provider credential
     * @param modelId provider-side model identifier
     * @return a new configuration carrying a fresh identifier and every inferred field
     */
    static ModelConfig infer(String providerId, String providerLabel, ModelConfig.Protocol protocol,
                             String baseUrl, String apiKey, String modelId) {
        String name = displayName(modelId);
        return ModelConfig.builder()
                .identity(Ids.prefixed("model"), name)
                .endpoint(providerId, modelId, baseUrl)
                .providerLabel(providerLabel)
                .secret(apiKey)
                .protocol(protocol)
                .context(ModelProfile.contextSize(modelId), ModelProfile.DEFAULT_MAX_TOKENS)
                .multimodal(ModelProfile.supportsReasoning(modelId),
                        ModelProfile.supportsVision(modelId),
                        ModelProfile.supportsVideo(modelId))
                .reasoningLevels(ModelProfile.reasoningLevels(modelId))
                .advanced(ModelProfile.reasoningField(modelId), new ArrayList<>())
                .build();
    }

    /**
     * Re-applies the family profile to the editable fields of a model-detail form.
     *
     * <p>Only the fields the profile owns are replaced (context, reasoning switch, image, video and
     * the reasoning levels); the display name, model id, tool-call limit, compression settings and
     * the custom reasoning field keep their current values so a re-detection never discards a user
     * override that the profile cannot know about.</p>
     *
     * @param form form to update in place; null leaves the state untouched
     * @param modelId provider-side model identifier driving the profile
     */
    static void apply(ModelDetailForm form, String modelId) {
        if (form == null) return;
        form.contextSize = ContextSizes.format(ModelProfile.contextSize(modelId));
        form.supportsReasoning = ModelProfile.supportsReasoning(modelId);
        form.supportsVision = ModelProfile.supportsVision(modelId);
        form.supportsVideo = ModelProfile.supportsVideo(modelId);
        form.reasoningLevels = new ArrayList<ReasoningLevel>(ModelProfile.reasoningLevels(modelId));
    }
}
