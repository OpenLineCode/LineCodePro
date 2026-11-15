package cn.lineai.runtime;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.feature.extensions.AgentExtensionEditPage;
import cn.lineai.feature.extensions.AgentExtensionsPage;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.extensions.GitHubSkillPage;
import cn.lineai.feature.extensions.LineCodeExtensionsPage;
import cn.lineai.feature.extensions.McpExtensionEditPage;
import cn.lineai.feature.extensions.McpExtensionsPage;
import cn.lineai.feature.extensions.SkillEditPage;
import cn.lineai.feature.extensions.SkillHubPage;
import cn.lineai.feature.extensions.SkillsExtensionsPage;
import cn.lineai.feature.extensions.TerminalProvidersPage;
import cn.lineai.feature.models.ModelDetailPage;
import cn.lineai.feature.models.ModelScreens;
import cn.lineai.feature.models.ProviderEditPage;
import cn.lineai.feature.models.ProviderListPage;
import cn.lineai.feature.models.ProviderModelsPage;
import cn.lineai.feature.workspace.ChangesPage;
import cn.lineai.feature.workspace.WorkspaceProjectsPage;
import cn.lineai.feature.workspace.WorkspaceScreens;
import cn.lineai.ui.i18n.R;

/** Models, extensions and workspace screens constructed outside the activity. */
public final class FeaturePageFactory {

    private final PageHost host;
    private final LocalModelBridge localModels;
    private SkillsExtensionsPage skillsPage;
    private WorkspaceProjectsPage workspacePage;
    private LocalModelBridge.Page localPage;

    /** Creates the factory; the local bridge is resolved once, reflecting the build flavor. */
    FeaturePageFactory(PageHost host) {
        this.host = host;
        this.localModels = LocalModelBridges.find();
    }

