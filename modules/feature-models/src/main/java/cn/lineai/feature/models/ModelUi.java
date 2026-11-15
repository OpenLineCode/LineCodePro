package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.TokenText;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Small view factories shared by the provider and model-management pages.
 *
 * <p>Every factory returns a freshly styled view so pages assemble rows without inheriting
 * listeners, and theme bindings restyle the existing children in place. The automatic capability
 * caption comes from {@link ModelProfile}, the single inference source of the module, so the
 * inferred line on a provider row and the values written on save cannot disagree.</p>
 */
final class ModelUi {

    /** Not instantiable; every factory is static. */
    private ModelUi() {
    }

    /**
     * Returns the user-facing protocol name.
     *
     * @param context context resolving the localized strings
     * @param protocol protocol to name; never null
     * @return localized protocol name
     */
    static String protocolLabel(Context context, ModelConfig.Protocol protocol) {
        return PresetText.protocol(context, protocol);
    }

    /**
     * Maps a protocol to the badge fill color; unknown values fall back to the OpenAI tone.
     *
     * @param protocol protocol to color; never null
     * @return badge fill color
     */
    static int protocolColor(ModelConfig.Protocol protocol) {
        switch (protocol) {
            case CODEX_RESPONSES: return Color.parseColor("#4B8BFF");
            case ANTHROPIC_MESSAGES: return Color.parseColor("#B86F50");
            case LOCAL_GGUF: return Color.parseColor("#2E7D62");
            case OPENAI_COMPATIBLE:
            default: return Color.parseColor("#10A37F");
        }
    }

    /**
     * Builds a compact protocol badge pill.
     *
     * @param context hosting context
     * @param tokens palette snapshot
     * @param protocol protocol rendered by the badge
     * @return a new badge view owned by the caller
     */
    static TextView protocolBadge(Context context, Tokens tokens, ModelConfig.Protocol protocol) {
        TextView badge = Ui.bold(context, protocolLabel(context, protocol), Tokens.FONT_XS,
                tokens.textOnColor);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(context, protocolColor(protocol), 8));
        Ui.padding(badge, 8, 4, 8, 4);
        return badge;
    }

    /**
     * Builds the inferred capability caption of a model id.
     *
     * @param context context resolving the localized strings
     * @param modelId provider-side model identifier; may be null
     * @return caption such as {@code 128k context · Reasoning · Image}
     */
    static String inferredMeta(Context context, String modelId) {
        String contextPhrase = context.getString(R.string.screen_provider_model_meta_context,
                TokenText.format(ModelProfile.contextSize(modelId)));
        return context.getString(R.string.screen_provider_model_meta, contextPhrase,
                capabilityCaption(context, modelId));
    }

    /**
     * Joins the inferred capability labels, falling back to the text-only label.
     *
     * @param context context resolving the localized strings
     * @param modelId provider-side model identifier; may be null
     * @return joined capability labels
     */
    private static String capabilityCaption(Context context, String modelId) {
        List<String> labels = new ArrayList<>();
        if (ModelProfile.supportsReasoning(modelId)) {
            labels.add(context.getString(R.string.screen_provider_model_reasoning));
        }
        if (ModelProfile.supportsVision(modelId)) {
            labels.add(context.getString(R.string.screen_provider_model_vision));
        }
        if (ModelProfile.supportsVideo(modelId)) {
            labels.add(context.getString(R.string.screen_provider_model_video));
        }
        if (labels.isEmpty()) return context.getString(R.string.screen_provider_model_text_only);
        return String.join(" · ", labels);
    }

    /**
     * Builds one tappable action row with a leading glyph, title, optional description and a
     * trailing chevron; the destructive variant tints the row with the danger color.
     *
     * @param context hosting context
     * @param theme shared binder receiving the restyle callback
     * @param glyph icon id from {@link Icons}
     * @param label resource id of the row title
     * @param description resource id of the secondary line, or 0 to hide it
     * @param danger true to tint the row for a destructive action
     * @param action tap action; never null
     * @return a new row view owned by the caller
     */
    static LinearLayout actionRow(Context context, ThemeBinder theme, int glyph, int label,
                                  int description, boolean danger, Runnable action) {
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        Ui.padding(row, 16, 14, 16, 14);
        IconButton glyphView = null;
        if (glyph != 0) {
            glyphView = new IconButton(context, glyph);
            glyphView.setIconSizeDp(34, 18);
            glyphView.setIconColor(danger ? tokens.danger : tokens.accent);
            glyphView.setClickable(false);
            row.addView(glyphView,
                    new LinearLayout.LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        }
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (glyph != 0) labelsParams.leftMargin = Ui.dp(context, 8);
        row.addView(labels, labelsParams);
        TextView title = Ui.medium(context, context.getString(label), Tokens.FONT_MD,
                danger ? tokens.danger : tokens.text);
        labels.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hint = null;
        if (description != 0) {
            hint = Ui.text(context, context.getString(description), Tokens.FONT_XS,
                    tokens.textTertiary, Typeface.NORMAL);
            hint.setLineSpacing(Ui.dp(context, 3), 1f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = Ui.dp(context, 2);
            labels.addView(hint, hintParams);
        }
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconSizeDp(20, 16);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setClickable(false);
        row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        TextView finalHint = hint;
        IconButton finalGlyph = glyphView;
        theme.bind(t -> {
            title.setTextColor(danger ? t.danger : t.text);
            chevron.setIconColor(t.textTertiary);
            if (finalHint != null) finalHint.setTextColor(t.textTertiary);
            if (finalGlyph != null) finalGlyph.setIconColor(danger ? t.danger : t.accent);
        });
        return row;
    }

    /**
     * Builds the accent-tinted "current" badge shown on the active provider card.
     *
     * @param context hosting context
     * @param tokens palette snapshot
     * @param label resource id of the badge caption
     * @return a new badge view owned by the caller
     */
    static TextView accentBadge(Context context, Tokens tokens, int label) {
        TextView badge = Ui.bold(context, context.getString(label), Tokens.FONT_XS,
                tokens.accent);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(context, tokens.accentMuted, 8));
        Ui.padding(badge, 8, 4, 8, 4);
        return badge;
    }
}
