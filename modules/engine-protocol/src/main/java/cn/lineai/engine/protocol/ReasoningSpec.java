package cn.lineai.engine.protocol;

import java.util.Locale;

import cn.lineai.core.model.ModelConfig;

/** Requested reasoning behaviour, before vendor-specific mapping. */
public final class ReasoningSpec {

    /** Lowercased base URL used for provider detection. */
    public final String baseUrl;
    /** Lowercased model identifier used for provider detection. */
    public final String modelId;
    /** Lowercased provider identifier used for provider detection. */
    public final String providerId;
    /** Requested effort level; defaults to {@code medium} when absent. */
    public final String requestedEffort;
    /** Whether reasoning content must be preserved across follow-up requests. */
    public final boolean preserve;
    /** Dotted wire-field path override; empty selects the protocol policy. */
    public final String explicitField;
    /** Explicit wire value for {@link #explicitField}; empty derives it from the effort. */
    public final String explicitValue;
    /** Whether follow-up tool rounds must replay this reasoning as {@code reasoning_content}. */
    public final boolean replayReasoning;
    /** Wire protocol of the target model, or null when the protocol decides the shape. */
    public final ModelConfig.Protocol protocol;

    /**
     * Additional wire-level overrides and model identity supplied by the chat layer.
     *
     * <p>A spec carries either the legacy effort token alone or these explicit overrides; the
     * nested value keeps the {@link ReasoningSpec} constructors within the parameter budget.</p>
     */
    public static final class Wire {
        /** Whether reasoning content is preserved across requests. */
        public final boolean preserve;
        /** Dotted wire-field path override; empty selects the protocol policy. */
        public final String field;
        /** Explicit wire value; empty derives it from the effort. */
        public final String value;
        /** Whether follow-up tool rounds replay the reasoning content. */
        public final boolean replay;
        /** Wire protocol of the target model, or null when unknown. */
        public final ModelConfig.Protocol protocol;

        /**
         * Creates the explicit overrides.
         *
         * @param preserve whether reasoning content is preserved across requests
         * @param field dotted wire-field path, may be null or empty
         * @param value explicit wire value, may be null or empty
         * @param replay whether follow-up tool rounds replay the reasoning content
         * @param protocol wire protocol of the target model, may be null
         */
        public Wire(boolean preserve, String field, String value, boolean replay,
                    ModelConfig.Protocol protocol) {
            this.preserve = preserve;
            this.field = trim(field);
            this.value = trim(value);
            this.replay = replay;
            this.protocol = protocol;
        }

        /** Returns the legacy overrides without an explicit field or value. */
        public static Wire none() {
            return new Wire(false, "", "", false, null);
        }

        /**
         * Creates overrides from protocol-level knowledge, without the legacy preserve flag.
         *
         * @param field dotted wire-field path, may be null or empty
         * @param value explicit wire value, may be null or empty
         * @param replay whether follow-up tool rounds replay the reasoning content
         * @param protocol wire protocol of the target model, may be null
         * @return the explicit overrides
         */
        public static Wire of(String field, String value, boolean replay,
                              ModelConfig.Protocol protocol) {
            return new Wire(false, field, value, replay, protocol);
        }
    }

    /**
     * Creates a spec; identity fields are lowercased for matching and a null effort becomes
     * {@code medium}.
     *
     * @param baseUrl provider base URL, may be null
     * @param modelId model identifier, may be null
     * @param providerId provider identifier, may be null
     * @param requestedEffort one of {@code off}, {@code low}, {@code medium}, {@code high},
     *        {@code max}, {@code auto}; may be null
     * @param preserve whether reasoning content is carried into subsequent requests
     */
    public ReasoningSpec(String baseUrl, String modelId, String providerId,
                         String requestedEffort, boolean preserve) {
        this(baseUrl, modelId, providerId, requestedEffort,
                new Wire(preserve, "", "", false, null));
    }

