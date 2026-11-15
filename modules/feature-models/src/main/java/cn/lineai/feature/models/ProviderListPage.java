package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Root of the model-management flow: one card per stored provider.
 *
 * <p>Cards show the provider name, protocol, endpoint and model count, never the model names
 * themselves; the current provider of the chat is marked with an accent badge. Tapping a card
 * opens its model list, while a long press offers edit and delete; deleting a provider cascades
 * the model identifiers through the listener so the host removes both in one transaction. The page
 * owns the module's loading idiom: the store readers run on the io scheduler, the list is rendered
 * on main, and rendering happens only when the loaded data actually differs so a no-op refresh
 * keeps the scroll position.</p>
 */
public final class ProviderListPage extends SettingsPage implements Refreshable {

    /** Navigation and deletion sink; the page never persists or navigates on its own. */
    public interface Listener {
        /** Reports that the header back button was pressed. */
        void onBack();

        /** Reports that the add-provider action was requested. */
        void onAddProvider();

        /**
         * Reports that a provider card was tapped.
         *
         * @param providerId identifier of the tapped provider
         */
        void onOpenProvider(String providerId);

        /**
         * Reports the edit action for a provider.
         *
         * @param providerId identifier of the provider to edit
         */
        void onEditProvider(String providerId);

        /**
         * Reports the confirmed deletion of a provider together with its models.
         *
         * @param providerId identifier of the provider to delete
         * @param modelIds identifiers of the models owned by that provider, never null
         */
        void onDeleteProvider(String providerId, List<String> modelIds);

        /** Reports whether the on-device model card is offered. */
        default boolean localModelsAvailable() {
            return false;
        }

        /** Reports that the on-device model card was tapped. */
        default void onLocalModels() {
        }
    }

    private final Listener listener;
    private final Scheduler scheduler;
    private final List<ProviderConfig> providers = new ArrayList<>();
    private final List<ModelConfig> models = new ArrayList<>();
    private final LinearLayout host;
    private String signature = "";
    private Supplier<List<ProviderConfig>> providerLoader;
    private Supplier<List<ModelConfig>> modelLoader;

    /**
     * Builds the provider list page and renders the empty state until the loaders run.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param listener navigation and deletion sink; never null
     * @param scheduler scheduler for the io/main refresh hop; null loads synchronously
     */
    public ProviderListPage(Context context, Tokens tokens, Listener listener,
                            Scheduler scheduler) {
        super(context, R.string.screen_provider_list_title, tokens, listener::onBack,
                Icons.PLUS, R.string.screen_provider_list_add, listener::onAddProvider);
        this.listener = listener;
        this.scheduler = scheduler;
        host = new LinearLayout(context);
        host.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(content, 16, 8, 16, 100);
        content.addView(host, new LinearLayout.LayoutParams(-1, -2));
        render();
    }

    /**
     * Installs the refresh suppliers used by {@link #onShown}. Both run on the io scheduler;
     * neither may touch views.
     *
     * @param providerLoader store reader producing the current providers
     * @param modelLoader store reader producing the current models
     */
    public void loader(Supplier<List<ProviderConfig>> providerLoader,
                       Supplier<List<ModelConfig>> modelLoader) {
        this.providerLoader = providerLoader;
        this.modelLoader = modelLoader;
    }

    /** Refreshes the cards on every visibility change. */
    @Override public void onShown() {
        if (providerLoader == null) return;
        if (scheduler == null) {
            apply(providerLoader.get(), modelLoader == null
                    ? new ArrayList<ModelConfig>() : modelLoader.get());
            return;
        }
        scheduler.io(() -> {
            List<ProviderConfig> latestProviders = providerLoader.get();
            List<ModelConfig> latestModels = modelLoader == null
                    ? new ArrayList<ModelConfig>() : modelLoader.get();
            scheduler.main(() -> apply(latestProviders, latestModels));
        });
    }

    /** Replaces page state when the loaded data differs; must run on the main thread. */
    private void apply(List<ProviderConfig> latestProviders, List<ModelConfig> latestModels) {
        List<ProviderConfig> nextProviders = latestProviders == null
                ? new ArrayList<ProviderConfig>() : latestProviders;
        List<ModelConfig> nextModels = latestModels == null
                ? new ArrayList<ModelConfig>() : latestModels;
        String nextSignature = signature(nextProviders, nextModels);
        if (nextSignature.equals(signature)) return;
        signature = nextSignature;
        providers.clear();
        providers.addAll(nextProviders);
        models.clear();
        models.addAll(nextModels);
        render();
    }

    /** Builds a cheap value signature of the rendered state; ModelConfig has no equality. */
    private String signature(List<ProviderConfig> nextProviders, List<ModelConfig> nextModels) {
        StringBuilder builder = new StringBuilder();
        for (ProviderConfig provider : nextProviders) {
            builder.append(provider.id).append('|').append(provider.name).append('|')
                    .append(provider.protocol).append('|').append(provider.baseUrl).append('|')
                    .append(provider.updatedAt).append(';');
        }
        builder.append('#');
        for (ModelConfig model : nextModels) {
            builder.append(model.id).append('|').append(model.providerId).append('|')
                    .append(model.name).append('|').append(model.isDefault).append(';');
        }
        return builder.toString();
    }
    /** Rebuilds every card from the current state, or shows the empty-state caption. */
    private void render() {
        host.removeAllViews();
        String currentProvider = currentProviderId();
        if (providers.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.screen_provider_list_empty),
                    Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
            empty.setLineSpacing(Ui.dp(getContext(), 3), 1f);
            Ui.padding(empty, 4, 12, 4, 12);
            host.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
        for (ProviderConfig provider : providers) {
            addProviderCard(provider, provider.id.equals(currentProvider));
        }
        if (listener.localModelsAvailable()) addLocalCard();
    }

