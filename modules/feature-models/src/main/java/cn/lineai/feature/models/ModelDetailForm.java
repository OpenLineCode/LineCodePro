package cn.lineai.feature.models;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ReasoningLevel;

/**
 * Pure editable state and validation of the model-detail form.
 *
 * <p>The form keeps a reference to the configuration it was loaded from and applies only the
 * fields the detail page exposes, so sampling, thinking effort, default/enabled flags and local
 * inference options survive an edit even though the screen does not show them. Plain strings back
 * the inputs; parsing and validation run only when the page saves. No Android type is referenced,
 * so the rules are unit-testable on the JVM.</p>
 */
final class ModelDetailForm {

    /** Output-token cap used when a new configuration has to be created without a base model. */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /** Validation outcomes; the page maps each value to a localized message. */
    enum Error {
        /** Form is ready to save. */
        NONE,
        /** Tool call limit is not {@code -1} or a non-negative integer. */
        TOOL_RANGE,
        /** Context size is non-empty but does not parse to a positive token count. */
        CONTEXT_RANGE,
        /** Manual compression mode was selected without a compression model id. */
        COMPRESSION_ID
    }

    private final ModelConfig base;
    String name = "";
    String contextSize = "";
    String toolCallLimit = String.valueOf(ModelConfig.DEFAULT_TOOL_CALL_LIMIT);
    boolean supportsReasoning = true;
    boolean supportsVision;
    boolean supportsVideo;
    boolean compressionEnabled;
    boolean compressionAuto = true;
    String compressionModelId = "";
    List<ReasoningLevel> reasoningLevels = new ArrayList<>();
    String reasoningField = "";
    List<HttpHeader> headers = new ArrayList<>();

    /** Not instantiable from outside; use {@link #from(ModelConfig)}. */
    private ModelDetailForm(ModelConfig base) {
        this.base = base;
    }

    /**
     * Copies a stored configuration into editable form state.
     *
     * @param model configuration to copy; null yields a blank form with the documented defaults
     * @return a new form carrying the editable fields of the configuration
     */
    static ModelDetailForm from(ModelConfig model) {
        ModelDetailForm form = new ModelDetailForm(model);
        if (model == null) return form;
        form.name = model.name;
        form.contextSize = model.contextSize > 0 ? ContextSizes.format(model.contextSize) : "";
        form.toolCallLimit = String.valueOf(model.toolCallLimit);
        form.supportsReasoning = model.supportsReasoning;
        form.supportsVision = model.supportsVision;
        form.supportsVideo = model.supportsVideo;
        form.compressionEnabled = model.compressionEnabled;
        form.compressionAuto = model.compressionAuto;
        form.compressionModelId = model.compressionModelId;
        form.reasoningLevels = new ArrayList<>(model.reasoningLevels);
        form.reasoningField = model.reasoningField;
        form.headers = new ArrayList<>(model.headers);
        return form;
    }

    /**
     * Returns the trimmed display name.
     *
     * @return trimmed name, or an empty string when blank
     */
    String displayName() {
        return name == null ? "" : name.trim();
    }

    /**
     * Parses the context-size field into a token count.
     *
     * @return parsed token count, or the unset sentinel for blank input
     */
    int contextTokens() {
        return ContextSizes.parse(contextSize);
    }

    /**
     * Reports whether the context field is blank (app default) or a positive token count.
     *
     * @return true when the value may be saved
     */
    boolean contextValid() {
        String value = contextSize == null ? "" : contextSize.trim();
        return value.isEmpty() || contextTokens() > ContextSizes.UNSET;
    }

    /**
     * Parses the tool-call limit field.
     *
     * @return the parsed limit, or null when blank, non-numeric or below the unlimited floor
     */
    Integer toolCallLimit() {
        if (toolCallLimit == null || toolCallLimit.trim().isEmpty()) return null;
        try {
            int value = Integer.parseInt(toolCallLimit.trim());
            if (value < ModelConfig.UNLIMITED_TOOL_CALLS) return null;
            return value;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /**
     * Checks whether manual compression is selected without a compression model id.
     *
     * @return true when a compression model id is required but missing
     */
    boolean needsCompressionId() {
        return compressionEnabled && !compressionAuto && compressionModelId.trim().isEmpty();
    }

    /**
     * Validates every field the save path needs.
     *
     * @return the first blocking error, or {@link Error#NONE}
     */
    Error validateForSave() {
        if (toolCallLimit() == null) return Error.TOOL_RANGE;
        if (!contextValid()) return Error.CONTEXT_RANGE;
        if (needsCompressionId()) return Error.COMPRESSION_ID;
        return Error.NONE;
    }

    /**
     * Save gate mirroring {@link #validateForSave}.
     *
     * @return true when the form satisfies every save requirement
     */
    boolean canSave() {
        return validateForSave() == Error.NONE;
    }

    /**
     * Returns the editable reasoning levels in display order.
     *
     * <p>Built-in rows keep their preset and value; a custom row with a blank label falls back to
     * its wire value, and rows without a value are dropped because the engine cannot send them.</p>
     *
     * @return a new list of sendable levels; never null
     */
    List<ReasoningLevel> sendableLevels() {
        List<ReasoningLevel> result = new ArrayList<>();
        if (reasoningLevels == null) return result;
        for (ReasoningLevel level : reasoningLevels) {
            if (level == null || level.value == null || level.value.trim().isEmpty()) continue;
            String value = level.value.trim();
            String label = level.label == null ? "" : level.label.trim();
            boolean custom = level.preset == null || level.preset.trim().isEmpty();
            result.add(new ReasoningLevel(custom ? "" : level.preset.trim(),
                    custom && label.isEmpty() ? value : label, value));
        }
        return result;
    }

    /**
     * Returns the non-blank header rows in the order the user entered them.
     *
     * @return a new list of named headers; never null
     */
    List<HttpHeader> normalizedHeaders() {
        List<HttpHeader> result = new ArrayList<>();
        if (headers == null) return result;
        for (HttpHeader header : headers) {
            if (header == null) continue;
            String headerName = header.name == null ? "" : header.name.trim();
            if (headerName.isEmpty()) continue;
            result.add(new HttpHeader(headerName, header.value == null ? "" : header.value.trim()));
        }
        return result;
    }

    /**
     * Builds the immutable configuration from the editable fields, preserving every field the
     * detail page does not expose.
     *
     * @return a new {@link ModelConfig}; callers must run {@link #validateForSave} first
     */
    ModelConfig build() {
        ModelConfig.Builder builder = base != null ? base.toBuilder() : ModelConfig.builder();
        int outputLimit = base == null ? DEFAULT_MAX_TOKENS : base.maxTokens;
        return builder
                .identity(base == null ? "" : base.id, displayName())
                .context(contextTokens(), outputLimit)
                .toolCalls(toolCallLimit())
                .compression(compressionEnabled, compressionAuto,
                        compressionModelId == null ? "" : compressionModelId.trim())
                .multimodal(supportsReasoning, supportsVision, supportsVideo)
                .reasoningLevels(sendableLevels())
                .advanced(reasoningField == null ? "" : reasoningField.trim(), normalizedHeaders())
                .build();
    }
}
