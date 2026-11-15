package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.feature.chat.toolcall.BoundedScrollView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Collapsible reasoning block (C++ {@code ReasoningTimelineBlock}, legacy
 * {@code ThinkingBlockView}).
 *
 * <p>The header is a 48dp disclosure row with a 13sp secondary label; the body is 13sp tertiary
 * text, capped at 180dp with its own scroll view when the thinking-scroll preference is on.</p>
 */
final class ReasoningBlockView extends LinearLayout {

    private final LinearLayout header;
    private final TextView label;
    private final IconButton chevron;
    private final BoundedScrollView scroller;
    private final TextView body;
    private Tokens tokens;
    private DisclosureState disclosure;
    private boolean byDefault;
    private String key = "";
    private boolean open;
    private boolean live;
    private boolean scrollable = true;

    /** Builds the 48dp disclosure header and the capped, scrollable body. */
    ReasoningBlockView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(header, 0, 8, 0, 8);
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> toggle());
        label = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        header.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(28, 16);
        chevron.setClickable(false);
        header.addView(chevron, new LayoutParams(Ui.dp(context, 28), Ui.dp(context, 32)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        scroller = new BoundedScrollView(context, 180);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setFillViewport(false);
        body = Ui.text(context, "", Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        body.setLineSpacing(Ui.dp(context, 3), 1f);
        scroller.addView(body, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(context, 4);
        addView(scroller, bodyParams);
        setPadding(0, 0, 0, Ui.dp(context, 10));
        applyTokens(tokens);
    }

    /**
     * Rebinds the block: text and scroll preference are applied only when changed, the open
     * state is resolved through the shared disclosure memory.
     *
     * @param key        disclosure identity of this block
     * @param text       reasoning text
     * @param live       true while the owning turn is still streaming
     * @param scrollable true to cap the body at 180dp with its own scroll
     * @param byDefault  open state before any user override
     * @param disclosure shared disclosure memory
     */
    void bind(String key, String text, boolean live, boolean scrollable, boolean byDefault,
              DisclosureState disclosure) {
        this.key = key;
        this.disclosure = disclosure;
        this.byDefault = byDefault;
        this.live = live;
        applyScrollPreference(scrollable);
        if (!text.equals(body.getText().toString())) {
            body.setText(text);
        }
        open = disclosure.isOpen(key, byDefault);
        this.live = live;
        updateLabel();
        applyState();
    }

    /**
     * Applies the thinking-scroll preference to an already rendered block, so flipping it does
     * not require a rebuild.
     *
     * @param scrollable true to cap the body at 180dp with its own scroll
     */
    void applyScrollPreference(boolean scrollable) {
        if (this.scrollable == scrollable) {
            return;
        }
        this.scrollable = scrollable;
        scroller.setMaxHeightDp(scrollable ? 180 : 0);
        scroller.setVerticalScrollBarEnabled(scrollable);
    }

    /** Called when the turn finishes so a live "thinking" header becomes the done label. */
    void setLive(boolean value) {
        if (live == value) {
            return;
        }
        live = value;
        updateLabel();
    }

    /** Chooses the "thinking" or "done" header label from the live flag. */
    private void updateLabel() {
        label.setText(getContext().getString(live
                ? R.string.message_assistant_thinking
                : R.string.thinking_done_label));
    }

    /** Applies the current design tokens to header and body. */
    void applyTokens(Tokens current) {
        tokens = current;
        label.setTextColor(current.textSecondary);
        chevron.setIconColor(current.textTertiary);
        body.setTextColor(current.textTertiary);
        body.setHighlightColor(current.accentMuted);
        body.setLinkTextColor(current.accent);
    }

    /** Header tap: flips the disclosure state. */
    private void toggle() {
        if (disclosure == null) {
            return;
        }
        disclosure.toggle(key);
        open = disclosure.isOpen(key, byDefault);
        applyState();
    }

    /** Points the chevron and shows or hides the body. */
    private void applyState() {
        chevron.setIconType(open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        scroller.setVisibility(open ? VISIBLE : GONE);
    }
}