    /** Resolves the provider of the current chat model, or an empty string when none exists. */
    private String currentProviderId() {
        for (ModelConfig model : models) {
            if (model.isDefault && !model.providerId.isEmpty()) return model.providerId;
        }
        for (ModelConfig model : models) {
            if (!model.providerId.isEmpty()) return model.providerId;
        }
        return "";
    }

    /** Builds one provider card with endpoint, model count and the current-provider badge. */
    private void addProviderCard(ProviderConfig provider, boolean current) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> listener.onOpenProvider(provider.id));
        List<String> modelIds = modelIdsOf(provider.id);
        card.setOnLongClickListener(v -> {
            showActions(provider, modelIds);
            return true;
        });
        card.setBackground(Ui.pressable(context, tokens));
        Ui.padding(card, 14, 14, 14, 14);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = Ui.dp(context, 8);
        host.addView(card, cardParams);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView name = Ui.medium(context, provider.name, Tokens.FONT_MD, tokens.text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleRow.addView(name, nameParams);
        titleRow.addView(ModelUi.protocolBadge(context, tokens, provider.protocol),
                new LinearLayout.LayoutParams(-2, -2));
        TextView endpoint = Ui.text(context, provider.baseUrl, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        endpoint.setSingleLine(true);
        endpoint.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams endpointParams = new LinearLayout.LayoutParams(-1, -2);
        endpointParams.topMargin = Ui.dp(context, 8);
        card.addView(endpoint, endpointParams);
        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = Ui.dp(context, 6);
        card.addView(footer, footerParams);
        TextView count = Ui.text(context,
                context.getString(R.string.screen_provider_list_models_count, modelIds.size()),
                Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        footer.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));
        if (current) {
            footer.addView(ModelUi.accentBadge(context, tokens,
                    R.string.screen_provider_list_default_badge),
                    new LinearLayout.LayoutParams(-2, -2));
        }
        theme.bind(t -> {
            card.setBackground(Ui.pressable(context, t));
            name.setTextColor(t.text);
            endpoint.setTextColor(t.textTertiary);
            count.setTextColor(t.textSecondary);
        });
    }

    /** Builds the on-device model entry card, shown only when the runtime supports it. */
    private void addLocalCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> listener.onLocalModels());
        card.setBackground(Ui.pressable(context, tokens));
        Ui.padding(card, 14, 14, 14, 14);
        host.addView(card, new LinearLayout.LayoutParams(-1, -2));
        IconButton icon = new IconButton(context, Icons.CPU);
        icon.setIconSizeDp(40, 22);
        icon.setClickable(false);
        icon.setIconColor(tokens.accent);
        card.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsParams.leftMargin = Ui.dp(context, 12);
        card.addView(labels, labelsParams);
        labels.addView(Ui.medium(context,
                context.getString(R.string.screen_provider_list_local_title), Tokens.FONT_MD,
                tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView description = Ui.text(context,
                context.getString(R.string.screen_provider_list_local_desc), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        description.setLineSpacing(Ui.dp(context, 3), 1f);
        labels.addView(description, new LinearLayout.LayoutParams(-1, -2));
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconSizeDp(20, 16);
        chevron.setClickable(false);
        chevron.setIconColor(tokens.textTertiary);
        card.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        theme.bind(t -> {
            card.setBackground(Ui.pressable(context, t));
            icon.setIconColor(t.accent);
            description.setTextColor(t.textTertiary);
            chevron.setIconColor(t.textTertiary);
        });
    }

    /** Returns the identifiers of the models owned by one provider. */
    private List<String> modelIdsOf(String providerId) {
        List<String> result = new ArrayList<>();
        for (ModelConfig model : models) {
            if (providerId.equals(model.providerId)) result.add(model.id);
        }
        return result;
    }

    /** Shows the long-press sheet with edit and confirmed delete actions. */
    private void showActions(ProviderConfig provider, List<String> modelIds) {
        Context context = getContext();
        List<ModelSheet.Item> items = new ArrayList<>();
        items.add(new ModelSheet.Item(context.getString(R.string.common_modify), "",
                false, () -> listener.onEditProvider(provider.id)));
        items.add(new ModelSheet.Item(context.getString(R.string.common_delete), "",
                true, () -> confirmDelete(provider, modelIds)));
        ModelSheet.show(context, theme.current(), provider.name, items);
    }

    /** Shows the delete confirmation; the host removes the provider and its models together. */
    private void confirmDelete(ProviderConfig provider, List<String> modelIds) {
        Context context = getContext();
        Dialogs.confirm(context, theme.current(),
                context.getString(R.string.screen_provider_delete_title),
                context.getString(R.string.screen_provider_delete_message, provider.name,
                        modelIds.size()),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_delete),
                confirmed -> {
                    if (!confirmed) return;
                    listener.onDeleteProvider(provider.id, modelIds);
                    for (int index = providers.size() - 1; index >= 0; index--) {
                        if (providers.get(index).id.equals(provider.id)) providers.remove(index);
                    }
                    for (int index = models.size() - 1; index >= 0; index--) {
                        if (modelIds.contains(models.get(index).id)) models.remove(index);
                    }
                    signature = signature(providers, models);
                    render();
                    onShown();
                });
    }
}
