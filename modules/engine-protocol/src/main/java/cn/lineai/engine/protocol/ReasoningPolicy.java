package cn.lineai.engine.protocol;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vendor-specific reasoning request fields, in the same first-match order the product has always
 * used: dashscope, minimax, deepseek, kimi, glm/mimo, openai, then a generic fallback.
 *
 * <p>An explicit dotted field override wins over every built-in branch. NVIDIA endpoints are
 * excluded outright because they reject unknown reasoning fields. Exactly one branch fires per
 * request, so the body must not already carry reasoning fields from another policy.</p>
 */
public final class ReasoningPolicy {

    private ReasoningPolicy() {
    }

    /**
     * Writes vendor-specific reasoning fields into a request body, mutating it in place.
     *
     * <p>Providers are detected from the spec's base URL, provider and model identifiers in the
     * fixed first-match order documented on the class; the first match decides which fields are
     * emitted and no other branch runs. When the spec carries an explicit dotted field, that field
     * receives the typed explicit value and the built-in levels are skipped.</p>
     *
     * @param body request body to mutate
     * @param spec requested reasoning behaviour; ignored when null
     * @throws JSONException when the body rejects an inserted value
     */
    public static void apply(JSONObject body, ReasoningSpec spec) throws JSONException {
        if (spec == null) {
            return;
        }
        if (spec.baseUrl.contains("integrate.api.nvidia.com") || spec.baseUrl.contains("api.nvidia.com")
                || spec.providerId.contains("nvidia")) {
            return;
        }
        if (!spec.explicitField.isEmpty()) {
            writePath(body, spec.explicitField, typed(spec.wireValue()));
            return;
        }
        String effort = spec.effectiveEffort();
        boolean enabled = spec.enabled();
        if (dashscope(spec)) {
            body.put("enable_thinking", enabled);
            if (enabled) {
                body.put("thinking_budget", spec.thinkingBudget());
            }
            if (spec.preserve) {
                body.put("preserve_thinking", true);
            }
            return;
        }
        if (minimax(spec)) {
            // MiniMax cannot disable reasoning; an off request simply omits the field.
            if (enabled) {
                body.put("reasoning_effort", minimaxEffort(effort));
            }
            return;
        }
        if (deepseek(spec)) {
            body.put("thinking", new JSONObject().put("type", enabled ? "enabled" : "disabled"));
            if (enabled) {
                body.put("reasoning_effort", deepseekEffort(effort));
            }
            return;
        }
        if (kimi(spec)) {
            // Kimi K3 and later reject a thinking field; the effort is the only switch.
            if (enabled) {
                body.put("reasoning_effort", kimiEffort(effort));
            }
            body.put("temperature", Math.max(1.0, body.optDouble("temperature", 0.2)));
            return;
        }
        if (moonshot(spec)) {
            if (spec.isGlm()) {
                writeGlm(body, effort, enabled, spec.preserve);
            } else {
                // MiMo documents thinking.type only and no effort level.
                body.put("thinking",
                        new JSONObject().put("type", enabled ? "enabled" : "disabled"));
            }
            return;
        }
        if (openAiReasoningModel(spec)) {
            if (enabled) {
                body.put("reasoning_effort", xhigh(effort));
            }
            return;
        }
        if (enabled) {
            body.put("reasoning", new JSONObject().put("effort", xhigh(effort)));
        }
    }

    /** Writes GLM's thinking switch plus effort, preserving the cache when requested. */
    private static void writeGlm(JSONObject body, String effort, boolean enabled, boolean preserve)
            throws JSONException {
        body.put("thinking", new JSONObject().put("type", enabled ? "enabled" : "disabled"));
        if (enabled) {
            body.put("reasoning_effort", effort);
        }
        if (preserve) {
            body.put("clear_thinking", false);
        }
    }

