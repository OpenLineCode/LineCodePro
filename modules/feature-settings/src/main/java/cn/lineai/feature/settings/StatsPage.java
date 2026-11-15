package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.TokenText;
import cn.lineai.data.api.UsageStats;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Token usage statistics over every persisted assistant message.
 *
 * <p>The snapshot is loaded on the I/O scheduler and rendered on the main thread: the summary
 * section reports totals plus the weighted cache hit rate, and the model section lists the
 * consumption per model. A failing loader degrades to the empty state instead of crashing the
 * settings stack, and the page reloads whenever it returns to the top of the stack.</p>
 */
public final class StatsPage extends SettingsPage implements Refreshable {

    /** Data access for the usage snapshot; implementations run on the I/O scheduler. */
    public interface Loader {
        /**
         * Loads the aggregated usage snapshot.
         *
         * @return totals plus the per-model breakdown; may be null
         */
        UsageStats load();

        /**
         * Resolves the display name of a model identifier.
         *
         * @param modelId provider-side model identifier
         * @return localized display name; a null or empty result falls back to the identifier
         */
        String modelName(String modelId);
    }

    private final Scheduler scheduler;
    private final Loader loader;
    private final SettingsSection summary;
    private final LinearLayout modelsHost;
    private final TextView empty;
    private final TextView totalValue;
    private final TextView inputValue;
    private final TextView outputValue;
    private final TextView cacheValue;

