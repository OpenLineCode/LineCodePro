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
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.data.api.UsageStats;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatsPageTest {

    private ActivityController<Activity> controller;
    private Activity activity;
    private FakeLoader loader;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        loader = new FakeLoader();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void rendersSummaryAndPerModelUsage() {
        loader.stats = populated();
        StatsPage page = page();

        assertNotNull("the page must show the total amount", findText(page, "2k"));
        assertNotNull("input tokens are rendered separately", findText(page, "1.5k"));
        assertNotNull("output tokens are rendered separately", findText(page, "500"));
        assertNotNull("the weighted cache hit rate is a one-decimal percentage",
                findText(page, percentage(50.0d)));
        assertNotNull("known model identifiers resolve to their display name",
                findText(page, "DeepSeek V4"));
        assertNotNull("unknown identifiers keep the raw model id",
                findText(page, "mystery-model"));
        assertNotNull(findText(page, activity.getString(R.string.screen_stats_model_requests, 12)));
        assertNotNull(findText(page, activity.getString(R.string.screen_stats_model_tokens, "1.5k")));
        assertNotNull(findText(page,
                activity.getString(R.string.screen_stats_model_cache, percentage(60.0d))));
        assertFalse("the empty state is hidden while usage exists",
                findText(page, text(R.string.screen_stats_empty)).isShown());
    }

    @Test
    public void rendersTheEmptyStateWhenNoUsageWasRecorded() {
        loader.stats = UsageStats.empty();
        StatsPage page = page();

        TextView empty = findText(page, text(R.string.screen_stats_empty));
        assertNotNull("the empty state must be rendered", empty);
        assertTrue("the empty state must be visible", empty.isShown());
        assertFalse("the summary must stay hidden without usage",
                findText(page, text(R.string.screen_stats_total_tokens)).isShown());
    }

    @Test
    public void rendersTheEmptyStateWhenTheLoaderFails() {
        loader.failure = new IllegalStateException("database unavailable");
        StatsPage page = page();

        TextView empty = findText(page, text(R.string.screen_stats_empty));
        assertNotNull("a failing loader degrades to the empty state", empty);
        assertTrue(empty.isShown());
    }

    @Test
    public void onShownReloadsTheSnapshot() {
        loader.stats = UsageStats.empty();
        StatsPage page = page();
        assertTrue(findText(page, text(R.string.screen_stats_empty)).isShown());

        loader.stats = populated();
        page.onShown();

        assertNotNull(findText(page, "2k"));
        assertFalse(findText(page, text(R.string.screen_stats_empty)).isShown());
    }

    private StatsPage page() {
        StatsPage page = new StatsPage(activity, Tokens.of(ThemePalette.dark()), () -> { },
                Scheduler.immediate(), loader);
        activity.setContentView(page);
        return page;
    }

    private static UsageStats populated() {
        return new UsageStats(1500L, 500L, 750L, Arrays.asList(
                new UsageStats.ModelUsage("deepseek-v4", "deepseek", 1000L, 500L, 600L, 12),
                new UsageStats.ModelUsage("mystery-model", "other", 500L, 0L, 150L, 1)));
    }

    private String percentage(double value) {
        return activity.getString(R.string.session_stats_percent_value,
                String.format(java.util.Locale.ROOT, "%.1f", value));
    }

    private String text(int id) {
        return activity.getString(id);
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

    /** Loader returning a fixed snapshot, or failing when configured. */
    private static final class FakeLoader implements StatsPage.Loader {
        UsageStats stats = UsageStats.empty();
        RuntimeException failure;

        @Override public UsageStats load() {
            if (failure != null) throw failure;
            return stats;
        }

        @Override public String modelName(String modelId) {
            if ("deepseek-v4".equals(modelId)) return "DeepSeek V4";
            return "";
        }
    }
}