    /**
     * Writes a dotted path with a typed value, creating intermediate objects.
     *
     * <p>{@code true}/{@code false} become booleans, integer literals become numbers and every
     * other value stays a string, so a custom field such as {@code reasoning_effort} keeps working
     * for providers whose level is numeric.</p>
     */
    private static void writePath(JSONObject body, String path, Object value) throws JSONException {
        String[] parts = path.split("\\.");
        JSONObject target = body;
        for (int index = 0; index < parts.length - 1; index++) {
            JSONObject next = target.optJSONObject(parts[index]);
            if (next == null) {
                next = new JSONObject();
                target.put(parts[index], next);
            }
            target = next;
        }
        target.put(parts[parts.length - 1], value);
    }

    /** Converts an explicit wire value to the JSON type its spelling implies. */
    private static Object typed(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        if (!value.isEmpty() && value.matches("-?\\d{1,9}")) {
            return Integer.valueOf(value);
        }
        return value;
    }

    /**
     * Maps effort to DeepSeek's levels; the provider documents low/high/max and accepts medium,
     * mapping it to its nearest documented level server-side.
     */
    private static String deepseekEffort(String effort) {
        if ("low".equals(effort)) {
            return "low";
        }
        if ("max".equals(effort)) {
            return "max";
        }
        if ("medium".equals(effort)) {
            return "medium";
        }
        return "high";
    }

    /** Maps effort to Kimi's only accepted levels: low, high and max. */
    private static String kimiEffort(String effort) {
        if ("low".equals(effort) || "minimal".equals(effort)) {
            return "low";
        }
        if ("max".equals(effort)) {
            return "max";
        }
        return "high";
    }

    /** Maps effort to MiniMax's documented levels, keeping the explicit maximum. */
    private static String minimaxEffort(String effort) {
        if ("minimal".equals(effort) || "low".equals(effort)) {
            return "low";
        }
        if ("max".equals(effort)) {
            return "max";
        }
        if ("medium".equals(effort)) {
            return "medium";
        }
        return "high";
    }

    /** Promotes {@code max} to {@code xhigh} for providers that use that spelling. */
    private static String xhigh(String effort) {
        return "max".equals(effort) ? "xhigh" : effort;
    }

    /** Matches Qwen/DashScope deployments by host or model name. */
    private static boolean dashscope(ReasoningSpec spec) {
        return spec.baseUrl.contains("dashscope") || spec.baseUrl.contains("aliyuncs")
                || spec.modelId.contains("qwen");
    }

    /** Matches MiniMax deployments by host or model name, including the M2 series. */
    private static boolean minimax(ReasoningSpec spec) {
        return spec.baseUrl.contains("minimax") || spec.modelId.contains("minimax")
                || spec.modelId.contains("abab") || spec.modelId.contains("m2");
    }

    /** Matches DeepSeek deployments by host or model name. */
    private static boolean deepseek(ReasoningSpec spec) {
        return spec.baseUrl.contains("deepseek") || spec.modelId.contains("deepseek");
    }

    /** Matches Kimi/Moonshot deployments by host or model name. */
    private static boolean kimi(ReasoningSpec spec) {
        return spec.baseUrl.contains("moonshot") || spec.baseUrl.contains("kimi")
                || spec.modelId.contains("kimi") || spec.modelId.contains("moonshot");
    }

    /** Matches GLM, MiMo and Xiaomi deployments, which share the thinking-field family. */
    private static boolean moonshot(ReasoningSpec spec) {
        return spec.baseUrl.contains("bigmodel") || spec.baseUrl.contains("zhipu")
                || spec.modelId.contains("glm") || spec.baseUrl.contains("mimo")
                || spec.baseUrl.contains("xiaomi") || spec.modelId.contains("mimo");
    }

    /** Matches OpenAI model families that accept a bare reasoning_effort field. */
    private static boolean openAiReasoningModel(ReasoningSpec spec) {
        return spec.modelId.startsWith("gpt-5") || spec.modelId.startsWith("o1")
                || spec.modelId.startsWith("o3") || spec.modelId.startsWith("o4");
    }
}
