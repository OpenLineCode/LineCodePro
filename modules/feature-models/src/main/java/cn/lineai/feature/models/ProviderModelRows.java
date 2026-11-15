package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Editable model rows of the provider editor.
 *
 * <p>The rows live in one grouped panel with hairline dividers, mirroring the MCP custom-header
 * editor. The panel is backed by a recycling {@link ListView} with a fixed viewport, so a catalog
 * with hundreds of identifiers reuses a handful of row views instead of building every editor at
 * once; the list itself scrolls inside the panel and no row is hidden. Each row edits a display
 * name and a model id, shows the inferred capability caption and offers a delete control; editing
 * the id of a row whose name was never customized reformats the name automatically. The control
 * only mutates its own entry list; persistence and identity matching happen in
 * {@link ProviderModelPlan} when the page saves.</p>
 */
final class ProviderModelRows extends LinearLayout {

    /** Approximate height of one row, used to size the fixed list viewport. */
    private static final int ROW_HEIGHT_DP = 88;
    /** Maximum viewport height; the list scrolls internally beyond this. */
    private static final int VIEWPORT_MAX_DP = 520;

    /** Change sink used to refresh dependent page state after a row is added or removed. */
    interface Listener {
        /** Invoked on the main thread whenever the row set changes. */
        void onChanged();
    }

    /** One mutable row: the text fields change as the user types. */
    private static final class Entry {
        String modelId;
        String displayName;
        boolean nameEdited;
        final ModelConfig original;
        final boolean fetched;

        Entry(String modelId, String displayName, ModelConfig original, boolean fetched) {
            this.modelId = modelId == null ? "" : modelId;
            this.displayName = displayName == null ? "" : displayName;
            this.original = original;
            this.fetched = fetched;
            String formatted = ModelProfile.displayName(this.modelId);
            boolean rawName = this.displayName.isEmpty() || this.displayName.equals(this.modelId);
            this.nameEdited = !rawName && !this.displayName.equals(formatted);
            if (!this.nameEdited && !formatted.isEmpty()) {
                this.displayName = formatted;
            }
        }
    }

    /** Recycled row views bound to the current entry. */
    private static final class Holder {
        EditText name;
        EditText modelId;
        TextView meta;
        Entry entry;
        boolean syncing;
    }

    private final ThemeBinder theme;
    private final Listener listener;
    private final List<Entry> entries = new ArrayList<>();
    private final ListView list;
    private Adapter adapter;
    private final LinearLayout manualRow;

