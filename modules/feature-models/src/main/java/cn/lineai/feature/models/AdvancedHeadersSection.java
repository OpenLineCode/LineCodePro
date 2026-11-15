package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Compact editable list of custom HTTP headers.
 *
 * <p>Used by the provider editor (shared provider headers) and by the model detail page (model
 * level headers). Rows mirror the MCP header editor: two compact inputs and a trailing trash
 * button, with an add row above them. The section rebuilds only when the header set changes, never
 * on theme changes; theme callbacks restyle the existing inputs in place. Blank-named rows are
 * ignored by {@link #headers()}.</p>
 */
final class AdvancedHeadersSection extends LinearLayout {

    private final ThemeBinder theme;
    private final int titleRes;
    private final List<HttpHeader> entries = new ArrayList<>();
    private final LinearLayout host;
    private EditText pendingFocus;

    /**
     * Builds the section and renders one row per initial header.
     *
     * @param context hosting context
     * @param theme binder shared with the page; restyle callbacks are registered on it
     * @param titleRes resource id of the section heading
     * @param initial headers to edit; null renders as empty
     */
    AdvancedHeadersSection(Context context, ThemeBinder theme, int titleRes,
                           List<HttpHeader> initial) {
        super(context);
        this.theme = theme;
        this.titleRes = titleRes;
        setOrientation(VERTICAL);
        if (initial != null) entries.addAll(initial);
        host = new LinearLayout(context);
        host.setOrientation(VERTICAL);
        addView(host, new LinearLayout.LayoutParams(-1, -2));
        render();
    }

    /**
     * Creates one empty header row and focuses its name input.
     */
    void addRow() {
        entries.add(new HttpHeader("", ""));
        render();
        if (pendingFocus != null) {
            EditText focus = pendingFocus;
            pendingFocus = null;
            focus.post(focus::requestFocus);
        }
    }

    /**
     * Returns the named header rows in display order.
     *
     * @return a new list of normalized headers; never null
     */
    List<HttpHeader> headers() {
        List<HttpHeader> result = new ArrayList<>();
        for (HttpHeader header : entries) {
            if (header.name == null || header.name.trim().isEmpty()) continue;
            result.add(new HttpHeader(header.name, header.value));
        }
        return result;
    }

    /** Rebuilds the full-width grouped panel; runs on data changes only. */
    private void render() {
        host.removeAllViews();
        Context context = getContext();
        TextView title = Ui.medium(context, context.getString(titleRes), Tokens.FONT_SM,
                theme.current().textSecondary);
        Ui.padding(title, 0, 20, 0, 8);
        host.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Ui.rounded(context, theme.current().surfaceElevated, 12));
        host.addView(group, new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> {
            title.setTextColor(t.textSecondary);
            group.setBackground(Ui.rounded(context, t.surfaceElevated, 12));
        });
        group.addView(addRowView(), new LinearLayout.LayoutParams(-1, -2));
        for (int index = 0; index < entries.size(); index++) {
            group.addView(divider(), dividerParams());
            group.addView(headerRow(index), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** Hairline divider between grouped header rows. */
    private android.view.View divider() {
        android.view.View line = new android.view.View(getContext());
        theme.bind(t -> line.setBackgroundColor(t.borderLight));
        return line;
    }

    /** Layout params of a hairline divider, inset after the row padding. */
    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 1);
        params.leftMargin = Ui.dp(getContext(), 16);
        params.rightMargin = Ui.dp(getContext(), 16);
        return params;
    }

    /** Builds the add-header row with the shared localized title and description. */
    private LinearLayout addRowView() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> addRow());
        Ui.padding(row, 16, 12, 16, 12);
        IconButton icon = new IconButton(context, Icons.PLUS);
        icon.setIconSizeDp(34, 18);
        icon.setClickable(false);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        row.addView(labels, labelParams);
        labels.addView(Ui.medium(context, context.getString(R.string.header_editor_add),
                Tokens.FONT_SM, tokens.accent), new LinearLayout.LayoutParams(-1, -2));
        TextView description = Ui.text(context, context.getString(R.string.header_editor_add_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        labels.addView(description, new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> {
            description.setTextColor(t.textTertiary);
            icon.setIconColor(t.accent);
        });
        return row;
    }

    /** Builds one editable name/value header row with a delete control. */
    private LinearLayout headerRow(int index) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 10, 16, 10);
        EditText name = compactField(entries.get(index).name, R.string.header_editor_name, index,
                true);
        EditText value = compactField(entries.get(index).value, R.string.header_editor_value,
                index, false);
        if (index == entries.size() - 1 && entries.get(index).name.isEmpty()) pendingFocus = name;
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                Ui.dp(context, 42), 1f);
        row.addView(name, nameParams);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0,
                Ui.dp(context, 42), 1f);
        valueParams.leftMargin = Ui.dp(context, 8);
        row.addView(value, valueParams);
        IconButton trash = new IconButton(context, Icons.TRASH_2);
        trash.setIconSizeDp(34, 16);
        trash.setContentDescription(context.getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            entries.remove(index);
            render();
        });
        LinearLayout.LayoutParams trashParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 34), Ui.dp(context, 42));
        trashParams.leftMargin = Ui.dp(context, 4);
        row.addView(trash, trashParams);
        theme.bind(t -> trash.setIconColor(t.textTertiary));
        return row;
    }

    /** Creates a compact input writing through to the entry lists as the user types. */
    private EditText compactField(String value, int hint, int index, boolean nameField) {
        EditText input = new EditText(getContext());
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setTextSize(Tokens.FONT_SM);
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        input.setMinHeight(Ui.dp(getContext(), 42));
        input.setPadding(Ui.dp(getContext(), 10), Ui.dp(getContext(), 8),
                Ui.dp(getContext(), 10), Ui.dp(getContext(), 8));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable s) {
                if (index >= entries.size()) return;
                String text = s == null ? "" : s.toString();
                HttpHeader current = entries.get(index);
                entries.set(index, nameField
                        ? new HttpHeader(text, current.value)
                        : new HttpHeader(current.name, text));
            }
        });
        theme.bind(t -> {
            input.setTextColor(t.text);
            input.setHintTextColor(t.textTertiary);
            input.setBackground(Ui.roundedStroke(getContext(), Color.TRANSPARENT, 8, t.borderLight));
        });
        return input;
    }
}
