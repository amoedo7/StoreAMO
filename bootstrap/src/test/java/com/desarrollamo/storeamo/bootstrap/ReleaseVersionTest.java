package com.desarrollamo.storeamo.bootstrap;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReleaseVersionTest {
    @Test
    public void parsesCurrentFourPartTags() {
        ReleaseVersion version = ReleaseVersion.parseTag("v0.4.3.64");
        assertNotNull(version);
        assertEquals("0.4.3.64", version.text);
        assertEquals(64, version.build);
    }

    @Test
    public void futureStableVersionSortsAfterCurrentDebugLine() {
        ReleaseVersion current = ReleaseVersion.parseTag("v0.4.3.64");
        ReleaseVersion stable = ReleaseVersion.parseTag("v0.5.0.1");
        assertNotNull(current);
        assertNotNull(stable);
        assertTrue(stable.compareTo(current) > 0);
        assertTrue(stable.isAtLeast(0, 5, 0));
    }

    @Test
    public void supportsThreePartFutureTags() {
        ReleaseVersion version = ReleaseVersion.parseTag("v1.0.0");
        assertNotNull(version);
        assertEquals("1.0.0", version.text);
        assertTrue(version.isAtLeast(0, 5, 0));
    }

    @Test
    public void ignoresBootstrapAndInvalidTags() {
        assertNull(ReleaseVersion.parseTag("bootstrap"));
        assertNull(ReleaseVersion.parseTag("latest"));
        assertNull(ReleaseVersion.parseTag("v0.5.beta"));
    }
}
