package cn.lineai.engine.protocol;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import cn.lineai.core.model.ModelConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReasoningPolicyTest {

    private JSONObject body(ReasoningSpec spec, double temperature) throws Exception {
        JSONObject body = new JSONObject();
        body.put("temperature", temperature);
        ReasoningPolicy.apply(body, spec);
        return body;
    }

    @Test
    public void deepseekSendsThinkingSwitchAndEffort() throws Exception {
        JSONObject enabled = body(spec("https://api.deepseek.com", "deepseek-chat", "medium"), 0.7);
        assertEquals("enabled", enabled.getJSONObject("thinking").getString("type"));
        assertEquals("medium", enabled.getString("reasoning_effort"));
        assertEquals("low", body(spec("https://api.deepseek.com", "deepseek-chat", "low"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("max", body(spec("https://api.deepseek.com", "deepseek-chat", "max"), 0.7)
                .getString("reasoning_effort"));
        JSONObject disabled = body(
                spec("https://api.deepseek.com", "deepseek-chat", "off"), 0.7);
        assertEquals("disabled", disabled.getJSONObject("thinking").getString("type"));
        assertFalse(disabled.has("reasoning_effort"));
    }

    @Test
    public void kimiSendsEffortWithoutThinkingField() throws Exception {
        JSONObject body = body(spec("https://api.moonshot.cn", "kimi-k2.7", "low"), 0.2);
        assertFalse(body.has("thinking"));
        assertEquals("low", body.getString("reasoning_effort"));
        assertEquals(1.0, body.getDouble("temperature"), 0.0001);
        assertEquals("high", body(spec("https://api.moonshot.cn", "kimi-k3", "medium"), 0.2)
                .getString("reasoning_effort"));
        assertEquals("max", body(spec("https://api.moonshot.cn", "kimi-k3", "max"), 0.2)
                .getString("reasoning_effort"));
        assertFalse(body(spec("https://api.moonshot.cn", "kimi-k3", "off"), 0.2)
                .has("reasoning_effort"));
    }

    @Test
    public void glmSendsThinkingSwitchAndLevels() throws Exception {
        JSONObject off = body(spec("https://open.bigmodel.cn", "glm-5", "off"), 0.7);
        assertEquals("disabled", off.getJSONObject("thinking").getString("type"));
        assertFalse(off.has("reasoning_effort"));
        JSONObject medium = body(spec("https://open.bigmodel.cn", "glm-5", "medium"), 0.7);
        assertEquals("enabled", medium.getJSONObject("thinking").getString("type"));
        assertEquals("medium", medium.getString("reasoning_effort"));
        assertEquals("max", body(spec("https://open.bigmodel.cn", "glm-5", "max"), 0.7)
                .getString("reasoning_effort"));
    }

    @Test
    public void glmPreserveKeepsTheCache() throws Exception {
        JSONObject body = body(new ReasoningSpec("https://open.bigmodel.cn", "glm-5", "",
                "high", true), 0.7);
        assertFalse(body.getBoolean("clear_thinking"));
    }

    @Test
    public void dashscopeSendsBudgetAndPreserveFlag() throws Exception {
        JSONObject body = body(new ReasoningSpec("https://dashscope.aliyuncs.com", "qwen3.7-max",
                "qwen", "high", true), 0.7);
        assertTrue(body.getBoolean("enable_thinking"));
        assertEquals(8192, body.getInt("thinking_budget"));
        assertTrue(body.getBoolean("preserve_thinking"));
        JSONObject off = body(spec("https://dashscope.aliyuncs.com", "qwen3.7-max", "off"), 0.7);
        assertFalse(off.getBoolean("enable_thinking"));
        assertFalse(off.has("thinking_budget"));
    }

    @Test
    public void minimaxSendsEffortOnly() throws Exception {
        JSONObject body = body(spec("https://api.minimax.chat", "minimax-m3", "medium"), 0.7);
        assertFalse(body.has("thinking"));
        assertEquals("medium", body.getString("reasoning_effort"));
        assertEquals("max", body(spec("https://api.minimax.chat", "minimax-m3", "max"), 0.7)
                .getString("reasoning_effort"));
        assertFalse(body(spec("https://api.minimax.chat", "minimax-m3", "off"), 0.7)
                .has("reasoning_effort"));
    }

    @Test
    public void mimoSendsThinkingSwitchOnly() throws Exception {
        JSONObject body = body(spec("https://api.example.com", "mimo-v2.5", "high"), 0.7);
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.has("reasoning_effort"));
        assertEquals("disabled", body(spec("https://api.example.com", "mimo-v2.5", "off"), 0.7)
                .getJSONObject("thinking").getString("type"));
    }

    @Test
    public void openAiReasoningModelsSendEffortWithoutAuto() throws Exception {
        assertEquals("medium", body(spec("https://api.openai.com/v1", "gpt-5.6", "auto"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("xhigh", body(spec("https://api.openai.com/v1", "o3-mini", "max"), 0.7)
                .getString("reasoning_effort"));
        assertFalse(body(spec("https://api.openai.com/v1", "gpt-5", "off"), 0.7)
                .has("reasoning_effort"));
    }

    @Test
    public void genericModelsUseNestedReasoningObject() throws Exception {
        JSONObject reasoning = body(spec("https://example.com/v1", "custom", "high"), 0.7)
                .getJSONObject("reasoning");
        assertEquals("high", reasoning.getString("effort"));
    }

    @Test
    public void explicitDottedFieldOverridesFamilyPolicy() throws Exception {
        ReasoningSpec spec = new ReasoningSpec("https://api.deepseek.com", "deepseek-chat", "",
                "high", new ReasoningSpec.Wire(false, "thinking.type", "enabled", false,
                        ModelConfig.Protocol.OPENAI_COMPATIBLE));
        JSONObject body = body(spec, 0.7);
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.has("reasoning_effort"));
    }

    @Test
    public void explicitFieldWritesTypedValues() throws Exception {
        ReasoningSpec flag = new ReasoningSpec("https://example.com/v1", "custom", "", "high",
                new ReasoningSpec.Wire(false, "reasoning.enabled", "true", false,
                        ModelConfig.Protocol.OPENAI_COMPATIBLE));
        assertTrue(body(flag, 0.7).getJSONObject("reasoning").getBoolean("enabled"));
        ReasoningSpec numeric = new ReasoningSpec("https://example.com/v1", "custom", "", "high",
                new ReasoningSpec.Wire(false, "reasoning.max_tokens", "2048", false,
                        ModelConfig.Protocol.OPENAI_COMPATIBLE));
        assertEquals(2048, body(numeric, 0.7).getJSONObject("reasoning").getInt("max_tokens"));
    }

    @Test
    public void nvidiaGatewayReceivesNoReasoningFields() throws Exception {
        JSONObject body = body(new ReasoningSpec("https://integrate.api.nvidia.com/v1", "gpt-5",
                "nvidia", "high", true), 0.7);
        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.has("reasoning"));
        assertFalse(body.has("thinking"));
    }

    @Test
    public void codecAppliesReasoningToRequestBody() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .model("deepseek-chat")
                .reasoning(new ReasoningSpec("https://api.deepseek.com", "deepseek-chat", "",
                        "low", false))
                .build();
        JSONObject body = new JSONObject(OpenAiChatCodec.requestBody(request));
        assertEquals("low", body.getString("reasoning_effort"));
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
    }

    @Test
    public void openAiParseReadsCachedPromptTokens() throws Exception {
        StreamEvent event = OpenAiChatCodec.parse("{\"choices\":[{\"delta\":{\"content\":\"hi\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":100,"
                + "\"completion_tokens\":20,\"prompt_tokens_details\":{\"cached_tokens\":60}}}");
        assertEquals(100, event.promptTokens);
        assertEquals(20, event.completionTokens);
        assertEquals(60, event.cachedTokens);
        StreamEvent fallback = OpenAiChatCodec.parse("{\"usage\":{\"prompt_tokens\":9,"
                + "\"completion_tokens\":3,\"prompt_cache_hit_tokens\":4}}");
        assertEquals(4, fallback.cachedTokens);
    }

    @Test
    public void openAiReplaysReasoningContentWhenRequested() throws Exception {
        cn.lineai.core.model.Message assistant = cn.lineai.core.model.Message.builder()
                .id("a").role(cn.lineai.core.model.Role.ASSISTANT).seq(2)
                .addBlock(cn.lineai.core.model.ContentBlock.reasoning("thought"))
                .addBlock(cn.lineai.core.model.ContentBlock.text("answer"))
                .build();
        ChatRequest replay = ChatRequest.builder()
                .model("deepseek-chat")
                .messages(Collections.singletonList(assistant))
                .reasoning(new ReasoningSpec("https://api.deepseek.com", "deepseek-chat", "",
                        "high", new ReasoningSpec.Wire(false, "", "", true,
                        ModelConfig.Protocol.OPENAI_COMPATIBLE)))
                .build();
        JSONObject message = new JSONObject(OpenAiChatCodec.requestBody(replay))
                .getJSONArray("messages").getJSONObject(0);
        assertEquals("thought", message.getString("reasoning_content"));
        ChatRequest plain = ChatRequest.builder()
                .model("deepseek-chat")
                .messages(Collections.singletonList(assistant))
                .build();
        assertFalse(new JSONObject(OpenAiChatCodec.requestBody(plain))
                .getJSONArray("messages").getJSONObject(0).has("reasoning_content"));
    }

    @Test
    public void anthropicParseSumsCacheTokensIntoInput() throws Exception {
        StreamEvent start = AnthropicCodec.parse("{\"type\":\"message_start\",\"message\":"
                + "{\"usage\":{\"input_tokens\":100,\"cache_read_input_tokens\":40,"
                + "\"cache_creation_input_tokens\":10}}}");
        assertEquals(150, start.promptTokens);
        assertEquals(40, start.cachedTokens);
        assertEquals(0, start.completionTokens);
        StreamEvent delta = AnthropicCodec.parse("{\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":25}}");
        assertEquals(25, delta.completionTokens);
        assertEquals("end_turn", delta.finishReason);
    }

    @Test
    public void anthropicClaudeFiveUsesAdaptiveThinking() throws Exception {
        JSONObject body = anthropicBody("claude-sonnet-5-5", "medium");
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"));
        assertEquals("summarized", body.getJSONObject("thinking").getString("display"));
        assertEquals("medium", body.getJSONObject("output_config").getString("effort"));
        assertFalse(body.getJSONObject("thinking").has("budget_tokens"));
        assertTrue(body.getInt("max_tokens") >= 4096);
        JSONObject legacy = anthropicBody("claude-sonnet-4-6", "medium");
        assertEquals("enabled", legacy.getJSONObject("thinking").getString("type"));
        assertTrue(legacy.getJSONObject("thinking").getInt("budget_tokens") > 0);
        assertFalse(legacy.has("output_config"));
    }

    @Test
    public void anthropicDisabledAndForcedFamiliesKeepTheRequestValid() throws Exception {
        JSONObject disabled = anthropicBody("claude-sonnet-5-5", "off");
        assertEquals("disabled", disabled.getJSONObject("thinking").getString("type"));
        assertFalse(disabled.has("output_config"));
        JSONObject forced = anthropicBody("claude-fable-5-1", "off");
        assertFalse(forced.has("thinking"));
    }

    @Test
    public void codexParseReadsCachedTokensAndKeepsEffort() throws Exception {
        StreamEvent event = CodexCodec.parse("{\"type\":\"response.completed\",\"response\":"
                + "{\"usage\":{\"input_tokens\":80,\"output_tokens\":12,"
                + "\"input_tokens_details\":{\"cached_tokens\":30}}}}");
        assertEquals(80, event.promptTokens);
        assertEquals(12, event.completionTokens);
        assertEquals(30, event.cachedTokens);
        ChatRequest request = ChatRequest.builder().model("gpt-5.6")
                .reasoning(new ReasoningSpec("https://api.openai.com/v1", "gpt-5.6", "", "max",
                        new ReasoningSpec.Wire(false, "", "max", false,
                                ModelConfig.Protocol.CODEX_RESPONSES)))
                .build();
        assertEquals("max", new JSONObject(CodexCodec.requestBody(request, "https://api.openai.com/v1"))
                .getJSONObject("reasoning").getString("effort"));
    }

    @Test
    public void usageOnlyEventsAreNotEmpty() {
        assertFalse(StreamEvent.of("", "", "", 12, 0, 3).isEmpty());
        assertTrue(StreamEvent.of("", "", "", 0, 0, 0).isEmpty());
        assertFalse(StreamEvent.text("hi").isEmpty());
    }

    private JSONObject anthropicBody(String modelId, String effort) throws Exception {
        ChatRequest request = ChatRequest.builder()
                .model(modelId)
                .reasoning(new ReasoningSpec("https://api.anthropic.com", modelId, "anthropic",
                        effort, false))
                .build();
        return new JSONObject(AnthropicCodec.requestBody(request));
    }

    private ReasoningSpec spec(String baseUrl, String model, String effort) {
        return new ReasoningSpec(baseUrl, model, "", effort, false);
    }
}
