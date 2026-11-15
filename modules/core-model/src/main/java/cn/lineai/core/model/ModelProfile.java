package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatic capability profile of a model identifier.
 *
 * <p>Providers rarely expose context size or modality through their catalog endpoints, so the
 * client infers a conservative profile from the model family and version and lets the user
 * override every inferred field on the model details screen. All methods are pure and
 * deterministic; the same identifier always produces the same profile.</p>
 */
public final class ModelProfile {

    /** Context window used when the family or version is unknown. */
    public static final int DEFAULT_CONTEXT = 128000;
    /** Context window of families that advertise a million-token window. */
    public static final int MILLION_CONTEXT = 1000000;
    /** Output-token default shared with the model form. */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    private static final Map<String, String> BRANDS = new HashMap<>();
    private static final List<ReasoningLevel> DEFAULT_LEVELS;
    private static final List<ReasoningLevel> CLAUDE_LEVELS;
    private static final List<ReasoningLevel> GPT_LEVELS;
    private static final List<ReasoningLevel> DEEPSEEK_LEVELS;
    private static final List<ReasoningLevel> KIMI_LEVELS;
    private static final List<ReasoningLevel> GLM_LEVELS;
    private static final List<ReasoningLevel> QWEN_LEVELS;
    private static final List<ReasoningLevel> MINIMAX_LEVELS;
    private static final List<ReasoningLevel> MIMO_LEVELS;

