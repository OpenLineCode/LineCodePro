package cn.lineai.feature.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ReasoningLevel;
import cn.lineai.data.api.SettingsStore;

import static org.junit.Assert.assertEquals;

public class ModelReasoningTest {

    @Test
    public void keyUsesTheModelConfigurationId() {
        ModelConfig model = ModelConfig.builder().identity("cfg-1", "Model")
                .endpoint("p", "gpt-5.6", "https://example.com/v1").build();
        assertEquals("model.cfg-1.reasoning", ModelReasoning.key(model));
    }

    @Test
    public void explicitLevelsWinOverTheFamilyProfile() {
        ModelConfig model = ModelConfig.builder().identity("cfg", "Model")
                .endpoint("p", "claude-sonnet-5-5", "https://example.com/v1")
                .reasoningLevels(Arrays.asList(ReasoningLevel.preset(ReasoningLevel.PRESET_MINIMAL),
                        ReasoningLevel.preset(ReasoningLevel.PRESET_MAX)))
                .build();
        assertEquals(2, ModelReasoning.levels(model).size());
        assertEquals(ReasoningLevel.PRESET_MINIMAL, ModelReasoning.levels(model).get(0).preset);
    }

    @Test
    public void selectedHonoursTheStoredValue() {
        FakeSettings settings = new FakeSettings();
        ModelConfig model = modelWithLevels();
        settings.values.put("model.cfg.reasoning", "high");
        assertEquals("high", ModelReasoning.selected(settings, model));
    }

    @Test
    public void selectedFallsBackToTheFirstLevel() {
        FakeSettings settings = new FakeSettings();
        settings.values.put("model.cfg.reasoning", "stale-level");
        assertEquals("low", ModelReasoning.selected(settings, modelWithLevels()));
    }

    @Test
    public void selectedFallsBackToTheLegacyPreferenceForAModelWithoutLevels() {
        FakeSettings settings = new FakeSettings();
        settings.values.put("ai.reasoning", "max");
        assertEquals("max", ModelReasoning.selected(settings, null));
    }

    private static ModelConfig modelWithLevels() {
        return ModelConfig.builder().identity("cfg", "Model")
                .endpoint("p", "custom", "https://example.com/v1")
                .reasoningLevels(Arrays.asList(ReasoningLevel.preset(ReasoningLevel.PRESET_LOW),
                        ReasoningLevel.preset(ReasoningLevel.PRESET_HIGH)))
                .build();
    }

    /** In-memory settings store with the documented fallback behaviour. */
    private static final class FakeSettings implements SettingsStore {
        final Map<String, String> values = new HashMap<>();

        @Override public String get(String key, String fallback) {
            return values.containsKey(key) ? values.get(key) : fallback;
        }

        @Override public int getInt(String key, int fallback) {
            String value = values.get(key);
            return value == null ? fallback : Integer.parseInt(value);
        }

        @Override public void put(String key, String value) {
            values.put(key, value);
        }

        @Override public void remove(String key) {
            values.remove(key);
        }

        @Override public void invalidate() {
        }

        @Override public Map<String, String> all() {
            return values;
        }
    }
}
