package cn.lineai.feature.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Modal card announcing a newer GitHub release.
 *
 * <p>The changelog scrolls inside a capped area so a long release body cannot push the actions off
 * screen. The mute switch persists immediately, while "Go to update" clears the mute again because
 * acting on the release ends the need for reminders. The dialog starts no threads: the caller
 * passes the action that opens the release page.</p>
 */
public final class UpdateDialog {

    private static final int CHANGELOG_HEIGHT_DP = 220;
    private static final float WIDTH_FRACTION = 0.86f;

    /** Not instantiable; the dialog is created through the static factory. */
    private UpdateDialog() {
    }

    /**
     * Shows the update card.
     *
     * @param context hosting context
     * @param tokens palette snapshot used for the panel and text colors
     * @param release release advertised by the card
     * @param preferences persisted mute flag written by the switch
     * @param onUpdate action opening the release page; run after the mute flag is cleared
     */
    public static void show(Context context, Tokens tokens, ReleaseInfo release,
                            UpdatePreferences preferences, Runnable onUpdate) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(context, tokens.surfaceElevated, 16));
        Ui.padding(panel, 20, 20, 20, 12);

        TextView heading = Ui.bold(context,
                context.getString(R.string.update_dialog_title, release.version), 18, tokens.text);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView label = Ui.medium(context,
                context.getString(R.string.update_dialog_changelog), 12, tokens.textSecondary);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = Ui.dp(context, 16);
        panel.addView(label, labelParams);

        TextView changelog = Ui.text(context, release.body, 12, tokens.textSecondary,
                Typeface.NORMAL);
        changelog.setTypeface(Typeface.MONOSPACE);
        changelog.setTextIsSelectable(true);
        changelog.setLineSpacing(Ui.dp(context, 2), 1f);
        Ui.padding(changelog, 12, 12, 12, 12);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackground(Ui.rounded(context, tokens.inputBg, 8));
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(changelog, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                -1, Ui.dp(context, CHANGELOG_HEIGHT_DP));
        scrollParams.topMargin = Ui.dp(context, 8);
        panel.addView(scroll, scrollParams);

        Switch mute = new Switch(context);
        mute.setText(R.string.update_dialog_mute);
        mute.setTextColor(tokens.text);
        mute.setTextSize(14);
        mute.setChecked(preferences.muted());
        mute.setOnCheckedChangeListener((button, checked) -> preferences.setMuted(checked));
        LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(-1, -2);
        muteParams.topMargin = Ui.dp(context, 12);
        panel.addView(mute, muteParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 16);
        panel.addView(actions, actionsParams);
        TextView cancel = action(context, tokens, R.string.update_action_cancel,
                tokens.textSecondary);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        TextView go = action(context, tokens, R.string.update_action_go, tokens.accent);
        go.setOnClickListener(v -> {
            preferences.setMuted(false);
            dialog.dismiss();
            onUpdate.run();
        });
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(-2, -2);
        goParams.leftMargin = Ui.dp(context, 8);
        actions.addView(go, goParams);

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (context.getResources().getDisplayMetrics().widthPixels
                    * WIDTH_FRACTION), -2);
        }
    }

    /** Builds one flat dialog action label. */
    private static TextView action(Context context, Tokens tokens, int label, int color) {
        TextView button = Ui.medium(context, context.getString(label), 15, color);
        button.setPadding(Ui.dp(context, 12), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));
        return button;
    }
}
