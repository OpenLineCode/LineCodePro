package cn.lineai.feature.settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateCheckerTest {

    @Test
    public void parsesTheLatestReleasePayload() {
        ReleaseInfo release = UpdateChecker.parse(payload());
        assertNotNull(release);
        assertEquals("2.2.0", release.version);
        assertEquals("v2.2.0", release.tag);
        assertEquals("Providers, statistics and fixes.", release.body);
        assertEquals("https://github.com/LangLang03/LineCodePro/releases/tag/v2.2.0",
                release.htmlUrl);
    }

    @Test
    public void keepsAPreReleaseTagIntactInTheVersion() {
        ReleaseInfo release = UpdateChecker.parse(payload("v2.3.0-beta", "beta notes"));
        assertNotNull(release);
        assertEquals("2.3.0-beta", release.version);
        assertEquals("v2.3.0-beta", release.tag);
    }

    @Test
    public void normalisesAMissingBodyToEmptyText() {
        ReleaseInfo release = UpdateChecker.parse("{\"tag_name\":\" v2.2.0 \","
                + "\"html_url\":\" https://github.com/LangLang03/LineCodePro/releases/tag/v2.2.0 \"}");
        assertNotNull(release);
        assertEquals("v2.2.0", release.tag);
        assertEquals("2.2.0", release.version);
        assertEquals("", release.body);
        assertEquals("https://github.com/LangLang03/LineCodePro/releases/tag/v2.2.0",
                release.htmlUrl);
    }

    @Test
    public void rejectsPayloadsWithoutRequiredFields() {
        assertNull(UpdateChecker.parse(null));
        assertNull(UpdateChecker.parse(""));
        assertNull(UpdateChecker.parse("not json"));
        assertNull(UpdateChecker.parse("{}"));
        assertNull(UpdateChecker.parse("{\"tag_name\":\"v2.2.0\"}"));
        assertNull(UpdateChecker.parse("{\"html_url\":\"https://example.com\"}"));
    }

    private static String payload() {
        return payload("v2.2.0", "Providers, statistics and fixes.");
    }

    private static String payload(String tag, String body) {
        return "{\"tag_name\":\"" + tag + "\",\"name\":\"LineCode Pro " + tag
                + "\",\"body\":\"" + body + "\",\"draft\":false,\"prerelease\":false,"
                + "\"html_url\":\"https://github.com/LangLang03/LineCodePro/releases/tag/"
                + tag + "\"}";
    }
}
