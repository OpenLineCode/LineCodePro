package cn.lineai.core.model;

/** One custom HTTP header sent with provider, model, or MCP requests. */
public final class HttpHeader {

    public final String name;
    public final String value;

    /**
     * Creates a header with both values trimmed.
     * @param name header name, null becomes empty
     * @param value header value, null becomes empty
     */
    public HttpHeader(String name, String value) {
        this.name = name == null ? "" : name.trim();
        this.value = value == null ? "" : value.trim();
    }

    /** Headers are equal when both the name and the value match. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HttpHeader)) {
            return false;
        }
        HttpHeader header = (HttpHeader) other;
        return name.equals(header.name) && value.equals(header.value);
    }

    /** Hashes the name and the value. */
    @Override
    public int hashCode() {
        return 31 * name.hashCode() + value.hashCode();
    }
}
