package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.EndpointUrl;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.engine.ai.ModelCatalogClient;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Create-or-edit form of one model provider.
 *
 * <p>The page edits the provider endpoint and its shared credential, fetches the provider catalog
 * on the io scheduler, and keeps the model rows in a {@link ProviderModelRows} editor. Quick-setup
 * preset chips fill protocol and base URL, the advanced switch reveals the shared header editor,
 * and saving produces the provider plus the exact model writes and deletions through
 * {@link ProviderModelPlan}; the host persists them and navigates to the provider's model list.</p>
 */
public final class ProviderEditPage extends SettingsPage {

    /** Persistence sink; the host saves the provider, the models and the deletions atomically. */
    public interface Listener {
        /**
         * Reports a validated save.
         *
         * @param provider configuration to insert or update
         * @param models model configurations to insert or update
         * @param deletedModelIds identifiers of the models removed on this page
         */
        void onSave(ProviderConfig provider, List<ModelConfig> models,
                    List<String> deletedModelIds);
    }

    private static final ModelConfig.Protocol[] PROTOCOLS = {
            ModelConfig.Protocol.OPENAI_COMPATIBLE,
            ModelConfig.Protocol.ANTHROPIC_MESSAGES,
            ModelConfig.Protocol.CODEX_RESPONSES
    };

    private final Scheduler scheduler;
    private final Listener listener;
    private final ProviderConfig editing;
    private final ProviderForm form;
    private final List<ModelConfig> existingModels;
    private final BooleanSupplier allowAnyHttp;
    private ProviderModelRows modelRows;
    private AdvancedHeadersSection advancedHeaders;
    private EditText nameInput;
    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private TextView baseUrlHint;
    private IconButton saveAction;
    private TextView fetchAction;
    private LinearLayout protocolRow;
    private final List<TextView> protocolChips = new ArrayList<>();
    private TextView presetValue;
    private LinearLayout advancedHost;
    private Switch advancedSwitch;
    private boolean fetching;

    /**
     * Builds the add or edit provider form.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param scheduler scheduler for catalog fetches; null runs them inline
     * @param back header back action
     * @param provider provider to edit; null opens the add form
     * @param models models currently stored for the provider; null renders as empty
     * @param allowAnyHttp live source of the security setting that permits plain HTTP to a
     *     public host; null means local and LAN hosts only
     * @param listener persistence sink; never null
     */
    public ProviderEditPage(Context context, Tokens tokens, Scheduler scheduler, Runnable back,
                            ProviderConfig provider, List<ModelConfig> models,
                            BooleanSupplier allowAnyHttp, Listener listener) {
        super(context, provider == null
                ? R.string.screen_provider_edit_title_add
                : R.string.screen_provider_edit_title_edit, tokens, back);
        this.scheduler = scheduler;
        this.listener = listener;
        this.editing = provider;
        this.form = ProviderForm.from(provider);
        this.existingModels = ownedBy(provider, models);
        this.allowAnyHttp = allowAnyHttp == null ? () -> false : allowAnyHttp;
        Ui.padding(content, 16, 0, 16, 100);

        buildBasics();
        buildModelRows();
        buildAdvanced();
        buildHeaderActions();
        updateSaveState();
    }

    /** Selects the models owned by the edited provider; the add form owns none. */
    private static List<ModelConfig> ownedBy(ProviderConfig provider, List<ModelConfig> models) {
        List<ModelConfig> result = new ArrayList<>();
        if (provider == null || models == null) return result;
        for (ModelConfig model : models) {
            if (model != null && provider.id.equals(model.providerId)) result.add(model);
        }
        return result;
    }

    /** Shows the preset drawer; choosing an entry fills the protocol and endpoint fields. */
    private void showPresetSheet() {
        List<ModelSheet.Item> items = new ArrayList<>();
        for (ModelPreset preset : ModelPreset.all()) {
            String label = PresetText.label(getContext(), preset.id);
            String description = PresetText.description(getContext(), preset.id) + " · "
                    + PresetText.protocol(getContext(), preset.protocol);
            items.add(new ModelSheet.Item(label, description, false,
                    () -> applyPreset(preset)));
        }
        ModelSheet.show(getContext(), theme.current(),
                getString(R.string.screen_provider_preset_picker_title), items);
    }

    /** Applies a preset to the editable fields and repaints the dependent controls in place. */
    private void applyPreset(ModelPreset preset) {
        syncForm();
        form.applyPreset(preset);
        baseUrlInput.setText(form.baseUrl);
        if (form.displayName().isEmpty()) nameInput.setText(PresetText.label(getContext(), preset.id));
        renderPresetValue(preset);
        renderProtocol();
        updateSaveState();
    }

