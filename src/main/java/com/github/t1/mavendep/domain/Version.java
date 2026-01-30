package com.github.t1.mavendep.domain;

public record Version(int major, int minor, int patch, String qualifier) implements Comparable<Version> {

    private static final int MIN_QUALIFIER_LENGTH = 2;

    private record NumberAndQualifier(int number, String qualifier) {}

    private static NumberAndQualifier parseNumberWithQualifier(String part) {
        if (part.contains("-")) {
            var split = part.split("-", 2);
            return new NumberAndQualifier(Integer.parseInt(split[0]), split[1]);
        }
        return new NumberAndQualifier(Integer.parseInt(part), "");
    }

    public static Version fromString(String version) {
        if (version == null) return null;
        try {
            var parts = version.split("\\.");
            return parseVersionParts(parts);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version string: '" + version + "'", e);
        }
    }

    private static Version parseVersionParts(String[] parts) {
        var major = parseMajor(parts);
        var minorAndQualifier = parseMinorAndQualifier(parts);
        var patchAndQualifier = parsePatchAndQualifier(parts, minorAndQualifier.qualifier());

        return new Version(major, minorAndQualifier.number(),
                patchAndQualifier.number(),
                patchAndQualifier.qualifier());
    }

    private static int parseMajor(String[] parts) {
        return Integer.parseInt(parts[0]);
    }

    private static NumberAndQualifier parseMinorAndQualifier(String[] parts) {
        if (parts.length < 2) {
            return new NumberAndQualifier(0, "");
        }
        return parseNumberWithQualifier(parts[1]);
    }

    private static NumberAndQualifier parsePatchAndQualifier(String[] parts, String inheritedQualifier) {
        if (parts.length < 3) {
            return new NumberAndQualifier(0, inheritedQualifier);
        }

        if (isQualifierOnly(parts[2])) {
            var combinedQualifier = combineQualifiers(inheritedQualifier, parts[2]);
            return new NumberAndQualifier(0, combinedQualifier);
        }

        return parseNumberWithQualifier(parts[2]);
    }

    private static boolean isQualifierOnly(String part) {
        if (Character.isDigit(part.charAt(0))) {
            return false;
        }
        if (part.length() < MIN_QUALIFIER_LENGTH) {
            throw new NumberFormatException("For input string: \"" + part + "\"");
        }
        return true;
    }

    private static String combineQualifiers(String first, String second) {
        return first.isEmpty() ? second : first + "." + second;
    }

    /// Compares versions semantically.
    ///
    /// ## Semantic Version Behavior
    /// - Versions are compared by major.minor.patch first
    /// - Released versions (no qualifier) are **greater than** unreleased versions (with qualifier)
    /// - Qualifiers are compared lexicographically (e.g., `rc1` > `beta1` > `alpha1` > `SNAPSHOT`)
    ///
    /// ## Examples
    /// ```
    /// 2.0.0 > 1.0.0                    // higher version > lower version
    /// 1.10.0 > 1.9.9                   // higher version > lower version
    /// 1.0.0 > 1.0.0-SNAPSHOT           // released > unreleased
    /// 3.0.0-rc1 > 3.0.0-beta1          // rc > beta
    /// 3.0.0-beta1 > 3.0.0-alpha1       // beta > alpha
    /// 3.0.0-alpha1 > 3.0.0-SNAPSHOT    // alpha > SNAPSHOT
    /// ```
    @Override
    public int compareTo(Version other) {
        if (other == null) return 1;
        if (this.major != other.major) {
            return this.major - other.major;
        }
        if (this.minor != other.minor) {
            return this.minor - other.minor;
        }
        if (this.patch != other.patch) {
            return this.patch - other.patch;
        }

        // Release versions (no qualifier) are newer than pre-release versions (with qualifier)
        if (this.qualifier.isEmpty() && !other.qualifier.isEmpty()) {
            return 1;
        }
        if (!this.qualifier.isEmpty() && other.qualifier.isEmpty()) {
            return -1;
        }

        // Compare qualifiers lexicographically
        return this.qualifier.compareTo(other.qualifier);
    }

    /// Most qualifiers like `-alpha1`, `-RC3` or `-SNAPSHOT` are considered pre-release versions.
    /// If we find exceptions, we may have to change this.
    public boolean isReleased() {
        return qualifier.isEmpty();
    }


    @Override
    public String toString() {
        var base = "%d.%d.%d".formatted(major, minor, patch);
        if (qualifier.isEmpty()) {
            return base;
        }
        return base + "-" + qualifier;
    }
}