    /**
     * Creates a spec carrying explicit wire overrides and model identity.
     *
     * @param baseUrl provider base URL, may be null
     * @param modelId model identifier, may be null
     * @param providerId provider identifier, may be null
     * @param requestedEffort requested level value, may be null
     * @param wire explicit overrides, null yields the legacy behaviour
     */
    public ReasoningSpec(String baseUrl, String modelId, String providerId,
                         String requestedEffort, Wire wire) {
        Wire overrides = wire == null ? Wire.none() : wire;
        this.baseUrl = lower(baseUrl);
        this.modelId = lower(modelId);
        this.providerId = lower(providerId);
        this.requestedEffort = requestedEffort == null ? "medium" : requestedEffort.trim();
        this.preserve = overrides.preserve;
        this.explicitField = overrides.field;
        this.explicitValue = overrides.value;
        this.replayReasoning = overrides.replay;
        this.protocol = overrides.protocol;
    }

    /** Returns a spec that disables reasoning entirely. */
    public static ReasoningSpec off() {
        return new ReasoningSpec("", "", "", "off", false);
    }

    /**
     * Returns whether the request asks for reasoning.
     *
     * <p>An explicit wire value answers this directly: {@code false}, {@code off}, {@code none}
     * and {@code disabled} switch reasoning off, every other value keeps it on. Without an
     * explicit value the effort token decides.</p>
     *
     * @return false when the request disables reasoning
     */
    public boolean enabled() {
        if (!explicitValue.isEmpty()) {
            return !isDisabledValue(explicitValue);
        }
        return !isDisabledValue(requestedEffort);
    }

    /** Concrete effort after vendor compatibility; {@code auto} becomes {@code medium}. */
    public String effectiveEffort() {
        return "auto".equals(compatibleEffort()) ? "medium" : compatibleEffort();
    }

    /** Effort written to the wire: the explicit value when present, else the effective one. */
    public String wireValue() {
        return explicitValue.isEmpty() ? effectiveEffort() : explicitValue;
    }

    /** GLM only distinguishes low/medium/high/xhigh/max; everything else keeps the value. */
    public String compatibleEffort() {
        String effort = requestedEffort;
        if (!isGlm()) {
            return effort;
        }
        if ("max".equals(effort) || "xhigh".equals(effort) || "high".equals(effort)
                || "medium".equals(effort)) {
            return effort;
        }
        if ("low".equals(effort) || "minimal".equals(effort) || "off".equals(effort)) {
            return "low";
        }
        return "high";
    }

    /**
     * Maps the effective effort to an explicit token budget for providers that require one.
     *
     * <p>The documented provider budgets are 1024 for low, 4096 for medium, 8192 for high and
     * 16000 for max; unknown or custom levels use the medium budget.</p>
     *
     * @return thinking budget in tokens
     */
    public int thinkingBudget() {
        String effort = effectiveEffort();
        if ("minimal".equals(effort) || "low".equals(effort)) {
            return 1024;
        }
        if ("high".equals(effort)) {
            return 8192;
        }
        if ("max".equals(effort) || "xhigh".equals(effort)) {
            return 16000;
        }
        return 4096;
    }

    /**
     * Returns {@code true} when the endpoint, provider or model identifies as a GLM (Zhipu)
     * deployment; GLM accepts the low/medium/high/xhigh/max effort levels.
     *
     * @return true for GLM deployments
     */
    public boolean isGlm() {
        return baseUrl.contains("bigmodel") || baseUrl.contains("zhipu")
                || providerId.contains("zhipu") || providerId.contains("glm")
                || modelId.contains("glm");
    }

    /** True for values that mean the provider must not reason at all. */
    private static boolean isDisabledValue(String value) {
        String normalized = lower(value);
        return "false".equals(normalized) || "off".equals(normalized)
                || "none".equals(normalized) || "disabled".equals(normalized);
    }

    /** Trims for case-insensitive matching; null becomes the empty string. */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Lowercases for case-insensitive provider matching; null becomes the empty string. */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
