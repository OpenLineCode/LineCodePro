package cn.lineai.core.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModelProfileTest {

    @Test public void formatsClaudeIdentifiers() {
        assertEquals("Claude Sonnet 5.5", ModelProfile.displayName("claude-sonnet-5-5"));
        assertEquals("Claude Opus 5", ModelProfile.displayName("claude-opus-5"));
        assertEquals("Claude Fable 5.1", ModelProfile.displayName("claude-fable-5-1"));
        assertEquals("Claude Sonnet 4.6", ModelProfile.displayName("claude-sonnet-4-6"));
    }

    @Test public void formatsOtherFamilies() {
        assertEquals("GPT-5.6", ModelProfile.displayName("gpt-5.6"));
        assertEquals("GPT-5", ModelProfile.displayName("gpt-5"));
        assertEquals("DeepSeek V4.1 Flash", ModelProfile.displayName("deepseek-v4.1-flash"));
        assertEquals("Kimi K2.7 Code", ModelProfile.displayName("kimi-k2.7-code"));
        assertEquals("GLM 5.2", ModelProfile.displayName("glm-5.2"));
        assertEquals("Qwen 3.7 Max", ModelProfile.displayName("qwen3.7-max"));
        assertEquals("MiniMax M3", ModelProfile.displayName("minimax-m3"));
        assertEquals("MiMo V2.5", ModelProfile.displayName("mimo-v2.5"));
    }

    @Test public void stripsVendorPrefixesAndKeepsCanonicalBrandCasing() {
        assertEquals("DeepSeek V4.1", ModelProfile.displayName("deepseek/deepseek-v4.1"));
        assertEquals("MiniMax M2.7", ModelProfile.displayName("MiniMaxAI/MiniMax-M2.7"));
        assertEquals("Qwen 3.6 Max", ModelProfile.displayName("Qwen/Qwen3.6-Max"));
        assertEquals("Claude Sonnet 5.5", ModelProfile.displayName("anthropic/claude-sonnet-5-5"));
        assertEquals("GPT-5", ModelProfile.displayName("openai/gpt-5"));
    }

    @Test public void keepsUnknownIdentifiersReadable() {
        assertEquals("My Model", ModelProfile.displayName("my-model"));
        assertEquals("", ModelProfile.displayName("  "));
    }

    @Test public void infersMillionContextForDocumentedThresholds() {
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("claude-sonnet-5"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("gpt-5"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("deepseek-v4"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("kimi-k2.7"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("glm-5.2"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("qwen3.7-max"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("MiniMax-M3"));
        assertEquals(ModelProfile.MILLION_CONTEXT, ModelProfile.contextSize("mimo-v2.5"));
    }

    @Test public void keepsDefaultContextBelowThresholdsAndForUnknownFamilies() {
        assertEquals(ModelProfile.DEFAULT_CONTEXT, ModelProfile.contextSize("claude-sonnet-4-6"));
        assertEquals(ModelProfile.DEFAULT_CONTEXT, ModelProfile.contextSize("gpt-4o"));
        assertEquals(ModelProfile.DEFAULT_CONTEXT, ModelProfile.contextSize("kimi-k2"));
        assertEquals(ModelProfile.DEFAULT_CONTEXT, ModelProfile.contextSize("glm-5.1"));
        assertEquals(ModelProfile.DEFAULT_CONTEXT, ModelProfile.contextSize("unknown-model"));
    }

    @Test public void infersImageSupportForDocumentedFamilies() {
        assertTrue(ModelProfile.supportsVision("claude-sonnet-5"));
        assertTrue(ModelProfile.supportsVision("gpt-5"));
        assertTrue(ModelProfile.supportsVision("deepseek-v4.1-flash"));
        assertFalse(ModelProfile.supportsVision("deepseek-v4.1-pro"));
        assertFalse(ModelProfile.supportsVision("glm-5.2"));
        assertFalse(ModelProfile.supportsVideo("gpt-5"));
    }

    @Test public void marksForcedThinkingFamiliesAsNotToggleable() {
        assertFalse(ModelProfile.reasoningToggleable("claude-fable-5"));
        assertFalse(ModelProfile.reasoningToggleable("claude-mythos-5"));
        assertFalse(ModelProfile.reasoningToggleable("kimi-k3"));
        assertFalse(ModelProfile.reasoningToggleable("minimax-m3"));
        assertFalse(ModelProfile.reasoningToggleable("glm-5.3"));
        assertTrue(ModelProfile.reasoningToggleable("kimi-k2.7"));
        assertTrue(ModelProfile.reasoningToggleable("deepseek-v4.1"));
        assertTrue(ModelProfile.reasoningToggleable("gpt-5"));
    }

    @Test public void exposesFamilyReasoningLevels() {
        List<ReasoningLevel> claude = ModelProfile.reasoningLevels("claude-sonnet-5");
        assertEquals(4, claude.size());
        assertEquals(ReasoningLevel.PRESET_LOW, claude.get(0).value);
        assertEquals(ReasoningLevel.PRESET_MAX, claude.get(3).value);

        List<ReasoningLevel> kimi = ModelProfile.reasoningLevels("kimi-k2.7");
        assertEquals(3, kimi.size());
        assertEquals(ReasoningLevel.PRESET_HIGH, kimi.get(1).value);

        List<ReasoningLevel> mimo = ModelProfile.reasoningLevels("mimo-v2.5");
        assertEquals(1, mimo.size());
        assertEquals(ReasoningLevel.PRESET_ON, mimo.get(0).value);
    }

    @Test public void replaysReasoningOnlyForFamiliesThatRequireIt() {
        assertTrue(ModelProfile.replaysReasoning("deepseek-v4.1"));
        assertTrue(ModelProfile.replaysReasoning("kimi-k3"));
        assertTrue(ModelProfile.replaysReasoning("mimo-v2.6"));
        assertFalse(ModelProfile.replaysReasoning("claude-sonnet-5"));
        assertFalse(ModelProfile.replaysReasoning("gpt-5"));
    }
}
