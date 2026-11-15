package cn.lineai.feature.settings;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateDialogTest {

    private ActivityController<Activity> controller;
    private Activity activity;
    private MapSettings settings;
    private int updateActions;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        settings = new MapSettings();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void showsTheReleaseChangelogAndThePersistedMuteState() {
        settings.values.put(UpdatePreferences.KEY_MUTED, "true");
        Dialog dialog = show();

        assertNotNull(findText(root(dialog),
                activity.getString(R.string.update_dialog_title, "2.2.1")));
        assertNotNull(findText(root(dialog),
                activity.getString(R.string.update_dialog_changelog)));
        assertNotNull(findText(root(dialog), "Fixes and statistics."));
        Switch mute = findSwitch(root(dialog));
        assertNotNull("the dialog must offer the mute switch", mute);
        assertTrue("the switch reflects the stored mute flag", mute.isChecked());
    }

    @Test
    public void switchingMutePersistsTheFlag() {
        Dialog dialog = show();
        Switch mute = findSwitch(root(dialog));
        assertNotNull(mute);
        assertFalse(mute.isChecked());

        mute.setChecked(true);

        assertTrue(new UpdatePreferences(settings).muted());
    }

    @Test
    public void cancelOnlyDismisses() {
        Dialog dialog = show();
        settings.values.put(UpdatePreferences.KEY_MUTED, "true");

        click(root(dialog), activity.getString(R.string.update_action_cancel));

        assertFalse(dialog.isShowing());
        assertEquals(0, updateActions);
        assertTrue("cancel keeps the mute flag untouched",
                new UpdatePreferences(settings).muted());
    }

    @Test
    public void goToUpdateClearsTheMuteAndRunsTheAction() {
        settings.values.put(UpdatePreferences.KEY_MUTED, "true");
        Dialog dialog = show();

        click(root(dialog), activity.getString(R.string.update_action_go));

        assertFalse(dialog.isShowing());
        assertEquals(1, updateActions);
        assertFalse("acting on the update cancels the mute", new UpdatePreferences(settings).muted());
    }

    private Dialog show() {
        UpdateDialog.show(activity, Tokens.of(ThemePalette.dark()),
                new ReleaseInfo("v2.2.1", "Fixes and statistics.",
                        "https://github.com/LangLang03/LineCodePro/releases/tag/v2.2.1"),
                new UpdatePreferences(settings), () -> updateActions++);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static View root(Dialog dialog) {
        return dialog.getWindow().getDecorView();
    }

    private static void click(View root, String text) {
        TextView label = findText(root, text);
        assertNotNull("missing action: " + text, label);
        View view = label;
        while (view != null && !view.isClickable()) {
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        assertNotNull("no clickable action for: " + text, view);
        view.performClick();
    }

    private static Switch findSwitch(View root) {
        List<Switch> switches = new ArrayList<>();
        collectSwitches(root, switches);
        return switches.isEmpty() ? null : switches.get(0);
    }

    private static void collectSwitches(View root, List<Switch> out) {
        if (root instanceof Switch) {
            out.add((Switch) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSwitches(group.getChildAt(i), out);
            }
        }
    }

    private static TextView findText(View root, String expected) {
        List<TextView> texts = new ArrayList<>();
        collectText(root, texts);
        for (TextView view : texts) {
            if (view.getText().toString().equals(expected)) return view;
        }
        return null;
    }

    private static void collectText(View root, List<TextView> out) {
        if (root instanceof TextView && !(root instanceof Switch)) {
            out.add((TextView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), out);
            }
        }
    }

    /** Minimal in-memory settings store used by the dialog tests. */
    private static final class MapSettings implements SettingsStore {
        final Map<String, String> values = new HashMap<>();

        @Override public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        @Override public int getInt(String key, int fallback) {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
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
            return new HashMap<>(values);
        }
    }
}
