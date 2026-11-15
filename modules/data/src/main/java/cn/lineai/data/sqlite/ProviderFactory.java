package cn.lineai.data.sqlite;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;

/**
 * Builds provider configurations from the legacy endpoint fields of a model.
 *
 * <p>Used by the implicit-provider save path and by the one-shot backfill that groups legacy model
 * rows. The name rule is identical in both: a non-empty provider label, else the base URL host,
 * else a generic fallback so a row always has a display name.</p>
 */
final class ProviderFactory {

    /** Display name used when neither a label nor a URL host is available. */
    static final String FALLBACK_NAME = "Provider";

    private ProviderFactory() {
    }

    /**
     * Builds a provider from a model's endpoint fields.
     *
     * @param model source model; must not be null
     * @param id identifier to assign to the provider
     * @return provider configuration carrying the model's protocol, URL and credential
     */
    static ProviderConfig fromModel(ModelConfig model, String id) {
        return ProviderConfig.builder()
                .id(id)
                .name(displayName(model.providerLabel, model.baseUrl))
                .protocol(model.protocol)
                .endpoint(model.baseUrl, model.apiKey)
                .build();
    }

    /**
     * Derives a provider display name from a label or a base URL.
     *
     * @param providerLabel user-assigned label, may be null or blank
     * @param baseUrl endpoint base URL, may be null or blank
     * @return the label, else the URL host, else {@link #FALLBACK_NAME}
     */
    static String displayName(String providerLabel, String baseUrl) {
        if (providerLabel != null && !providerLabel.trim().isEmpty()) return providerLabel.trim();
        String host = host(baseUrl);
        return host.isEmpty() ? FALLBACK_NAME : host;
    }

    /** Extracts the URI host; blank or malformed URLs yield an empty string. */
    private static String host(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(value);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (java.net.URISyntaxException error) {
            return "";
        }
    }
}