    /**
     * Builds the row editor from the models currently stored for the edited provider.
     *
     * @param context hosting context
     * @param theme binder shared with the page; restyle callbacks are registered on it
     * @param existing models stored for the provider; null renders as empty
     * @param listener change sink; never null
     */
    ProviderModelRows(Context context, ThemeBinder theme, List<ModelConfig> existing,
                      Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        if (existing != null) {
            for (ModelConfig model : existing) {
                if (model != null) {
                    entries.add(new Entry(model.modelId, model.name, model, false));
                }
            }
        }
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        group.setBackground(Ui.rounded(context, theme.current().surfaceElevated, 12));
        addView(group, new LayoutParams(-1, -2));
        theme.bind(t -> group.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        list = new ListView(context);
        list.setItemsCanFocus(true);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setDivider(new ColorDrawable(theme.current().borderLight));
        list.setDividerHeight(1);
        theme.bind(t -> list.setDivider(new ColorDrawable(t.borderLight)));
        adapter = new Adapter();
        list.setAdapter(adapter);
        group.addView(list, new LayoutParams(-1, 0));
        View divider = new View(context);
        theme.bind(t -> divider.setBackgroundColor(t.borderLight));
        group.addView(divider, new LayoutParams(-1, 1));
        manualRow = manualAdd();
        group.addView(manualRow, new LayoutParams(-1, -2));
        updateViewport();
    }

    /** Appends one blank manual row and re-renders the editor. */
    void addManual() {
        entries.add(new Entry("", "", null, false));
        recordsChanged();
        list.smoothScrollToPosition(entries.size() - 1);
    }

    /**
     * Replaces the rows produced by catalog queries and appends the new identifiers.
     *
     * <p>Stored rows and rows the user added by hand are kept, and an identifier already present
     * in either group is not added a second time.</p>
     *
     * @param modelIds identifiers returned by the provider catalog; null is treated as empty
     */
    void applyFetched(List<String> modelIds) {
        entries.removeIf(entry -> entry.fetched && entry.original == null);
        if (modelIds != null) {
            for (String raw : modelIds) {
                String modelId = raw == null ? "" : raw.trim();
                if (modelId.isEmpty() || containsModelId(modelId)) continue;
                entries.add(new Entry(modelId, ModelProfile.displayName(modelId), null, true));
            }
        }
        recordsChanged();
    }

    /**
     * Returns the current rows for {@link ProviderModelPlan}.
     *
     * @return a new list of drafts in display order
     */
    List<ProviderModelPlan.Draft> drafts() {
        List<ProviderModelPlan.Draft> result = new ArrayList<>();
        for (Entry entry : entries) {
            result.add(new ProviderModelPlan.Draft(entry.modelId, entry.displayName,
                    entry.original, entry.fetched));
        }
        return result;
    }

    /** True when any row already carries the identifier. */
    private boolean containsModelId(String modelId) {
        for (Entry entry : entries) {
            if (entry.modelId.trim().equals(modelId)) return true;
        }
        return false;
    }

    /** Reloads the adapter for a structural change, resizes the viewport and reports the change. */
    private void recordsChanged() {
        adapter = new Adapter();
        list.setAdapter(adapter);
        updateViewport();
        listener.onChanged();
    }

    /** Sizes the fixed list viewport to the row count, capped so long catalogs scroll inside. */
    private void updateViewport() {
        int rows = entries.size();
        int height = rows == 0 ? 0 : Math.min(rows, VIEWPORT_MAX_DP / ROW_HEIGHT_DP)
                * Ui.dp(getContext(), ROW_HEIGHT_DP);
        LayoutParams params = (LayoutParams) list.getLayoutParams();
        if (params.height == height) return;
        params.height = height;
        list.setLayoutParams(params);
    }

    /** Builds the manual-add row appended inside the grouped panel. */
    private LinearLayout manualAdd() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> addManual());
        Ui.padding(row, 14, 12, 14, 12);
        IconButton plus = new IconButton(context, Icons.PLUS);
        plus.setIconSizeDp(34, 18);
        plus.setClickable(false);
        row.addView(plus, new LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        row.addView(labels, labelParams);
        labels.addView(Ui.medium(context, context.getString(R.string.screen_provider_manual_add),
                Tokens.FONT_SM, tokens.accent), new LayoutParams(-1, -2));
        labels.addView(Ui.text(context,
                context.getString(R.string.screen_provider_manual_add_desc), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL), new LayoutParams(-1, -2));
        theme.bind(t -> {
            plus.setIconColor(t.accent);
            ((TextView) labels.getChildAt(0)).setTextColor(t.accent);
            ((TextView) labels.getChildAt(1)).setTextColor(t.textTertiary);
        });
        return row;
    }

    /** Recycling adapter over the mutable entry list. */
    private final class Adapter extends BaseAdapter {
        @Override public int getCount() {
            return entries.size();
        }

