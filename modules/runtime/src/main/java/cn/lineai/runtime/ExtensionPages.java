package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.FailureReporter;
import cn.lineai.core.kernel.error.FailureText;
import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.engine.ai.ModelProbe;
import cn.lineai.engine.prompt.PromptId;
import cn.lineai.feature.extensions.AgentExtensionEditPage;
import cn.lineai.feature.extensions.AgentExtensionsPage;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.extensions.McpExtensionEditPage;
import cn.lineai.feature.extensions.McpExtensionsPage;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolKind;

/**
 * Agent and MCP extension screens.
 *
 * <p>Extracted from {@link FeaturePageFactory} so the AI authoring path can report failures into
 * the shared log without pushing that factory past its line budget. Discovery and drafting
 * failures reach the user as a toast; they are recorded here as well so they survive the toast.</p>
 */
final class ExtensionPages {

    private static final String DRAFT_TITLE = "Agent draft";

    private final PageHost host;

    /**
     * Creates the extension page builder.
     *
     * @param host page host supplying the activity, navigation and application graph
     */
    ExtensionPages(PageHost host) {
        this.host = host;
    }

    /** Extension agent list page wired to the agent extension store. */
    AgentExtensionsPage agentExtensionsPage() {
        return new AgentExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new AgentExtensionsPage.Listener() {
                    public List<AgentExtension> load() {
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
                    public void edit(AgentExtension agent) {
                        host.navigation().push(ExtensionScreens.AGENT_EDIT.key(
                                ExtensionScreens.agentArgs(agent.id)));
                    }
                });
    }

    /** Agent editor page; loads the edited agent and the phone-tool-free catalog. */
    AgentExtensionEditPage agentEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        AgentExtension editing = null;
        if (!id.isEmpty()) {
            for (AgentExtension agent : host.graph().agentExtensions().list()) {
                if (agent.id.equals(id)) { editing = agent; break; }
            }
        }
        final AgentExtension target = editing;
        List<Tool> agentTools = new ArrayList<>();
        for (Tool tool : host.graph().tools().registry().all()) {
            if (tool.kind() != ToolKind.PHONE) {
                agentTools.add(tool);
            }
        }
        return new AgentExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                host.graph().scheduler(), target, agentTools,
                new AgentExtensionEditPage.Listener() {
                    public void onSave(AgentExtension extension) {
                        host.graph().agentExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String agentId) {
                        host.graph().agentExtensions().delete(agentId);
                        host.navigation().back();
                    }
                    public AgentExtension generateDraft(String description) throws Exception {
                        return agentDraft(description);
                    }
                });
    }

    /**
     * Wires the selected model, the tool catalog and the prompt template into the use case.
     *
     * <p>A failed call and an answer without a usable draft are both recorded: the page only
     * shows a toast, so the log is the only place the cause survives.</p>
     */
    private AgentExtension agentDraft(String description) throws Exception {
        FailureReporter failures = host.graph().errorLogs();
        ModelConfig model = host.graph().models().selected();
        if (model == null) {
            failures.record(DRAFT_TITLE, "No model selected", description);
            throw new IllegalStateException("No model selected");
        }
        try {
            AgentExtension draft = AgentDrafts.generate(model,
                    host.graph().prompts().current(PromptId.AGENT_DRAFT),
                    host.graph().tools().registry().all(), description, new ModelProbe());
            if (draft == null) {
                failures.record(DRAFT_TITLE + " " + model.displayName(),
                        "Model returned no usable draft", description);
            }
            return draft;
        } catch (Exception error) {
            failures.record(DRAFT_TITLE + " " + model.displayName(),
                    FailureText.message(error), FailureText.stack(error));
            throw error;
        }
    }

    /** MCP server list page wired to the MCP extension store. */
    McpExtensionsPage mcpExtensionsPage() {
        return new McpExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                new McpExtensionsPage.Listener() {
                    public List<McpExtension> load() {
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
                    public void edit(McpExtension server) {
                        host.navigation().push(ExtensionScreens.MCP_EDIT.key(
                                ExtensionScreens.mcpArgs(server.id)));
                    }
                });
    }

    /** MCP server editor page; loads the edited server when the args carry an id. */
    McpExtensionEditPage mcpEditPage(Params.Args args) {
        String id = args.get("id") instanceof String ? (String) args.get("id") : "";
        McpExtension editing = null;
        if (!id.isEmpty()) {
            for (McpExtension server : host.graph().mcpExtensions().list()) {
                if (server.id.equals(id)) { editing = server; break; }
            }
        }
        return new McpExtensionEditPage(host.activity(), host.tokens(), host.navigation()::back,
                editing, host.graph().scheduler(), host.graph().errorLogs(),
                new McpExtensionEditPage.Listener() {
                    public void onSave(McpExtension extension) {
                        host.graph().mcpExtensions().save(extension);
                        host.navigation().back();
                    }
                    public void onDelete(String serverId) {
                        host.graph().mcpExtensions().delete(serverId);
                        host.navigation().back();
                    }
                });
    }
}
