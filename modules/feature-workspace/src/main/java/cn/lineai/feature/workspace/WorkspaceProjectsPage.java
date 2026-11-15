package cn.lineai.feature.workspace;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.lineai.core.model.Project;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Workspace project list with selection and creation.
 *
 * <p>The page rebuilds all cards on every reload; the name field instance is deliberately reused
 * across rebuilds so in-progress typing survives a refresh. Switching or creating while a
 * conversation is active is blocked with a confirmation-style notice, keeping the workspace and the
 * active conversation consistent. Loading and mutation go through the synchronous {@link Listener}
 * on the main thread; the contract expects cheap local access, not network I/O.</p>
 */
public final class WorkspaceProjectsPage extends SettingsPage {

    /**
     * Store and navigation access for the project list; implemented by the host.
     */
    public interface Listener {
        /**
         * Loads the current project list.
         *
         * @return projects in display order; never null
         */
        List<Project> list();

        /**
         * Selects a project as the active workspace.
         *
         * @param id id of the project to select
         */
        void select(String id);

        /**
         * Deletes a project.
         *
         * @param id id of the project to delete
         */
        void delete(String id);

        /**
         * Creates a managed workspace project from a display name.
         *
         * @param name display name; normalised into a folder name by the host
         * @return the created project, or null when creation failed
         */
        Project create(String name);

        /** Reports that the open-in-files-app action was requested. */
        void openExternal();

        /** Reports that the changes view was requested. */
        void onChanges();

        /** Address {@code user@host:port} shown for SSH projects instead of their path. */
        default String sshAddress() {
            return "";
        }

        /** True when the current conversation already has messages. */
        default boolean conversationActive() {
            return false;
        }

        /** Installed terminal providers offered as remote workspaces; never null in the app. */
        default List<TerminalProviderConfig> terminalProviders() {
            return java.util.Collections.emptyList();
        }

        /** True when the active workspace is a terminal provider rather than a project row. */
        default boolean terminalWorkspaceActive() {
            return false;
        }

        /** Identifier of the selected terminal provider, or empty when none is selected. */
        default String selectedTerminalProviderId() {
            return "";
        }

        /** Switches the workspace to one terminal provider application. */
        default void selectTerminalProvider(String providerId) {
        }

        /** Opens the terminal provider management page. */
        default void onTerminalProviders() {
        }
    }

    private final Listener listener;
    private final FormField nameField;

