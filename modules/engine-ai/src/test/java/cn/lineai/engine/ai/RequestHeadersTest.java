package cn.lineai.engine.ai;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.model.HttpHeader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestHeadersTest {

    private Map<String, String> base() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer key");
        return headers;
    }

    @Test
    public void modelHeadersOverrideProviderHeaders() {
        Map<String, String> merged = RequestHeaders.merge(base(),
                Collections.singletonList(new HttpHeader("Authorization", "Bearer other")));
        assertEquals("Bearer other", merged.get("Authorization"));
        assertEquals("application/json", merged.get("Accept"));
    }

    @Test
    public void protectedTransportHeadersAreIgnored() {
        Map<String, String> merged = RequestHeaders.merge(base(), java.util.Arrays.asList(
                new HttpHeader("Content-Type", "text/plain"),
                new HttpHeader("content-length", "42"),
                new HttpHeader("Host", "example.com"),
                new HttpHeader("Connection", "close")));
        assertFalse(merged.containsKey("Content-Type"));
        assertFalse(merged.containsKey("content-length"));
        assertFalse(merged.containsKey("Host"));
        assertFalse(merged.containsKey("Connection"));
    }

    @Test
    public void blankAndNullHeadersAreSkipped() {
        Map<String, String> merged = RequestHeaders.merge(base(), java.util.Arrays.asList(
                null, new HttpHeader("", "value"), new HttpHeader("X-Trace", "abc")));
        assertEquals("abc", merged.get("X-Trace"));
        assertEquals(3, merged.size());
    }

    @Test
    public void missingModelHeadersLeaveTheBaseUntouched() {
        assertTrue(RequestHeaders.merge(base(), null).equals(base()));
        assertTrue(RequestHeaders.merge(base(), Collections.<HttpHeader>emptyList()).equals(base()));
    }
}