    /** Updates the preset selector caption to the applied preset. */
    private void renderPresetValue(ModelPreset preset) {
        if (presetValue == null) return;
        presetValue.setText(preset == null
                ? getString(R.string.screen_provider_preset_none)
                : PresetText.label(getContext(), preset.id));
        presetValue.setTextColor(theme.current().text);
    }

    /** Builds the name, protocol, preset, endpoint and credential fields. */
    private void buildBasics() {
        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.screen_provider_field_name)),
                FormFields.labelParams(getContext(), 16, 8));
        nameInput = FormFields.input(getContext(), theme.current(), form.name,
                getString(R.string.screen_provider_hint_name), false, false);
        content.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));

        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.screen_model_add_provider_title)),
                FormFields.labelParams(getContext(), 16, 8));
        protocolRow = new LinearLayout(getContext());
        protocolRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < PROTOCOLS.length; index++) {
            ModelConfig.Protocol protocol = PROTOCOLS[index];
            TextView chip = Ui.medium(getContext(), ModelUi.protocolLabel(getContext(), protocol),
                    Tokens.FONT_SM, theme.current().textSecondary);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setOnClickListener(v -> chooseProtocol(protocol));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    Ui.dp(getContext(), 44), 1f);
            if (index > 0) params.leftMargin = Ui.dp(getContext(), 8);
            protocolRow.addView(chip, params);
            protocolChips.add(chip);
        }
        content.addView(protocolRow, new LinearLayout.LayoutParams(-1, -2));

        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.screen_provider_preset_field)),
                FormFields.labelParams(getContext(), 16, 8));
        LinearLayout presetSelector = new LinearLayout(getContext());
        presetSelector.setGravity(Gravity.CENTER_VERTICAL);
        presetSelector.setClickable(true);
        presetSelector.setBackground(Ui.roundedStroke(getContext(), Color.TRANSPARENT, 12,
                theme.current().border));
        Ui.padding(presetSelector, 12, 0, 8, 0);
        presetSelector.setOnClickListener(v -> showPresetSheet());
        presetValue = Ui.medium(getContext(), getString(R.string.screen_provider_preset_none),
                Tokens.FONT_MD, theme.current().text);
        presetSelector.addView(presetValue, new LinearLayout.LayoutParams(0, -2, 1f));
        IconButton presetChevron = new IconButton(getContext(), Icons.CHEVRON_DOWN);
        presetChevron.setIconColor(theme.current().textTertiary);
        presetChevron.setIconSizeDp(18, 14);
        presetChevron.setClickable(false);
        presetSelector.addView(presetChevron, new LinearLayout.LayoutParams(
                Ui.dp(getContext(), 18), Ui.dp(getContext(), 18)));
        content.addView(presetSelector, new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 48)));
        theme.bind(t -> {
            presetSelector.setBackground(Ui.roundedStroke(getContext(), Color.TRANSPARENT, 12,
                    t.border));
            presetValue.setTextColor(t.text);
            presetChevron.setIconColor(t.textTertiary);
        });

        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.screen_provider_field_base_url)),
                FormFields.labelParams(getContext(), 16, 8));
        baseUrlInput = FormFields.input(getContext(), theme.current(), form.baseUrl,
                getString(R.string.screen_provider_hint_base_url), false, false);
        content.addView(baseUrlInput, new LinearLayout.LayoutParams(-1, -2));
        baseUrlHint = Ui.text(getContext(), urlHint(), Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        baseUrlHint.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = Ui.dp(getContext(), 8);
        content.addView(baseUrlHint, hintParams);

        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.screen_provider_field_api_key)),
                FormFields.labelParams(getContext(), 16, 8));
        LinearLayout credentialRow = new LinearLayout(getContext());
        credentialRow.setGravity(Gravity.CENTER_VERTICAL);
        apiKeyInput = FormFields.input(getContext(), theme.current(), form.apiKey,
                getString(R.string.screen_provider_hint_api_key), false, true);
        credentialRow.addView(apiKeyInput, new LinearLayout.LayoutParams(0,
                Ui.dp(getContext(), 48), 1f));
        fetchAction = Ui.medium(getContext(), getString(R.string.screen_provider_fetch_short),
                Tokens.FONT_SM, theme.current().textOnColor);
        fetchAction.setGravity(Gravity.CENTER);
        fetchAction.setClickable(true);
        fetchAction.setOnClickListener(v -> fetch());
        Ui.padding(fetchAction, 14, 0, 14, 0);
        LinearLayout.LayoutParams fetchParams = new LinearLayout.LayoutParams(-2,
                Ui.dp(getContext(), 44));
        fetchParams.leftMargin = Ui.dp(getContext(), 8);
        credentialRow.addView(fetchAction, fetchParams);
        content.addView(credentialRow, new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> renderFetchState(t));

        TextWatcher watcher = watcher(this::updateSaveState);
        nameInput.addTextChangedListener(watcher);
        baseUrlInput.addTextChangedListener(watcher);
        apiKeyInput.addTextChangedListener(watcher);
        theme.bind(t -> {
            baseUrlHint.setTextColor(t.textTertiary);
            renderProtocol();
        });
        renderProtocol();
        renderPresetValue(matchingPreset());
    }

    /** Returns the preset matching the current endpoint, or null for a custom endpoint. */
    private ModelPreset matchingPreset() {
        for (ModelPreset preset : ModelPreset.all()) {
            if (preset.protocol == form.protocol && preset.baseUrl.equals(form.endpoint())) {
                return preset;
            }
        }
        return null;
    }
    private void chooseProtocol(ModelConfig.Protocol protocol) {
        if (form.protocol == protocol) return;
        form.protocol = protocol;
        renderProtocol();
        updateSaveState();
    }

    /** Repaints the protocol chips and endpoint hint for the selected protocol. */
    private void renderProtocol() {
        boolean local = !form.protocolSelectable();
        protocolRow.setVisibility(local ? GONE : VISIBLE);
        Tokens tokens = theme.current();
        for (int index = 0; index < protocolChips.size(); index++) {
            ModelConfig.Protocol protocol = PROTOCOLS[index];
            boolean active = form.protocol == protocol;
            TextView chip = protocolChips.get(index);
            chip.setTextColor(active ? tokens.textOnColor : tokens.textSecondary);
            chip.setBackground(active
                    ? Ui.rounded(getContext(), tokens.accent, 12)
                    : Ui.rounded(getContext(), tokens.surfaceLight, 12));
        }
        baseUrlHint.setText(urlHint());
    }

    /** Returns the endpoint hint of the selected protocol. */
    private String urlHint() {
        if (form.protocol == ModelConfig.Protocol.CODEX_RESPONSES) {
            return getString(R.string.screen_model_add_url_codex);
        }
        if (form.protocol == ModelConfig.Protocol.ANTHROPIC_MESSAGES) {
            return getString(R.string.screen_model_add_url_anthropic);
        }
        return getString(R.string.screen_model_add_url_openai);
    }

    /** Builds the model-row editor; the grouped rows own their manual-add entry. */
    private void buildModelRows() {
        section(R.string.screen_provider_models_section);
        modelRows = new ProviderModelRows(getContext(), theme, existingModels, this::updateSaveState);
        content.addView(modelRows, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Builds the advanced switch hiding the shared header editor. */
    private void buildAdvanced() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 0, 18, 0, 8);
        row.addView(FormFields.label(getContext(), theme.current(),
                getString(R.string.screen_provider_advanced)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        advancedSwitch = new Switch(getContext());
        FormFields.tintSwitch(advancedSwitch, theme.current());
        advancedSwitch.setChecked(!form.headers.isEmpty());
        row.addView(advancedSwitch, new LinearLayout.LayoutParams(-2, -2));
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        advancedHost = new LinearLayout(getContext());
        advancedHost.setOrientation(LinearLayout.VERTICAL);
        advancedHeaders = new AdvancedHeadersSection(getContext(), theme,
                R.string.header_editor_section, form.headers);
        advancedHost.addView(advancedHeaders, new LinearLayout.LayoutParams(-1, -2));
        advancedHost.setVisibility(advancedSwitch.isChecked() ? VISIBLE : GONE);
        advancedSwitch.setOnCheckedChangeListener((view, checked) ->
                advancedHost.setVisibility(checked ? VISIBLE : GONE));
        content.addView(advancedHost, new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> FormFields.tintSwitch(advancedSwitch, t));
    }

    /** Builds the top-right save icon; the form has no bottom button. */
    private void buildHeaderActions() {
        saveAction = new IconButton(getContext(), Icons.SAVE);
        saveAction.setIconSizeDp(38, 20);
        saveAction.setContentDescription(getString(R.string.common_save));
        saveAction.setOnClickListener(v -> save());
        bar().trailingAction(saveAction);
        theme.bind(t -> saveAction.setIconColor(
                form.canSave(allowAnyHttp.getAsBoolean()) ? t.accent : t.textTertiary));
    }

    /** Copies the editable widgets back into the pure form. */
    private void syncForm() {
        form.name = FormFields.value(nameInput);
        form.baseUrl = FormFields.value(baseUrlInput);
        form.apiKey = FormFields.value(apiKeyInput);
        form.headers = advancedHeaders.headers();
    }

    /** Repaints the fetch and save actions for the current state and palette. */
    private void updateSaveState() {
        syncForm();
        renderFetchState(theme.current());
        boolean can = form.canSave(allowAnyHttp.getAsBoolean());
        Tokens tokens = theme.current();
        if (saveAction != null) {
            saveAction.setIconColor(can ? tokens.accent : tokens.textTertiary);
            saveAction.setAlpha(can ? 1f : 0.55f);
        }
    }

    /** Repaints the fetch action for the current state and palette. */
    private void renderFetchState(Tokens tokens) {
        boolean can = !fetching && !form.endpoint().isEmpty();
        fetchAction.setText(getString(fetching
                ? R.string.screen_provider_fetch_loading : R.string.screen_provider_fetch_button));
        fetchAction.setTextColor(can || fetching ? tokens.textOnColor : tokens.textTertiary);
        fetchAction.setBackground(Ui.rounded(getContext(),
                can || fetching ? tokens.accent : tokens.surfaceLight, 12));
        fetchAction.setAlpha(can || fetching ? 1f : 0.5f);
        fetchAction.setEnabled(can);
    }

    /** Fetches the provider catalog and merges the identifiers into the model rows. */
    private void fetch() {
        if (fetching) return;
        syncForm();
        String endpoint = form.endpoint();
        if (endpoint.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_provider_save_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (EndpointUrl.problem(endpoint, allowAnyHttp.getAsBoolean())
                != EndpointUrl.Problem.NONE) {
            Toast.makeText(getContext(), R.string.screen_provider_save_invalid_url,
                    Toast.LENGTH_LONG).show();
            return;
        }
        fetching = true;
        renderFetchState(theme.current());
        final ModelConfig.Protocol protocol = form.protocol;
        final String url = endpoint;
        final String key = form.credential();
        final List<HttpHeader> headers = advancedHeaders.headers();
        runOnIo(() -> {
            try {
                List<String> ids = new ModelCatalogClient().fetch(protocol, url, key, headers);
                runOnMain(() -> {
                    fetching = false;
                    renderFetchState(theme.current());
                    if (ids == null || ids.isEmpty()) {
                        Toast.makeText(getContext(), R.string.screen_provider_fetch_empty,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    modelRows.applyFetched(ids);
                    updateSaveState();
                });
            } catch (Exception error) {
                runOnMain(() -> {
                    fetching = false;
                    renderFetchState(theme.current());
                    Toast.makeText(getContext(), R.string.screen_provider_fetch_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Validates the form and hands the provider, model writes and deletions to the host. */
    private void save() {
        syncForm();
        ProviderForm.Error problem = form.validateForSave(allowAnyHttp.getAsBoolean());
        if (problem != ProviderForm.Error.NONE) {
            // A blank field and an unusable URL need different advice: telling the user to "fill
            // in the required fields" when the URL is merely malformed sends them in circles.
            Toast.makeText(getContext(), problem == ProviderForm.Error.INVALID_BASE_URL
                            ? R.string.screen_provider_save_invalid_url
                            : R.string.screen_provider_save_require,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (editing != null && editing.protocol != form.protocol && !existingModels.isEmpty()) {
            Dialogs.confirm(getContext(), theme.current(),
                    getString(R.string.screen_provider_protocol_change_title),
                    getString(R.string.screen_provider_protocol_change_message),
                    getString(R.string.common_cancel), getString(R.string.common_confirm),
                    confirmed -> {
                        if (confirmed) finishSave();
                    });
            return;
        }
        finishSave();
    }

    /** Builds the immutable writes and reports them; identity is kept when editing. */
    private void finishSave() {
        ProviderConfig provider = form.build(() -> Ids.prefixed("provider"));
        ProviderModelPlan plan = ProviderModelPlan.of(provider, existingModels,
                modelRows.drafts());
        listener.onSave(provider, plan.models, plan.deletedIds);
    }

    /** Runs work on the io scheduler, or inline when the host supplied none. */
    private void runOnIo(Runnable work) {
        if (scheduler == null) {
            work.run();
        } else {
            scheduler.io(work);
        }
    }

    /** Runs UI work on main, or inline when the host supplied no scheduler. */
    private void runOnMain(Runnable work) {
        if (scheduler == null) {
            work.run();
        } else {
            scheduler.main(work);
        }
    }

    /** Creates a text watcher forwarding every change to the supplied action. */
    private TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable s) {
                action.run();
            }
        };
    }

    /** Resolves a string resource on the hosting context. */
    private String getString(int resource) {
        return getContext().getString(resource);
    }
}
