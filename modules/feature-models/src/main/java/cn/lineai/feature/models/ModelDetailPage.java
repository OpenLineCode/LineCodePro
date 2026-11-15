package cn.lineai.feature.models;

import android.content.Context;
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

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.ProviderConfig;
import cn.lineai.engine.ai.ModelCatalogClient;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Detail settings of one stored model.
 *
 * <p>The page edits the display name, context window, tool-call limit, the three capability
 * switches, the reasoning-level list, the custom reasoning field and the model-level headers, and
 * reuses {@link CompressionSection} for the dedicated compaction model. Fields the page does not
 * show are preserved by {@link ModelDetailForm}. "Run automatic detection again" applies
 * {@link ModelProfile} to the editable fields without touching user overrides, while set-default
 * and delete report through the listener. The io scheduler is only used by the compression
 * section; the page itself never blocks.</p>
 */
public final class ModelDetailPage extends SettingsPage {

    /** Persistence and navigation sink. */
    public interface Listener {
        /**
         * Reports a validated save.
         *
         * @param model configuration to update
         */
        void onSave(ModelConfig model);
    }

    private final ModelConfig model;
    private final ProviderConfig provider;
    private final ModelDetailForm form;
    private final Listener listener;
    private EditText contextInput;
    private EditText toolLimitInput;
    private TextView contextError;
    private Switch reasoningSwitch;
    private Switch visionSwitch;
    private Switch videoSwitch;
    private final ReasoningLevelsSection levelsSection;
    private AdvancedHeadersSection headersSection;
    private CompressionSection compression;
    private EditText reasoningFieldInput;
    private LinearLayout advancedHost;
    private Switch advancedSwitch;
    private IconButton saveAction;

    /**
     * Builds the model detail page.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param scheduler scheduler used by the compression section; never null
     * @param back header back action
     * @param model model to edit; never null
     * @param provider provider of the model, used for the label and header filtering; may be null
     * @param listener persistence sink; never null
     */
    public ModelDetailPage(Context context, Tokens tokens, Scheduler scheduler, Runnable back,
                           ModelConfig model, ProviderConfig provider, Listener listener) {
        super(context, R.string.screen_model_detail_title, tokens, back);
        this.model = model;
        this.provider = provider;
        this.listener = listener;
        this.form = ModelDetailForm.from(model);
        this.form.headers = modelHeaders();
        Ui.padding(content, 16, 0, 16, 100);

        buildIdentity();
        buildContext();
        buildToolLimit();
        buildCapabilities();
        levelsSection = new ReasoningLevelsSection(context, theme, model.modelId,
                form.reasoningLevels);
        content.addView(levelsSection, new LinearLayout.LayoutParams(-1, -2));
        buildAdvanced();
        buildCompression(scheduler);
        buildHeaderActions();
        updateSaveState();
    }

