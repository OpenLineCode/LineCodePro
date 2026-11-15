package cn.lineai.feature.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelProfile;
import cn.lineai.core.model.Project;
import cn.lineai.core.model.ReasoningLevel;
import cn.lineai.core.model.TokenText;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Chat overlay sheets matching the C++ {@code chat_overlays.cpp} framing.
 *
 * <p>Every sheet uses {@link Sheets#showBottom} (24dp radius, 16dp side inset and bottom gap),
 * the shared 17sp bold title header with a 48dp close action, 56dp picker rows with a 15sp medium
 * title and 11sp tertiary detail, and the 52dp / 68.57dp menu rows used by the more, permission
 * and compaction menus.</p>
 */
public final class ChatSheets {

    /** C++ kPickerPanelMaximumHeight: the model/reasoning pickers cap at 400dp. */
    static final int PICKER_MAX_HEIGHT_DP = 400;
    /** C++ PickerSheetPanel min_height: a short picker still reads as a drawer. */
    static final int PICKER_MIN_HEIGHT_DP = 240;

    /** Static utility; not instantiable. */
    private ChatSheets() {
    }

    // ------------------------------------------------------------- entry points

    /** Loads the model list on io, then opens the picker on main. */
    public static void showModelPicker(ChatHost host) {
        host.scheduler().io(() -> {
            List<ModelConfig> models = host.models().list();
            ModelConfig selected = host.models().selected();
            host.scheduler().main(() -> showModelPickerList(host, models, selected));
        });
    }

    /** Renders the loaded model rows; selecting one persists the choice and refreshes the header. */
    private static void showModelPickerList(ChatHost host, List<ModelConfig> models,
                                            ModelConfig selected) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        String selectedId = selected == null ? "" : selected.id;

        LinearLayout panel = pickerPanel(host, R.string.slash_command_model_title,
                context.getString(R.string.composer_model_picker_subtitle));
        for (ModelConfig model : models) {
            String title = model.name == null || model.name.isEmpty()
                    ? model.modelId : model.name;
            String detail = model.providerLabel == null || model.providerLabel.isEmpty()
                    ? model.modelId : model.providerLabel + " · " + model.modelId;
            LinearLayout row = pickerRow(host, title, detail, model.id.equals(selectedId));
            row.setOnClickListener(v -> {
                host.models().select(model.id);
                host.chat().refreshModel();
                dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (models.isEmpty()) {
            panel.addView(emptyRow(host, R.string.composer_model_picker_empty),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(bottomSpacer(host));
        Sheets.showBottom(context, tokens, panel, PICKER_MAX_HEIGHT_DP,
                PICKER_MIN_HEIGHT_DP);
    }

    /**
     * Per-model reasoning-level picker.
     *
     * <p>The rows come from the selected model's own level list, falling back to the automatic
     * family profile. The chosen wire value is stored under
     * {@code model.<ModelConfig.id>.reasoning} so every model keeps its own level. Models that
     * cannot reason, forced-thinking families and models without levels report a toast instead of
     * opening an unusable picker.</p>
     */
    public static void showReasoningPicker(ChatHost host) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        ModelConfig model = host.models().selected();
        if (model == null) {
            Toast.makeText(context, R.string.composer_model_picker_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!model.supportsReasoning) {
            Toast.makeText(context, R.string.model_reasoning_locked,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<ReasoningLevel> levels = ModelReasoning.levels(model);
        if (levels.isEmpty()) {
            Toast.makeText(context, R.string.composer_reasoning_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ModelProfile.reasoningToggleable(model.modelId)) {
            Toast.makeText(context, R.string.model_reasoning_forced, Toast.LENGTH_SHORT).show();
        }
        String title = context.getString(R.string.composer_reasoning_title, model.displayName());
        LinearLayout panel = pickerPanelText(host, title,
                context.getString(R.string.composer_reasoning_subtitle2));
        String current = ModelReasoning.selected(host.settings(), model);
        String key = ModelReasoning.key(model);
        for (ReasoningLevel level : levels) {
            String value = level.value;
            LinearLayout row = pickerRow(host, levelLabel(context, level),
                    context.getString(R.string.composer_reasoning_value, value),
                    value.equals(current));
            row.setOnClickListener(v -> {
                host.settings().put(key, value);
                dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(bottomSpacer(host));
        Sheets.showBottom(context, tokens, panel, PICKER_MAX_HEIGHT_DP,
                PICKER_MIN_HEIGHT_DP);
    }

    /** Localized row title of a reasoning level; custom levels show their own label. */
    private static String levelLabel(Context context, ReasoningLevel level) {
        if (!level.label.isEmpty()) {
            return level.label;
        }
        int resource = presetLabel(level.preset);
        return resource == 0 ? level.value : context.getString(resource);
    }

    /** Resource id of a built-in level preset; zero when no built-in label exists. */
    private static int presetLabel(String preset) {
        switch (preset) {
            case ReasoningLevel.PRESET_OFF:
                return R.string.screen_llm_thinking_off_label;
            case ReasoningLevel.PRESET_AUTO:
                return R.string.screen_llm_thinking_auto_label;
            case ReasoningLevel.PRESET_MINIMAL:
                return R.string.model_reasoning_minimal_label;
            case ReasoningLevel.PRESET_LOW:
                return R.string.screen_llm_thinking_low_label;
            case ReasoningLevel.PRESET_MEDIUM:
                return R.string.screen_llm_thinking_medium_label;
            case ReasoningLevel.PRESET_HIGH:
                return R.string.screen_llm_thinking_high_label;
            case ReasoningLevel.PRESET_MAX:
                return R.string.screen_llm_thinking_max_label;
            case ReasoningLevel.PRESET_ON:
                return R.string.model_reasoning_on_label;
            default:
                return 0;
        }
    }

    /**
     * Session statistics sheet computed from the persisted transcript.
     *
     * <p>Rows are grouped into context, token usage, performance and tools; every value is
     * formatted by {@link SessionStats} with the shared token formatter, and a row without data
     * shows the neutral empty marker instead of a misleading zero.</p>
     */
    public static Dialog showContextUsage(ChatHost host) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        ChatController chat = host.chat();
        SessionStats stats = new SessionStats(chat.state.value().messages,
                chat.usedTokens(), chat.maxTokens());
        LinearLayout panel = standardPanel(host,
                context.getString(R.string.session_stats_title));
        sectionRow(host, panel, R.string.session_stats_section_context);
        usageRow(host, panel, R.string.context_usage_used,
                TokenText.format(stats.usedTokens));
        usageRow(host, panel, R.string.context_usage_limit,
                TokenText.format(stats.maxTokens));
        usageRow(host, panel, R.string.context_usage_percent,
                context.getString(R.string.session_stats_percent_value,
                        stats.contextPercent()));
        sectionRow(host, panel, R.string.session_stats_section_usage);
        usageRow(host, panel, R.string.session_stats_input,
                TokenText.format(stats.inputTokens));
        usageRow(host, panel, R.string.session_stats_output,
                TokenText.format(stats.outputTokens));
        usageRow(host, panel, R.string.session_stats_cache_hit,
                emptyWhenBlank(context, stats.cacheHitText()));
        sectionRow(host, panel, R.string.session_stats_section_performance);
        usageRow(host, panel, R.string.session_stats_ttft, stats.ttftSamples == 0
                ? emptyWhenBlank(context, "")
                : context.getString(R.string.session_stats_ttft_value,
                        stats.averageTtftMs()));
        usageRow(host, panel, R.string.session_stats_tps, stats.durationMs <= 0L
                ? emptyWhenBlank(context, "")
                : context.getString(R.string.session_stats_tps_value, stats.tpsText()));
        sectionRow(host, panel, R.string.session_stats_section_tools);
        usageRow(host, panel, R.string.session_stats_tool_calls,
                TokenText.format(stats.toolCalls));
        panel.addView(bottomSpacer(host));
        return Sheets.showBottom(context, tokens, panel);
    }

    /** Renders the neutral empty marker for a statistics row without data. */
    private static String emptyWhenBlank(Context context, String value) {
        return value.isEmpty() ? context.getString(R.string.session_stats_empty) : value;
    }

    /** Adds a small section caption inside a statistics sheet. */
    private static void sectionRow(ChatHost host, LinearLayout parent, int label) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        TextView caption = text(context, context.getString(label), Tokens.FONT_XS,
                tokens.textTertiary);
        Ui.padding(caption, 24, 14, 24, 4);
        parent.addView(caption, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Alias to {@link ChatMenuSheets#showMore} for the shared entry vocabulary. */
    public static void showMore(ChatHost host) {
        ChatMenuSheets.showMore(host);
    }

    /** Alias to {@link ChatMenuSheets#showPermission}. */
    public static void showPermission(ChatHost host) {
        ChatMenuSheets.showPermission(host);
    }

    /** Alias to {@link ChatMenuSheets#showContextMenu}. */
    public static void showContextMenu(ChatHost host) {
        ChatMenuSheets.showContextMenu(host);
    }

    /** Alias to {@link ChatMenuSheets#showCompaction}. */
    public static void showCompaction(ChatHost host, Runnable onConfirm) {
        ChatMenuSheets.showCompaction(host, onConfirm);
    }

    /** Alias to {@link ChatAttachmentSheet#show} with a pre-built tree. */
    public static void showAttachmentPicker(ChatHost host, DrawerPanel.FileNode tree,
                                            List<String> selected, Consumer<String> onToggle) {
        ChatAttachmentSheet.show(host, tree, selected, onToggle);
    }

    /** Loads the tree, then opens the attachment picker with the current composer selection. */
    public static void showAttachmentTree(ChatHost host,
                                          Supplier<List<String>> selected,
                                          Consumer<String> onToggle) {
        loadWorkspaceTree(host, tree -> ChatAttachmentSheet.show(host, tree, selected.get(),
                onToggle));
    }

    /**
     * Loads the whole workspace tree off the main thread for the drawer and the attachment
     * picker. Directories first, then case-insensitive names; the node budget keeps huge
     * workspaces from blocking the drawer.
     */
    public static void loadWorkspaceTree(ChatHost host, Consumer<DrawerPanel.FileNode> callback) {
        if (host.remoteWorkspaceActive()) {
            host.loadRemoteWorkspaceTree(callback);
            return;
        }
        Project project = host.projects().selected();
        if (project == null) {
            callback.accept(null);
            return;
        }
        final java.io.File root = new java.io.File(project.path);
        Scheduler scheduler = host.scheduler();
        scheduler.io(() -> {
            DrawerPanel.FileNode tree = buildTree(root, "", 0, new int[] {4000});
            scheduler.main(() -> callback.accept(tree));
        });
    }

    /**
     * Builds a tree from the terminal provider listing output.
     *
     * <p>The provider returns one directory or file per line, prefixed with {@code D} or
     * {@code F}; paths are relative to the provider home and become the node paths used by the
     * drawer and attachment picker.</p>
     *
     * @param rootLabel display label of the provider home
     * @param raw listing output; null or empty yields an empty root
     * @return the remote workspace tree
     */
    public static DrawerPanel.FileNode remoteTree(String rootLabel, String raw) {
        java.util.Map<String, java.util.List<String[]>> childrenByParent =
                new java.util.LinkedHashMap<>();
        java.util.Set<String> directories = new java.util.LinkedHashSet<>();
        directories.add("");
        if (raw != null) {
            for (String line : raw.split("\n")) {
                String value = line.trim();
                if (value.length() < 3 || (value.charAt(0) != 'D' && value.charAt(0) != 'F')) {
                    continue;
                }
                boolean directory = value.charAt(0) == 'D';
                String path = cleanRemotePath(value.substring(2).trim());
                if (path.isEmpty()) continue;
                if (directory) directories.add(path);
                String parent = path.lastIndexOf('/') < 0 ? ""
                        : path.substring(0, path.lastIndexOf('/'));
                childrenByParent.computeIfAbsent(parent, key -> new java.util.ArrayList<>())
                        .add(new String[] {path, directory ? "1" : "0"});
            }
        }
        return remoteNode(rootLabel, "", childrenByParent, directories);
    }

    /** Recursively builds one remote node from the listing maps. */
    private static DrawerPanel.FileNode remoteNode(String name, String path,
                                                   java.util.Map<String, java.util.List<String[]>>
                                                           childrenByParent,
                                                   java.util.Set<String> directories) {
        boolean directory = directories.contains(path);
        java.util.List<DrawerPanel.FileNode> nodes = new java.util.ArrayList<>();
        if (directory) {
            java.util.List<String[]> children = childrenByParent.get(path);
            if (children != null) {
                java.util.Collections.sort(children, (left, right) -> left[1].equals(right[1])
                        ? left[0].compareToIgnoreCase(right[0])
                        : right[1].compareTo(left[1]));
                for (String[] child : children) {
                    String childPath = child[0];
                    String childName = childPath.substring(childPath.lastIndexOf('/') + 1);
                    nodes.add(remoteNode(childName, childPath, childrenByParent, directories));
                }
            }
        }
        return new DrawerPanel.FileNode(name, path, directory, nodes);
    }

    /** Removes a leading "./" and trailing slashes from a provider-listed path. */
    private static String cleanRemotePath(String path) {
        String value = path;
        while (value.startsWith("./")) value = value.substring(2);
        if (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    /** Recursive tree build; directories first, then case-insensitive names, budget-capped. */
    private static DrawerPanel.FileNode buildTree(java.io.File dir, String relative, int depth,
                                                  int[] budget) {
        java.util.List<DrawerPanel.FileNode> children = new java.util.ArrayList<>();
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.isDirectory() != b.isDirectory()
                    ? (a.isDirectory() ? -1 : 1)
                    : a.getName().compareToIgnoreCase(b.getName()));
            for (java.io.File child : files) {
                if (budget[0]-- <= 0) {
                    break;
                }
                String path = relative.isEmpty()
                        ? child.getName() : relative + "/" + child.getName();
                children.add(child.isDirectory()
                        ? buildTree(child, path, depth + 1, budget)
                        : new DrawerPanel.FileNode(child.getName(), path, false,
                                Collections.<DrawerPanel.FileNode>emptyList()));
            }
        }
        return new DrawerPanel.FileNode(dir.getName(), relative, true, children);
    }

    /** Current permission mode for checkmark rendering. */
    static String permissionMode(ChatHost host) {
        return ToolModes.mode(host.settings());
    }

    // ------------------------------------------------------------------ framing

    /** Vertical container. */
    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /** Centered horizontal container. */
    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /** dp conversion. */
    static int dp(Context context, float value) {
        return Ui.dp(context, value);
    }

    /** Regular-weight label. */
    static TextView text(Context context, String value, int sizeSp, int color) {
        return Ui.text(context, value, sizeSp, color, Typeface.NORMAL);
    }

    /** Shared 36x4 grabber + 17sp bold title + hairline of the C++ {@code StandardSheet}. */
    static LinearLayout standardPanel(ChatHost host, String title) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = column(context);
        View handle = new View(context);
        handle.setBackground(Ui.rounded(context, tokens.textTertiary, 2));
        LinearLayout handleRow = row(context);
        handleRow.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.padding(handleRow, 0, 8, 0, 4);
        handleRow.addView(handle, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4)));
        panel.addView(handleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = Ui.bold(context, title, Tokens.FONT_LG, tokens.text);
        Ui.padding(heading, 24, 12, 24, 22);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(divider(context, tokens));
        return panel;
    }

    /** Picker header from C++ {@code PickerSheetPanel}: title/subtitle + 48dp close action. */
    static LinearLayout pickerPanel(ChatHost host, int titleRes, String subtitle) {
        return pickerPanelText(host, host.activity().getString(titleRes), subtitle);
    }

    /** Picker header variant taking an already-resolved title string. */
    static LinearLayout pickerPanelText(ChatHost host, String title, String subtitle) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = column(context);
        LinearLayout header = row(context);
        Ui.padding(header, 20, 14, 20, 10);
        LinearLayout labels = column(context);
        labels.addView(Ui.bold(context, title, Tokens.FONT_LG, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text(context, subtitle, Tokens.FONT_XS, tokens.textTertiary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(context, 3);
        labels.addView(detail, detailParams);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(new View(context), new LinearLayout.LayoutParams(dp(context, 12), 1));
        header.addView(closeButton(host), new LinearLayout.LayoutParams(
                dp(context, 48), dp(context, 48)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(divider(context, tokens));
        panel.setMinimumHeight(dp(context, PICKER_MIN_HEIGHT_DP));
        return panel;
    }

    /** C++ {@code PickerSheetRow}: 56dp, 15sp medium title, 11sp tertiary detail, 16dp check. */
    static LinearLayout pickerRow(ChatHost host, String title, String detail, boolean selected) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, 56));
        Ui.padding(row, 20, 12, 20, 12);
        LinearLayout labels = column(context);
        labels.addView(Ui.medium(context, title, 15, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView detailView = text(context, detail, Tokens.FONT_XS, tokens.textTertiary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(context, 2);
        labels.addView(detailView, detailParams);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selected) {
            android.widget.ImageView check = new android.widget.ImageView(context);
            check.setImageResource(Icons.drawable(Icons.CHECK));
            check.setColorFilter(tokens.accent);
            row.addView(check, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
        }
        return row;
    }

    /** C++ {@code DescribedMenuRow}: 16sp title + 11sp tertiary detail, optional 18dp check. */
    static LinearLayout describedRow(ChatHost host, String title, String detail,
                                     boolean selected, int minimumHeightDp) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, minimumHeightDp));
        Ui.padding(row, 16, 14, 16, 14);
        row.setBackground(selected ? Ui.rounded(context, tokens.accentMuted, 0) : null);
        LinearLayout labels = column(context);
        labels.addView(text(context, title, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        if (detail != null && !detail.isEmpty()) {
            TextView detailView = text(context, detail, Tokens.FONT_XS, tokens.textTertiary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(context, 2);
            labels.addView(detailView, params);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selected) {
            android.widget.ImageView check = new android.widget.ImageView(context);
            check.setImageResource(Icons.drawable(Icons.CHECK));
            check.setColorFilter(tokens.accent);
            row.addView(check, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));
        }
        return row;
    }

    /** C++ {@code PlainMenuRow}: bare 15sp label with a 52dp minimum height. */
    static LinearLayout plainRow(ChatHost host, String label) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, 52));
        row.addView(text(context, label, 15, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    /** Padded tertiary message for empty pickers. */
    static LinearLayout emptyRow(ChatHost host, int message) {
        Context context = host.activity();
        LinearLayout wrapper = column(context);
        Ui.padding(wrapper, 20, 24, 20, 24);
        wrapper.addView(text(context, context.getString(message), Tokens.FONT_SM,
                host.tokens().textTertiary), new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    /** Sub-pixel hairline. */
    static View divider(Context context, Tokens tokens) {
        View divider = new View(context);
        divider.setBackgroundColor(tokens.borderLight);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1,
                Math.round(context.getResources().getDisplayMetrics().density / 2.625f))));
        return divider;
    }

    /** C++ SheetBottomSpacer: keeps the last row clear of the system navigation bar. */
    static View bottomSpacer(ChatHost host) {
        return bottomSpacer(host, 16);
    }

    /** Spacer with an explicit height. */
    static View bottomSpacer(ChatHost host, int heightDp) {
        View spacer = new View(host.activity());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(host.activity(), heightDp)));
        return spacer;
    }

    /** 48dp close glyph that dismisses the hosting dialog. */
    static View closeButton(ChatHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        android.widget.ImageView close = new android.widget.ImageView(context);
        close.setImageResource(Icons.drawable(Icons.CLOSE));
        close.setColorFilter(tokens.textSecondary);
        close.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        Ui.padding(close, 15, 15, 15, 15);
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss(close));
        return close;
    }

    /** Dismisses the dialog hosting the view. */
    static void dismiss(View view) {
        Sheets.dismiss(view);
    }

    /** One label/value row of the context-usage sheet. */
    private static void usageRow(ChatHost host, LinearLayout parent, int label, String value) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.addView(text(context, context.getString(label), 14, tokens.textSecondary),
                new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.medium(context, value, 14, tokens.text),
                new LinearLayout.LayoutParams(-2, -2));
        Ui.padding(row, 24, 0, 24, 0);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(context, 42)));
    }

}
