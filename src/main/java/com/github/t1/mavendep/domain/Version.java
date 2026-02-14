package com.github.t1.mavendep.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.t1.mavendep.report.Logger.log;

/// Parses version strings by splitting on `.`, `-`, and transitions between digits and non-digits.
/// Leading numeric segments (separated by `.` or `-`) form the version number (major, minor, patch).
/// The qualifier starts at the first segment containing non-digits, or the first numeric segment > 10000
/// (which typically indicates a timestamp or build number).
/// Provides [#isReleased()] semantics based on known qualifier classifications.
public final class Version implements Comparable<Version> {
    private static final int NUMERIC_QUALIFIER_THRESHOLD = 10000;
    private static final Set<String> RELEASE_QUALIFIERS = Set.of("final", "release", "ga", "sp");
    private static final Set<String> PRE_RELEASE_QUALIFIERS = Set.of(
            "snapshot", "alpha", "a", "beta", "b", "rc", "m", "milestone", "cr", "pr", "preview", "dev", "incubating");

    private final String original;
    private final List<String> parts; // fine-grained parts for comparison
    private final List<String> segments; // coarse segments split by '.' and '-' only
    private final int qualifierSegment; // index into segments where qualifier begins, or -1

    private Version(String version) {
        this.original = version;
        this.parts = splitParts(version);
        this.segments = splitSegments(version);
        this.qualifierSegment = findQualifierSegment();
    }

    public static Version fromString(String version) {
        if (version == null) return null;
        return new Version(version);
    }

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
    ///
    /// - No qualifier → released
    /// - Purely numeric qualifier (e.g. timestamp `201606060606`) → released
    /// - Known release qualifiers (`Final`, `GA`, `RELEASE`, `SP`) → released
    /// - Known pre-release qualifiers (`SNAPSHOT`, `alpha`, `beta`, `RC`, `M`, ...) → not released
    /// - Unknown string qualifiers → warning logged, assumed pre-release
    public boolean isReleased() {
        return qualifier().map(q -> {
            var label = q.replaceFirst("[^a-zA-Z].*", "").toLowerCase();
            if (label.isEmpty()) return true; // purely numeric qualifier
            if (RELEASE_QUALIFIERS.contains(label)) return true;
            if (PRE_RELEASE_QUALIFIERS.contains(label)) return false;
            log("Warning: unknown version qualifier '" + q + "' in " + this + "; assuming pre-release");
            return false;
        }).orElse(true);
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
                cmp = qualifierRank(left) - qualifierRank(right);
                if (cmp == 0) cmp = left.compareToIgnoreCase(right);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int qualifierRank(String qualifier) {
        return switch (qualifier.toLowerCase()) {
            case "alpha", "a" -> 0;
            case "beta", "b" -> 1;
            case "milestone", "m" -> 2;
            case "rc", "cr" -> 3;
            case "snapshot" -> 4;
            case "final", "ga", "release", "" -> 5;
            case "sp" -> 6;
            default -> 7;
        };
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version v)) return false;
        return compareTo(v) == 0;
    }

    @Override public int hashCode() {return normalizedParts().hashCode();}

    /// Returns parts with trailing "0" elements removed, so that e.g. `1.0` and `1.0.0` have the same hash.
    private List<String> normalizedParts() {
        int end = parts.size();
        while (end > 0 && parts.get(end - 1).equals("0")) end--;
        return parts.subList(0, end);
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

    /// Splits on `.` and `-` only, preserving mixed alphanumeric segments like `RC1` or `SP02`.
    private static List<String> splitSegments(String version) {
        var segments = new ArrayList<String>();
        var current = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (c == '.' || c == '-') {
                if (!current.isEmpty()) segments.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) segments.add(current.toString());
        return segments;
    }

    /// Splits on `.`, `-`, and transitions between digits and non-digits for fine-grained comparison.
    private static List<String> splitParts(String version) {
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            boolean separator = c == '.' || c == '-'; // these must not become their own part
            if (separator || !(current.isEmpty() || Character.isDigit(c) == Character.isDigit(current.charAt(0)))) {
                if (!current.isEmpty()) parts.add(current.toString());
                current = new StringBuilder();
            }
            if (!separator) current.append(c);
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts;
    }
}
