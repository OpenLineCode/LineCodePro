package cn.lineai.engine.ai;

import java.util.List;

import cn.lineai.core.model.McpToolInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class McpClientTest {

    @Test public void parsesToolsFromJsonRpcResponse() {
        List<McpToolInfo> tools = McpClient.parseTools(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                        + "{\"name\":\"search\",\"description\":\"Search the web\","
                        + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"
                        + "{\"description\":\"missing name\"}]}}");
        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name);
        assertTrue(tools.get(0).enabled);
        assertEquals("Search the web", tools.get(0).description);
        assertTrue(tools.get(0).inputSchemaJson.contains("\"object\""));
    }

    @Test public void parsesToolsFromEventStream() {
        String body = "event: message\n"
                + "data: {\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"ping\"}]}}\n\n";
        assertEquals("ping", McpClient.parseTools(body).get(0).name);
    }

    @Test public void toolResultTextJoinsContentBlocks() {
        String text = McpClient.toolResultText(
                "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"one\"},"
                        + "{\"type\":\"text\",\"text\":\"two\"}]}}");
        assertEquals("one\ntwo", text);
    }

    @Test public void normalizesUrlsAndRejectsRemoteCleartext() {
        assertEquals("https://example.com/mcp",
                McpClient.normalizeUrl("https://example.com/mcp/"));
        assertEquals("http://127.0.0.1:3000/mcp",
                McpClient.normalizeUrl("http://127.0.0.1:3000/mcp"));
        assertEquals("https://example.com/mcp",
                McpClient.normalizeUrl("  https://example.com/mcp/  "));
        try {
            McpClient.normalizeUrl("http://example.com/mcp");
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            // remote cleartext is not allowed
        }
    }

    @Test public void allowsCleartextLanAndPrivateHosts() {
        assertEquals("http://192.168.1.7:8788/mcp",
                McpClient.normalizeUrl("http://192.168.1.7:8788/mcp"));
        assertEquals("http://10.0.2.2:11434/v1",
                McpClient.normalizeUrl("http://10.0.2.2:11434/v1"));
        assertEquals("http://172.16.5.4:8080/mcp",
                McpClient.normalizeUrl("http://172.16.5.4:8080/mcp"));
        assertEquals("http://172.31.255.254/mcp",
                McpClient.normalizeUrl("http://172.31.255.254/mcp"));
        assertEquals("http://169.254.10.20/mcp",
                McpClient.normalizeUrl("http://169.254.10.20/mcp"));
        assertEquals("http://localhost:8788/mcp",
                McpClient.normalizeUrl("http://localhost:8788/mcp"));
        assertEquals("http://mcp-server.local:8788/mcp",
                McpClient.normalizeUrl("http://mcp-server.local:8788/mcp"));
        assertEquals("http://[fe80::1]:8788/mcp",
                McpClient.normalizeUrl("http://[fe80::1]:8788/mcp"));
        assertEquals("http://[fd00::12]:8788/mcp",
                McpClient.normalizeUrl("http://[fd00::12]:8788/mcp"));
    }

    @Test public void rejectsCleartextLookalikesAndPublicAddresses() {
        String[] rejected = {
                "http://example.com/mcp",
                "http://8.8.8.8/mcp",
                "http://172.32.0.1/mcp",
                "http://192.169.1.1/mcp",
                "http://localhost.example.com/mcp",
                "http://127.0.0.1.example.com/mcp",
                "http://192.168.1.7.example.com/mcp",
                "ftp://192.168.1.7/mcp",
                "http:///mcp",
                "not a url",
        };
        for (String raw : rejected) {
            try {
                McpClient.normalizeUrl(raw);
                throw new AssertionError("expected rejection: " + raw);
            } catch (IllegalArgumentException expected) {
                // public cleartext, look-alike hosts and malformed URLs stay rejected
            }
        }
    }
}
