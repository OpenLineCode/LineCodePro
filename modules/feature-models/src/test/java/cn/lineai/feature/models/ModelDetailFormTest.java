package cn.lineai.feature.models;

import java.util.List;

import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ReasoningLevel;
import org.junit.Test;
import static org.junit.Assert.*;

/** Validation and preservation rules of the pure model-detail form. */
public class ModelDetailFormTest {

    @Test public void requiresValidToolLimitAndContext() {
        ModelDetailForm form = ModelDetailForm.from(baseModel());
        form.toolCallLimit = "abc";
        assertEquals(ModelDetailForm.Error.TOOL_RANGE, form.validateForSave());
        form.toolCallLimit = "-2";
        assertEquals(ModelDetailForm.Error.TOOL_RANGE, form.validateForSave());
        form.toolCallLimit = "-1";
        assertEquals(ModelDetailForm.Error.NONE, form.validateForSave());
        form.contextSize = "lots";
        assertEquals(ModelDetailForm.Error.CONTEXT_RANGE, form.validateForSave());
        assertFalse(form.canSave());
        form.contextSize = "128k";
        assertEquals(ModelDetailForm.Error.NONE, form.validateForSave());
        assertEquals(128000, form.contextTokens());
    }

    @Test public void emptyContextFallsBackToUnset() {
        ModelDetailForm form = ModelDetailForm.from(baseModel());
        form.contextSize = "";
        assertTrue(form.contextValid());
        assertEquals(ContextSizes.UNSET, form.contextTokens());
        assertEquals(ContextSizes.UNSET, form.build().contextSize);
    }

    @Test public void compressionRequiresModelIdWhenNotAutomatic() {
        ModelDetailForm form = ModelDetailForm.from(baseModel());
        form.compressionEnabled = true;
        form.compressionAuto = false;
        assertEquals(ModelDetailForm.Error.COMPRESSION_ID, form.validateForSave());
        form.compressionModelId = "compress";
        assertEquals(ModelDetailForm.Error.NONE, form.validateForSave());
        form.compressionAuto = true;
        form.compressionModelId = "";
        assertEquals(ModelDetailForm.Error.NONE, form.validateForSave());
    }

    @Test public void buildPreservesFieldsThePageDoesNotEdit() {
        ModelConfig original = baseModel();
        ModelDetailForm form = ModelDetailForm.from(original);
        form.name = "Renamed model";
        form.contextSize = "1m";
        form.toolCallLimit = "42";
        form.supportsVision = false;
        form.supportsVideo = true;
        ModelConfig saved = form.build();
        assertEquals(original.id, saved.id);
        assertEquals("Renamed model", saved.name);
        assertEquals(1000000, saved.contextSize);
        assertEquals(2048, saved.maxTokens);
        assertEquals(42, saved.toolCallLimit);
        assertEquals(original.temperature, saved.temperature, 0.0001f);
        assertEquals(original.reasoningEffort, saved.reasoningEffort);
        assertEquals(original.protocol, saved.protocol);
        assertEquals(original.baseUrl, saved.baseUrl);
        assertEquals(original.apiKey, saved.apiKey);
        assertEquals(original.providerId, saved.providerId);
        assertTrue(saved.isDefault);
        assertFalse(saved.enabled);
        assertTrue(saved.supportsTools);
        assertFalse(saved.supportsVision);
        assertTrue(saved.supportsVideo);
    }

    @Test public void sendableLevelsDropBlankValuesAndNameCustomRows() {
        ModelDetailForm form = ModelDetailForm.from(baseModel());
        form.reasoningLevels.clear();
        form.reasoningLevels.add(ReasoningLevel.preset(ReasoningLevel.PRESET_LOW));
        form.reasoningLevels.add(new ReasoningLevel("", "", ""));
        form.reasoningLevels.add(new ReasoningLevel("", "", "turbo"));
        form.reasoningLevels.add(new ReasoningLevel("", "Turbo", "turbo-2"));
        List<ReasoningLevel> saved = form.build().reasoningLevels;
        assertEquals(3, saved.size());
        assertEquals(ReasoningLevel.PRESET_LOW, saved.get(0).preset);
        assertEquals("turbo", saved.get(1).label);
        assertEquals("Turbo", saved.get(2).label);
    }

    @Test public void headersAreNormalizedAndReasoningFieldTrimmed() {
        ModelDetailForm form = ModelDetailForm.from(baseModel());
        form.headers.add(new HttpHeader("", "orphan"));
        form.headers.add(new HttpHeader(" X-Title ", " LineCode "));
        form.reasoningField = "  reasoning.effort  ";
        ModelConfig saved = form.build();
        assertEquals(1, saved.headers.size());
        assertEquals("X-Title", saved.headers.get(0).name);
        assertEquals("reasoning.effort", saved.reasoningField);
    }

    @Test public void formatsContextForEditingWithoutLosingPrecision() {
        ModelConfig model = baseModel().toBuilder().context(131072, 2048).build();
        ModelDetailForm form = ModelDetailForm.from(model);
        assertEquals("131.072K", form.contextSize);
        assertEquals(131072, form.contextTokens());
    }

    private ModelConfig baseModel() {
        return ModelConfig.builder()
                .identity("model_1", "Base model")
                .endpoint("provider_1", "gpt-5", "https://api.example.com/v1")
                .providerLabel("Example")
                .toolCalls(200)
                .compression(true, true, "")
                .secret("key")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .context(64000, 2048)
                .sampling(0.3f, ModelConfig.ReasoningEffort.HIGH)
                .flags(true, false)
                .capabilities(true, true)
                .multimodal(true, true, false)
                .build();
    }
}
