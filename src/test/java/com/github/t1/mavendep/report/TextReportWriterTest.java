package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class TextReportWriterTest {
    private static final Pom DUMMY_POM = Pom.parse(Path.of("pom.xml")).orElseThrow();

    @TempDir Path tempDir;

    private Pom dummyPom() {
        var pomFile = tempDir.resolve("pom.xml");
        try {
            writeString(pomFile, """
                    <project>
                    </project>
                    """);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Pom.parse(pomFile).orElseThrow();
    }

    private String writeToString(List<ProjectReport> reports) {
        var buffer = new ByteArrayOutputStream();
        new TextReportWriter(new PrintStream(buffer), reports).run();
        return buffer.toString();
    }

    @Test
    void shouldGenerateTableFormatReport() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Maven Dependency Update Report");
        then(text).contains("  Project:");
        then(text).contains("pom.xml");
        then(text).contains("┌─");  // Table top border
        then(text).contains("│ Artifact ID");  // Table header
        then(text).contains("├─");  // Header separator
        then(text).contains("│ junit-jupiter");
        then(text).contains("│ 5.10.0");
        then(text).contains("│ 5.10.1");
        then(text).contains("│ patch");
        then(text).contains("└─");  // Table bottom border
        then(text).contains("  Summary:");
    }

    @Test
    void shouldShowSummary() {
        var update1 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var update2 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("5.3.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                major
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update1, update2), List.of(), 2);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Summary: 2 dependencies, 2 updates available");
        then(text).contains("1 major");
        then(text).contains("1 patch");
    }

    @Test
    void shouldShowSummaryWhenNoUpdates() {
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 5);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).doesNotContain("All dependencies are up to date");
        then(text).contains("Summary: 5 dependencies, 0 updates available (0 major, 0 minor, 0 patch)");
    }

    @Test
    void shouldNotShowDependenciesHeader() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).doesNotContain("Outdated Dependencies:");
        then(text).doesNotContain("Dependencies:");
    }

    @Test
    void shouldShowTotalDependencyCountInSummary() {
        var update1 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var update2 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework.boot", "spring-boot-starter-web", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.1"),
                List.of(),
                minor
        );
        var update3 = new DependencyUpdate(
                new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind", Version.fromString("2.15.0"), compile, null),
                Version.fromString("2.16.1"),
                List.of(),
                minor
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update1, update2, update3), List.of(), 3);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Summary: 3 dependencies, 3 updates available");
    }

    @Test
    void shouldDisplayGroupIdInFirstColumn() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ Group ID");
        then(text).contains("│ org.junit.jupiter");
    }

    @Test
    void shouldDisplayArtifactIdHeader() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ Artifact ID");
    }

    @Test
    void shouldShowAllDependenciesWhenIncludingUpToDate() {
        var outdated = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var upToDate = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(outdated, upToDate), List.of(), 2);

        var text = writeToString(List.of(report));

        then(text).contains("│ junit-jupiter");
        then(text).contains("│ spring-core");
        then(text).contains("│ none");
        then(text).contains("Summary: 2 dependencies, 1 updates available");
    }

    @Test
    void shouldFilterOutUpToDateDependenciesWhenShowAllIsFalse() {
        var outdated = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var upToDate = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(outdated, upToDate), List.of(), 2);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ junit-jupiter");
        then(text).doesNotContain("│ spring-core");
        then(text).doesNotContain("│ none");
        then(text).contains("Summary: 2 dependencies, 1 updates available");
    }

    @Test
    void shouldDisplayTypeScopeColumn() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ Type/Scope");
        then(text).contains("│ dependency/test");
    }

    @Test
    void shouldSortDependenciesByType() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                minor
        );
        var pluginUpdate = new DependencyUpdate(
                new Dependency(plugin, "org.apache.maven.plugins", "maven-compiler-plugin", Version.fromString("3.11.0"), compile, null),
                Version.fromString("3.12.0"),
                List.of(),
                minor
        );
        var dependencyUpdate = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        // Add in reverse order: plugin, dependency, parent
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(dependencyUpdate), List.of(pluginUpdate), 3);

        var text = writeToString(List.of(report.onlyUpdates()));

        var parentIndex = text.indexOf("spring-boot-starter-parent");
        var dependencyIndex = text.indexOf("junit-jupiter");
        var pluginIndex = text.indexOf("maven-compiler-plugin");

        then(parentIndex).isGreaterThan(0);
        then(dependencyIndex).isGreaterThan(0);
        then(pluginIndex).isGreaterThan(0);

        // Should be sorted by type: parent < dependency < plugin
        then(parentIndex).isLessThan(dependencyIndex);
        then(dependencyIndex).isLessThan(pluginIndex);
    }

    @Test
    void shouldDisplayQuestionMarkWhenLatestVersionIsNull() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "unknown-artifact", Version.fromString("1.0.0"), compile, null),
                null,
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).contains("│ unknown-artifact");
        then(text).contains("│ 1.0.0");
        then(text).contains("│ ?");  // Latest version is null
    }

    @Test
    void shouldDisplayParentPomAsFirstRowInTableWithPseudoScope() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                minor
        );
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ parent");
        then(text).contains("│ spring-boot-starter-parent");
        var parentIndex = text.indexOf("spring-boot-starter-parent");
        var junitIndex = text.indexOf("junit-jupiter");
        then(parentIndex).isLessThan(junitIndex);
    }

    @Test
    void shouldDisplayQuestionMarkWhenParentLatestVersionIsNull() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "com.example", "unknown-parent", Version.fromString("1.0.0"), compile, null),
                null,
                List.of(),
                none
        );
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).contains("│ parent");
        then(text).contains("│ unknown-parent");
        then(text).contains("│ 1.0.0");
        then(text).contains("│ ?");  // Latest version is null
    }

    @Test
    void shouldDisplayManagedWhenCurrentVersionIsNull() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "managed-artifact", null, compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).contains("│ managed-artifact");
        then(text).contains("│ <managed>");
        then(text).contains("│ 2.0.0");
    }

    @Test
    void shouldDisplayManagedWhenParentCurrentVersionIsNull() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "com.example", "managed-parent", null, compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                none
        );
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).contains("│ parent");
        then(text).contains("│ managed-parent");
        then(text).contains("│ <managed>");
        then(text).contains("│ 2.0.0");
    }

    @Test
    void shouldShowIndividualSummaryForEachProject() {
        var update1 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var update2 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("5.3.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                major
        );
        var dummyPom2 = dummyPom();
        var report1 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update1), List.of(), 1);
        var report2 = new ProjectReport(dummyPom2, Optional.empty(), List.of(update2), List.of(), 1);

        var text = writeToString(List.of(report1.onlyUpdates(), report2.onlyUpdates()));

        var project1Index = text.indexOf(DUMMY_POM.path().toAbsolutePath().toString());
        then(project1Index).isGreaterThanOrEqualTo(0);
        var project2Index = text.indexOf(dummyPom2.path().toAbsolutePath().toString());
        then(project2Index).isGreaterThanOrEqualTo(project1Index);
        var summary1Text = text.substring(project1Index, project2Index);
        var summary2Text = text.substring(project2Index);
        then(summary1Text).contains("Summary: 1 dependency, 1 updates available (0 major, 0 minor, 1 patch)");
        then(summary2Text).contains("Summary: 1 dependency, 1 updates available (1 major, 0 minor, 0 patch)");
    }

    @Test
    void shouldShowTotalSummaryForMultipleProjects() {
        var update1 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var update2 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("5.3.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                major
        );
        var update3 = new DependencyUpdate(
                new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind", Version.fromString("2.15.0"), compile, null),
                Version.fromString("2.16.0"),
                List.of(),
                minor
        );
        var report1 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update1), List.of(), 1);
        var report2 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update2, update3), List.of(), 2);

        var text = writeToString(List.of(report1.onlyUpdates(), report2.onlyUpdates()));

        then(text).contains("Total Summary: 3 dependencies, 3 updates available (1 major, 1 minor, 1 patch)");
    }

    @Test
    void shouldNotShowTotalSummaryForSingleProject() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).doesNotContain("Total Summary");
    }

    @Test
    void shouldShowSummaryForUpToDateDependencies() {
        var upToDate = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(upToDate), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Summary: 1 dependency, 0 updates available (0 major, 0 minor, 0 patch)");
    }

    @Test
    void shouldShowSummaryForMixOfOutdatedAndUpToDateDependencies() {
        var outdated = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var upToDate = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(outdated, upToDate), List.of(), 2);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Summary: 2 dependencies, 1 updates available (0 major, 0 minor, 1 patch)");
    }

    @Test
    void shouldNotShowTotalSummaryWhenAllProjectsHaveNoDependencies() {
        var report1 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);
        var report2 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);

        var text = writeToString(List.of(report1.onlyUpdates(), report2.onlyUpdates()));

        then(text).doesNotContain("Total Summary");
    }

    @Test
    void shouldShowTotalSummaryForMultipleProjectsWithUpToDateDependencies() {
        var upToDate1 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var upToDate2 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.1"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                none
        );
        var report1 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(upToDate1), List.of(), 1);
        var report2 = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(upToDate2), List.of(), 1);

        var text = writeToString(List.of(report1.onlyUpdates(), report2.onlyUpdates()));

        then(text).contains("Total Summary: 2 dependencies, 0 updates available (0 major, 0 minor, 0 patch)");
    }

    @Test
    void shouldShowAllDependenciesCountEvenWhenOnlyShowingOutdated() {
        var outdated = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(outdated), List.of(), 5);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("Summary: 5 dependencies, 1 updates available (0 major, 0 minor, 1 patch)");
    }

    @Test
    void shouldDisplayTypeScopeColumnWithDependencyValue() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ Type/Scope");
        then(text).contains("│ dependency/compile");
    }

    @Test
    void shouldDisplayTypeScopeColumnWithParentValue() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                minor
        );
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        then(text).contains("│ parent");
        var parentTypeIndex = text.indexOf("│ parent");
        var dependencyTypeIndex = text.indexOf("│ dependency/test");
        then(parentTypeIndex).isGreaterThan(0);
        then(dependencyTypeIndex).isGreaterThan(0);
    }

    @Test void shouldShowCommittedVersionColumn() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.9.0"), compile, null),
                Version.fromString("6.0.3"),
                List.of(),
                major
        ).withCommittedVersion(Version.fromString("5.10.0"));
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).contains("│ Committed");
        then(text).contains("│ 5.10.0");
        then(text).contains("│ 5.9.0");
    }

    @Test void shouldNotShowCommittedColumnWhenNoCommittedVersions() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("6.0.3"),
                List.of(),
                major
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report));

        then(text).doesNotContain("Committed");
    }

    @Test
    void shouldDisplayParentWithoutScope() {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                minor
        );
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(update), List.of(), 1);

        var text = writeToString(List.of(report.onlyUpdates()));

        var lines = text.split("\n");
        var parentLine = "";
        for (var line : lines) {
            if (line.contains("spring-boot-starter-parent")) {
                parentLine = line;
                break;
            }
        }
        var parts = parentLine.split("│");
        then(parts[1].trim()).isEqualTo("parent");
    }
}
