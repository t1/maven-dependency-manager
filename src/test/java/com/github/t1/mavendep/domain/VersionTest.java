package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.sort;
import static org.assertj.core.api.BDDAssertions.then;

class VersionTest {

    @Nested
    class Parsing {
        @Test
        void shouldReturnNullForNullInput() {
            then(Version.fromString(null)).isNull();
        }

        @Test
        void shouldParseThreePartVersion() {
            var version = Version.fromString("1.2.3");

            then(version.major()).isEqualTo(1);
            then(version.minor()).isEqualTo(2);
            then(version.patch()).isEqualTo(3);
        }

        @Test
        void shouldParseTwoPartVersion() {
            var version = Version.fromString("1.2");

            then(version.major()).isEqualTo(1);
            then(version.minor()).isEqualTo(2);
            then(version.patch()).isEqualTo(0);
        }

        @Test
        void shouldParseSinglePartVersion() {
            var version = Version.fromString("1");

            then(version.major()).isEqualTo(1);
            then(version.minor()).isEqualTo(0);
            then(version.patch()).isEqualTo(0);
        }

        @Test
        void shouldParseTwoPartVersionWithQualifier() {
            var version = Version.fromString("2.2-RC1");

            then(version.major()).isEqualTo(2);
            then(version.minor()).isEqualTo(2);
            then(version.patch()).isEqualTo(0);
            then(version.qualifier().orElseThrow()).isEqualTo("RC1");
        }

        @Test
        void shouldParseFourPartVersionWithDotQualifier() {
            var version = Version.fromString("5.1.5.Final");

            then(version.major()).isEqualTo(5);
            then(version.minor()).isEqualTo(1);
            then(version.patch()).isEqualTo(5);
            then(version.qualifier().orElseThrow()).isEqualTo("Final");
        }

        @Test
        void shouldParseLargeNumericVersionPartAsQualifier() {
            var version = Version.fromString("0.7.7.201606060606");

            then(version.major()).isEqualTo(0);
            then(version.minor()).isEqualTo(7);
            then(version.patch()).isEqualTo(7);
            then(version.qualifier().orElseThrow()).isEqualTo("201606060606");
        }

        @Test
        void shouldParseMixedHyphenAndDotVersionIntoParts() {
            var version = Version.fromString("16.0.SP02-1xxx2");

            then(version.major()).isEqualTo(16);
            then(version.minor()).isEqualTo(0);
            then(version.patch()).isEqualTo(0);
            then(version.qualifier().orElseThrow()).isEqualTo("SP02-1xxx2");
        }

        @Test
        void shouldReturnEmptyQualifierForPlainVersion() {
            var version = Version.fromString("1.2.3");

            then(version.qualifier()).isEmpty();
        }
    }

