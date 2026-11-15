package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.engine.ai.ModelProbe;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Models of one provider: default selection, editing, testing and deletion.
 *
 * <p>Tapping a row makes it the chat default; long-pressing offers edit and delete. The header
 * carries a lightning action that probes every model with the canonical 1+1 request, a plus action
 * that reopens the provider editor, and the back button. Failed probes are listed in a toast and
 * their row names stay tinted with the danger color until the next test. Like the provider list,
 * the page reads the store on io, renders on main and rebuilds only when the loaded data differs.</p>
 */
public final class ProviderModelsPage extends SettingsPage implements Refreshable {

    /** Navigation and persistence sink. */
    public interface Listener {
        /** Reports that the header back button was pressed. */
        void onBack();

        /**
         * Reports a tapped model row.
         *
         * @param modelId identifier of the tapped model
         */
        void onOpenModel(String modelId);

        /**
         * Reports the add action for the provider.
         *
         * @param providerId identifier of the provider whose models are being edited
         */
        void onAddModels(String providerId);

        /**
         * Reports that a model should become the chat default.
         *
         * @param modelId identifier of the selected model
         */
        void onSetDefault(String modelId);

        /**
         * Reports the confirmed deletion of a model.
         *
         * @param modelId identifier of the model to delete
         */
        void onDeleteModel(String modelId);
    }

    private final ProviderConfig provider;
    private final Listener listener;
    private final Scheduler scheduler;
    private final List<ModelConfig> models = new ArrayList<>();
    private final Set<String> failedIds = new HashSet<>();
    private final LinearLayout host;
    private String signature = "";
    private Supplier<List<ModelConfig>> loader;
    private IconButton testAction;
    private boolean testing;

    /**
     * Builds the model list of one provider and renders the empty state until the loader runs.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param provider provider whose models are listed; never null
     * @param listener navigation and persistence sink; never null
     * @param scheduler scheduler for the io/main refresh hop; null loads synchronously
     */
    public ProviderModelsPage(Context context, Tokens tokens, ProviderConfig provider,
                              Listener listener, Scheduler scheduler) {
        super(context, R.string.screen_provider_models_title, tokens, listener::onBack,
                0, 0, null);
        this.provider = provider;
        this.listener = listener;
        this.scheduler = scheduler;
        host = new LinearLayout(context);
        host.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(content, 0, 0, 0, 100);
        content.addView(host, new LinearLayout.LayoutParams(-1, -2));
        buildHeaderActions();
        render();
    }

