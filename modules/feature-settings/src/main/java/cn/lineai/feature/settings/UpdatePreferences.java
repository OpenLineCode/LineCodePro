package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/**
 * Persisted update-check preferences.
 *
 * <p>Only one bit survives between launches: whether the user asked to stop being reminded about
 * new releases. Choosing "Go to update" clears it again, because acting on the update implies the
 * reminder has served its purpose.</p>
 */
public final class UpdatePreferences {

    /** Settings key storing whether automatic update prompts are muted. */
    public static final String KEY_MUTED = "update.muted";

    private final SettingsStore settings;

    /**
     * Creates the preference wrapper.
     *
     * @param settings backing settings store; never null
     */
    public UpdatePreferences(SettingsStore settings) {
        this.settings = settings;
    }

    /**
     * Returns whether update prompts are muted.
     *
     * @return true when the user asked not to be reminded again
     */
    public boolean muted() {
        return Boolean.parseBoolean(settings.get(KEY_MUTED, "false"));
    }

    /**
     * Persists the mute flag.
     *
     * @param muted true to suppress future reminders
     */
    public void setMuted(boolean muted) {
        settings.put(KEY_MUTED, Boolean.toString(muted));
    }
}
