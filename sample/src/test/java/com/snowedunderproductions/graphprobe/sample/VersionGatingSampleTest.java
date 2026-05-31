package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.snowedunderproductions.graphprobe.annotations.EnabledForVersion;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates {@link EnabledForVersion} gating tests on the running system
 * version. The current version is taken from the {@code currentVersion} system
 * property (default {@code "0.0.0"}); run with {@code -DcurrentVersion=2.0.0} to
 * see the second test become enabled.
 *
 * <p>The skipped test deliberately calls {@code fail(...)}: if the gating ever
 * stopped working and the method ran, the build would fail — so a green build
 * proves the method was genuinely skipped.
 *
 * @author Paul Snow
 */
class VersionGatingSampleTest {

    @Test
    @EnabledForVersion(minimumVersion = "0.0.0")
    void runsWhenCurrentVersionMeetsMinimum() {
        assertThat(true).isTrue();
    }

    @Test
    @EnabledForVersion(minimumVersion = "9.9.9")
    void skippedWhenCurrentVersionBelowMinimum() {
        fail("Should have been skipped: default currentVersion 0.0.0 is below 9.9.9");
    }
}