        @Override public Object getItem(int position) {
            return entries.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                holder = createHolder();
                convertView = holderRow(holder);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }
            holder.entry = entries.get(position);
            holder.syncing = true;
            holder.name.setText(holder.entry.displayName);
            holder.name.setSelection(holder.name.getText().length());
            holder.modelId.setText(holder.entry.modelId);
            holder.modelId.setSelection(holder.modelId.getText().length());
            holder.syncing = false;
            updateMeta(holder.meta, holder.entry.modelId);
            return convertView;
        }
    }

    /** Creates the child views of one recycled row and wires their listeners. */
    private Holder createHolder() {
        Holder holder = new Holder();
        holder.name = field("", getContext().getString(R.string.screen_provider_model_name_hint),
                true);
        holder.modelId = field("",
                getContext().getString(R.string.screen_provider_model_id_hint), false);
        holder.meta = Ui.text(getContext(), "", Tokens.FONT_XS, theme.current().textTertiary,
                Typeface.NORMAL);
        holder.name.addTextChangedListener(watcher(text -> {
            if (holder.syncing || holder.entry == null) return;
            holder.entry.displayName = text;
            holder.entry.nameEdited = true;
            listener.onChanged();
        }));
        holder.modelId.addTextChangedListener(watcher(text -> {
            if (holder.syncing || holder.entry == null) return;
            holder.entry.modelId = text;
            if (!holder.entry.nameEdited) {
                String formatted = ModelProfile.displayName(text);
                holder.entry.displayName = formatted;
                holder.syncing = true;
                holder.name.setText(formatted);
                holder.name.setSelection(holder.name.getText().length());
                holder.syncing = false;
            }
            updateMeta(holder.meta, text);
            listener.onChanged();
        }));
        return holder;
    }

    /** Builds one row view from a holder; the holder is attached as the view tag. */
    private LinearLayout holderRow(Holder holder) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        Ui.padding(card, 14, 10, 14, 10);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(titleRow, new LayoutParams(-1, -2));
        titleRow.addView(holder.name, new LayoutParams(0, Ui.dp(context, 42), 1f));
        LayoutParams modelIdParams = new LayoutParams(0, Ui.dp(context, 42), 1f);
        modelIdParams.leftMargin = Ui.dp(context, 8);
        titleRow.addView(holder.modelId, modelIdParams);
        IconButton trash = new IconButton(context, Icons.TRASH_2);
        trash.setIconSizeDp(34, 16);
        trash.setContentDescription(context.getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            if (holder.entry == null) return;
            int index = entries.indexOf(holder.entry);
            if (index < 0) return;
            entries.remove(index);
            recordsChanged();
        });
        LayoutParams trashParams = new LayoutParams(Ui.dp(context, 34), Ui.dp(context, 42));
        trashParams.leftMargin = Ui.dp(context, 4);
        titleRow.addView(trash, trashParams);
        holder.meta.setVisibility(View.GONE);
        LayoutParams metaParams = new LayoutParams(-1, -2);
        metaParams.topMargin = Ui.dp(context, 6);
        card.addView(holder.meta, metaParams);
        theme.bind(t -> {
            holder.name.setTextColor(t.text);
            holder.modelId.setTextColor(t.text);
            holder.name.setBackground(Ui.roundedStroke(context, Color.TRANSPARENT, 8,
                    t.borderLight));
            holder.modelId.setBackground(Ui.roundedStroke(context, Color.TRANSPARENT, 8,
                    t.borderLight));
            holder.meta.setTextColor(t.textTertiary);
            trash.setIconColor(t.textTertiary);
        });
        return card;
    }

    /** Shows or hides the inferred capability caption for the current model id. */
    private void updateMeta(TextView meta, String modelId) {
        String value = modelId == null ? "" : modelId.trim();
        if (value.isEmpty()) {
            meta.setVisibility(View.GONE);
            return;
        }
        meta.setText(ModelUi.inferredMeta(getContext(), value));
        meta.setVisibility(View.VISIBLE);
    }

    /** Creates a compact single-line input with the shared field styling. */
    private EditText field(String value, String hint, boolean suggestions) {
        EditText input = new EditText(getContext());
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setHintTextColor(theme.current().textTertiary);
        input.setTextColor(theme.current().text);
        input.setTextSize(Tokens.FONT_SM);
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        input.setMinHeight(Ui.dp(getContext(), 42));
        input.setPadding(Ui.dp(getContext(), 10), Ui.dp(getContext(), 8),
                Ui.dp(getContext(), 10), Ui.dp(getContext(), 8));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (suggestions ? 0 : android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setBackground(Ui.roundedStroke(getContext(), Color.TRANSPARENT, 8,
                theme.current().borderLight));
        return input;
    }

    /** Builds a text watcher forwarding only the changed text to the supplied consumer. */
    private TextWatcher watcher(java.util.function.Consumer<String> consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable s) {
                consumer.accept(s == null ? "" : s.toString());
            }
        };
    }
}
