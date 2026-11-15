package cn.lineai.feature.models;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

/**
 * Screen identifiers of the model-management flow and their argument helpers.
 *
 * <p>The flow is a back-stack tree rooted at the provider list: a provider opens its model list,
 * and a model opens its detail page. Provider editing is reached from either the root list (add
 * or long-press edit) or the provider model list (add models of that provider), so it hangs off
 * the root instead of a single parent. Local models keep their own screen and are attached to the
 * root. Registration with the {@link ScreenRegistry} is the module's only wiring entry point;
 * screens load their data on the io scheduler and render on the main thread.</p>
 */
public final class ModelScreens {
    /** Root screen: the provider list, with no arguments. */
    public static final ScreenId<UiState, Params.Empty> LIST =
            new ScreenId<>("models", null, true, Params.Empty::fromArgs);
    /** Model list of one provider; arguments carry the provider {@code id}. */
    public static final ScreenId<UiState, Params.Args> PROVIDER_MODELS =
            new ScreenId<>("models.provider", LIST, true, args -> args);
    /** Provider editor; empty arguments add a provider, an {@code id} argument edits one. */
    public static final ScreenId<UiState, Params.Args> PROVIDER_EDIT =
            new ScreenId<>("models.provider.edit", LIST, true, args -> args);
    /** Model detail page; arguments carry the model {@code id}. */
    public static final ScreenId<UiState, Params.Args> MODEL_EDIT =
            new ScreenId<>("models.model.edit", PROVIDER_MODELS, true, args -> args);
    /** On-device model screen attached to the root list; carries the edited model {@code id}. */
    public static final ScreenId<UiState, Params.Args> LOCAL =
            new ScreenId<>("models.local", LIST, true, args -> args);

    /** Argument key holding the provider or model identifier. */
    private static final String ARG_ID = "id";

    /** Not instantiable; the registry wiring is static. */
    private ModelScreens() {
    }

    /**
     * Registers every model screen with the navigation registry.
     *
     * @param registry registry owned by the navigation host
     */
    public static void register(ScreenRegistry registry) {
        registry.register(LIST).register(PROVIDER_MODELS).register(PROVIDER_EDIT)
                .register(MODEL_EDIT).register(LOCAL);
    }

    /**
     * Builds provider-model arguments carrying a provider id.
     *
     * @param providerId provider identifier; null or blank yields empty arguments
     * @return arguments with the {@code id} key
     */
    public static Params.Args providerArgs(String providerId) {
        return providerId == null || providerId.trim().isEmpty()
                ? Params.Args.empty() : Params.Args.of(ARG_ID, providerId.trim());
    }

    /**
     * Builds provider-editor arguments; empty arguments open the add-provider form.
     *
     * @param providerId provider to edit, or null to add a new one
     * @return arguments with the {@code id} key, or empty when adding
     */
    public static Params.Args providerEditArgs(String providerId) {
        return providerArgs(providerId);
    }

    /**
     * Builds model-detail arguments carrying a model id.
     *
     * @param modelId model configuration identifier; null or blank yields empty arguments
     * @return arguments with the {@code id} key
     */
    public static Params.Args modelArgs(String modelId) {
        return modelId == null || modelId.trim().isEmpty()
                ? Params.Args.empty() : Params.Args.of(ARG_ID, modelId.trim());
    }

    /**
     * Builds local-model arguments carrying the edited model id.
     *
     * @param modelId local model configuration identifier, or null for a new model
     * @return arguments with the {@code id} key, or empty for a new model
     */
    public static Params.Args localArgs(String modelId) {
        return modelArgs(modelId);
    }

    /**
     * Reads the identifier carried by a screen key.
     *
     * @param args serialized arguments of the screen key; never null
     * @return the {@code id} argument, or an empty string when absent
     */
    public static String idOf(Params.Args args) {
        Object value = args == null ? null : args.get(ARG_ID);
        return value instanceof String ? (String) value : "";
    }
}