    /** Installs the lightning test action left of the existing plus action in the header. */
    private void buildHeaderActions() {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        testAction = new IconButton(getContext(), Icons.ZAP);
        testAction.setIconSizeDp(38, 20);
        testAction.setContentDescription(getContext().getString(R.string.screen_provider_models_test));
        testAction.setOnClickListener(v -> testAll());
        actions.addView(testAction, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38),
                Ui.dp(getContext(), 38)));
        IconButton add = new IconButton(getContext(), Icons.PLUS);
        add.setIconSizeDp(38, 20);
        add.setContentDescription(getContext().getString(R.string.screen_provider_models_add));
        add.setOnClickListener(v -> listener.onAddModels(provider.id));
        actions.addView(add, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38),
                Ui.dp(getContext(), 38)));
        bar().trailingAction(actions);
        theme.bind(t -> {
            testAction.setIconColor(testing ? t.textTertiary : t.text);
            add.setIconColor(t.text);
        });
    }

    /**
     * Probes every listed model with the canonical 1+1 request on the io scheduler, then highlights
     * failures and reports them in one toast.
     */
    private void testAll() {
        if (testing || models.isEmpty()) return;
        testing = true;
        testAction.setAlpha(0.5f);
        Toast.makeText(getContext(), R.string.screen_provider_models_testing,
                Toast.LENGTH_SHORT).show();
        List<ModelConfig> snapshot = new ArrayList<>(models);
        Runnable work = () -> {
            Set<String> failed = new HashSet<>();
            List<String> failedNames = new ArrayList<>();
            for (ModelConfig model : snapshot) {
                try {
                    new ModelProbe().complete(model);
                } catch (RuntimeException error) {
                    failed.add(model.id);
                    failedNames.add(model.displayName());
                }
            }
            Runnable report = () -> applyTestResult(failed, failedNames, snapshot.size());
            if (scheduler == null) {
                report.run();
            } else {
                scheduler.main(report);
            }
        };
        if (scheduler == null) {
            work.run();
        } else {
            scheduler.io(work);
        }
    }

    /** Applies the probe outcome: failure tint, toast and restored action state. */
    private void applyTestResult(Set<String> failed, List<String> failedNames, int total) {
        failedIds.clear();
        failedIds.addAll(failed);
        testing = false;
        testAction.setAlpha(1f);
        render();
        String message = failedNames.isEmpty()
                ? getContext().getString(R.string.screen_provider_models_test_ok, total)
                : getContext().getString(R.string.screen_provider_models_test_failed,
                        String.join(", ", failedNames));
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    /**
     * Installs the refresh supplier used by {@link #onShown}; it runs on the io scheduler and may
     * not touch views.
     *
     * @param loader store reader producing every stored model
     */
    public void loader(Supplier<List<ModelConfig>> loader) {
        this.loader = loader;
    }

    /** Refreshes the rows on every visibility change. */
    @Override public void onShown() {
        if (loader == null) return;
        if (scheduler == null) {
            apply(loader.get());
            return;
        }
        scheduler.io(() -> {
            List<ModelConfig> latest = loader.get();
            scheduler.main(() -> apply(latest));
        });
    }

    /** Replaces page state when the loaded models differ; must run on the main thread. */
    private void apply(List<ModelConfig> latest) {
        List<ModelConfig> next = new ArrayList<>();
        if (latest != null) {
            for (ModelConfig model : latest) {
                if (model != null && provider.id.equals(model.providerId)) next.add(model);
            }
        }
        String nextSignature = signature(next);
        if (nextSignature.equals(signature)) return;
        signature = nextSignature;
        models.clear();
        models.addAll(next);
        render();
    }

    /** Builds a cheap value signature of the rendered rows; ModelConfig has no equality. */
    private String signature(List<ModelConfig> values) {
        StringBuilder builder = new StringBuilder();
        for (ModelConfig model : values) {
            builder.append(model.id).append('|').append(model.modelId).append('|')
                    .append(model.name).append('|').append(model.isDefault).append('|')
                    .append(failedIds.contains(model.id)).append(';');
        }
        return builder.toString();
    }

    /** Rebuilds the rows from the current state, or shows the empty-state caption. */
    private void render() {
        host.removeAllViews();
        SettingsSection section = new SettingsSection(getContext(), provider.name, theme);
        host.addView(section, new LinearLayout.LayoutParams(-1, -2));
        if (models.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.screen_provider_models_empty),
                    Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
            empty.setLineSpacing(Ui.dp(getContext(), 3), 1f);
            Ui.padding(empty, 16, 14, 16, 14);
            section.row(empty, false, 0);
            return;
        }
        for (int index = 0; index < models.size(); index++) {
            section.row(row(models.get(index)), index < models.size() - 1, 16);
        }
    }

    /** Builds one model row; tapping sets the chat default and long-press offers edit/delete. */
    private LinearLayout row(ModelConfig model) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> setDefault(model));
        row.setOnLongClickListener(v -> {
            showActions(model);
            return true;
        });
        Ui.padding(row, 16, 12, 16, 12);
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean failed = failedIds.contains(model.id);
        TextView name = Ui.medium(context, model.displayName(), Tokens.FONT_MD,
                failed ? tokens.danger : tokens.text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView modelId = Ui.text(context, model.modelId, Tokens.FONT_XS, tokens.textTertiary,
                Typeface.NORMAL);
        modelId.setSingleLine(true);
        modelId.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams modelIdParams = new LinearLayout.LayoutParams(-1, -2);
        modelIdParams.topMargin = Ui.dp(context, 2);
        info.addView(modelId, modelIdParams);
        if (model.isDefault) {
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.leftMargin = Ui.dp(context, 8);
            row.addView(ModelUi.accentBadge(context, tokens,
                    R.string.screen_provider_models_default_badge), badgeParams);
        }
        theme.bind(t -> {
            name.setTextColor(failed ? t.danger : t.text);
            modelId.setTextColor(t.textTertiary);
        });
        return row;
    }

    /** Shows the long-press sheet: edit the model or delete it after confirmation. */
    private void showActions(ModelConfig model) {
        Context context = getContext();
        List<ModelSheet.Item> items = new ArrayList<>();
        items.add(new ModelSheet.Item(context.getString(R.string.common_modify), "", false,
                () -> listener.onOpenModel(model.id)));
        items.add(new ModelSheet.Item(context.getString(R.string.common_delete), "", true,
                () -> confirmDelete(model)));
        ModelSheet.show(context, theme.current(), model.displayName(), items);
    }

    /** Marks one model as the default locally and reports the change to the host. */
    private void setDefault(ModelConfig target) {
        listener.onSetDefault(target.id);
        for (int index = 0; index < models.size(); index++) {
            ModelConfig model = models.get(index);
            if (model.isDefault == model.id.equals(target.id)) continue;
            models.set(index, model.toBuilder()
                    .flags(model.id.equals(target.id), model.enabled).build());
        }
        signature = signature(models);
        render();
    }

    /** Shows the delete confirmation and removes the row optimistically after confirmation. */
    private void confirmDelete(ModelConfig model) {
        Context context = getContext();
        Dialogs.confirm(context, theme.current(),
                context.getString(R.string.common_delete),
                context.getString(R.string.screen_provider_models_delete_message,
                        model.displayName()),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_delete),
                confirmed -> {
                    if (!confirmed) return;
                    listener.onDeleteModel(model.id);
                    for (int index = models.size() - 1; index >= 0; index--) {
                        if (models.get(index).id.equals(model.id)) models.remove(index);
                    }
                    signature = signature(models);
                    render();
                    onShown();
                });
    }
}
