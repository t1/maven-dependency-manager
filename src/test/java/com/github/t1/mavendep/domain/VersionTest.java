package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.sort;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class VersionTest {

    @Test
    void shouldParseVersion() {
        var version = Version.fromString("1.2.3");

        then(version.major()).isEqualTo(1);
        then(version.minor()).isEqualTo(2);
        then(version.patch()).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForEqualVersions() {
        var v1 = Version.fromString("1.2.3");
        var v2 = Version.fromString("1.2.3");

        then(v1).isEqualByComparingTo(v2);
    }

    @Test
    void shouldReturnNegativeWhenFirstVersionIsLess() {
        var v1 = Version.fromString("1.2.2");
        var v2 = Version.fromString("1.2.3");

        then(v1).isLessThan(v2);
    }

    @Test
    void shouldReturnPositiveWhenFirstVersionIsGreater() {
        var v1 = Version.fromString("2.0.0");
        var v2 = Version.fromString("1.9.9");

        then(v1).isGreaterThan(v2);
    }

    @Test
    void shouldCompareReleaseAsGreaterThanSnapshot() {
        var snapshot = Version.fromString("1.2.3-SNAPSHOT");
        var release = Version.fromString("1.2.3");

        then(release).isGreaterThan(snapshot);
    }

    @Test
    void shouldRecognizePatchUpdate() {
        var current = Version.fromString("1.2.3");
        var newer = Version.fromString("1.2.4");

        then(UpdateType.between(current, newer)).isEqualTo(UpdateType.patch);
    }

    @Test
    void shouldRecognizeMinorUpdate() {
        var current = Version.fromString("1.2.3");
        var newer = Version.fromString("1.3.0");

        then(UpdateType.between(current, newer)).isEqualTo(UpdateType.minor);
    }

    @Test
    void shouldRecognizeMajorUpdate() {
        var current = Version.fromString("1.2.3");
        var newer = Version.fromString("2.0.0");

        then(UpdateType.between(current, newer)).isEqualTo(UpdateType.major);
    }

    @Test
    void shouldRecognizeNoUpdateWhenVersionsAreEqual() {
        var current = Version.fromString("1.2.3");
        var same = Version.fromString("1.2.3");

        then(UpdateType.between(current, same)).isEqualTo(UpdateType.none);
    }

    @Test
    void shouldFormatVersionToString() {
        var version = Version.fromString("1.2.3");

        then(version.toString()).isEqualTo("1.2.3");
    }

    @Test
    void shouldFormatVersionWithQualifierToString() {
        var version = Version.fromString("1.2.3-SNAPSHOT");

        then(version.toString()).isEqualTo("1.2.3-SNAPSHOT");
    }

    @Test
    void shouldRoundTripVersionThroughString() {
        var original = "2.5.7-RC1";

        var version = Version.fromString(original);

        then(version.toString()).isEqualTo(original);
    }

    @Test
    void shouldIdentifyMilestoneVersions() {
        var m1 = Version.fromString("3.0.0-M1");
        var m2 = Version.fromString("3.0.0-M2");
        var lowercase = Version.fromString("3.0.0-m3");

        then(m1.isReleased()).isFalse();
        then(m2.isReleased()).isFalse();
        then(lowercase.isReleased()).isFalse();
    }

    @Test
    void shouldIdentifySnapshotVersionsAsNotReleased() {
        var snapshot = Version.fromString("3.0.0-SNAPSHOT");

        then(snapshot.isReleased()).isFalse();
    }

    @Test
    void shouldIdentifyRcVersionsAsNotReleased() {
        var rc = Version.fromString("3.0.0-RC1");

        then(rc.isReleased()).isFalse();
    }

    @Test
    void shouldIdentifyReleaseVersionsAsReleased() {
        var release = Version.fromString("3.0.0");

        then(release.isReleased()).isTrue();
    }

    @Test
    void shouldParseTwoPartVersion() {
        var version = Version.fromString("1.2");

        then(version.major()).isEqualTo(1);
        then(version.minor()).isEqualTo(2);
        then(version.patch()).isEqualTo(0);
    }

    @Test
    void shouldParseTwoPartVersionWithQualifier() {
        var version = Version.fromString("2.2-RC1");

        then(version.major()).isEqualTo(2);
        then(version.minor()).isEqualTo(2);
        then(version.patch()).isEqualTo(0);
        then(version.qualifier()).isEqualTo("RC1");
    }

    @Test
    void shouldParseSinglePartVersion() {
        var version = Version.fromString("1");

        then(version.major()).isEqualTo(1);
        then(version.minor()).isEqualTo(0);
        then(version.patch()).isEqualTo(0);
    }

    @Test
    void shouldIdentifyAlphaVersionsAsNotReleased() {
        var alpha = Version.fromString("2.0.0-alpha0");

        then(alpha.isReleased()).isFalse();
    }

    @Test
    void shouldIdentifyBetaVersionsAsNotReleased() {
        var beta = Version.fromString("2.0.0-beta1");

        then(beta.isReleased()).isFalse();
    }

    @Test
    void shouldSortVersionsInSemanticOrder() {
        var released1 = Version.fromString("1.0.0");
        var unreleased2 = Version.fromString("2.0.0-SNAPSHOT");
        var versions = new ArrayList<>(List.of(unreleased2, released1));

        sort(versions);

        then(versions).containsExactly(released1, unreleased2);
    }

    @Test
    void shouldSortSnapshotBeforeReleasedWithSameVersionNumber() {
        var released = Version.fromString("1.0.0");
        var unreleased = Version.fromString("1.0.0-SNAPSHOT");
        var versions = new ArrayList<>(List.of(released, unreleased));

        sort(versions);

        then(versions).containsExactly(unreleased, released);
    }

    @Test
    void shouldSortReleasedVersionsInSemanticOrder() {
        var v1 = Version.fromString("1.0.0");
        var v2 = Version.fromString("2.0.0");
        var v3 = Version.fromString("1.5.0");
        var v4 = Version.fromString("1.15.0");
        var versions = new ArrayList<>(List.of(v4, v2, v3, v1));

        sort(versions);

        then(versions).containsExactly(v1, v3, v4, v2);
    }

    @Test
    void shouldSortUnreleasedVersionsInSemanticOrder() {
        var v1 = Version.fromString("1.0.0-SNAPSHOT");
        var v2 = Version.fromString("2.0.0-SNAPSHOT");
        var v3 = Version.fromString("1.5.0-RC1");
        var versions = new ArrayList<>(List.of(v2, v3, v1));

        sort(versions);

        then(versions).containsExactly(v1, v3, v2);
    }

    @Test
    void shouldCompareReleasedVersionAsGreaterThanUnreleased() {
        var released = Version.fromString("1.0.0");
        var unreleased = Version.fromString("1.0.0-SNAPSHOT");

        then(released.compareTo(unreleased)).isPositive();
    }

    @Test
    void shouldCompareEqualVersionsAsEqual() {
        var v1 = Version.fromString("1.2.3");
        var v2 = Version.fromString("1.2.3");

        then(v1.compareTo(v2)).isZero();
    }

    @Test
    void shouldCompareQualifiersLexicographically() {
        var alpha = Version.fromString("3.0.0-alpha1");
        var beta = Version.fromString("3.0.0-beta1");

        then(beta.compareTo(alpha)).isPositive();
        then(alpha.compareTo(beta)).isNegative();
    }

    @Test
    void shouldCompareRcAsGreaterThanBeta() {
        var beta = Version.fromString("3.0.0-beta1");
        var rc = Version.fromString("3.0.0-rc1");

        then(rc.compareTo(beta)).isPositive();
    }

    @Test
    void shouldCompareBetaAsGreaterThanAlpha() {
        var alpha = Version.fromString("3.0.0-alpha1");
        var beta = Version.fromString("3.0.0-beta1");

        then(beta.compareTo(alpha)).isPositive();
    }

    @Test
    void shouldCompareAlphaAsGreaterThanSnapshot() {
        var snapshot = Version.fromString("3.0.0-SNAPSHOT");
        var alpha = Version.fromString("3.0.0-alpha1");

        then(alpha.compareTo(snapshot)).isPositive();
    }

    // Test list for version parsing robustness
    @Test
    void shouldThrowExceptionForInvalidVersionString() {
        thenThrownBy(() -> Version.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void shouldThrowExceptionForNonNumericMinor() {
        thenThrownBy(() -> Version.fromString("1.x.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.x.3");
    }

    @Test
    void shouldThrowExceptionForNonNumericPatch() {
        thenThrownBy(() -> Version.fromString("1.2.x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.2.x");
    }

    @Test
    void shouldParseTwoPartVersionWithComplexQualifier() {
        var version = Version.fromString("16.0.SP02-1xxx2");

        then(version.major()).isEqualTo(16);
        then(version.minor()).isEqualTo(0);
        then(version.patch()).isEqualTo(0);
        then(version.qualifier()).isEqualTo("SP02-1xxx2");
    }
}
