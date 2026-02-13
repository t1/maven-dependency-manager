package com.github.t1.mavendep.domain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.github.t1.mavendep.report.Logger.log;

/// Represents a parsed version string as a list of parts (integers and strings).
///
/// ## Parsing
/// Version strings are split on dots and hyphens. Each segment becomes a `NumericPart`
/// (if all digits) or a `StringPart`. The original string is preserved for `toString()`.
///
/// ## Comparison
/// - Parts are compared pairwise; missing parts are padded with `NumericPart(0)`
/// - Numeric parts compare by value; string parts compare lexicographically
/// - At the same position, string parts sort before numeric parts (pre-release < release)
///
/// ## Examples
/// ```
/// "1.2.3"          → parts [1, 2, 3]
/// "5.1.5.Final"    → parts [5, 1, 5, "Final"]
/// "1.2.3-SNAPSHOT"  → parts [1, 2, 3, "SNAPSHOT"]
/// "1.0-a9"          → parts [1, 0, "a", 9]  (mixed segments are split)
/// "1.0" equals "1.0.0" (zero-padding)
/// "1.2.3-Final" equals "1.2.3.Final" (separators interchangeable)
/// ```
public record Version(List<Part> parts, String original) implements Comparable<Version> {

    public sealed interface Part extends Comparable<Part> {
        Part ZERO = new NumericPart(BigInteger.ZERO);
    }

    public record NumericPart(BigInteger value) implements Part {
        public NumericPart(long value) {this(BigInteger.valueOf(value));}

        @Override public int compareTo(Part other) {
            if (other instanceof NumericPart(var v)) return value.compareTo(v);
            return 1; // numeric after string (release > pre-release)
        }

        @Override public String toString() {return value.toString();}
    }

    public record StringPart(String value) implements Part {
        @Override public int compareTo(Part other) {
            if (other instanceof StringPart(var v)) return value.compareTo(v);
            return -1; // string before numeric (pre-release < release)
        }

        @Override public String toString() {return value;}
    }

    private static final Set<String> RELEASE_QUALIFIERS = Set.of("Final", "RELEASE", "GA");
    private static final Set<String> PRE_RELEASE_QUALIFIERS = Set.of(
            "snapshot", "alpha", "beta", "rc", "m", "cr", "pr", "preview", "dev", "incubating");

    private static final Pattern SEGMENT_TOKEN = Pattern.compile("[a-zA-Z]+|\\d+");

    public static Version fromString(String version) {
        if (version == null) return null;
        var segments = version.split("[.\\-]");
        var parts = new ArrayList<Part>();
        for (var segment : segments) {
            parts.addAll(parseSegment(segment));
        }
        return new Version(List.copyOf(parts), version);
    }

    private static List<Part> parseSegment(String segment) {
        if (segment.isEmpty()) throw new IllegalArgumentException("Invalid version segment: empty");
        var parts = new ArrayList<Part>();
        var matcher = SEGMENT_TOKEN.matcher(segment);
        while (matcher.find()) {
            parts.add(parsePart(matcher.group()));
        }
        return parts;
    }

    private static Part parsePart(String token) {
        return Character.isDigit(token.charAt(0))
                ? new NumericPart(new BigInteger(token))
                : new StringPart(token);
    }

    /// Returns the numeric value at the given index, or `0` if the index is out of bounds or the part is not numeric.
    public int numericPart(int index) {
        return part(index) instanceof NumericPart(var v) ? v.intValueExact() : 0;
    }

    /// Returns the part at the given index, or a {@link NumericPart} of `0` if out of bounds.
    public Part part(int index) {
        return index >= parts.size() ? Part.ZERO : parts.get(index);
    }

    public int major() {return numericPart(0);}

    public int minor() {return numericPart(1);}

    public int patch() {return numericPart(2);}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version v)) return false;
        return compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        var end = parts.size();
        while (end > 0 && Part.ZERO.equals(parts.get(end - 1))) end--;
        return Objects.hash(parts.subList(0, end));
    }

    /// Compares versions semantically.
    ///
    /// Parts are compared pairwise. Missing parts are padded with `NumericPart(0)`.
    /// At the same position, string parts sort before numeric parts (pre-release < release).
    @Override
    public int compareTo(Version other) {
        var maxLen = Math.max(this.parts.size(), other.parts.size());
        for (var i = 0; i < maxLen; i++) {
            var result = part(i).compareTo(other.part(i));
            if (result != 0) return result;
        }
        return 0;
    }

    /// Determines if this version represents a release (not a pre-release).
    ///
    /// - No string parts → released
    /// - Known release qualifiers (`Final`, `GA`, `RELEASE`) → released
    /// - Known pre-release qualifiers (`SNAPSHOT`, `alpha`, `beta`, `RC`, `M`, ...) → not released
    /// - Unknown string qualifiers → warning logged, assumed pre-release
    public boolean isReleased() {
        var qualifier = lastQualifier();
        if (qualifier == null) return true;
        if (RELEASE_QUALIFIERS.contains(qualifier)) return true;
        if (isKnownPreRelease(qualifier)) return false;
        log("Warning: unknown version qualifier '" + qualifier + "' in " + original + "; assuming pre-release");
        return false;
    }

    private String lastQualifier() {
        for (var i = parts.size() - 1; i >= 0; i--) {
            if (parts.get(i) instanceof StringPart(var v)) return v;
        }
        return null;
    }

    private static boolean isKnownPreRelease(String qualifier) {
        var lower = qualifier.toLowerCase();
        return PRE_RELEASE_QUALIFIERS.stream().anyMatch(lower::startsWith);
    }

    @Override
    public String toString() {return original;}
}
