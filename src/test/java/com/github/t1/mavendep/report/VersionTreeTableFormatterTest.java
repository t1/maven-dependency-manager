package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.BDDAssertions.then;

class VersionTreeTableFormatterTest {

    @Test void shouldReturnEmptyStringForEmptyList() {
        var result = VersionTreeTableFormatter.format(List.of());

        then(result).isEmpty();
    }

    @Test void shouldFormatSingleVersion() {
        var result = VersionTreeTableFormatter.format(List.of(Version.fromString("3.25.1")));

        then(result).isEqualTo("""
                ┌───────┬───────┬─────────┐
                │ Major │ Minor │ Version │
                ├───────┼───────┼─────────┤
                │ 3     │ 25    │ 3.25.1  │
                └───────┴───────┴─────────┘
                """);
    }

    @Test void shouldFormatMultiplePatchVersionsInColumns() {
        var result = VersionTreeTableFormatter.format(List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.25.2"),
                Version.fromString("3.25.3")));

        then(result).isEqualTo("""
                ┌───────┬───────┬─────────────────────────────┐
                │ Major │ Minor │ Version                     │
                ├───────┼───────┼─────────┬─────────┬─────────┤
                │ 3     │ 25    │ 3.25.1  │ 3.25.2  │ 3.25.3  │
                └───────┴───────┴─────────┴─────────┴─────────┘
                """);
    }

    @Test void shouldSpanMajorColumnAcrossMinorVersions() {
        var result = VersionTreeTableFormatter.format(List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.26.0"),
                Version.fromString("3.27.7")));

        then(result).isEqualTo("""
                ┌───────┬───────┬─────────┐
                │ Major │ Minor │ Version │
                ├───────┼───────┼─────────┤
                │ 3     │ 25    │ 3.25.1  │
                │       │ 26    │ 3.26.0  │
                │       │ 27    │ 3.27.7  │
                └───────┴───────┴─────────┘
                """);
    }

    @Test void shouldSeparateMajorVersionsWithBorder() {
        var result = VersionTreeTableFormatter.format(List.of(
                Version.fromString("2.15.0"),
                Version.fromString("2.15.1"),
                Version.fromString("3.25.1"),
                Version.fromString("3.26.0")));

        then(result).isEqualTo("""
                ┌───────┬───────┬───────────────────┐
                │ Major │ Minor │ Version           │
                ├───────┼───────┼─────────┬─────────┤
                │ 2     │ 15    │ 2.15.0  │ 2.15.1  │
                ├───────┼───────┼─────────┼─────────┤
                │ 3     │ 25    │ 3.25.1  │         │
                │       │ 26    │ 3.26.0  │         │
                └───────┴───────┴─────────┴─────────┘
                """);
    }

    @Test void shouldWrapVersionsToNextRowWhenExceeding120Chars() {
        var versions = IntStream.rangeClosed(0, 14)
                .mapToObj(i -> Version.fromString("3.25." + i))
                .toList();

        var result = VersionTreeTableFormatter.format(versions);

        then(result).contains("│ Major │ Minor │ Version");
        then(result).contains("│ 3     │ 25    │");
        then(result).contains("│       │       │");
        var lines = result.lines().toList();
        then(lines).allMatch(line -> line.length() <= 120);
    }
}
