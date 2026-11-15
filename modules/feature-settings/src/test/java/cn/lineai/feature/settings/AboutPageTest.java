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

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AboutPageTest {

    private ActivityController<Activity> controller;
    private Activity activity;
    private int githubOpens;
    private int licenseOpens;
    private int updateChecks;

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
    public void offersTheUpdateCheckAndDispatchesIt() {
        AboutPage page = page();
        activity.setContentView(page);

        assertNotNull("the update entry must be listed",
                findText(page, activity.getString(R.string.screen_about_check_update_title)));
        clickableWithText(page, activity.getString(R.string.screen_about_check_update_title))
                .performClick();

        assertEquals(1, updateChecks);
        assertEquals(0, githubOpens);
        assertEquals(0, licenseOpens);
    }

    @Test
    public void keepsTheDeveloperAndLegalEntriesWorking() {
        AboutPage page = page();
        activity.setContentView(page);

        clickableWithText(page, activity.getString(R.string.screen_about_github_label)).performClick();
        clickableWithText(page, activity.getString(R.string.screen_about_open_source_licenses))
                .performClick();

        assertEquals(1, githubOpens);
        assertEquals(1, licenseOpens);
        assertEquals(0, updateChecks);
    }

    private AboutPage page() {
        return new AboutPage(activity, Tokens.of(ThemePalette.dark()), () -> { },
                "LineCode Pro", "2.2.0", 52, new AboutPage.Listener() {
                    public void openGithub() {
                        githubOpens++;
                    }

                    public void openLicenses() {
                        licenseOpens++;
                    }

                    public void checkForUpdates() {
                        updateChecks++;
                    }
                });
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
