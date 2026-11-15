package cn.lineai.feature.models;

import java.util.List;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.ReasoningLevel;
import org.junit.Test;
import static org.junit.Assert.*;

/** Inference rules applied to newly created model rows and to re-detection. */
public class ModelProfileInferenceTest {

    @Test public void newModelGetsEveryInferredField() {
        ModelConfig model = ModelInference.infer("provider_1", "Example",
                ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.example.com/v1", "key",
                "claude-sonnet-5-5");
        assertTrue(model.id.startsWith("model_"));
        assertEquals("provider_1", model.providerId);
        assertEquals("Example", model.providerLabel);
        assertEquals("claude-sonnet-5-5", model.modelId);
        assertEquals("Claude Sonnet 5.5", model.displayName());
        assertEquals(ModelProfile.MILLION_CONTEXT, model.contextSize);
        assertEquals(ModelProfile.DEFAULT_MAX_TOKENS, model.maxTokens);
        assertTrue(model.supportsReasoning);
        assertTrue(model.supportsVision);
        assertFalse(model.supportsVideo);
        assertEquals(ModelProfile.reasoningLevels("claude-sonnet-5-5"), model.reasoningLevels);
        assertTrue(model.reasoningField.isEmpty());
        assertEquals(ModelConfig.Protocol.OPENAI_COMPATIBLE, model.protocol);
        assertEquals("https://api.example.com/v1", model.baseUrl);
        assertEquals("key", model.apiKey);
    }

    @Test public void familyDefaultsFollowTheIdentifier() {
        ModelConfig gpt = ModelInference.infer("p", "P",
                ModelConfig.Protocol.CODEX_RESPONSES, "https://api.example.com/v1", "k", "gpt-5");
        assertEquals("GPT-5", gpt.displayName());
        assertEquals(ModelProfile.MILLION_CONTEXT, gpt.contextSize);
        assertTrue(gpt.supportsVision);
        assertTrue(hasPreset(gpt.reasoningLevels, ReasoningLevel.PRESET_MINIMAL));

        ModelConfig deepseek = ModelInference.infer("p", "P",
                ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.example.com/v1", "k",
                "deepseek-v4.1-flash");
        assertEquals(ModelProfile.MILLION_CONTEXT, deepseek.contextSize);
        assertTrue(deepseek.supportsVision);

        ModelConfig unknown = ModelInference.infer("p", "P",
                ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.example.com/v1", "k",
                "custom-model");
        assertEquals(ModelProfile.DEFAULT_CONTEXT, unknown.contextSize);
        assertFalse(unknown.supportsVision);
        assertEquals("Custom Model", unknown.displayName());
    }

    @Test public void redetectionOverwritesOnlyProfileFields() {
        ModelDetailForm form = ModelDetailForm.from(ModelConfig.builder()
                .identity("m1", "My name")
                .endpoint("p1", "gpt-5", "https://api.example.com/v1")
                .toolCalls(17)
                .context(64000, 4096)
                .multimodal(true, false, false)
                .reasoningLevels(java.util.Collections.singletonList(
                        ReasoningLevel.preset(ReasoningLevel.PRESET_OFF)))
                .advanced("reasoning.effort", new java.util.ArrayList<>())
                .build());
        form.reasoningLevels.clear();
        form.reasoningLevels.add(ReasoningLevel.preset(ReasoningLevel.PRESET_OFF));
        ModelInference.apply(form, "claude-sonnet-5-5");
        assertEquals("1M", form.contextSize);
        assertTrue(form.supportsReasoning);
        assertTrue(form.supportsVision);
        assertFalse(form.supportsVideo);
        assertEquals(ModelProfile.reasoningLevels("claude-sonnet-5-5"), form.reasoningLevels);
        assertEquals("17", form.toolCallLimit);
        assertEquals("reasoning.effort", form.reasoningField);
        assertEquals("My name", form.name);
    }

    @Test public void displayNameFallsBackGracefully() {
        assertEquals("", ModelInference.displayName(null));
        assertEquals("", ModelInference.displayName("   "));
        assertEquals("Kimi K2.7", ModelInference.displayName("kimi-k2.7"));
    }

    /** True when the levels contain the preset. */
    private static boolean hasPreset(List<ReasoningLevel> levels, String preset) {
        for (ReasoningLevel level : levels) {
            if (level.preset.equals(preset)) return true;
        }
        return false;
    }
}