    /**
     * Builds the page, the shared name field and the initial card list.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param back action run by the header back button
     * @param listener store and navigation access; never null
     */
    public WorkspaceProjectsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.sheet_title_workspace, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        nameField = new FormField(context, theme, R.string.workspace_field_name, "",
                context.getString(R.string.workspace_hint_name), false);
        reload();
    }

    /**
     * Rebuilds the content column: project rows (or the empty-state caption) followed by the
     * changes, external-access and creation cards. Must run on the main thread.
     */
    public void reload() {
        content.removeAllViews();
        List<Project> projects = listener.list();
        List<Project> local = new java.util.ArrayList<>();
        List<Project> ssh = new java.util.ArrayList<>();
        for (Project project : projects) {
            if (isSsh(project)) ssh.add(project);
            else local.add(project);
        }
        sectionLabel(R.string.workspace_section_local);
        addProjectRows(local, true);
        sectionLabel(R.string.workspace_section_ssh);
        addProjectRows(ssh, false);
        sectionLabel(R.string.workspace_section_terminal);
        addTerminalProviders();
        content.addView(changesCard(), cardParams());
        content.addView(externalCard(), cardParams());
        content.addView(createCard(), cardParams());
    }

    /** Adds one project group; an empty group shows its dedicated caption. */
    private void addProjectRows(List<Project> projects, boolean localGroup) {
        if (projects.isEmpty()) {
            addEmpty(localGroup ? R.string.workspace_empty : R.string.workspace_ssh_empty);
            return;
        }
        for (Project project : projects) content.addView(row(project), rowParams());
    }

    /** Adds the terminal provider workspace group with a management action. */
    private void addTerminalProviders() {
        List<TerminalProviderConfig> providers = listener.terminalProviders();
        boolean active = listener.terminalWorkspaceActive();
        String selectedId = listener.selectedTerminalProviderId();
        if (providers == null || providers.isEmpty()) {
            addEmpty(R.string.workspace_terminal_empty);
        } else {
            for (TerminalProviderConfig provider : providers) {
                if (!provider.enabled) continue;
                content.addView(terminalRow(provider, active && provider.id.equals(selectedId)),
                        rowParams());
            }
        }
        LinearLayout manage = new LinearLayout(getContext());
        manage.setGravity(Gravity.CENTER);
        manage.setClickable(true);
        manage.setBackground(Ui.roundedStroke(getContext(), theme.current().surfaceLight, 10,
                theme.current().border));
        manage.addView(Ui.bold(getContext(), getContext().getString(
                R.string.workspace_terminal_manage), Tokens.FONT_SM,
                theme.current().textSecondary), new LayoutParams(-2, -2));
        manage.setOnClickListener(v -> listener.onTerminalProviders());
        Ui.padding(manage, 12, 10, 12, 10);
        content.addView(manage, rowParams());
    }

    /** Adds a small group heading above one workspace category. */
    private void sectionLabel(int label) {
        TextView title = Ui.medium(getContext(), getContext().getString(label),
                Tokens.FONT_SM, theme.current().textSecondary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 16);
        params.bottomMargin = Ui.dp(getContext(), 8);
        params.leftMargin = Ui.dp(getContext(), 4);
        content.addView(title, params);
        theme.bind(t -> title.setTextColor(t.textSecondary));
    }

    /** Adds one centered empty-state caption. */
    private void addEmpty(int label) {
        TextView empty = Ui.text(getContext(), getContext().getString(label), Tokens.FONT_SM,
                theme.current().textTertiary, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        Ui.padding(empty, 16, 18, 16, 18);
        content.addView(empty, rowParams());
        theme.bind(t -> empty.setTextColor(t.textTertiary));
    }

    /** Builds one selectable terminal provider workspace row. */
    private LinearLayout terminalRow(TerminalProviderConfig provider, boolean selected) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context,
                selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                selected ? tokens.accent : tokens.border));
        Ui.padding(row, 14, 12, 12, 12);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (listener.conversationActive()) {
                showSwitchBlocked();
                return;
            }
            listener.selectTerminalProvider(provider.id);
            reload();
        });
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, Icons.TERMINAL);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        labels.addView(Ui.bold(context, provider.name, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView detail = Ui.text(context, provider.packageName, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = Ui.dp(context, 2);
        labels.addView(detail, detailParams);
        if (selected) {
            TextView badge = Ui.medium(context, context.getString(R.string.workspace_selected),
                    Tokens.FONT_XS, tokens.accent);
            badge.setBackground(Ui.rounded(context, tokens.accentMuted, 999));
            Ui.padding(badge, 8, 3, 8, 3);
            row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        }
        theme.bind(t -> {
            row.setBackground(Ui.roundedStroke(context,
                    selected ? t.inputBg : t.surfaceElevated, 12,
                    selected ? t.accent : t.border));
            detail.setTextColor(t.textTertiary);
        });
        return row;
    }

    /**
     * Builds one project row: folder icon, name and subtitle (SSH address when applicable), the
     * selected badge, plus tap-to-select and long-press-to-delete handlers. Long press on the
     * default project is intentionally inert.
     */
    private LinearLayout row(Project project) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean ssh = isSsh(project);
        boolean selected = project.selected && !listener.terminalWorkspaceActive();
        String address = listener.sshAddress();
        String subtitle = ssh && !address.isEmpty() ? address : project.path;
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context,
                selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                selected ? tokens.accent : tokens.border));
        Ui.padding(row, 14, 12, 12, 12);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (selected) {
                return;
            }
            if (listener.conversationActive()) {
                showSwitchBlocked();
                return;
            }
            listener.select(project.id);
            reload();
        });
        row.setOnLongClickListener(v -> {
            if (Project.DEFAULT_ID.equals(project.id)) return true;
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.drawer_project_remove_title),
                    context.getString(R.string.drawer_project_remove_message,
                            WorkspaceNames.label(context, project)),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_delete),
                    confirmed -> {
                        if (confirmed) {
                            listener.delete(project.id);
                            reload();
                        }
                    });
            return true;
        });

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, Icons.FOLDER);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        labels.addView(Ui.bold(context, WorkspaceNames.label(context, project), Tokens.FONT_MD,
                tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView path = Ui.text(context, subtitle, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(-1, -2);
        pathParams.topMargin = Ui.dp(context, 2);
        labels.addView(path, pathParams);

        if (selected) {
            TextView badge = Ui.medium(context,
                    context.getString(R.string.workspace_selected),
                    Tokens.FONT_XS, tokens.accent);
            badge.setBackground(Ui.rounded(context, tokens.accentMuted, 999));
            Ui.padding(badge, 8, 3, 8, 3);
            row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        }
        theme.bind(t -> row.setBackground(Ui.roundedStroke(context,
                selected ? t.inputBg : t.surfaceElevated, 12,
                selected ? t.accent : t.border)));
        return row;
    }

    /** Builds the changes-entry card that opens the changes view. */
    private LinearLayout changesCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_changes_open),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.workspace_changes_open_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        card.setClickable(true);
        card.setOnClickListener(v -> listener.onChanges());
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    /** Builds the external-access card with the open-in-files-app button. */
    private LinearLayout externalCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_open_folder),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.workspace_open_folder_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 10, tokens.border));
        button.addView(Ui.bold(context, context.getString(R.string.workspace_open_folder),
                Tokens.FONT_SM, tokens.textSecondary), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> listener.openExternal());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 10);
        card.addView(button, buttonParams);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    /** Builds the creation card hosting the shared name field and the create button. */
    private LinearLayout createCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_create_title),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = Ui.dp(context, 10);
        // reload() rebuilds the cards, and the field instance is reused across reloads: it must be
        // detached from the previous card or addView throws "child already has a parent".
        if (nameField.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) nameField.getParent()).removeView(nameField);
        }
        card.addView(nameField, fieldParams);
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 12));
        button.addView(Ui.bold(context, context.getString(R.string.workspace_create),
                Tokens.FONT_MD, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> create());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
        buttonParams.topMargin = Ui.dp(context, 12);
        card.addView(button, buttonParams);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    /**
     * Creates a project from the name field: blocked while a conversation is active, rejected with
     * a toast when the name is empty, and only reloaded when the host actually created something.
     */
    private void create() {
        if (listener.conversationActive()) {
            showSwitchBlocked();
            return;
        }
        String name = nameField.value();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.workspace_create_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Project created = listener.create(name);
        if (created != null) {
            Toast.makeText(getContext(), R.string.workspace_created, Toast.LENGTH_SHORT).show();
            nameField.input().setText("");
            reload();
        }
    }

    /** Reports whether a project is SSH-backed, from either its id prefix or its path scheme. */
    private static boolean isSsh(Project project) {
        String id = project.id == null ? "" : project.id;
        String path = project.path == null ? "" : project.path;
        return id.startsWith("ssh:") || path.startsWith("ssh://");
    }

    /** Shows the informational dialog explaining that switching is blocked during an active
     * conversation; confirming does nothing by design. */
    private void showSwitchBlocked() {
        Dialogs.confirm(getContext(), theme.current(),
                getContext().getString(R.string.workspace_switch_blocked_title),
                getContext().getString(R.string.workspace_switch_blocked_message),
                getContext().getString(R.string.common_cancel),
                getContext().getString(R.string.common_confirm),
                confirmed -> { });
    }

    /** Builds the project-row layout params carrying the uniform bottom margin. */
    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }

    /** Builds the card layout params carrying the uniform top margin. */
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 12);
        return params;
    }
}