    /** Builds the name field, the read-only model id and the provider label. */
    private void buildIdentity() {
        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_field_name)),
                FormFields.labelParams(getContext(), 16, 8));
        EditText name = FormFields.input(getContext(), theme.current(), form.name, "", false, false);
        name.addTextChangedListener(watcher(() -> {
            form.name = FormFields.value(name);
            updateSaveState();
        }));
        content.addView(name, new LinearLayout.LayoutParams(-1, -2));

        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_field_model_id)),
                FormFields.labelParams(getContext(), 16, 8));
        TextView modelId = Ui.text(getContext(), model.modelId, Tokens.FONT_MD,
                theme.current().textSecondary, Typeface.NORMAL);
        modelId.setBackground(Ui.roundedStroke(getContext(), theme.current().inputBg, 12,
                theme.current().border));
        Ui.padding(modelId, 16, 12, 16, 12);
        content.addView(modelId, new LinearLayout.LayoutParams(-1, -2));
        TextView providerLabel = Ui.text(getContext(),
                getString(R.string.model_detail_provider_label, providerName()),
                Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(-1, -2);
        providerParams.topMargin = Ui.dp(getContext(), 8);
        content.addView(providerLabel, providerParams);
        theme.bind(t -> {
            modelId.setTextColor(t.textSecondary);
            modelId.setBackground(Ui.roundedStroke(getContext(), t.inputBg, 12, t.border));
            providerLabel.setTextColor(t.textTertiary);
        });
    }

    /** Builds the context-size field with its inline validation hint. */
    private void buildContext() {
        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_field_context)),
                FormFields.labelParams(getContext(), 16, 8));
        contextInput = FormFields.input(getContext(), theme.current(), form.contextSize,
                getString(R.string.model_field_context_size_hint), false, false);
        content.addView(contextInput, new LinearLayout.LayoutParams(-1, -2));
        contextError = hint(R.string.model_detail_context_invalid);
        contextError.setTextColor(theme.current().danger);
        contextError.setVisibility(GONE);
        content.addView(contextError, FormFields.labelParams(getContext(), 8, 0));
        content.addView(hint(R.string.model_detail_hint_context),
                FormFields.labelParams(getContext(), 8, 0));
        contextInput.addTextChangedListener(watcher(() -> {
            form.contextSize = FormFields.value(contextInput);
            updateSaveState();
        }));
    }

    /** Builds the tool-call limit field. */
    private void buildToolLimit() {
        content.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_field_tool_limit)),
                FormFields.labelParams(getContext(), 16, 8));
        toolLimitInput = FormFields.input(getContext(), theme.current(), form.toolCallLimit,
                getString(R.string.screen_model_add_hint_tool_call_limit), false, false);
        content.addView(toolLimitInput, new LinearLayout.LayoutParams(-1, -2));
        content.addView(hint(R.string.screen_model_add_max_tool_calls_hint),
                FormFields.labelParams(getContext(), 8, 0));
        toolLimitInput.addTextChangedListener(watcher(() -> {
            form.toolCallLimit = FormFields.value(toolLimitInput);
            updateSaveState();
        }));
    }

    /** Builds the capability switches inside one grouped panel. */
    private void buildCapabilities() {
        cn.lineai.ui.kit.SettingsSection section =
                section(R.string.model_detail_section_capabilities);
        boolean forced = !ModelProfile.reasoningToggleable(model.modelId);
        LinearLayout reasoningRow = switchRow(R.string.model_detail_reasoning,
                form.supportsReasoning, !forced,
                checked -> form.supportsReasoning = checked);
        reasoningSwitch = (Switch) reasoningRow.getChildAt(1);
        section.row(reasoningRow, true, 16);
        if (forced) {
            TextView hint = hint(R.string.model_detail_forced_reasoning);
            Ui.padding(hint, 16, 4, 16, 10);
            section.row(hint, true, 0);
        }
        LinearLayout visionRow = switchRow(R.string.model_detail_vision, form.supportsVision,
                true, checked -> form.supportsVision = checked);
        visionSwitch = (Switch) visionRow.getChildAt(1);
        section.row(visionRow, true, 16);
        LinearLayout videoRow = switchRow(R.string.model_detail_video, form.supportsVideo,
                true, checked -> form.supportsVideo = checked);
        videoSwitch = (Switch) videoRow.getChildAt(1);
        section.row(videoRow, false, 16);
    }

    /** Builds one labelled switch row; the switch is the second child of the returned row. */
    private LinearLayout switchRow(int label, boolean checked, boolean enabled,
                                   java.util.function.Consumer<Boolean> change) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 12, 16, 12);
        TextView title = Ui.medium(getContext(), getString(label), Tokens.FONT_MD,
                theme.current().text);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(getContext());
        FormFields.tintSwitch(toggle, theme.current());
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        toggle.setAlpha(enabled ? 1f : 0.45f);
        toggle.setOnCheckedChangeListener((view, value) -> change.accept(value));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            title.setTextColor(t.text);
            FormFields.tintSwitch(toggle, t);
        });
        return row;
    }

    /** Builds the advanced switch with the reasoning-field and header editors. */
    private void buildAdvanced() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 20, 16, 8);
        row.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_advanced)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        advancedSwitch = new Switch(getContext());
        FormFields.tintSwitch(advancedSwitch, theme.current());
        advancedSwitch.setChecked(advancedActive());
        row.addView(advancedSwitch, new LinearLayout.LayoutParams(-2, -2));
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> FormFields.tintSwitch(advancedSwitch, t));

        advancedHost = new LinearLayout(getContext());
        advancedHost.setOrientation(LinearLayout.VERTICAL);
        reasoningFieldInput = FormFields.input(getContext(), theme.current(),
                form.reasoningField, getString(R.string.model_detail_reasoning_field), false, false);
        advancedHost.addView(FormFields.label(getContext(), theme.current(),
                        getString(R.string.model_detail_reasoning_field)),
                FormFields.labelParams(getContext(), 0, 8));
        advancedHost.addView(reasoningFieldInput, new LinearLayout.LayoutParams(-1, -2));
        advancedHost.addView(hint(R.string.model_detail_hint_reasoning_field),
                FormFields.labelParams(getContext(), 8, 0));
        advancedHost.addView(hint(R.string.model_detail_reasoning_field_protocol),
                FormFields.labelParams(getContext(), 6, 10));
        headersSection = new AdvancedHeadersSection(getContext(), theme,
                R.string.model_detail_headers, form.headers);
        advancedHost.addView(headersSection, new LinearLayout.LayoutParams(-1, -2));
        advancedHost.setVisibility(advancedSwitch.isChecked() ? VISIBLE : GONE);
        advancedSwitch.setOnCheckedChangeListener((view, checked) ->
                advancedHost.setVisibility(checked ? VISIBLE : GONE));
        content.addView(advancedHost, new LinearLayout.LayoutParams(-1, -2));
        reasoningFieldInput.addTextChangedListener(watcher(() -> {
            form.reasoningField = FormFields.value(reasoningFieldInput);
            updateSaveState();
        }));
    }

    /** Builds the shared compression section for protocols with a dedicated compaction model. */
    private void buildCompression(Scheduler scheduler) {
        ModelConfig.Protocol[] protocol = {model.protocol};
        compression = new CompressionSection(getContext(), theme.current(),
                protocol, form.compressionEnabled, form.compressionAuto, form.compressionModelId,
                (type, url, key) -> new ModelCatalogClient().fetch(type, url, key,
                        catalogHeaders()),
                (ids, selected, picker) -> ModelPickerSheet.show(getContext(), theme.current(),
                        ids, selected, picker),
                scheduler, () -> model.baseUrl, () -> model.apiKey, this::updateSaveState);
        content.addView(compression, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Builds the top-right detect and save icons; the page has no bottom action block. */
    private void buildHeaderActions() {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        IconButton detect = new IconButton(getContext(), Icons.REFRESH_CW);
        detect.setIconSizeDp(38, 20);
        detect.setContentDescription(getString(R.string.model_detail_detect_short));
        detect.setOnClickListener(v -> autoDetect());
        actions.addView(detect, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38),
                Ui.dp(getContext(), 38)));
        saveAction = new IconButton(getContext(), Icons.SAVE);
        saveAction.setIconSizeDp(38, 20);
        saveAction.setContentDescription(getString(R.string.common_save));
        saveAction.setOnClickListener(v -> save());
        actions.addView(saveAction, new LinearLayout.LayoutParams(Ui.dp(getContext(), 38),
                Ui.dp(getContext(), 38)));
        bar().trailingAction(actions);
        theme.bind(t -> {
            detect.setIconColor(t.textSecondary);
            saveAction.setIconColor(form.canSave() ? t.accent : t.textTertiary);
        });
    }

    /** Re-applies the family profile to the editable fields and re-renders them. */
    private void autoDetect() {
        ModelInference.apply(form, model.modelId);
        contextInput.setText(form.contextSize);
        reasoningSwitch.setChecked(form.supportsReasoning);
        visionSwitch.setChecked(form.supportsVision);
        videoSwitch.setChecked(form.supportsVideo);
        levelsSection.setLevels(form.reasoningLevels);
        updateSaveState();
        Toast.makeText(getContext(), R.string.model_detail_auto_detect_done,
                Toast.LENGTH_SHORT).show();
    }

    /** Copies the editable widgets back into the pure form. */
    private void syncForm() {
        form.contextSize = FormFields.value(contextInput);
        form.toolCallLimit = FormFields.value(toolLimitInput);
        form.reasoningField = FormFields.value(reasoningFieldInput);
        form.reasoningLevels = levelsSection.levels();
        form.headers = headersSection.headers();
        form.compressionEnabled = compression.compressionEnabled();
        form.compressionAuto = compression.isAuto();
        form.compressionModelId = compression.modelId();
    }

    /** Repaints the save action and the inline context error for the current state. */
    private void updateSaveState() {
        boolean can = form.canSave();
        Tokens tokens = theme.current();
        if (contextError != null && contextInput != null) {
            contextError.setVisibility(form.contextValid() ? GONE : VISIBLE);
        }
        if (saveAction != null) {
            saveAction.setIconColor(can ? tokens.accent : tokens.textTertiary);
            saveAction.setAlpha(can ? 1f : 0.55f);
        }
    }

    /** Validates the form and hands the updated configuration to the host. */
    private void save() {
        syncForm();
        ModelDetailForm.Error error = form.validateForSave();
        if (error != ModelDetailForm.Error.NONE) {
            Toast.makeText(getContext(), message(error), Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onSave(form.build());
    }

    /** Maps a validation outcome to its localized message. */
    private int message(ModelDetailForm.Error error) {
        switch (error) {
            case TOOL_RANGE: return R.string.screen_model_add_tool_call_range_invalid;
            case CONTEXT_RANGE: return R.string.model_detail_context_invalid;
            case COMPRESSION_ID: return R.string.screen_model_add_require_compaction_id;
            case NONE:
            default: return R.string.common_unknown_error;
        }
    }

    /** Resolves the provider label shown under the read-only model id. */
    private String providerName() {
        if (provider != null && !provider.name.isEmpty()) return provider.name;
        if (model.providerLabel != null && !model.providerLabel.isEmpty()) {
            if (!getString(R.string.model_provider_custom).equals(model.providerLabel)) {
                return model.providerLabel;
            }
        }
        return getString(R.string.model_provider_custom);
    }

    /** Returns the model-level headers, hiding entries inherited from the provider. */
    private List<HttpHeader> modelHeaders() {
        List<HttpHeader> source = model.headers == null
                ? new ArrayList<HttpHeader>() : model.headers;
        if (provider == null || provider.headers == null || provider.headers.isEmpty()) {
            return new ArrayList<>(source);
        }
        List<HttpHeader> result = new ArrayList<>();
        for (HttpHeader header : source) {
            if (!provider.headers.contains(header)) result.add(header);
        }
        return result;
    }

    /** Returns provider headers followed by the edited model headers, ready for catalog calls. */
    private List<HttpHeader> catalogHeaders() {
        List<HttpHeader> result = new ArrayList<>();
        if (provider != null && provider.headers != null) result.addAll(provider.headers);
        result.addAll(headersSection.headers());
        return result;
    }

    /** True when the advanced section starts open because it already holds values. */
    private boolean advancedActive() {
        return !form.reasoningField.isEmpty() || !form.headers.isEmpty();
    }

    /** Builds a small muted hint line. */
    private TextView hint(int resource) {
        TextView hint = Ui.text(getContext(), getString(resource), Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        hint.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        return hint;
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

    /** Resolves a formatted string resource on the hosting context. */
    private String getString(int resource, Object... arguments) {
        return getContext().getString(resource, arguments);
    }
}
