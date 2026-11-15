package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/**
 * Persisted update-check preferences.
 *
 * <p>Two bits survive between launches. The mute bit records whether the user asked to stop being
 * reminded about new releases; choosing "Go to update" clears it again, because acting on the
 * update implies the reminder has served its purpose. The manual-check bit records whether the user
 * has ever asked About &gt; Check for updates, which is the explicit opt-in that permits automatic
 * checks on later launches.</p>
 */
public final class UpdatePreferences {

    /** Settings key storing whether automatic update prompts are muted. */
    public static final String KEY_MUTED = "update.muted";

    /** Settings key storing whether the user has triggered a manual update check. */
    public static final String KEY_MANUAL_CHECKED = "update.manualChecked";

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

    /**
     * Returns whether the user has triggered a manual update check at least once.
     *
     * @return true when a manual check opted in to update checks
     */
    public boolean manualChecked() {
        return Boolean.parseBoolean(settings.get(KEY_MANUAL_CHECKED, "false"));
    }

    /**
     * Persists the manual-check flag.
     *
     * @param checked true to record that the user asked for an update check
     */
    public void setManualChecked(boolean checked) {
        settings.put(KEY_MANUAL_CHECKED, Boolean.toString(checked));
    }

    /**
     * Returns whether an automatic launch-time update check is permitted.
     *
     * <p>Automatic checks stay off until the user has asked for one manually, so a fresh install
     * never contacts GitHub on launch; muting always wins over that opt-in.</p>
     *
     * @return true when a manual check happened and reminders are not muted
     */
    public boolean autoCheckAllowed() {
        return manualChecked() && !muted();
    }
}
