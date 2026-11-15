package cn.lineai.core.model;

/**
 * One selectable reasoning level of a model.
 *
 * <p>The preset doubles as a localization key in the UI; a blank preset marks a user-defined
 * entry whose display label lives in {@link #label}. The value is the exact wire-level identifier
 * sent to the provider.</p>
 */
public final class ReasoningLevel {

    /** Built-in level identifiers understood by the UI and the reasoning policy. */
    public static final String PRESET_OFF = "off";
    public static final String PRESET_AUTO = "auto";
    public static final String PRESET_MINIMAL = "minimal";
    public static final String PRESET_LOW = "low";
    public static final String PRESET_MEDIUM = "medium";
    public static final String PRESET_HIGH = "high";
    public static final String PRESET_MAX = "max";
    public static final String PRESET_ON = "on";

    /** Localization key of a built-in level; empty for a custom entry. */
    public final String preset;
    /** Display label of a custom entry; empty for a built-in level. */
    public final String label;
    /** Exact value written to the provider request. */
    public final String value;

    /**
     * Creates one level.
     * @param preset built-in identifier, or empty for a custom entry
     * @param label custom display label, or empty for a built-in level
     * @param value wire-level value, null becomes empty
     */
    public ReasoningLevel(String preset, String label, String value) {
        this.preset = preset == null ? "" : preset.trim();
        this.label = label == null ? "" : label.trim();
        this.value = value == null ? "" : value.trim();
    }

    /**
     * Creates a built-in level whose value matches its preset.
     * @param preset built-in identifier
     * @return a level carrying the preset as its wire value
     */
    public static ReasoningLevel preset(String preset) {
        return new ReasoningLevel(preset, "", preset);
    }

    /** True when the level carries no preset and no custom label. */
    public boolean isBlank() {
        return preset.isEmpty() && label.isEmpty() && value.isEmpty();
    }

    /** Rows are equal when preset, label and value match. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReasoningLevel)) {
            return false;
        }
        ReasoningLevel level = (ReasoningLevel) other;
        return preset.equals(level.preset) && label.equals(level.label)
                && value.equals(level.value);
    }

    /** Hashes preset, label and value. */
    @Override
    public int hashCode() {
        int result = preset.hashCode();
        result = 31 * result + label.hashCode();
        return 31 * result + value.hashCode();
    }
}
