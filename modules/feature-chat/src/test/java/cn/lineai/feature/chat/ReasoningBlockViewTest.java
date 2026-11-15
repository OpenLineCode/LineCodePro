package cn.lineai.feature.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.core.model.ToolCallRecord;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.toolcall.ToolCallGroupView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Rendering contracts of the reasoning disclosure and the per-step tool header. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReasoningBlockViewTest {

    private static final int WIDTH = 1080;
    private static final int CAP_DP = 180;

    /** The reported bug: an expanded body measured inside the timeline stayed empty. */
    @Test
    public void expandedBodyShowsTheReasoningTextInsideAnUnboundedTimeline() {
        ReasoningBlockView view = block("checking the build output", true);
        assertEquals(View.VISIBLE, scroller(view).getVisibility());
        assertEquals("checking the build output",
                ((TextView) scroller(view).getChildAt(0)).getText().toString());
        assertTrue("expanded reasoning body must have height",
                scroller(view).getMeasuredHeight() > 0);
    }

    @Test
    public void expandedBodyWithoutInnerScrollingAlsoKeepsItsHeight() {
        ReasoningBlockView view = block("checking the build output", false);
        assertTrue("expanded reasoning body must have height",
                scroller(view).getMeasuredHeight() > 0);
    }

    /** Long thoughts keep the 180dp cap when the parent leaves the height unbounded. */
    @Test
    public void longReasoningInsideTheTimelineStaysCapped() {
        ReasoningBlockView view = block(longThought(), true);
        int height = scroller(view).getMeasuredHeight();
        assertTrue("capped body must stay visible: " + height, height > 0);
        assertTrue("capped body must respect the cap: " + height, height <= dp(CAP_DP) + 1);
    }

    /** Long thoughts stay capped when the parent offers more room than the cap. */
    @Test
    public void longReasoningStaysCappedUnderAGenerousParent() {
        ReasoningBlockView view = measuredBlock(longThought(), true,
                View.MeasureSpec.makeMeasureSpec(dp(CAP_DP * 3), View.MeasureSpec.AT_MOST));
        int height = scroller(view).getMeasuredHeight();
        assertTrue("capped body must respect the cap: " + height, height <= dp(CAP_DP) + 1);
    }

    /** Disabling the scroll preference expands the block to its natural height. */
    @Test
    public void longReasoningGrowsFullyWithoutInnerScrolling() {
        ReasoningBlockView view = block(longThought(), false);
        assertTrue("natural body must exceed the cap: " + scroller(view).getMeasuredHeight(),
                scroller(view).getMeasuredHeight() > dp(CAP_DP));
    }

    /** Flipping the scroll preference restyles a reasoning block that is already on screen. */
    @Test
    public void scrollPreferenceAppliesToAlreadyRenderedReasoningBlocks() {
        Context context = RuntimeEnvironment.getApplication();
        Tokens tokens = Tokens.of(ThemePalette.dark());
        TurnAssembler.Row row = toolRow(true, 1, longThought());
        ProcessPanel panel = new ProcessPanel(context, tokens);
        panel.bind(row, tokens, env(true, true), row.key);
        ReasoningBlockView block = findBlock(panel);
        assertNotNull("the process panel must render the reasoning block", block);
        measure(block, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertTrue("the scroll cap must apply while the preference is on",
                scroller(block).getMeasuredHeight() <= dp(CAP_DP) + 1);

        panel.bind(row, tokens, env(true, false), row.key);
        measure(block, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertTrue("turning the scroll preference off must expand the rendered block",
                scroller(block).getMeasuredHeight() > dp(CAP_DP));
    }

    @Test
    public void perStepModeDropsTheCountForSingleCallGroups() {
        Context context = RuntimeEnvironment.getApplication();
        Tokens tokens = Tokens.of(ThemePalette.dark());
        ProcessPanel panel = panel(toolRow(true, 1), tokens, true);

        assertEquals(context.getString(R.string.chat_tools_step_label), headerLabel(find(panel)));
    }

    @Test
    public void perStepModeKeepsTheCountForParallelBatches() {
        Context context = RuntimeEnvironment.getApplication();
        Tokens tokens = Tokens.of(ThemePalette.dark());
        ProcessPanel panel = panel(toolRow(true, 2), tokens, true);

        assertEquals(context.getString(R.string.chat_tools_count, 2), headerLabel(find(panel)));
    }

    @Test
    public void foldedModeAlwaysShowsTheCallCount() {
        Context context = RuntimeEnvironment.getApplication();
        Tokens tokens = Tokens.of(ThemePalette.dark());
        ProcessPanel panel = panel(toolRow(false, 1), tokens, false);

        assertEquals(context.getString(R.string.chat_tools_count, 1), headerLabel(find(panel)));
    }

    /** The header label must follow a preference flip without rebuilding the panel. */
    @Test
    public void toolCountLabelFollowsTheSettingFlip() {
        Context context = RuntimeEnvironment.getApplication();
        Tokens tokens = Tokens.of(ThemePalette.dark());
        TurnAssembler.Row row = toolRow(false, 1);
        ProcessPanel panel = panel(row, tokens, true);
        assertEquals(context.getString(R.string.chat_tools_step_label), headerLabel(find(panel)));

        panel.bind(row, tokens, env(false), row.key);
        assertEquals(context.getString(R.string.chat_tools_count, 1), headerLabel(find(panel)));
    }

    /** Builds one expanded reasoning block measured like a timeline row. */
    private static ReasoningBlockView block(String text, boolean scrollable) {
        return measuredBlock(text, scrollable,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    /** Builds one expanded reasoning block and measures it under the given parent height spec. */
    private static ReasoningBlockView measuredBlock(String text, boolean scrollable,
                                                     int heightMeasureSpec) {
        Context context = RuntimeEnvironment.getApplication();
        ReasoningBlockView view = new ReasoningBlockView(context, Tokens.of(ThemePalette.dark()));
        view.bind("row:reasoning", text, false, scrollable, false, new DisclosureState());
        measure(view, heightMeasureSpec);
        view.getChildAt(0).performClick();
        measure(view, heightMeasureSpec);
        return view;
    }

    /** Measures and lays the block out the way the timeline's scroll column does. */
    private static void measure(View view, int heightMeasureSpec) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                heightMeasureSpec);
        view.layout(0, 0, WIDTH, view.getMeasuredHeight());
    }

    private static ScrollView scroller(ReasoningBlockView view) {
        return (ScrollView) view.getChildAt(1);
    }

    private static int dp(int value) {
        return Math.round(value * RuntimeEnvironment.getApplication().getResources()
                .getDisplayMetrics().density);
    }

    /** A thought long enough that the 180dp cap must clip it. */
    private static String longThought() {
        StringBuilder thought = new StringBuilder();
        for (int line = 0; line < 120; line++) {
            thought.append("reasoning line ").append(line).append('\n');
        }
        return thought.toString();
    }

    /** Binds a fresh process panel for one tool round. */
    private static ProcessPanel panel(TurnAssembler.Row row, Tokens tokens, boolean stepThinking) {
        ProcessPanel panel = new ProcessPanel(RuntimeEnvironment.getApplication(), tokens);
        panel.bind(row, tokens, env(stepThinking), row.key);
        return panel;
    }

    /** Display environment carrying just the per-step thinking preference. */
    private static RowEnv env(boolean stepThinking) {
        UiPreferences preferences = UiPreferences.defaults()
                .with(UiPreferences.Field.STEP_THINKING, Boolean.toString(stepThinking));
        return new RowEnv(new DisclosureState(), null, preferences, null, null, null, null);
    }

    /** Display environment with per-step thinking and thinking-scroll values; blocks auto-open. */
    private static RowEnv env(boolean stepThinking, boolean thinkingScroll) {
        UiPreferences preferences = UiPreferences.defaults()
                .with(UiPreferences.Field.STEP_THINKING, Boolean.toString(stepThinking))
                .with(UiPreferences.Field.THINKING_SCROLL, Boolean.toString(thinkingScroll))
                .with(UiPreferences.Field.THINKING_AUTO_EXPAND, "true");
        return new RowEnv(new DisclosureState(), null, preferences, null, null, null, null);
    }

    /**
     * One user turn with one assistant round. The process shape is identical for both preference
     * values, so the label can be asserted across a flip; the round carries no prose, so the
     * reasoning only surfaces in per-step mode.
     */
    private static TurnAssembler.Row toolRow(boolean stepThinking, int calls) {
        return toolRow(stepThinking, calls, "thinking one");
    }

    /** {@link #toolRow(boolean, int)} with explicit reasoning text. */
    private static TurnAssembler.Row toolRow(boolean stepThinking, int calls, String reasoning) {
        List<ToolCallRecord> records = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder().id("u1").role(Role.USER)
                .status(Message.Status.COMPLETE).seq(1L)
                .addBlock(ContentBlock.text("go")).times(1L, 1L).build());
        Message.Builder round = Message.builder().id("a1").role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE).seq(2L)
                .addBlock(ContentBlock.reasoning(reasoning)).times(2L, 2L);
        for (int index = 0; index < calls; index++) {
            records.add(new ToolCallRecord("call-" + index, "read_file", "{}"));
        }
        messages.add(round.toolCalls(records).build());
        for (int index = 0; index < calls; index++) {
            messages.add(Message.builder().id("t" + index).role(Role.TOOL)
                    .status(Message.Status.COMPLETE).seq(3L + index)
                    .tool("call-" + index, "read_file")
                    .addBlock(ContentBlock.text("ok")).times(3L, 3L).build());
        }
        messages.add(Message.builder().id("a2").role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE).seq(9L)
                .addBlock(ContentBlock.text("done")).times(9L, 9L).build());
        List<TurnAssembler.Row> rows = TurnAssembler.assemble(messages,
                Collections.<String>emptySet(), false, stepThinking);
        return rows.get(1);
    }

    private static String headerLabel(ToolCallGroupView group) {
        ViewGroup header = (ViewGroup) group.getChildAt(0);
        return ((TextView) header.getChildAt(1)).getText().toString();
    }

    private static ToolCallGroupView find(View view) {
        if (view instanceof ToolCallGroupView) {
            return (ToolCallGroupView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int index = 0; index < parent.getChildCount(); index++) {
                ToolCallGroupView found = find(parent.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ReasoningBlockView findBlock(View view) {
        if (view instanceof ReasoningBlockView) {
            return (ReasoningBlockView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int index = 0; index < parent.getChildCount(); index++) {
                ReasoningBlockView found = findBlock(parent.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
