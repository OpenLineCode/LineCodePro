package cn.lineai.core.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModelConfigAdvancedTest {

    @Test public void reasoningLevelsAndHeadersSurviveRoundTrip() {
        List<ReasoningLevel> levels = Arrays.asList(
                ReasoningLevel.preset(ReasoningLevel.PRESET_LOW),
                new ReasoningLevel("", "Custom", "custom-value"));
        List<HttpHeader> headers = Arrays.asList(new HttpHeader("X-Team", "linecode"));
        ModelConfig model = ModelConfig.builder()
                .identity("m1", "Model")
                .reasoningLevels(levels)
                .advanced("reasoning.effort", headers)
                .build();
        ModelConfig copy = model.toBuilder().build();
        assertEquals(levels, copy.reasoningLevels);
        assertEquals("reasoning.effort", copy.reasoningField);
        assertEquals(headers, copy.headers);
    }

    @Test public void blankAdvancedValuesNormalizeToEmptyCollections() {
        ModelConfig model = ModelConfig.builder().identity("m1", "Model").build();
        assertTrue(model.reasoningLevels.isEmpty());
        assertTrue(model.headers.isEmpty());
        assertEquals("", model.reasoningField);
    }

    @Test public void messageTimingRoundTrips() {
        Message message = Message.builder().id("m").role(Role.ASSISTANT)
                .tokens(1200, 300, 900).timing(480L, 5200L).build();
        assertEquals(1200, message.inputTokens);
        assertEquals(300, message.outputTokens);
        assertEquals(900, message.cachedTokens);
        assertEquals(480L, message.ttftMs);
        assertEquals(5200L, message.durationMs);
        Message copy = message.toBuilder().build();
        assertEquals(480L, copy.ttftMs);
        assertEquals(5200L, copy.durationMs);
    }

    @Test public void reasoningLevelEqualityCoversEveryField() {
        assertEquals(new ReasoningLevel("low", "", "low"), ReasoningLevel.preset("low"));
        assertTrue(!new ReasoningLevel("", "Custom", "v").equals(ReasoningLevel.preset("low")));
        assertTrue(new ReasoningLevel("", "", "").isBlank());
    }
}