    static {
        BRANDS.put("claude", "Claude");
        BRANDS.put("gpt", "GPT");
        BRANDS.put("glm", "GLM");
        BRANDS.put("deepseek", "DeepSeek");
        BRANDS.put("kimi", "Kimi");
        BRANDS.put("moonshot", "Moonshot");
        BRANDS.put("qwen", "Qwen");
        BRANDS.put("minimax", "MiniMax");
        BRANDS.put("mimo", "MiMo");
        BRANDS.put("gemini", "Gemini");
        BRANDS.put("openai", "OpenAI");
        BRANDS.put("anthropic", "Anthropic");
        BRANDS.put("google", "Google");
        BRANDS.put("minimaxai", "MiniMax");
        BRANDS.put("moonshotai", "Moonshot");
        BRANDS.put("zhipuai", "Zhipu");
        BRANDS.put("xiaomi", "Xiaomi");
        BRANDS.put("xai", "xAI");
        BRANDS.put("nvidia", "NVIDIA");
        BRANDS.put("mistralai", "Mistral");
        BRANDS.put("bytedance", "ByteDance");
        BRANDS.put("stepfun", "StepFun");
        BRANDS.put("hunyuan", "Hunyuan");
        BRANDS.put("doubao", "Doubao");
        BRANDS.put("ernie", "ERNIE");
        DEFAULT_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH);
        CLAUDE_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH, ReasoningLevel.PRESET_MAX);
        GPT_LEVELS = levels(ReasoningLevel.PRESET_MINIMAL, ReasoningLevel.PRESET_LOW,
                ReasoningLevel.PRESET_MEDIUM, ReasoningLevel.PRESET_HIGH);
        DEEPSEEK_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH, ReasoningLevel.PRESET_MAX);
        KIMI_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_HIGH,
                ReasoningLevel.PRESET_MAX);
        GLM_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH, ReasoningLevel.PRESET_MAX);
        QWEN_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH);
        MINIMAX_LEVELS = levels(ReasoningLevel.PRESET_LOW, ReasoningLevel.PRESET_MEDIUM,
                ReasoningLevel.PRESET_HIGH, ReasoningLevel.PRESET_MAX);
        MIMO_LEVELS = levels(ReasoningLevel.PRESET_ON);
    }

    /** Not instantiable; every profile is a static lookup. */
    private ModelProfile() {
    }

    /**
     * Formats a raw model identifier as a display name.
     *
     * <p>Known brand tokens keep their canonical casing, version tokens become dot-separated
     * numbers, and single-letter series prefixes bind to the version, so
     * {@code claude-sonnet-5-5} reads {@code Claude Sonnet 5.5} and {@code kimi-k2.7} reads
     * {@code Kimi K2.7}.</p>
     *
     * @param modelId provider-side model identifier, may be null or blank
     * @return formatted display name, or the trimmed identifier when nothing can be formatted
     */
    public static String displayName(String modelId) {
        String id = modelId == null ? "" : modelId.trim();
        if (id.isEmpty()) return "";
        String name = id;
        int slash = id.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < id.length()) {
            name = id.substring(slash + 1);
        }
        List<String> raw = tokens(name);
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            String token = raw.get(index);
            if (index + 1 < raw.size() && isSeriesPrefix(token) && isVersion(raw.get(index + 1))) {
                parts.add(token.toUpperCase(Locale.ROOT) + raw.get(index + 1));
                index++;
                continue;
            }
            if (isVersion(token)) {
                StringBuilder version = new StringBuilder(token);
                while (index + 1 < raw.size() && isNumeric(raw.get(index + 1))) {
                    version.append('.').append(raw.get(index + 1));
                    index++;
                }
                parts.add(version.toString());
                continue;
            }
            parts.add(BRANDS.containsKey(token) ? BRANDS.get(token) : capitalize(token));
        }
        if (parts.isEmpty()) return id;
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            String part = parts.get(index);
            if (out.length() == 0) {
                out.append(part);
            } else if (index > 0 && "GPT".equals(parts.get(index - 1)) && isVersion(part)) {
                out.append('-').append(part);
            } else {
                out.append(' ').append(part);
            }
        }
        return out.toString();
    }

    /**
     * Infers the context window from the model family and version.
     *
     * @param modelId provider-side model identifier, may be null
     * @return one million for the documented long-context families, otherwise the default
     */
    public static int contextSize(String modelId) {
        String id = lower(modelId);
        Version claude = version(id, "claude");
        Version gpt = version(id, "gpt");
        Version deepseek = version(id, "deepseek");
        Version kimi = version(id, "kimi");
        Version moonshot = version(id, "moonshot");
        Version glm = version(id, "glm");
        Version qwen = version(id, "qwen");
        Version minimax = version(id, "minimax");
        Version mimo = version(id, "mimo");
        if (atLeast(claude, 5, 0) || atLeast(gpt, 5, 0) || atLeast(deepseek, 4, 0)
                || atLeast(kimi, 2, 7) || atLeast(moonshot, 2, 7) || atLeast(glm, 5, 2)
                || atLeast(qwen, 3, 7) || atLeast(minimax, 3, 0) || atLeast(mimo, 2, 5)) {
            return MILLION_CONTEXT;
        }
        return DEFAULT_CONTEXT;
    }

    /**
     * Infers image-input support. Claude 5, GPT-5 and the DeepSeek Flash line default to on.
     *
     * @param modelId provider-side model identifier, may be null
     * @return true when the family is known to accept images by default
     */
    public static boolean supportsVision(String modelId) {
        String id = lower(modelId);
        if (atLeast(version(id, "claude"), 5, 0) || atLeast(version(id, "gpt"), 5, 0)) {
            return true;
        }
        return atLeast(version(id, "deepseek"), 4, 1) && id.contains("flash");
    }

    /**
     * Infers video-input support; no family is enabled by default.
     *
     * @param modelId provider-side model identifier, may be null
     * @return always false until a family profile enables it
     */
    public static boolean supportsVideo(String modelId) {
        return false;
    }

    /**
     * Reports whether the family supports explicit reasoning by default.
     *
     * @param modelId provider-side model identifier, may be null
     * @return true for every family this client knows; the toggle remains user-editable
     */
    public static boolean supportsReasoning(String modelId) {
        return true;
    }

    /**
     * Reports whether the family allows disabling reasoning at all.
     *
     * <p>Several current families reject a disabled/off request, so the model form greys the
     * reasoning toggle out instead of allowing a request that the provider would reject.</p>
     *
     * @param modelId provider-side model identifier, may be null
     * @return false for families that are documented as forced-thinking
     */
    public static boolean reasoningToggleable(String modelId) {
        String id = lower(modelId);
        Version claude = version(id, "claude");
        if (atLeast(claude, 5, 0) && (id.contains("fable") || id.contains("mythos"))) {
            return false;
        }
        if (atLeast(version(id, "kimi"), 3, 0) || atLeast(version(id, "minimax"), 3, 0)
                || atLeast(version(id, "glm"), 5, 3)) {
            return false;
        }
        return true;
    }

    /**
     * Returns the default reasoning levels of the model family.
     *
     * @param modelId provider-side model identifier, may be null
     * @return unmodifiable levels in display order; never empty
     */
    public static List<ReasoningLevel> reasoningLevels(String modelId) {
        String id = lower(modelId);
        if (id.contains("claude")) return CLAUDE_LEVELS;
        if (id.contains("gpt")) return GPT_LEVELS;
        if (id.contains("deepseek")) return DEEPSEEK_LEVELS;
        if (id.contains("kimi") || id.contains("moonshot")) return KIMI_LEVELS;
        if (id.contains("glm")) return GLM_LEVELS;
        if (id.contains("qwen")) return QWEN_LEVELS;
        if (id.contains("minimax")) return MINIMAX_LEVELS;
        if (id.contains("mimo")) return MIMO_LEVELS;
        return DEFAULT_LEVELS;
    }

    /**
     * Returns the provider-specific reasoning field path used when no explicit override exists.
     *
     * <p>Built-in families are handled by the protocol policy, so the default is empty; a user
     * configured override on the model details screen takes precedence.</p>
     *
     * @param modelId provider-side model identifier, may be null
     * @return empty string, signalling automatic family handling
     */
    public static String reasoningField(String modelId) {
        return "";
    }

    /**
     * Reports whether tool-call rounds must replay the previous reasoning text.
     *
     * @param modelId provider-side model identifier, may be null
     * @return true for DeepSeek, Kimi and MiMo, whose tool rounds require {@code reasoning_content}
     */
    public static boolean replaysReasoning(String modelId) {
        String id = lower(modelId);
        return id.contains("deepseek") || id.contains("kimi") || id.contains("moonshot")
                || id.contains("mimo");
    }

    /** Returns the default reasoning levels as unmodifiable presets. */
    private static List<ReasoningLevel> levels(String... presets) {
        List<ReasoningLevel> result = new ArrayList<>();
        for (String preset : presets) result.add(ReasoningLevel.preset(preset));
        return Collections.unmodifiableList(result);
    }

    /** Splits an identifier into display tokens, separating embedded version suffixes. */
    private static List<String> tokens(String id) {
        List<String> result = new ArrayList<>();
        for (String part : id.toLowerCase(Locale.ROOT).split("[-_/\\s]+")) {
            if (part.isEmpty()) continue;
            int split = lettersPrefix(part);
            if (split > 0 && split < part.length()) {
                result.add(part.substring(0, split));
                result.add(part.substring(split));
            } else {
                result.add(part);
            }
        }
        return result;
    }

    /** Returns the length of the leading letter run, or zero when the token has none. */
    private static int lettersPrefix(String token) {
        int index = 0;
        while (index < token.length() && Character.isLetter(token.charAt(index))) index++;
        return index;
    }

    /** True for a single-letter series prefix such as {@code k}, {@code m} or {@code v}. */
    private static boolean isSeriesPrefix(String token) {
        return token.length() == 1 && Character.isLetter(token.charAt(0));
    }

    /** True for a version token such as {@code 5}, {@code 5.5} or {@code 4-6}. */
    private static boolean isVersion(String token) {
        if (token.isEmpty()) return false;
        char first = token.charAt(0);
        if (!Character.isDigit(first)) return false;
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (!Character.isDigit(value) && value != '.' && value != '-') return false;
        }
        return true;
    }

    /** True for a plain integer token. */
    private static boolean isNumeric(String token) {
        if (token.isEmpty()) return false;
        for (int index = 0; index < token.length(); index++) {
            if (!Character.isDigit(token.charAt(index))) return false;
        }
        return true;
    }

    /** Capitalises an unknown word. */
    private static String capitalize(String token) {
        if (token.isEmpty()) return token;
        return Character.toUpperCase(token.charAt(0)) + token.substring(1);
    }

    /** Lowercases a model identifier for matching. */
    private static String lower(String modelId) {
        return modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
    }

    /** Extracts the first version that follows {@code keyword} in the identifier. */
    private static Version version(String id, String keyword) {
        int start = id.indexOf(keyword);
        if (start < 0) return Version.NONE;
        int index = start + keyword.length();
        while (index < id.length() && !Character.isDigit(id.charAt(index))) index++;
        if (index >= id.length()) return Version.NONE;
        int majorStart = index;
        while (index < id.length() && Character.isDigit(id.charAt(index))) index++;
        int major = parse(id.substring(majorStart, index));
        int minor = 0;
        if (index < id.length() && (id.charAt(index) == '.' || id.charAt(index) == '-')) {
            int minorStart = index + 1;
            int end = minorStart;
            while (end < id.length() && Character.isDigit(id.charAt(end))) end++;
            if (end - minorStart > 0 && end - minorStart <= 2) {
                minor = parse(id.substring(minorStart, end));
            }
        }
        return new Version(major, minor);
    }

    /** Parses a small integer, tolerating overflow by clamping to zero. */
    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    /** True when the version is present and at least the given major/minor pair. */
    private static boolean atLeast(Version version, int major, int minor) {
        return version.major > 0 || version.minor > 0
                ? version.compareTo(new Version(major, minor)) >= 0 : false;
    }

    /** Immutable major/minor pair used for threshold comparisons. */
    private static final class Version implements Comparable<Version> {
        static final Version NONE = new Version(0, 0);
        final int major;
        final int minor;

        Version(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        @Override public int compareTo(Version other) {
            int majorOrder = Integer.compare(major, other.major);
            return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
        }
    }
}
