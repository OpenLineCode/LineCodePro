package cn.lineai.feature.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ReasoningLevel;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.TodoItem;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.protocol.ReasoningSpec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatTurnConfigTest {

    private AgentExtension agent(boolean enabled) {
        return AgentExtension.builder()
                .name("Test Fixer")
                .slug("test-fixer")
                .trigger("when tests fail")
                .prompt("Fix failing tests.")
                .toolNames(Arrays.asList("read_file", "edit_file"))
                .enabled(enabled)
                .build();
    }

    @Test public void agentSectionIncludesOnlyEnabledAgents() {
        String section = ChatTurnConfig.agentSection(Arrays.asList(
                agent(true), AgentExtension.builder().name("Hidden").slug("hidden")
                        .prompt("secret").enabled(false).build()));
        assertTrue(section.contains("Test Fixer"));
        assertTrue(section.contains("when tests fail"));
        assertTrue(section.contains("read_file"));
        assertFalse(section.contains("Hidden"));
    }

    @Test public void emptyOrMissingAgentsProduceNoSection() {
        assertEquals("", ChatTurnConfig.agentSection(null));
        assertEquals("", ChatTurnConfig.agentSection(Collections.<AgentExtension>emptyList()));
        assertEquals("", ChatTurnConfig.agentSection(
                Collections.singletonList(agent(false))));
    }

    @Test public void skillSectionIncludesOnlyEnabledSkills() {
        Skill enabled = Skill.builder().name("Review").description("Android review")
                .content("# Review\nCheck native views.").enabled(true).build();
        Skill disabled = Skill.builder().name("Hidden").content("secret").enabled(false).build();
        String section = ChatTurnConfig.skillSection(Arrays.asList(enabled, disabled));
        assertTrue(section.contains("Review"));
        assertTrue(section.contains("Check native views."));
        assertFalse(section.contains("Hidden"));
    }

    @Test public void todoSectionMarksStatuses() {
        String section = ChatTurnConfig.todoSection(Arrays.asList(
                new TodoItem("done", TodoItem.Status.COMPLETED),
                new TodoItem("now", TodoItem.Status.IN_PROGRESS),
                new TodoItem("later", TodoItem.Status.PENDING)));
        assertTrue(section.contains("[x] done"));
        assertTrue(section.contains("[~] now"));
        assertTrue(section.contains("[ ] later"));
    }

    @Test public void reasoningSpecReadsThePerModelKey() {
        FakeSettings settings = new FakeSettings();
        ModelConfig model = model("m1", "deepseek-v4", ModelConfig.Protocol.OPENAI_COMPATIBLE,
                Arrays.asList(level(ReasoningLevel.PRESET_LOW), level(ReasoningLevel.PRESET_HIGH)));
        settings.values.put("model.m1.reasoning", "high");

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, model);

        assertEquals("high", spec.requestedEffort);
        assertEquals("high", spec.explicitValue);
        assertEquals(ModelConfig.Protocol.OPENAI_COMPATIBLE, spec.protocol);
        assertTrue(spec.enabled());
        assertTrue(spec.replayReasoning);
    }

    @Test public void reasoningSpecFallsBackToTheFirstModelLevel() {
        FakeSettings settings = new FakeSettings();
        ModelConfig model = model("m2", "gpt-5.6", ModelConfig.Protocol.CODEX_RESPONSES,
                Arrays.asList(level(ReasoningLevel.PRESET_MINIMAL),
                        level(ReasoningLevel.PRESET_HIGH)));
        settings.values.put("model.m2.reasoning", "unknown-level");

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, model);

        assertEquals(ReasoningLevel.PRESET_MINIMAL, spec.requestedEffort);
        assertFalse(spec.replayReasoning);
        assertEquals(ModelConfig.Protocol.CODEX_RESPONSES, spec.protocol);
    }

    @Test public void reasoningSpecUsesTheProfileLevelsWithoutExplicitLevels() {
        FakeSettings settings = new FakeSettings();
        ModelConfig model = model("m3", "claude-sonnet-5-5",
                ModelConfig.Protocol.ANTHROPIC_MESSAGES, Collections.<ReasoningLevel>emptyList());

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, model);

        assertEquals(ReasoningLevel.PRESET_LOW, spec.requestedEffort);
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, spec.protocol);
    }

    @Test public void reasoningSpecCarriesTheExplicitModelField() {
        FakeSettings settings = new FakeSettings();
        ModelConfig model = ModelConfig.builder().identity("m4", "Custom")
                .endpoint("p", "custom", "https://example.com/v1")
                .reasoningLevels(Collections.singletonList(level(ReasoningLevel.PRESET_HIGH)))
                .advanced("reasoning.effort", Collections.<cn.lineai.core.model.HttpHeader>emptyList())
                .build();

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, model);

        assertEquals("reasoning.effort", spec.explicitField);
        assertEquals(ReasoningLevel.PRESET_HIGH, spec.explicitValue);
    }

    @Test public void reasoningSpecLocksUnsupportedModelsOff() {
        FakeSettings settings = new FakeSettings();
        settings.values.put("model.m5.reasoning", "high");
        ModelConfig model = ModelConfig.builder().identity("m5", "No reasoning")
                .endpoint("p", "model", "https://example.com/v1")
                .reasoningLevels(Collections.singletonList(level(ReasoningLevel.PRESET_HIGH)))
                .multimodal(false, false, false)
                .build();

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, model);

        assertFalse(spec.enabled());
    }

    @Test public void reasoningSpecFallsBackToTheLegacyPreferenceWithoutAModel() {
        FakeSettings settings = new FakeSettings();
        settings.values.put("ai.reasoning", "max");

        ReasoningSpec spec = ChatTurnConfig.reasoningSpec(settings, null);

        assertEquals("max", spec.requestedEffort);
        assertTrue(spec.enabled());
    }

    @Test public void systemPromptFallsBackToAgentSectionWithoutAssembler() {
        String section = ChatTurnConfig.agentSection(
                Collections.singletonList(agent(true)));
        assertEquals(section, ChatTurnConfig.systemPrompt(null, null, null, section, "", ""));
    }

    private static ReasoningLevel level(String preset) {
        return ReasoningLevel.preset(preset);
    }

    private static ModelConfig model(String id, String modelId, ModelConfig.Protocol protocol,
                                     java.util.List<ReasoningLevel> levels) {
        return ModelConfig.builder().identity(id, id)
                .endpoint("p", modelId, "https://example.com/v1")
                .protocol(protocol)
                .reasoningLevels(levels)
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