    /** Builds the view for one feature screen key; throws on unregistered routes. */
    View create(ScreenKey<?, ?> key) {
        if (key.id() == ExtensionScreens.AGENTS) return agentExtensionsPage();
        if (key.id() == ExtensionScreens.AGENT_EDIT) return agentEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.MCPS) return mcpExtensionsPage();
        if (key.id() == ExtensionScreens.MCP_EDIT) return mcpEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.SKILLS) return skillsExtensionsPage();
        if (key.id() == ExtensionScreens.SKILL_EDIT) return skillEditPage((Params.Args) key.params());
        if (key.id() == ExtensionScreens.SKILL_HUB) {
            return new SkillHubPage(host.activity(), host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(),
                    slug -> host.navigation().push(
                            ExtensionScreens.SKILL_DETAIL.key(cn.lineai.core.mvi.nav.Params.Args.of(
                                    "slug", slug))),
                    () -> host.navigation().push(ExtensionScreens.SKILL_LOGIN.key(
                            Params.Empty.INSTANCE)),
                    () -> host.navigation().push(ExtensionScreens.SKILL_PUBLISH.key(
                            Params.Empty.INSTANCE)));
        }
        if (key.id() == ExtensionScreens.SKILL_DETAIL) {
            String slug = key.args().get("slug") instanceof String
                    ? (String) key.args().get("slug") : "";
            return new cn.lineai.feature.extensions.SkillHubDetailPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(), slug);
        }
        if (key.id() == ExtensionScreens.SKILL_LOGIN) {
            return new cn.lineai.feature.extensions.SkillHubLoginPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    () -> {
                        android.widget.Toast.makeText(host.activity(),
                                R.string.skillhub_login_success, android.widget.Toast.LENGTH_SHORT)
                                .show();
                        host.navigation().back();
                    });
        }
        if (key.id() == ExtensionScreens.SKILL_PUBLISH) {
            return new cn.lineai.feature.extensions.SkillHubPublishPage(host.activity(),
                    host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills(), () -> host.navigation().back());
        }
        if (key.id() == ExtensionScreens.SKILL_GITHUB) {
            return new GitHubSkillPage(host.activity(), host.tokens(), host.navigation()::back,
                    host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                    host.graph().skills());
        }
        if (key.id() == ExtensionScreens.LINECODE) {
            return new LineCodeExtensionsPage(host.activity(), host.tokens(),
                    host.navigation()::back);
        }
        if (key.id() == ExtensionScreens.TERMINAL_PROVIDERS) return terminalProvidersPage();
        if (key.id() == WorkspaceScreens.PROJECTS) return workspaceProjectsPage();
        if (key.id() == WorkspaceScreens.CHANGES) {
            return new ChangesPage(host.activity(), host.tokens(), host.navigation()::back,
                    new ChangesPage.Listener() {
                        public java.util.List<cn.lineai.core.model.FileDiff> load() {
                            return host.graph().diffs().list();
                        }
                        public boolean revert(String id) { return host.graph().diffs().revert(id); }
                    });
        }
        if (key.id() == ModelScreens.LIST) return providerListPage();
        if (key.id() == ModelScreens.PROVIDER_MODELS) {
            cn.lineai.core.model.ProviderConfig provider =
                    host.graph().providers().find(ModelScreens.idOf(key.args()));
            if (provider == null) {
                host.unavailable();
                return providerListPage();
            }
            return providerModelsPage(provider);
        }
        if (key.id() == ModelScreens.PROVIDER_EDIT) {
            String providerId = ModelScreens.idOf(key.args());
            cn.lineai.core.model.ProviderConfig provider =
                    providerId.isEmpty() ? null : host.graph().providers().find(providerId);
            return providerEditPage(provider);
        }
        if (key.id() == ModelScreens.MODEL_EDIT) {
            cn.lineai.core.model.ModelConfig existing =
                    host.graph().models().find(ModelScreens.idOf(key.args()));
            if (existing == null) {
                host.unavailable();
                return providerListPage();
            }
            if (existing.protocol == cn.lineai.core.model.ModelConfig.Protocol.LOCAL_GGUF
                    && localModels != null) {
                return localModelPage(existing);
            }
            return modelDetailPage(existing,
                    host.graph().providers().find(existing.providerId));
        }
        if (key.id() == ModelScreens.LOCAL) {
            String modelId = ModelScreens.idOf(key.args());
            return localModelPage(modelId.isEmpty()
                    ? null : host.graph().models().find(modelId));
        }
        throw new IllegalArgumentException("Unregistered feature screen: " + key.id().route());
    }

    /** Forwards an imported GGUF path to the open local model page, if any. */
    public void applyLocalModelPath(String path) {
        if (localPage != null) localPage.setModelPath(path);
    }

    /** Refreshes the cached workspace projects page after SAF-driven changes. */
    public void reloadWorkspace() {
        if (workspacePage != null) workspacePage.reload();
    }

    /** Refreshes the cached skills page after a SAF skill import. */
    public void reloadSkills() {
        if (skillsPage != null) skillsPage.reload();
    }

    /** Extension agent list page wired to the agent extension store. */
    private AgentExtensionsPage agentExtensionsPage() {
        return new AgentExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new AgentExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.AgentExtension> load() {
                        return host.graph().agentExtensions().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().agentExtensions().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().agentExtensions().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.AGENT_EDIT.key(
                                ExtensionScreens.agentArgs("")));
                    }
                    public void edit(cn.lineai.core.model.AgentExtension agent) {
                        host.navigation().push(ExtensionScreens.AGENT_EDIT.key(
                                ExtensionScreens.agentArgs(agent.id)));
                    }
                });
    }

    /** Agent editor page; loads the edited agent and the phone-tool-free catalog. */
    private AgentExtensionEditPage agentEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.AgentExtension editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.AgentExtension agent
                    : host.graph().agentExtensions().list()) {
                if (agent.id.equals(id)) { editing = agent; break; }
            }
        }
        final cn.lineai.core.model.AgentExtension target = editing;
        java.util.List<cn.lineai.tool.api.Tool> agentTools = new java.util.ArrayList<>();
        for (cn.lineai.tool.api.Tool tool : host.graph().tools().registry().all()) {
            if (tool.kind() != cn.lineai.tool.api.ToolKind.PHONE) {
                agentTools.add(tool);
            }
        }
        return new AgentExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                host.graph().scheduler(), target, agentTools,
                new AgentExtensionEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.AgentExtension extension) {
                        host.graph().agentExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String agentId) {
                        host.graph().agentExtensions().delete(agentId);
                        host.navigation().back();
                    }
                    public cn.lineai.core.model.AgentExtension generateDraft(String description)
                            throws Exception {
                        return agentDraft(description);
                    }
                });
    }

    /** Wires the selected model, the tool catalog and the prompt template into the use case. */
    private cn.lineai.core.model.AgentExtension agentDraft(String description) throws Exception {
        cn.lineai.core.model.ModelConfig model = host.graph().models().selected();
        if (model == null) {
            throw new IllegalStateException("No model selected");
        }
        return AgentDrafts.generate(model,
                host.graph().prompts().current(cn.lineai.engine.prompt.PromptId.AGENT_DRAFT),
                host.graph().tools().registry().all(), description,
                new cn.lineai.engine.ai.ModelProbe());
    }

    /** MCP server list page wired to the MCP extension store. */
    private McpExtensionsPage mcpExtensionsPage() {
        return new McpExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new McpExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.McpExtension> load() {
                        return host.graph().mcpExtensions().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().mcpExtensions().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().mcpExtensions().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.MCP_EDIT.key(
                                ExtensionScreens.mcpArgs("")));
                    }
                    public void edit(cn.lineai.core.model.McpExtension server) {
                        host.navigation().push(ExtensionScreens.MCP_EDIT.key(
                                ExtensionScreens.mcpArgs(server.id)));
                    }
                });
    }

    /** MCP server editor page; loads the edited server when the args carry an id. */
    private McpExtensionEditPage mcpEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.McpExtension editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.McpExtension server
                    : host.graph().mcpExtensions().list()) {
                if (server.id.equals(id)) { editing = server; break; }
            }
        }
        return new McpExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                editing, host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class),
                new McpExtensionEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.McpExtension extension) {
                        host.graph().mcpExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String serverId) {
                        host.graph().mcpExtensions().delete(serverId);
                        host.navigation().back();
                    }
                });
    }

    /** Skills list page; cached so SAF imports can trigger a reload. */
    private SkillsExtensionsPage skillsExtensionsPage() {
        skillsPage = new SkillsExtensionsPage(host.activity(), host.tokens(),
                host.navigation()::back, new SkillsExtensionsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.Skill> load() {
                        return host.graph().skills().list();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().skills().setEnabled(id, enabled);
                    }
                    public void delete(String id) { host.graph().skills().delete(id); }
                    public void add() {
                        host.navigation().push(ExtensionScreens.SKILL_EDIT.key(
                                ExtensionScreens.skillArgs("")));
                    }
                    public void edit(cn.lineai.core.model.Skill skill) {
                        host.navigation().push(ExtensionScreens.SKILL_EDIT.key(
                                ExtensionScreens.skillArgs(skill.id)));
                    }
                    public void openHub() {
                        host.navigation().push(ExtensionScreens.SKILL_HUB.key(Params.Empty.INSTANCE));
                    }
                    public void openGithub() {
                        host.navigation().push(ExtensionScreens.SKILL_GITHUB.key(
                                Params.Empty.INSTANCE));
                    }
                    public void openLogin() {
                        host.navigation().push(ExtensionScreens.SKILL_LOGIN.key(
                                Params.Empty.INSTANCE));
                    }
                    public void openPublish() {
                        host.navigation().push(ExtensionScreens.SKILL_PUBLISH.key(
                                Params.Empty.INSTANCE));
                    }
                    public void importSkill() { host.pickSkillArchive(); }
                });
        return skillsPage;
    }

    /** Skill editor page; loads the edited skill when the args carry an id. */
    private SkillEditPage skillEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        cn.lineai.core.model.Skill editing = null;
        if (!id.isEmpty()) {
            for (cn.lineai.core.model.Skill skill : host.graph().skills().list()) {
                if (skill.id.equals(id)) { editing = skill; break; }
            }
        }
        final cn.lineai.core.model.Skill target = editing;
        return new SkillEditPage(host.activity(), host.tokens(), host.navigation()::back, target,
                new SkillEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.Skill skill) {
                        host.graph().skills().save(skill);
                        host.navigation().back();
                    }
                    public void onDelete(String skillId) {
                        host.graph().skills().delete(skillId);
                        host.navigation().back();
                    }
                });
    }

    /** Terminal provider management page; refreshes discovery before rendering. */
    private TerminalProvidersPage terminalProvidersPage() {
        host.graph().refreshTerminalProviders();
        return new TerminalProvidersPage(host.activity(), host.tokens(), host.navigation()::back,
                new TerminalProvidersPage.Listener() {
                    public java.util.List<cn.lineai.core.model.TerminalProviderConfig> installed() {
                        return host.graph().terminalProviders().list();
                    }
                    public java.util.List<cn.lineai.core.model.ScannedTerminalProvider> scan() {
                        return cn.lineai.shell.TerminalProviderDiscovery.scan(host.activity());
                    }
                    public void add(cn.lineai.core.model.ScannedTerminalProvider provider) {
                        if (!host.graph().trustsProviderSignature(provider.packageName)) {
                            Toast.makeText(host.activity(),
                                    R.string.toast_terminal_provider_signature_untrusted,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        host.graph().terminalProviders().add(
                                new cn.lineai.core.model.TerminalProviderConfig(
                                        "", true, provider.name, provider.packageName,
                                        provider.serviceClass, provider.providerType, 0, 0));
                        host.graph().refreshTerminalProviders();
                    }
                    public void setEnabled(String id, boolean enabled) {
                        host.graph().terminalProviders().setEnabled(id, enabled);
                        host.graph().refreshTerminalProviders();
                    }
                    public void delete(String id) {
                        host.graph().terminalProviders().delete(id);
                        host.graph().refreshTerminalProviders();
                    }
                    public void onDownloadExtension() {
                        host.openLink("https://github.com/OpenLineCode/"
                                + "LineCodeTerminalExtension/releases");
                    }
                });
    }

    /** Workspace listener handling selection, creation, SSH status and change routing. */
    private WorkspaceProjectsPage.Listener workspaceListener() {
        return new WorkspaceProjectsPage.Listener() {
                    public java.util.List<cn.lineai.core.model.Project> list() {
                        return host.graph().projects().list();
                    }
                    public void select(String id) {
                        clearTerminalWorkspace();
                        host.graph().projects().select(id);
                        host.applyProjectLabel(host.graph().projects().selected());
                    }
                    public void delete(String id) {
                        host.graph().projects().delete(id);
                        host.applyProjectLabel(host.graph().projects().selected());
                    }
                    public cn.lineai.core.model.Project create(String name) {
                        clearTerminalWorkspace();
                        java.io.File root = new java.io.File(host.activity().getFilesDir(),
                                "workspace");
                        String safe = cn.lineai.feature.workspace.WorkspaceNames.folder(name);
                        if (safe.isEmpty()) safe = "project";
                        java.io.File dir = new java.io.File(root, safe);
                        int index = 2;
                        while (dir.exists()) {
                            dir = new java.io.File(root, safe + "-" + index);
                            index++;
                        }
                        dir.mkdirs();
                        cn.lineai.core.model.Project created = host.graph().projects().create(
                                name.trim(), dir.getAbsolutePath());
                        host.applyProjectLabel(created);
                        return created;
                    }
                    public void openExternal() { host.openExternalWorkspace(); }
                    public String sshAddress() {
                        cn.lineai.core.model.SshConfig config =
                                new cn.lineai.shell.SshPreferences(
                                        host.graph().settings()).config();
                        if (config.host == null || config.host.isEmpty()) return "";
                        return (config.username.isEmpty() ? "" : config.username + "@")
                                + config.host + ":" + config.port;
                    }
                    public boolean conversationActive() {
                        return host.graph().chat().hasMessages();
                    }
                    public java.util.List<cn.lineai.core.model.TerminalProviderConfig>
                            terminalProviders() {
                        return host.graph().terminalProviders().list();
                    }
                    public boolean terminalWorkspaceActive() {
                        return cn.lineai.feature.settings.McpPreferences.MODE_TERMINAL_PROVIDER
                                .equals(new cn.lineai.feature.settings.McpPreferences(
                                        host.graph().settings()).mode());
                    }
                    public String selectedTerminalProviderId() {
                        return host.graph().terminalProviderSelectedId();
                    }
                    public void selectTerminalProvider(String providerId) {
                        new cn.lineai.feature.settings.McpPreferences(host.graph().settings())
                                .setMode(cn.lineai.feature.settings.McpPreferences
                                        .MODE_TERMINAL_PROVIDER);
                        host.graph().selectTerminalProvider(providerId);
                        cn.lineai.core.model.TerminalProviderConfig selected =
                                host.graph().selectedTerminalProvider();
                        String label = selected == null || selected.name.isEmpty()
                                ? providerId : selected.name;
                        host.graph().settings().put(
                                cn.lineai.feature.chat.ChatController.KEY_PROJECT_LABEL, label);
                        host.graph().chat().refreshModel();
                    }
                    public void onTerminalProviders() {
                        host.navigation().push(ExtensionScreens.TERMINAL_PROVIDERS.key(
                                Params.Empty.INSTANCE));
                    }
            public void onChanges() {
                host.navigation().push(WorkspaceScreens.CHANGES.key(Params.Empty.INSTANCE));
            }
        };
    }

    /** Returns the workspace selection to a project row after a terminal workspace was active. */
    private void clearTerminalWorkspace() {
        new cn.lineai.feature.settings.McpPreferences(host.graph().settings())
                .setMode(cn.lineai.feature.settings.McpPreferences.MODE_LOCAL);
    }

    /** Workspace projects page; cached so SAF results can trigger a reload. */
    private View workspaceProjectsPage() {
        workspacePage = new WorkspaceProjectsPage(host.activity(), host.tokens(),
                host.navigation()::back, workspaceListener());
        return workspacePage;
    }

    /** Provider list root; reloads providers and models on every visit. */
    private View providerListPage() {
        ProviderListPage page = new ProviderListPage(host.activity(), host.tokens(),
                new ProviderListPage.Listener() {
                    public void onBack() { host.navigation().back(); }
                    public void onAddProvider() {
                        host.navigation().push(ModelScreens.PROVIDER_EDIT.key(
                                ModelScreens.providerEditArgs(null)));
                    }
                    public void onOpenProvider(String providerId) {
                        host.navigation().push(ModelScreens.PROVIDER_MODELS.key(
                                ModelScreens.providerArgs(providerId)));
                    }
                    public void onEditProvider(String providerId) {
                        host.navigation().push(ModelScreens.PROVIDER_EDIT.key(
                                ModelScreens.providerEditArgs(providerId)));
                    }
                    public void onDeleteProvider(String providerId,
                                                 java.util.List<String> modelIds) {
                        host.graph().models().delete(modelIds);
                        host.graph().providers().delete(providerId);
                        host.graph().chat().refreshModel();
                    }
                    public boolean localModelsAvailable() { return localModels != null; }
                    public void onLocalModels() {
                        if (localModels == null) {
                            Toast.makeText(host.activity(),
                                    R.string.screen_model_add_local_picker_disabled,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        host.navigation().push(ModelScreens.LOCAL.key(
                                ModelScreens.localArgs(null)));
                    }
                }, host.graph().scheduler());
        page.loader(() -> host.graph().providers().list(),
                () -> host.graph().models().list());
        return page;
    }

    /** Models of one provider; tapping a row opens the model detail page. */
    private View providerModelsPage(cn.lineai.core.model.ProviderConfig provider) {
        ProviderModelsPage page = new ProviderModelsPage(host.activity(), host.tokens(),
                provider, new ProviderModelsPage.Listener() {
                    public void onBack() { host.navigation().back(); }
                    public void onOpenModel(String modelId) {
                        host.navigation().push(ModelScreens.MODEL_EDIT.key(
                                ModelScreens.modelArgs(modelId)));
                    }
                    public void onAddModels(String providerId) {
                        host.navigation().push(ModelScreens.PROVIDER_EDIT.key(
                                ModelScreens.providerEditArgs(providerId)));
                    }
                    public void onSetDefault(String modelId) {
                        host.graph().models().select(modelId);
                        host.graph().chat().refreshModel();
                    }
                    public void onDeleteModel(String modelId) {
                        host.graph().models().delete(
                                java.util.Collections.singletonList(modelId));
                        host.graph().chat().refreshModel();
                    }
                }, host.graph().scheduler());
        page.loader(() -> host.graph().models().list());
        return page;
    }

    /** Add or edit a provider, including its fetched and manual model rows. */
    private View providerEditPage(cn.lineai.core.model.ProviderConfig provider) {
        return new ProviderEditPage(host.activity(), host.tokens(),
                host.graph().scheduler(), host.navigation()::back, provider,
                host.graph().models().list(),
                new ProviderEditPage.Listener() {
                    public void onSave(cn.lineai.core.model.ProviderConfig saved,
                                       java.util.List<cn.lineai.core.model.ModelConfig> models,
                                       java.util.List<String> deletedModelIds) {
                        host.graph().providers().save(saved);
                        for (cn.lineai.core.model.ModelConfig model : models) {
                            host.graph().models().save(model);
                        }
                        if (!deletedModelIds.isEmpty()) {
                            host.graph().models().delete(deletedModelIds);
                        }
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                        host.navigation().push(ModelScreens.PROVIDER_MODELS.key(
                                ModelScreens.providerArgs(saved.id)));
                    }
                });
    }

    /** Model detail page; saves and deletes return to the provider model list. */
    private View modelDetailPage(cn.lineai.core.model.ModelConfig existing,
                                 cn.lineai.core.model.ProviderConfig provider) {
        return new ModelDetailPage(host.activity(), host.tokens(),
                host.graph().scheduler(), host.navigation()::back, existing, provider,
                new ModelDetailPage.Listener() {
                    public void onSave(cn.lineai.core.model.ModelConfig model) {
                        host.graph().models().save(model);
                        host.graph().chat().refreshModel();
                        host.navigation().back();
                    }
                });
    }

    /** Local GGUF model page from the optional bridge; falls back when absent. */
    private View localModelPage(cn.lineai.core.model.ModelConfig existing) {
        if (localModels == null) {
            host.unavailable();
            return providerListPage();
        }
        localPage = localModels.createPage(host.activity(), host.tokens(),
                host.navigation()::back, existing, new LocalModelBridge.Listener() {
                    public void onPickFile() { host.pickLocalModelFile(); }
                    public void onSave(cn.lineai.core.model.ModelConfig model) {
                        host.graph().models().save(model);
                        host.graph().models().select(model.id);
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                    }
                    public void onDelete(String id) {
                        host.graph().models().delete(java.util.Collections.singletonList(id));
                        host.graph().chat().refreshModel();
                        host.navigation().popTo(ModelScreens.LIST);
                    }
                });
        return localPage.view();
    }
}
