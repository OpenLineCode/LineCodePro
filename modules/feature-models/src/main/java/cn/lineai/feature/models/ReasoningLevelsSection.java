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

import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.ReasoningLevel;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Editable reasoning-level list of one model.
 *
 * <p>A built-in row shows the localized preset label and a read-only wire value; a custom row lets
 * the user edit both the label and the wire value. The trailing "Custom" row appends a blank entry
 * and "Restore defaults" reloads the family profile from {@link ModelProfile}. The section
 * rebuilds only when rows are added or removed; theme changes restyle the existing inputs in
 * place. Entries without a wire value are dropped later, by the form.</p>
 */
final class ReasoningLevelsSection extends LinearLayout {

    private final ThemeBinder theme;
    private final String modelId;
    private final List<ReasoningLevel> levels = new ArrayList<>();
    private final LinearLayout host;

    /**
     * Builds the section and renders one row per initial level.
     *
     * @param context hosting context
     * @param theme binder shared with the page; restyle callbacks are registered on it
     * @param modelId provider-side model identifier driving {@link ModelProfile} defaults
     * @param initial levels to edit; null renders the family defaults
     */
    ReasoningLevelsSection(Context context, ThemeBinder theme, String modelId,
                           List<ReasoningLevel> initial) {
        super(context);
        this.theme = theme;
        this.modelId = modelId == null ? "" : modelId;
        host = new LinearLayout(context);
        host.setOrientation(VERTICAL);
        addView(host, new LinearLayout.LayoutParams(-1, -2));
        setLevels(initial == null || initial.isEmpty()
                ? ModelProfile.reasoningLevels(this.modelId) : initial);
    }

    /**
     * Replaces the level rows and re-renders the section.
     *
     * @param values new levels in display order; null renders the empty state
     */
    void setLevels(List<ReasoningLevel> values) {
        levels.clear();
        if (values != null) {
            for (ReasoningLevel level : values) {
                if (level != null) levels.add(level);
            }
        }
        render();
    }

    /**
     * Reloads the level rows from the family profile of the edited model.
     */
    void restoreDefaults() {
        setLevels(ModelProfile.reasoningLevels(modelId));
    }

    /**
     * Returns the rows in their current order; blank entries are kept so the form can drop them.
     *
     * @return a new list of editable levels; never null
     */
    List<ReasoningLevel> levels() {
        return new ArrayList<>(levels);
    }

