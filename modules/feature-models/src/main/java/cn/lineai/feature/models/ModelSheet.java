package cn.lineai.feature.models;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Bottom sheet listing the long-press actions of a provider or model row.
 *
 * <p>The sheet is built fresh for each invocation from static string arguments, so it needs no
 * theme binding; every action dismisses the sheet before running its callback, which keeps a slow
 * listener from interacting with a visible sheet. Confirmation dialogs are owned by the pages,
 * not by the sheet.</p>
 */
final class ModelSheet {

    /** One selectable sheet action. */
    static final class Item {
        final String label;
        final String description;
        final boolean danger;
        final Runnable action;

        /**
         * Creates one action row.
         *
         * @param label primary text
         * @param description secondary text; empty hides the line
         * @param danger true to tint the row with the danger color
         * @param action run after the sheet dismisses; never null
         */
        Item(String label, String description, boolean danger, Runnable action) {
            this.label = label == null ? "" : label;
            this.description = description == null ? "" : description;
            this.danger = danger;
            this.action = action;
        }
    }

    /** Not instantiable; the sheet is created through {@link #show}. */
    private ModelSheet() {
    }

    /**
     * Builds and shows the action sheet.
     *
     * @param context hosting context
     * @param tokens palette snapshot
     * @param title sheet heading, normally the tapped row's display name
     * @param items actions in display order; an empty list shows only the heading
     */
    static void show(Context context, Tokens tokens, String title, List<Item> items) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(context, tokens.surfaceElevated, 16));
        View handle = new View(context);
        handle.setBackground(Ui.rounded(context, tokens.textTertiary, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = Ui.dp(context, 8);
        handleParams.bottomMargin = Ui.dp(context, 4);
        panel.addView(handle, handleParams);
        TextView heading = Ui.bold(context, title, 17, tokens.text);
        Ui.padding(heading, 16, 0, 16, 12);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(context);
        divider.setBackgroundColor(tokens.borderLight);
        panel.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        Dialog[] holder = new Dialog[1];
        for (Item item : items) {
            panel.addView(actionRow(context, tokens, item, holder),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        panel.setPadding(0, 0, 0, Ui.dp(context, 12));
        holder[0] = Sheets.showBottom(context, tokens, panel);
    }

    /** Builds one action row that dismisses the sheet before running its callback. */
    private static LinearLayout actionRow(Context context, Tokens tokens, Item item,
                                          Dialog[] holder) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            item.action.run();
        });
        Ui.padding(row, 16, 14, 16, 14);
        row.addView(Ui.text(context, item.label, Tokens.FONT_MD,
                item.danger ? tokens.danger : tokens.text, Typeface.NORMAL),
                new LinearLayout.LayoutParams(-1, -2));
        if (!item.description.isEmpty()) {
            TextView description = Ui.text(context, item.description, Tokens.FONT_XS,
                    tokens.textTertiary, Typeface.NORMAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(context, 2);
            row.addView(description, params);
        }
        return row;
    }
}
