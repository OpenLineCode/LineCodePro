package cn.lineai.feature.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionNumbersTest {

    @Test
    public void treatsMissingSegmentsAndTheTagMarkerAsEqual() {
        assertEquals(0, VersionNumbers.compare("v2.2.0", "2.2.0"));
        assertEquals(0, VersionNumbers.compare("2.2", "2.2.0"));
        assertEquals(0, VersionNumbers.compare("2", "2.0.0"));
        assertEquals(0, VersionNumbers.compare(" 2.2.0 ", "V2.2.0"));
        assertEquals(0, VersionNumbers.compare(null, "0.0.0"));
        assertEquals(0, VersionNumbers.compare("", "0.0.0"));
        assertEquals(0, VersionNumbers.compare("garbage", "0.0.0"));
    }

    @Test
    public void comparesEveryNumericSegment() {
        assertTrue(VersionNumbers.isNewer("2.2.1", "2.2.0"));
        assertTrue(VersionNumbers.isNewer("2.10.0", "2.9.9"));
        assertTrue(VersionNumbers.isNewer("10.0.0", "9.9.9"));
        assertTrue(VersionNumbers.isNewer("v2.3.0", "2.2.0"));
        assertFalse(VersionNumbers.isNewer("2.2.0", "2.2.0"));
        assertFalse(VersionNumbers.isNewer("2.1.9", "2.2.0"));
        assertFalse(VersionNumbers.isNewer("1.99.99", "2.0.0"));
    }

    @Test
    public void ranksPreReleasesBelowTheRelease() {
        assertTrue(VersionNumbers.compare("2.2.0-beta", "2.2.0") < 0);
        assertTrue(VersionNumbers.compare("2.2.0", "2.2.0-beta") > 0);
        assertTrue(VersionNumbers.compare("2.2.0-rc1", "2.2.0") < 0);
        assertTrue(VersionNumbers.isNewer("2.2.0", "2.2.0-beta"));
        assertFalse(VersionNumbers.isNewer("2.2.0-beta", "2.2.0"));
        assertTrue(VersionNumbers.isNewer("2.2.1-beta", "2.2.0"));
    }

    @Test
    public void ordersPreReleaseSuffixesTextually() {
        assertTrue(VersionNumbers.compare("2.2.0-beta.2", "2.2.0-beta.1") > 0);
        assertTrue(VersionNumbers.compare("2.2.0-alpha", "2.2.0-beta") < 0);
    }

    @Test
    public void ignoresBuildMetadata() {
        assertEquals(0, VersionNumbers.compare("2.2.0+build.7", "2.2.0"));
        assertTrue(VersionNumbers.isNewer("2.2.1+9", "2.2.0"));
    }

    @Test
    public void parsesSegmentsWithTrailingText() {
        assertEquals(0, VersionNumbers.compare("2.2.0 (52)", "2.2.0"));
        assertTrue(VersionNumbers.isNewer("2.2.0rc1", "2.1.9"));
    }
}