    /** Rebuilds the rows from the current list; runs on data changes only. */
    private void render() {
        host.removeAllViews();
        SettingsSection section = new SettingsSection(getContext(),
                R.string.model_detail_section_reasoning_levels, theme);
        host.addView(section, new LinearLayout.LayoutParams(-1, -2));
        if (levels.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.model_detail_reasoning_empty),
                    Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
            empty.setLineSpacing(Ui.dp(getContext(), 3), 1f);
            Ui.padding(empty, 16, 14, 16, 14);
            section.row(empty, false, 0);
        }
        for (int index = 0; index < levels.size(); index++) {
            section.row(levelRow(index), false, 0);
        }
        section.row(addCustomRow(), false, 0);
        section.row(restoreRow(), false, 0);
    }

    /** Builds the "add custom level" row. */
    private LinearLayout addCustomRow() {
        return actionRow(R.string.model_detail_reasoning_add_custom, Icons.PLUS, false, () -> {
            levels.add(new ReasoningLevel("", "", ""));
            render();
        });
    }

    /** Builds the "restore family defaults" row. */
    private LinearLayout restoreRow() {
        return actionRow(R.string.model_detail_reasoning_restore, Icons.REFRESH_CW, false,
                this::restoreDefaults);
    }

    /** Builds one tappable plain action row without a description line. */
    private LinearLayout actionRow(int label, int glyph, boolean danger, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        Ui.padding(row, 16, 13, 16, 13);
        IconButton icon = new IconButton(context, glyph);
        icon.setIconSizeDp(30, 16);
        icon.setClickable(false);
        icon.setIconColor(danger ? tokens.danger : tokens.accent);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 30), Ui.dp(context, 30)));
        TextView title = Ui.medium(context, context.getString(label), Tokens.FONT_MD,
                danger ? tokens.danger : tokens.text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = Ui.dp(context, 8);
        row.addView(title, titleParams);
        theme.bind(t -> {
            icon.setIconColor(danger ? t.danger : t.accent);
            title.setTextColor(danger ? t.danger : t.text);
        });
        return row;
    }

    /** Builds one level row: label column, wire-value input and a delete control. */
    private LinearLayout levelRow(int index) {
        Context context = getContext();
        ReasoningLevel level = levels.get(index);
        boolean builtIn = !level.preset.isEmpty();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 10, 16, 10);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                Ui.dp(context, builtIn ? 76 : 108), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(labels, labelsParams);
        if (builtIn) {
            TextView label = Ui.medium(context, presetLabel(context, level.preset),
                    Tokens.FONT_SM, theme.current().text);
            labels.addView(label, new LinearLayout.LayoutParams(-1, -2));
            theme.bind(t -> label.setTextColor(t.text));
        } else {
            EditText labelInput = compactField(level.label,
                    R.string.model_detail_reasoning_label_hint, index, true);
            labels.addView(labelInput, new LinearLayout.LayoutParams(-1, Ui.dp(context, 42)));
        }
        EditText valueInput = compactField(level.value,
                R.string.model_detail_reasoning_value_hint, index, false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0,
                Ui.dp(context, 42), 1f);
        valueParams.leftMargin = Ui.dp(context, 8);
        row.addView(valueInput, valueParams);
        IconButton trash = new IconButton(context, Icons.TRASH_2);
        trash.setIconSizeDp(34, 16);
        trash.setContentDescription(context.getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            levels.remove(index);
            render();
        });
        LinearLayout.LayoutParams trashParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 34), Ui.dp(context, 42));
        trashParams.leftMargin = Ui.dp(context, 4);
        row.addView(trash, trashParams);
        theme.bind(t -> trash.setIconColor(t.textTertiary));
        return row;
    }

    /**
     * Resolves the localized label of a built-in preset.
     *
     * @param context context resolving the string resources
     * @param preset preset identifier from {@link ReasoningLevel}
     * @return localized label, or the raw preset when it has no translation yet
     */
    private static String presetLabel(Context context, String preset) {
        int label = presetLabelRes(preset);
        return label == 0 ? preset : context.getString(label);
    }

    /** Maps a preset to its localized label resource, or 0 for an unknown preset. */
    private static int presetLabelRes(String preset) {
        switch (preset) {
            case ReasoningLevel.PRESET_OFF: return R.string.screen_llm_thinking_off_label;
            case ReasoningLevel.PRESET_AUTO: return R.string.screen_llm_thinking_auto_label;
            case ReasoningLevel.PRESET_MINIMAL: return R.string.model_reasoning_minimal_label;
            case ReasoningLevel.PRESET_LOW: return R.string.screen_llm_thinking_low_label;
            case ReasoningLevel.PRESET_MEDIUM: return R.string.screen_llm_thinking_medium_label;
            case ReasoningLevel.PRESET_HIGH: return R.string.screen_llm_thinking_high_label;
            case ReasoningLevel.PRESET_MAX: return R.string.screen_llm_thinking_max_label;
            case ReasoningLevel.PRESET_ON: return R.string.model_reasoning_on_label;
            default: return 0;
        }
    }

    /** Creates a compact input writing through to the level list as the user types. */
    private EditText compactField(String value, int hint, int index, boolean labelField) {
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
                if (index >= levels.size()) return;
                String text = s == null ? "" : s.toString();
                ReasoningLevel current = levels.get(index);
                levels.set(index, labelField
                        ? new ReasoningLevel(current.preset, text, current.value)
                        : new ReasoningLevel(current.preset, current.label, text));
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
