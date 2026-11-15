package cn.lineai.feature.workspace;

import android.content.Context;

import cn.lineai.core.model.Project;
import cn.lineai.ui.i18n.R;

/**
 * Display labels and folder-name normalisation shared by the workspace flow.
 *
 * <p>Package-level contract: normalisation must remain deterministic and idempotent so the same
 * display name always maps to the same directory on every device locale. The built-in project is
 * always labelled with the localized default-workspace caption instead of its stored name.</p>
 */
public final class WorkspaceNames {

    /** Not instantiable; normalisation and labels are static. */
    private WorkspaceNames() {
    }

    /**
     * Turns a display name into a directory name without destroying non-Latin text.
     *
     * <p>Only characters that are illegal in a path segment are replaced; CJK names stay readable
     * instead of collapsing to a lone dash. Returns an empty string when nothing usable remains.</p>
     */
    public static String folder(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", "-");
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            return "";
        }
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    /**
     * Returns the label a project row shows: the localized default-workspace caption for the
     * built-in project, otherwise the stored project name.
     *
     * @param context context resolving the caption string
     * @param project project to label; null yields the default caption
     * @return the project display label
     */
    public static String label(Context context, Project project) {
        return label(project, context.getString(R.string.workspace_default_local));
    }

    /**
     * Pure label resolution used by the Android free tests.
     *
     * @param project project to label; null yields the default caption
     * @param defaultLabel caption used for the built-in default project
     * @return the project display label
     */
    static String label(Project project, String defaultLabel) {
        return project == null || Project.DEFAULT_ID.equals(project.id)
                ? defaultLabel : project.name;
    }
}
