package com.github.t1.mavendep.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.github.t1.mavendep.domain.Logger.log;

/// Parses version strings by splitting on `.`, `-`, and transitions between digits and non-digits.
/// Leading numeric segments (separated by `.` or `-`) form the version number (major, minor, patch).
/// The qualifier starts at the first segment containing non-digits, or the first numeric segment > 10000
/// (which typically indicates a timestamp or build number).
/// Provides [#isReleased(ArtifactRef)] semantics based on known qualifier classifications.
public final class Version implements Comparable<Version> {
    private static final int NUMERIC_QUALIFIER_THRESHOLD = 10000;

    private final String original;
    private final List<String> parts; // fine-grained parts for comparison
    private final List<String> segments; // coarse segments split by '.' and '-' only
    private final int qualifierSegment; // index into segments where qualifier begins, or -1

    private Version(String version) {
        this.original = version;
        this.parts = split(version, Version::isDigitTransition);
        this.segments = split(version, (_, _) -> false);
        this.qualifierSegment = findQualifierSegment();
    }

    public static Version fromString(String version) {return (version == null) ? null : new Version(version);}

    public int major() {return versionSegment(0);}

    public int minor() {return versionSegment(1);}

    public int patch() {return versionSegment(2);}

    private int versionSegment(int index) {
        if (index >= versionSegmentCount()) return 0;
        return Integer.parseInt(segments.get(index));
    }

    private int versionSegmentCount() {
        return qualifierSegment < 0 ? segments.size() : qualifierSegment;
    }

    /// Determines if this version represents a release (not a pre-release).
    /// See [ReleaseClassifier] for the classification rules.
    ///
    /// @param artifactContext added to the warning, so the user knows where this happens
    public boolean isReleased(ArtifactRef artifactContext) {
        return new ReleaseClassifier(artifactContext).isReleased();
    }

    public Optional<String> qualifier() {
        if (qualifierSegment < 0) return Optional.empty();
        return Optional.of(String.join("-", segments.subList(qualifierSegment, segments.size())));
    }

    @Override public int compareTo(Version other) {
        int size = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < size; i++) {
            var left = i < parts.size() ? parts.get(i) : "0";
            var right = i < other.parts.size() ? other.parts.get(i) : "0";
            int cmp;
            if (isNumeric(left) && isNumeric(right)) {
                cmp = Long.compare(Long.parseLong(left), Long.parseLong(right));
            } else if (isNumeric(left)) {
                cmp = 1; // numeric > qualifier (e.g. "1.0" > "1.0-alpha")
            } else if (isNumeric(right)) {
                cmp = -1;
            } else {
                cmp = QualifierType.of(left).compareTo(QualifierType.of(right));
                if (cmp == 0) cmp = left.compareToIgnoreCase(right);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version v)) return false;
        return compareTo(v) == 0;
    }

    @Override public int hashCode() {return normalizedParts().hashCode();}

    /// Returns parts normalized for hashing: trailing "0" elements removed (so `1.0` and `1.0.0` match)
    /// and lowercased (so `RC1` and `rc1` match), consistent with case-insensitive [#compareTo].
    private List<String> normalizedParts() {
        int end = parts.size();
        while (end > 0 && parts.get(end - 1).equals("0")) end--;
        return parts.subList(0, end).stream().map(String::toLowerCase).toList();
    }

    @Override public String toString() {return original;}

    private static boolean isNumeric(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private static boolean isVersionNumber(String segment) {
        return isNumeric(segment) && Long.parseLong(segment) <= NUMERIC_QUALIFIER_THRESHOLD;
    }

    /// Finds the index in [#segments] where the qualifier starts.
    /// The qualifier begins at the first segment that contains non-digit characters
    /// or is a number > [#NUMERIC_QUALIFIER_THRESHOLD].
    private int findQualifierSegment() {
        for (int i = 0; i < segments.size(); i++)
            if (!isVersionNumber(segments.get(i))) return i;
        return -1;
    }

    /// Splits a version string into tokens, always splitting on `.` and `-` separators (which are dropped).
    /// The `extraSplit` predicate can trigger additional splits (e.g. on digit/letter transitions)
    /// where the character starts a new token rather than being dropped.
    private static List<String> split(String version, BiPredicate<Character, StringBuilder> extraSplit) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            var c = version.charAt(i);
            var separator = c == '.' || c == '-';
            if (separator || extraSplit.test(c, current)) {
                if (!current.isEmpty()) tokens.add(current.toString());
                current = new StringBuilder();
            }
            if (!separator) current.append(c);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private static boolean isDigitTransition(char c, StringBuilder current) {
        return !current.isEmpty() && Character.isDigit(c) != Character.isDigit(current.charAt(0));
    }

    /// Enumerates known qualifier types in comparison order.
    /// The enum ordinal determines the comparison rank;
    /// types ordered at or after [#RELEASE] are considered released.
    private enum QualifierType {
        ALPHA("alpha", "a"),
        BETA("beta", "b"),
        MILESTONE("milestone", "m"),
        RC("rc", "cr"),
        SNAPSHOT("snapshot"),
        OTHER_PRE_RELEASE("pr", "preview", "dev", "incubating"),
        UNKNOWN,
        RELEASE("final", "ga", "release", ""),
        SERVICE_PACK("sp");

        private final List<String> aliases;

        QualifierType(String... aliases) {this.aliases = List.of(aliases);}

        boolean isReleased() {return ordinal() >= RELEASE.ordinal();}

        static QualifierType of(String name) {
            var lower = name.toLowerCase();
            for (var type : values())
                if (type.aliases.contains(lower)) return type;
            return UNKNOWN;
        }
    }

    /// Classifies version qualifiers as released or pre-release.
    ///
    /// - Purely numeric qualifier (e.g. timestamp `201606060606`) → released
    /// - Known release qualifiers (`Final`, `GA`, `RELEASE`, `SP`) → released
    /// - Known pre-release qualifiers (`SNAPSHOT`, `alpha`, `beta`, `RC`, `M`, ...) → not released
    /// - Unknown string qualifiers → warning logged, assumed pre-release
    private class ReleaseClassifier {
        private final ArtifactRef context;

        private ReleaseClassifier(ArtifactRef context) {this.context = context;}

        boolean isReleased() {
            return qualifier()
                    .map(this::classify)
                    .orElse(true);
        }

        private boolean classify(String qualifier) {
            var type = QualifierType.of(alphabeticPrefix(qualifier));
            if (type == QualifierType.UNKNOWN) logUnknownQualifier(qualifier);
            return type.isReleased();
        }

        private static String alphabeticPrefix(String qualifier) {
            return qualifier.replaceFirst("[^a-zA-Z].*", "").toLowerCase();
        }

        private void logUnknownQualifier(String qualifier) {
            log().warning(context, "unknown version qualifier '" + qualifier + "' in " + Version.this +
                                   "; assuming pre-release");
        }
    }
}
