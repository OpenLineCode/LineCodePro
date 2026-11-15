package cn.lineai.feature.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsHomeTest {

    private static final int[] ROW_TITLES = {
            R.string.settings_row_tutorial_title,
            R.string.settings_row_models_title, R.string.screen_llm_title,
            R.string.screen_input_title, R.string.settings_row_output_title,
            R.string.settings_row_theme_title,
            R.string.settings_row_tool_settings_title, R.string.settings_row_mcp_title,
            R.string.settings_row_extensions_title, R.string.settings_row_ssh_title,
            R.string.settings_row_terminal_providers_title,
            R.string.settings_row_advanced_title,
            R.string.settings_row_workspace_title,
            R.string.settings_row_stats_title, R.string.settings_row_storage_title,
            R.string.settings_row_memory_title, R.string.settings_row_data_title,
            R.string.settings_row_error_logs_title, R.string.settings_row_keep_alive_title,
            R.string.screen_settings_section_security, R.string.settings_row_about_title
    };

    private static final SettingsHome.Destination[] ROW_DESTINATIONS = {
            SettingsHome.Destination.TUTORIAL,
            SettingsHome.Destination.MODELS, SettingsHome.Destination.BEHAVIOR,
            SettingsHome.Destination.INPUT, SettingsHome.Destination.OUTPUT,
            SettingsHome.Destination.THEME,
            SettingsHome.Destination.TOOLS, SettingsHome.Destination.MCP,
            SettingsHome.Destination.EXTENSIONS, SettingsHome.Destination.SSH,
            SettingsHome.Destination.TERMINAL_PROVIDERS,
            SettingsHome.Destination.ADVANCED,
            SettingsHome.Destination.WORKSPACE,
            SettingsHome.Destination.STATS, SettingsHome.Destination.STORAGE,
            SettingsHome.Destination.MEMORY, SettingsHome.Destination.DATA,
            SettingsHome.Destination.LOGS, SettingsHome.Destination.KEEP_ALIVE,
            SettingsHome.Destination.SECURITY, SettingsHome.Destination.ABOUT
    };

    private ActivityController<Activity> controller;
    private Activity activity;
    private final List<SettingsHome.Destination> opened = new ArrayList<>();

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void rendersEverySettingsGroup() {
        SettingsHome home = page();
        int[] groups = {
                R.string.screen_settings_section_models, R.string.screen_settings_section_chat,
                R.string.screen_settings_section_tools, R.string.screen_settings_section_workspace,
                R.string.screen_settings_section_data, R.string.screen_settings_section_security,
                R.string.screen_settings_section_info
        };
        for (int group : groups) {
            assertNotNull("missing group: " + heading(group),
                    findText(home, heading(group)));
        }
    }

    @Test
    public void routesEveryDestinationRow() {
        SettingsHome home = page();
        for (int index = 0; index < ROW_TITLES.length; index++) {
            String title = text(ROW_TITLES[index]);
            int before = opened.size();
            clickableWithText(home, title).performClick();
            assertEquals("unexpected dispatch for " + title, before + 1, opened.size());
            assertEquals("unexpected destination for " + title,
                    ROW_DESTINATIONS[index], opened.get(opened.size() - 1));
        }
    }

    private SettingsHome page() {
        SettingsHome home = new SettingsHome(activity, Tokens.of(ThemePalette.dark()),
                () -> { }, opened::add);
        activity.setContentView(home);
        return home;
    }

    private String text(int id) {
        return activity.getString(id);
    }

    private String heading(int id) {
        return text(id).toUpperCase(Locale.ROOT);
    }

    private static View clickableWithText(View root, String expected) {
        TextView label = findText(root, expected);
        assertNotNull("missing text: " + expected, label);
        View view = label;
        while (view != null && !view.isClickable()) {
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        assertNotNull("no clickable ancestor for: " + expected, view);
        return view;
    }

    private static TextView findText(View root, String expected) {
        List<TextView> texts = new ArrayList<>();
        collect(root, texts);
        for (TextView view : texts) {
            if (view.getText().toString().equals(expected)) return view;
        }
        return null;
    }

    private static void collect(View root, List<TextView> out) {
        if (root instanceof TextView) {
            out.add((TextView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), out);
            }
        }
    }
}