    @Nested
    class IsReleased {
        @Test
        void shouldIdentifyPlainVersionAsReleased() {
            then(Version.fromString("3.0.0").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifyNumericQualifierAsReleased() {
            then(Version.fromString("0.7.7.201606060606").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifyFinalQualifierAsReleased() {
            then(Version.fromString("5.1.5.Final").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifyGaQualifierAsReleased() {
            then(Version.fromString("3.0.0-GA").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifyReleaseQualifierAsReleased() {
            then(Version.fromString("4.3.0.RELEASE").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifySpQualifierAsReleased() {
            then(Version.fromString("16.0-SP1").isReleased("test")).isTrue();
        }

        @Test
        void shouldIdentifySnapshotAsNotReleased() {
            then(Version.fromString("3.0.0-SNAPSHOT").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyAlphaAsNotReleased() {
            then(Version.fromString("2.0.0-alpha0").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyBetaAsNotReleased() {
            then(Version.fromString("2.0.0-beta1").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyRcAsNotReleased() {
            then(Version.fromString("3.0.0-RC1").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyCrAsNotReleased() {
            then(Version.fromString("3.0.0-CR1").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyMilestoneAsNotReleased() {
            then(Version.fromString("3.0.0-M1").isReleased("test")).isFalse();
            then(Version.fromString("3.0.0-M2").isReleased("test")).isFalse();
            then(Version.fromString("3.0.0-m3").isReleased("test")).isFalse();
        }

        @Test
        void shouldIdentifyOtherPreReleaseQualifiersAsNotReleased() {
            then(Version.fromString("2.0.0-preview1").isReleased("test")).isFalse();
            then(Version.fromString("2.0.0-dev1").isReleased("test")).isFalse();
            then(Version.fromString("2.0.0-incubating").isReleased("test")).isFalse();
            then(Version.fromString("2.0.0-pr1").isReleased("test")).isFalse();
        }

        @Test
        void shouldIncludeContextInUnknownQualifierWarning() {
            var version = Version.fromString("1.0.0-unknownQualifier");
            var stderr = new ByteArrayOutputStream();
            var originalErr = System.err;
            System.setErr(new PrintStream(stderr));
            try {

                var released = version.isReleased("com.example:my-artifact");

                then(released).isFalse();
                then(stderr.toString()).contains("com.example:my-artifact");
            } finally {
                System.setErr(originalErr);
            }
        }

        @Test
        void shouldHandleNullContext() {
            var version = Version.fromString("1.0.0-unknownQualifier");
            var stderr = new ByteArrayOutputStream();
            var originalErr = System.err;
            System.setErr(new PrintStream(stderr));
            try {

                var released = version.isReleased(null);

                then(released).isFalse();
                then(stderr.toString()).doesNotContain("[");
            } finally {
                System.setErr(originalErr);
            }
        }
    }

    @Nested
    class Comparison {
        @Test
        void shouldCompareEqualVersionsAsEqual() {
            var v1 = Version.fromString("1.2.3");
            var v2 = Version.fromString("1.2.3");

            then(v1).isEqualByComparingTo(v2);
        }

        @Test
        void shouldCompareLesserVersionAsLess() {
            var v1 = Version.fromString("1.2.2");
            var v2 = Version.fromString("1.2.3");

            then(v1).isLessThan(v2);
        }

        @Test
        void shouldCompareGreaterVersionAsGreater() {
            var v1 = Version.fromString("2.0.0");
            var v2 = Version.fromString("1.9.9");

            then(v1).isGreaterThan(v2);
        }

        @Test
        void shouldCompareShorterVersionAsLessWhenPrefixMatches() {
            var shorter = Version.fromString("1.0");
            var longer = Version.fromString("1.0.1");

            then(shorter).isLessThan(longer);
        }

        @Test
        void shouldCompareReleaseAsGreaterThanSnapshot() {
            var snapshot = Version.fromString("1.2.3-SNAPSHOT");
            var release = Version.fromString("1.2.3");

            then(release).isGreaterThan(snapshot);
        }

        @Test
        void shouldCompareAlphaAsLessThanBeta() {
            then(Version.fromString("3.0.0-beta1")).isGreaterThan(Version.fromString("3.0.0-alpha1"));
        }

        @Test
        void shouldCompareBetaAsLessThanMilestone() {
            then(Version.fromString("3.0.0-M1")).isGreaterThan(Version.fromString("3.0.0-beta1"));
        }

        @Test
        void shouldCompareMilestoneAsLessThanRc() {
            then(Version.fromString("3.0.0-rc1")).isGreaterThan(Version.fromString("3.0.0-M1"));
        }

        @Test
        void shouldCompareRcAsLessThanSnapshot() {
            then(Version.fromString("3.0.0-SNAPSHOT")).isGreaterThan(Version.fromString("3.0.0-rc1"));
        }

        @Test
        void shouldCompareFinalQualifierAsGreaterThanSnapshot() {
            var snapshot = Version.fromString("1.0.0-SNAPSHOT");
            var finalVersion = Version.fromString("1.0.0-Final");

            then(finalVersion).isGreaterThan(snapshot);
        }

        @Test
        void shouldCompareReleaseQualifierAsGreaterThanUnknownQualifier() {
            var unknown = Version.fromString("1.0.0-whatever");
            var release = Version.fromString("1.0.0-Final");

            then(release).isGreaterThan(unknown);
        }

        @Test
        void shouldCompareStringPartsBeforeNumericParts() {
            var preRelease = Version.fromString("1.0.0.Final");
            var numeric = Version.fromString("1.0.1");

            then(preRelease).isLessThan(numeric);
        }

        @Test
        void shouldCompareA9AsLessThanA10() {
            var a9 = Version.fromString("1.0-a9");
            var a10 = Version.fromString("1.0-a10");

            then(a9).isLessThan(a10);
        }
    }

    @Nested
    class Sorting {
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
        void shouldSortReleasedVersionsNumerically() {
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
    }

    @Nested
    class Equality {
        @Test
        void shouldTreatVersionsWithTrailingZerosAsEqual() {
            thenEqual("1.0", "1.0.0");
        }

        @Test
        void shouldTreatSeparatorsAsInterchangeable() {
            thenEqual("1.2.3-Final", "1.2.3.Final");
        }

        @Test
        void shouldTreatQualifierCaseAsInterchangeable() {
            thenEqual("1.0-RC1", "1.0-rc1");
        }

        private void thenEqual(String version, String other) {
            var v1 = Version.fromString(version);
            var v2 = Version.fromString(other);
            then(v1).isEqualTo(v2).hasSameHashCodeAs(v2);
        }
    }

    @Nested
    class StringRepresentation {
        @Test
        void shouldFormatVersionToString() {
            then(Version.fromString("1.2.3").toString()).isEqualTo("1.2.3");
        }

        @Test
        void shouldFormatVersionWithQualifierToString() {
            then(Version.fromString("1.2.3-SNAPSHOT").toString()).isEqualTo("1.2.3-SNAPSHOT");
        }

        @Test
        void shouldRoundTripVersionThroughString() {
            then(Version.fromString("2.5.7-RC1").toString()).isEqualTo("2.5.7-RC1");
        }

        @Test
        void shouldRoundTripFourPartVersionWithDotQualifier() {
            then(Version.fromString("5.1.5.Final").toString()).isEqualTo("5.1.5.Final");
        }
    }

    @Nested
    class UpdateTypeDetection {
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
    }
}
