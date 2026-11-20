package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Normalisation and validation rules of a model provider endpoint. */
public class EndpointUrlTest {

    @Test public void fillsInAMissingScheme() {
        assertEquals("https://api.example.com/v1",
                EndpointUrl.normalize("api.example.com/v1"));
        assertEquals("https://api.example.com/v1",
                EndpointUrl.normalize("  api.example.com/v1  "));
        assertEquals("https://api.example.com/v1",
                EndpointUrl.normalize("//api.example.com/v1"));
        assertEquals("http://127.0.0.1:11434/v1",
                EndpointUrl.normalize("127.0.0.1:11434/v1"));
        assertEquals("http://localhost:8080/v1",
                EndpointUrl.normalize("localhost:8080/v1"));
        assertEquals("http://10.0.2.2:11434/v1",
                EndpointUrl.normalize("10.0.2.2:11434/v1"));
        assertEquals("http://192.168.1.10:11434/v1",
                EndpointUrl.normalize("192.168.1.10:11434/v1"));
    }

    @Test public void keepsAnExplicitScheme() {
        assertEquals("http://192.168.1.10:8080/v1",
                EndpointUrl.normalize("http://192.168.1.10:8080/v1"));
        assertEquals("HTTPS://api.example.com/v1",
                EndpointUrl.normalize("HTTPS://api.example.com/v1"));
    }

    @Test public void foldsFullWidthAndInvisibleCharacters() {
        assertEquals("https://api.example.com/v1", EndpointUrl.normalize(
                "\uff48\uff54\uff54\uff50\uff53\uff1a\uff0f\uff0fapi.example.com/v1"));
        assertEquals("https://api.example.com/v1", EndpointUrl.normalize(
                "\ufeffhttps://api.example.com/v1\u200b"));
        assertEquals("https://api.example.com/v1", EndpointUrl.normalize(
                "\u3000https://api.example.com/v1\u3000"));
        assertEquals(EndpointUrl.Problem.MALFORMED,
                EndpointUrl.problem("https://api.example.com\u3000/v1"));
    }

    @Test public void dropsTrailingSlashesButKeepsTheSeparator() {
        assertEquals("https://api.example.com/v1",
                EndpointUrl.normalize("https://api.example.com/v1///"));
        assertEquals("https://api.example.com", EndpointUrl.normalize("https://api.example.com/"));
        assertEquals("https://", EndpointUrl.normalize("https://"));
    }

    @Test public void classifiesUnusableEndpoints() {
        assertEquals(EndpointUrl.Problem.EMPTY, EndpointUrl.problem(null));
        assertEquals(EndpointUrl.Problem.EMPTY, EndpointUrl.problem("   "));
        assertEquals(EndpointUrl.Problem.MALFORMED,
                EndpointUrl.problem("https://api.example.com/v 1"));
        assertEquals(EndpointUrl.Problem.MALFORMED,
                EndpointUrl.problem("https://api.example.com/v\n1"));
        assertEquals(EndpointUrl.Problem.UNSUPPORTED_SCHEME,
                EndpointUrl.problem("ftp://api.example.com/v1"));
        assertEquals(EndpointUrl.Problem.CLEARTEXT,
                EndpointUrl.problem("http://api.example.com/v1"));
        assertEquals(EndpointUrl.Problem.CLEARTEXT,
                EndpointUrl.problem("http://localhost.example.com/v1"));
    }

    @Test public void acceptsUsableEndpoints() {
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("https://api.example.com/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("http://localhost:8080/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("http://[::1]:8080/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("http://10.0.2.2:11434/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("api.example.com/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("http://192.168.1.10:11434/v1"));
        assertEquals(EndpointUrl.Problem.NONE, EndpointUrl.problem("http://dev-machine.local/v1"));
    }

    @Test public void httpAllowanceAdmitsPublicCleartextHosts() {
        assertEquals(EndpointUrl.Problem.NONE,
                EndpointUrl.problem("http://api.example.com/v1", true));
        assertEquals(EndpointUrl.Problem.NONE,
                EndpointUrl.problem("http://192.168.1.10/v1", true));
        assertEquals(EndpointUrl.Problem.UNSUPPORTED_SCHEME,
                EndpointUrl.problem("ftp://api.example.com/v1", true));
        assertEquals(EndpointUrl.Problem.MALFORMED,
                EndpointUrl.problem("https://api.example.com/v 1", true));
    }

    @Test public void recognisesLocalAndLanHosts() {
        assertTrue(EndpointUrl.isLocalHost("localhost"));
        assertTrue(EndpointUrl.isLocalHost("127.0.0.1"));
        assertTrue(EndpointUrl.isLocalHost("[::1]"));
        assertTrue(EndpointUrl.isLocalHost("10.0.2.2"));
        assertTrue(EndpointUrl.isLocalHost("192.168.1.10"));
        assertTrue(EndpointUrl.isLocalHost("10.1.2.3"));
        assertTrue(EndpointUrl.isLocalHost("172.20.0.1"));
        assertTrue(EndpointUrl.isLocalHost("169.254.1.1"));
        assertTrue(EndpointUrl.isLocalHost("dev-machine.local"));
        assertFalse(EndpointUrl.isLocalHost("api.example.com"));
        assertFalse(EndpointUrl.isLocalHost("localhost.example.com"));
        assertFalse(EndpointUrl.isLocalHost(""));
    }

    @Test public void describeNamesTheProblemWithoutLeakingCredentials() {
        String detail = EndpointUrl.describe(EndpointUrl.Problem.CLEARTEXT,
                "http://user:secret@api.example.com/v1?key=abc");
        assertTrue(detail.contains("plain http"));
        assertTrue(detail.contains("http://api.example.com/v1"));
        assertTrue(detail.contains("Security settings"));
        assertFalse(detail.contains("secret"));
        assertFalse(detail.contains("key=abc"));

        assertTrue(EndpointUrl.describe(EndpointUrl.Problem.EMPTY, "")
                .contains("missing base URL"));
        assertTrue(EndpointUrl.describe(EndpointUrl.Problem.UNSUPPORTED_SCHEME, "ftp://host/v1")
                .contains("http:// or https://"));
    }
}
