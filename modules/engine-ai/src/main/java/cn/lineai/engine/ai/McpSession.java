package cn.lineai.engine.ai;

/**
 * Negotiated MCP session.
 *
 * <p>The identifier is issued by the server in the {@code Mcp-Session-Id} response header during
 * initialization and must accompany every later request. Servers implementing the stateless
 * profile issue no identifier and answer no handshake, so a session can instead be a marker that
 * carries the protocol revision in request metadata.</p>
 */
final class McpSession {

    /** Server-issued session identifier, empty when the server issued none. */
    final String id;

    /** Protocol revision in effect for later requests. */
    final String protocolVersion;

    /** Whether the endpoint uses the stateless profile rather than a session handshake. */
    final boolean stateless;

    /**
     * Creates a session-bound session.
     *
     * @param id server-issued identifier; null is treated as empty
     * @param protocolVersion negotiated revision; null is treated as empty
     */
    McpSession(String id, String protocolVersion) {
        this(id, protocolVersion, false);
    }

    /**
     * Creates a session.
     *
     * @param id server-issued identifier; null is treated as empty
     * @param protocolVersion negotiated revision; null is treated as empty
     * @param stateless whether the endpoint uses the stateless profile
     */
    McpSession(String id, String protocolVersion, boolean stateless) {
        this.id = id == null ? "" : id;
        this.protocolVersion = protocolVersion == null ? "" : protocolVersion;
        this.stateless = stateless;
    }

    /** Creates the marker used for endpoints that answer no handshake. */
    static McpSession stateless(String protocolVersion) {
        return new McpSession("", protocolVersion, true);
    }

    /** Reports whether the server issued a session identifier. */
    boolean hasId() {
        return !id.isEmpty();
    }

    /** Reports whether a protocol revision was negotiated. */
    boolean hasProtocolVersion() {
        return !protocolVersion.isEmpty();
    }
}

