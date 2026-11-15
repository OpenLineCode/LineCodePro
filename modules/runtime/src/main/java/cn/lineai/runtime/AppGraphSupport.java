package cn.lineai.runtime;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.io.File;

import cn.lineai.core.model.Project;
import cn.lineai.data.api.ProjectStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.prompt.AssetPromptCatalog;
import cn.lineai.engine.prompt.PromptCatalog;
import cn.lineai.engine.prompt.PromptStore;
import cn.lineai.feature.chat.ChatController;

/** Static environment helpers used while assembling {@link AppGraph}. */
final class AppGraphSupport {

    /** Not instantiable; every helper is static. */
    private AppGraphSupport() {
    }

    /** Ensures the default workspace project exists and mirrors its display label into settings. */
    static Project defaultProject(Context context, ProjectStore projects, File workspace,
                                  SettingsStore settingsStore) {
        Project project = projects.ensureDefault(workspace);
        String label = Project.DEFAULT_ID.equals(project.id)
                ? context.getString(cn.lineai.ui.i18n.R.string.workspace_default_name)
                : project.name;
        settingsStore.put(ChatController.KEY_PROJECT_LABEL, label);
        return project;
    }

    /** Prompt store over bundled asset templates with settings-backed overrides. */
    static PromptStore promptStore(Context context, SettingsStore settingsStore) {
        PromptCatalog catalog = new AssetPromptCatalog(
                path -> context.getAssets().open(path));
        return new PromptStore(catalog, new SettingsPromptOverrides(settingsStore));
    }

    /** Dark configuration check used for the initial theme selection. */
    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * One free-form device/app summary used at the top of failure diagnostics.
     *
     * @param context application context used to read the package metadata
     * @return manufacturer/model, Android release/SDK and app version/code
     */
    static String deviceSummary(Context context) {
        String versionName = "";
        long versionCode = 0L;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName == null ? "" : info.versionName;
            versionCode = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Metadata is best-effort; the rest of the summary still identifies the build.
            versionName = "";
            versionCode = 0L;
        }
        String device = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER) + " "
                + (Build.MODEL == null ? "" : Build.MODEL);
        return device.trim() + " (Android " + Build.VERSION.RELEASE + ", SDK "
                + Build.VERSION.SDK_INT + "), app " + versionName + " (code " + versionCode + ")";
    }
}
