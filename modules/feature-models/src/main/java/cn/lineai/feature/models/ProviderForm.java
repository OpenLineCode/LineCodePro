package cn.lineai.feature.models;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;

/**
 * Pure editable state and validation of the provider form.
 *
 * <p>The class keeps plain strings so the editor can bind inputs directly without parsing on every
 * keystroke; parsing happens only when the page builds the configuration. It holds no Android
 * references, which keeps the validation rules testable on the JVM. A header row with a blank name
 * is dropped when the configuration is built, while the value of a named row is preserved
 * verbatim.</p>
 */
final class ProviderForm {

    /** Validation outcomes; the page maps each value to a localized message. */
    enum Error {
        /** Form is ready to save. */
        NONE,
        /** Display name is blank. */
        MISSING_NAME,
        /** Base URL is blank. */
        MISSING_BASE_URL,
        /** API key is blank. */
        MISSING_KEY
    }

    /** Protocols a stored provider may speak; on-device models do not use the provider form. */
    private static final ModelConfig.Protocol[] SUPPORTED = {
            ModelConfig.Protocol.OPENAI_COMPATIBLE,
            ModelConfig.Protocol.ANTHROPIC_MESSAGES,
            ModelConfig.Protocol.CODEX_RESPONSES
    };

    String id = "";
    String name = "";
    ModelConfig.Protocol protocol = ModelConfig.Protocol.OPENAI_COMPATIBLE;
    String baseUrl = "";
    String apiKey = "";
    List<HttpHeader> headers = new ArrayList<>();
    long createdAt;

    /** Not instantiable from outside; use {@link #blank()} or {@link #from(ProviderConfig)}. */
    private ProviderForm() {
    }

    /**
     * Returns the protocols offered by the protocol selector.
     *
     * @return a fresh array in display order
     */
    static ModelConfig.Protocol[] supportedProtocols() {
        return SUPPORTED.clone();
    }

    /**
     * Creates a blank form for a new provider.
     *
     * @return a form with the OpenAI-compatible protocol and every field empty
     */
    static ProviderForm blank() {
        return new ProviderForm();
    }

    /**
     * Copies an existing provider into editable form state.
     *
     * @param provider provider to copy; null yields a blank form
     * @return a new form carrying the provider's values
     */
    static ProviderForm from(ProviderConfig provider) {
        ProviderForm form = new ProviderForm();
        if (provider == null) return form;
        form.id = provider.id;
        form.name = provider.name;
        form.protocol = provider.protocol;
        form.baseUrl = provider.baseUrl;
        form.apiKey = provider.apiKey;
        form.headers = new ArrayList<>(provider.headers);
        form.createdAt = provider.createdAt;
        return form;
    }

    /**
     * Seeds the endpoint and protocol from a quick-setup preset.
     *
     * @param preset preset to apply; a null preset leaves the form untouched
     * @return this form, for fluent wiring in the page
     */
    ProviderForm applyPreset(cn.lineai.core.model.ModelPreset preset) {
        if (preset == null) return this;
        protocol = preset.protocol;
        baseUrl = preset.baseUrl;
        return this;
    }

    /**
     * Returns the trimmed display name.
     *
     * @return trimmed name, or an empty string when blank
     */
    String displayName() {
        return name == null ? "" : name.trim();
    }

    /**
     * Returns the trimmed base URL.
     *
     * @return trimmed URL, or an empty string when blank
     */
    String endpoint() {
        return baseUrl == null ? "" : baseUrl.trim();
    }

    /**
     * Returns the trimmed credential.
     *
     * @return trimmed key, or an empty string when blank
     */
    String credential() {
        return apiKey == null ? "" : apiKey.trim();
    }

    /**
     * Validates the fields the save path needs, in screen order so the first error matches the
     * topmost broken input.
     *
     * @return the first blocking error, or {@link Error#NONE}
     */
    Error validateForSave() {
        if (displayName().isEmpty()) return Error.MISSING_NAME;
        if (endpoint().isEmpty()) return Error.MISSING_BASE_URL;
        if (credential().isEmpty()) return Error.MISSING_KEY;
        return Error.NONE;
    }

    /**
     * Save gate mirroring {@link #validateForSave} so the action and the message never disagree.
     *
     * @return true when every required field is filled in
     */
    boolean canSave() {
        return validateForSave() == Error.NONE;
    }

    /**
     * Reports whether the active protocol may be changed by the user.
     *
     * @return true for the three remote protocols
     */
    boolean protocolSelectable() {
        return protocol != ModelConfig.Protocol.LOCAL_GGUF;
    }

    /**
     * Returns the non-blank header rows in the order the user entered them.
     *
     * @return a new list owning freshly created header values; never null
     */
    List<HttpHeader> normalizedHeaders() {
        List<HttpHeader> result = new ArrayList<>();
        if (headers == null) return result;
        for (HttpHeader header : headers) {
            if (header == null) continue;
            String headerName = header.name == null ? "" : header.name.trim();
            if (headerName.isEmpty()) continue;
            result.add(new HttpHeader(headerName, header.value == null ? "" : header.value.trim()));
        }
        return result;
    }

    /**
     * Builds the immutable provider configuration from the current fields.
     *
     * @param now creation timestamp used when the form is new; an existing provider keeps its
     *     original creation time
     * @param idFactory factory returning a fresh identifier for a new provider; ignored when the
     *     form already carries an id
     * @return a new {@link ProviderConfig}
     */
    ProviderConfig build(long now, java.util.function.Supplier<String> idFactory) {
        String providerId = id == null || id.isEmpty() ? idFactory.get() : id;
        long created = createdAt > 0 ? createdAt : now;
        return ProviderConfig.builder()
                .id(providerId)
                .name(displayName())
                .protocol(protocol)
                .endpoint(endpoint(), credential())
                .headers(normalizedHeaders())
                .times(created, now)
                .build();
    }

    /**
     * Builds the immutable provider configuration with the current wall clock.
     *
     * @param idFactory factory returning a fresh identifier for a new provider
     * @return a new {@link ProviderConfig}
     */
    ProviderConfig build(java.util.function.Supplier<String> idFactory) {
        return build(System.currentTimeMillis(), idFactory);
    }
}