    /**
     * Builds the statistics page and performs the initial asynchronous load.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param scheduler scheduler enforcing the I/O-to-main threading contract
     * @param loader data access for the usage snapshot
     */
    public StatsPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                     Loader loader) {
        super(context, R.string.screen_stats_title, tokens, back);
        this.scheduler = scheduler;
        this.loader = loader;
        summary = section(R.string.screen_stats_section_summary);
        totalValue = valueRow(summary, R.string.screen_stats_total_tokens, true);
        inputValue = valueRow(summary, R.string.screen_stats_input_tokens, true);
        outputValue = valueRow(summary, R.string.screen_stats_output_tokens, true);
        cacheValue = valueRow(summary, R.string.screen_stats_average_cache, false);
        modelsHost = new LinearLayout(context);
        modelsHost.setOrientation(VERTICAL);
        content.addView(modelsHost, new LayoutParams(-1, -2));
        empty = Ui.text(context, context.getString(R.string.screen_stats_empty),
                Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        Ui.padding(empty, 24, 48, 24, 48);
        theme.bind(t -> empty.setTextColor(t.textTertiary));
        empty.setVisibility(GONE);
        content.addView(empty, new LayoutParams(-1, -2));
        load();
    }

    /** Reloads the snapshot whenever the page returns to the top of the stack. */
    @Override
    public void onShown() {
        load();
    }

    /**
     * Loads the snapshot off the main thread and renders it there; a loader failure falls back to
     * the empty snapshot so the page stays usable.
     */
    private void load() {
        scheduler.io(() -> {
            UsageStats loaded;
            try {
                loaded = loader.load();
            } catch (RuntimeException error) {
                loaded = UsageStats.empty();
            }
            UsageStats snapshot = loaded == null ? UsageStats.empty() : loaded;
            scheduler.main(() -> render(snapshot));
        });
    }

    /** Renders the snapshot: summary values, the per-model breakdown, or the empty state. */
    private void render(UsageStats stats) {
        boolean isEmpty = stats.isEmpty();
        summary.setVisibility(isEmpty ? GONE : VISIBLE);
        modelsHost.setVisibility(isEmpty ? GONE : VISIBLE);
        empty.setVisibility(isEmpty ? VISIBLE : GONE);
        if (isEmpty) {
            modelsHost.removeAllViews();
            return;
        }
        totalValue.setText(TokenText.format(stats.totalTokens()));
        inputValue.setText(TokenText.format(stats.inputTokens));
        outputValue.setText(TokenText.format(stats.outputTokens));
        cacheValue.setText(percent(stats.cacheRate()));
        renderModels(stats);
    }

    /** Rebuilds the per-model section; unknown identifiers keep their raw identifier as label. */
    private void renderModels(UsageStats stats) {
        modelsHost.removeAllViews();
        if (stats.models.isEmpty()) return;
        SettingsSection section = new SettingsSection(getContext(),
                R.string.screen_stats_section_models, theme);
        modelsHost.addView(section, new LayoutParams(-1, -2));
        for (int index = 0; index < stats.models.size(); index++) {
            UsageStats.ModelUsage usage = stats.models.get(index);
            section.row(modelRow(usage), index + 1 < stats.models.size(), 0);
        }
    }

    /**
     * Builds a summary line with a fixed label and a value updated by {@link #render}.
     *
     * @param section section receiving the row
     * @param label string resource of the statistics name
     * @param divider true to separate the row from the next one
     * @return the value view owned by the row
     */
    private TextView valueRow(SettingsSection section, int label, boolean divider) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 14, 16, 14);
        TextView name = Ui.text(context, context.getString(label), Tokens.FONT_MD,
                tokens.textSecondary, Typeface.NORMAL);
        row.addView(name, new LayoutParams(0, -2, 1f));
        TextView value = Ui.medium(context, "", Tokens.FONT_MD, tokens.text);
        value.setGravity(Gravity.END);
        row.addView(value, new LayoutParams(-2, -2));
        section.row(row, divider, 0);
        theme.bind(t -> {
            name.setTextColor(t.textSecondary);
            value.setTextColor(t.text);
        });
        return value;
    }

    /**
     * Builds one per-model row: display name and provider on the left, total tokens and cache hit
     * rate on the right.
     */
    private LinearLayout modelRow(UsageStats.ModelUsage usage) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 12, 16, 12);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelParams = new LayoutParams(0, -2, 1f);
        labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        TextView name = Ui.medium(context, modelLabel(usage.modelId), Tokens.FONT_MD, tokens.text);
        labels.addView(name, new LayoutParams(-1, -2));
        LinearLayout meta = new LinearLayout(context);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaParams = new LayoutParams(-1, -2);
        metaParams.topMargin = Ui.dp(context, 2);
        labels.addView(meta, metaParams);
        TextView provider = Ui.text(context, usage.providerId, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        meta.addView(provider, new LayoutParams(0, -2, 1f));
        TextView requests = Ui.text(context,
                context.getString(R.string.screen_stats_model_requests, usage.requests),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        meta.addView(requests, new LayoutParams(-2, -2));

        LinearLayout values = new LinearLayout(context);
        values.setOrientation(VERTICAL);
        values.setGravity(Gravity.END);
        row.addView(values, new LayoutParams(-2, -2));
        TextView amount = Ui.medium(context,
                context.getString(R.string.screen_stats_model_tokens,
                        TokenText.format(usage.totalTokens())),
                Tokens.FONT_SM, tokens.text);
        amount.setGravity(Gravity.END);
        values.addView(amount, new LayoutParams(-1, -2));
        TextView cache = Ui.text(context,
                context.getString(R.string.screen_stats_model_cache, percent(usage.cacheRate())),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        cache.setGravity(Gravity.END);
        LinearLayout.LayoutParams cacheParams = new LayoutParams(-1, -2);
        cacheParams.topMargin = Ui.dp(context, 2);
        values.addView(cache, cacheParams);

        theme.bind(t -> {
            name.setTextColor(t.text);
            provider.setTextColor(t.textTertiary);
            requests.setTextColor(t.textTertiary);
            amount.setTextColor(t.text);
            cache.setTextColor(t.textTertiary);
        });
        return row;
    }

    /** Resolves a model label, falling back to the raw identifier when the name is unknown. */
    private String modelLabel(String modelId) {
        String resolved;
        try {
            resolved = loader.modelName(modelId);
        } catch (RuntimeException error) {
            resolved = null;
        }
        return resolved == null || resolved.isEmpty() ? modelId : resolved;
    }

    /** Formats a 0..1 cache hit ratio as a one-decimal percentage. */
    private String percent(double rate) {
        return getContext().getString(R.string.session_stats_percent_value,
                String.format(Locale.ROOT, "%.1f", Math.max(0d, rate) * 100d));
    }
}
