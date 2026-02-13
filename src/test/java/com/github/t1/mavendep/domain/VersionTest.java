package com.github.t1.mavendep.domain;

import com.github.t1.mavendep.domain.Version.NumericPart;
import com.github.t1.mavendep.domain.Version.StringPart;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.sort;
import static org.assertj.core.api.BDDAssertions.then;

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
        then(version.part(2)).isEqualTo(new StringPart("RC"));
        then(version.part(3)).isEqualTo(new NumericPart(1));
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


    @Test
    void shouldParseFourPartVersionWithDotQualifier() {
        var version = Version.fromString("5.1.5.Final");

        then(version.major()).isEqualTo(5);
        then(version.minor()).isEqualTo(1);
        then(version.patch()).isEqualTo(5);
        then(version.part(3)).isEqualTo(new StringPart("Final"));
    }

    @Test
    void shouldIdentifyFinalQualifierAsReleased() {
        var version = Version.fromString("5.1.5.Final");

        then(version.isReleased()).isTrue();
    }

    @Test
    void shouldRoundTripFourPartVersionWithDotQualifier() {
        var original = "5.1.5.Final";

        var version = Version.fromString(original);

        then(version.toString()).isEqualTo(original);
    }

    @Test
    void shouldTreatVersionsWithTrailingZerosAsEqual() {
        thenEqual("1.0", "1.0.0");
    }

    @Test
    void shouldTreatSeparatorsAsInterchangeable() {
        thenEqual("1.2.3-Final", "1.2.3.Final");
    }

    static void thenEqual(String version, String version1) {
        var v1 = Version.fromString(version);
        var v2 = Version.fromString(version1);
        then(v1).isEqualTo(v2).hasSameHashCodeAs(v2);
    }

    @Test
    void shouldCompareStringPartsBeforeNumericParts() {
        var preRelease = Version.fromString("1.0.0.Final");
        var numeric = Version.fromString("1.0.1");

        then(preRelease).isLessThan(numeric);
    }

    @Test
    void shouldParseVersionIntoParts() {
        var version = Version.fromString("5.1.5.Final");

        then(version.parts()).containsExactly(
                new NumericPart(5), new NumericPart(1),
                new NumericPart(5), new StringPart("Final"));
    }

    @Test
    void shouldCompareA9AsLessThanA10() {
        var a9 = Version.fromString("1.0-a9");
        var a10 = Version.fromString("1.0-a10");

        then(a9).isLessThan(a10);
    }

    @Test
    void shouldParseLargeNumericVersionPart() {
        var version = Version.fromString("0.7.7.201606060606");

        then(version.parts()).containsExactly(
                new NumericPart(0), new NumericPart(7),
                new NumericPart(7), new NumericPart(new BigInteger("201606060606")));
    }

    @Test
    void shouldParseMixedHyphenAndDotVersionIntoParts() {
        var version = Version.fromString("16.0.SP02-1xxx2");

        then(version.parts()).containsExactly(
                new NumericPart(16), new NumericPart(0),
                new StringPart("SP"), new NumericPart(2),
                new NumericPart(1), new StringPart("xxx"), new NumericPart(2));
    }
}
