package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One model provider endpoint: protocol, base URL, credential and shared request headers. */
public final class ProviderConfig {

    public final String id;
    public final String name;
    public final ModelConfig.Protocol protocol;
    public final String baseUrl;
    public final String apiKey;
    public final List<HttpHeader> headers;
    public final long createdAt;
    public final long updatedAt;

    /** Creates a configuration from builder state; strings and lists are defensively copied. */
    private ProviderConfig(Builder b) {
        this.id = b.id == null ? "" : b.id;
        this.name = b.name == null || b.name.trim().isEmpty() ? defaultName(b.baseUrl) : b.name.trim();
        this.protocol = b.protocol == null ? ModelConfig.Protocol.OPENAI_COMPATIBLE : b.protocol;
        this.baseUrl = b.baseUrl == null ? "" : b.baseUrl.trim();
        this.apiKey = b.apiKey == null ? "" : b.apiKey;
        this.headers = Collections.unmodifiableList(new ArrayList<>(b.headers));
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    /** Derives a readable provider name from a base URL when the user left the name blank. */
    private static String defaultName(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(value);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) return uri.getHost();
        } catch (java.net.URISyntaxException error) {
            // A malformed endpoint is reported by the form; the raw value stays the name.
            return value;
        }
        return value;
    }

    /** Returns a builder pre-filled with every field of this configuration. */
    public Builder toBuilder() {
        return builder()
                .id(id)
                .name(name)
                .protocol(protocol)
                .endpoint(baseUrl, apiKey)
                .headers(headers)
                .times(createdAt, updatedAt);
    }

    /** Returns a new empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ProviderConfig}. */
    public static final class Builder {
        private String id = "";
        private String name = "";
        private ModelConfig.Protocol protocol = ModelConfig.Protocol.OPENAI_COMPATIBLE;
        private String baseUrl = "";
        private String apiKey = "";
        private List<HttpHeader> headers = new ArrayList<>();
        private long createdAt;
        private long updatedAt;

        /**
         * Sets the identifier.
         * @param value identifier to assign
         * @return this builder
         */
        public Builder id(String value) {
            id = value == null ? "" : value;
            return this;
        }

        /**
         * Sets the display name.
         * @param value display name; blank falls back to the endpoint host
         * @return this builder
         */
        public Builder name(String value) {
            name = value == null ? "" : value;
            return this;
        }

        /**
         * Sets the wire protocol.
         * @param value protocol, null falls back to OpenAI-compatible
         * @return this builder
         */
        public Builder protocol(ModelConfig.Protocol value) {
            protocol = value;
            return this;
        }

        /**
         * Sets the endpoint and credential.
         * @param url API base URL
         * @param key API credential
         * @return this builder
         */
        public Builder endpoint(String url, String key) {
            baseUrl = url == null ? "" : url;
            apiKey = key == null ? "" : key;
            return this;
        }

        /**
         * Sets the headers sent with every model request of this provider.
         * @param value header list, null yields an empty list
         * @return this builder
         */
        public Builder headers(List<HttpHeader> value) {
            headers = value == null ? new ArrayList<HttpHeader>() : new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the lifecycle timestamps.
         * @param created creation timestamp in epoch milliseconds
         * @param updated last modification timestamp in epoch milliseconds
         * @return this builder
         */
        public Builder times(long created, long updated) {
            createdAt = created;
            updatedAt = updated;
            return this;
        }

        /**
         * Returns the immutable configuration accumulated by this builder.
         * @return a new {@link ProviderConfig}
         */
        public ProviderConfig build() {
            return new ProviderConfig(this);
        }
    }
}
