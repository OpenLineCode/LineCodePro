package cn.lineai.feature.chat;

import java.util.List;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.ReasoningLevel;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.SettingsStore;

/**
 * Per-model reasoning level preferences.
 *
 * <p>The chosen level is stored under {@code model.<ModelConfig.id>.reasoning} so every model
 * keeps its own level when the user switches between them. A model that never stored a valid
 * level falls back to its first selectable level; without a selected model the legacy global
 * reasoning preference applies.</p>
 */
final class ModelReasoning {

    /** Settings key prefix of the per-model reasoning level. */
    private static final String KEY_PREFIX = "model.";
    /** Settings key suffix of the per-model reasoning level. */
    private static final String KEY_SUFFIX = ".reasoning";

    /** Static utility; not instantiable. */
    private ModelReasoning() {
    }

    /**
     * Returns the per-model settings key.
     *
     * @param model model configuration; null yields the empty string
     * @return settings key embedding the model configuration id
     */
    static String key(ModelConfig model) {
        return model == null ? "" : KEY_PREFIX + model.id + KEY_SUFFIX;
    }

    /**
     * Returns the selectable levels of a model.
     *
     * <p>Explicit levels configured on the model win; otherwise the automatic family profile
     * supplies the levels. The result is never modified and may be empty only when the model is
     * null and the profile lookup produced no level.</p>
     *
     * @param model model configuration, may be null
     * @return levels in display order
     */
    static List<ReasoningLevel> levels(ModelConfig model) {
        if (model != null && !model.reasoningLevels.isEmpty()) {
            return model.reasoningLevels;
        }
        return ModelProfile.reasoningLevels(model == null ? "" : model.modelId);
    }

    /**
     * Resolves the effective level value of a model.
     *
     * <p>A stored value is honoured only while it matches one of the model's current levels, so a
     * level removed from the model configuration cannot leak into a request.</p>
     *
     * @param settings settings store providing the per-model value
     * @param model model configuration, may be null
     * @return the effective level value; the legacy global preference when no model is selected
     */
    static String selected(SettingsStore settings, ModelConfig model) {
        if (model == null) {
            return settings.get(UiPreferences.Field.REASONING.key,
                    UiPreferences.Field.REASONING.fallback);
        }
        List<ReasoningLevel> levels = levels(model);
        String stored = settings.get(key(model), "");
        if (!stored.isEmpty() && matches(levels, stored)) {
            return stored;
        }
        if (!levels.isEmpty()) {
            return levels.get(0).value;
        }
        return settings.get(UiPreferences.Field.REASONING.key,
                UiPreferences.Field.REASONING.fallback);
    }

    /** True when {@code value} is one of the level wire values. */
    private static boolean matches(List<ReasoningLevel> levels, String value) {
        for (ReasoningLevel level : levels) {
            if (level.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
