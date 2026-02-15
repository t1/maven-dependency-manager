package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DependencyAnalyzerTest {

    @TempDir
    Path tempDir;

    @Mock
    private MavenRepository mockRepository;

    @Test
    void shouldAnalyzeSingleProject() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(
                        Version.fromString("5.10.0"),
                        Version.fromString("5.10.1"),
                        Version.fromString("5.11.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.pom().path()).isEqualTo(pomFile);
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.groupId()).isEqualTo("org.junit.jupiter");
        then(update.artifactId()).isEqualTo("junit-jupiter");
        then(update.currentVersion()).isEqualTo(Version.fromString("5.10.0"));
        then(update.latestVersion()).isEqualTo(Version.fromString("5.11.0"));
        then(update.updateType()).isEqualTo(UpdateType.minor);
    }

    private Path writePom(String pomContent) {
        var pomFile = tempDir.resolve("pom.xml");
        try {
            writeString(pomFile, pomContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pomFile;
    }

    @Test
    void shouldIncludeDependenciesAlreadyUpToDate() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(
                        Version.fromString("5.10.0"),
                        Version.fromString("5.10.1")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        then(reports.getFirst().dependencyUpdates()).hasSize(1);
        var update = reports.getFirst().dependencyUpdates().getFirst();
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldParseDependenciesWithMilestoneVersions() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.0.0-M1</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter"))
                .willReturn(List.of(
                        Version.fromString("3.0.0-M1"),
                        Version.fromString("3.0.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.currentVersion()).isEqualTo(Version.fromString("3.0.0-M1"));
    }

    @Test
    void shouldNotSuggestMilestoneVersionsAsUpdates() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter"))
                .willReturn(List.of(
                        Version.fromString("3.0.0"),
                        Version.fromString("3.1.0-M1"),
                        Version.fromString("3.1.0-M2"),
                        Version.fromString("3.1.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.latestVersion()).isEqualTo(Version.fromString("3.1.0"));
    }

    @Test
    void shouldReportAllDependenciesIncludingUpToDate() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.3.0</version>
                        </dependency>
                        <dependency>
                            <groupId>com.google.guava</groupId>
                            <artifactId>guava</artifactId>
                            <version>32.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.1")));
        given(mockRepository.getAvailableVersions("org.springframework", "spring-core"))
                .willReturn(List.of(
                        Version.fromString("5.3.0"),
                        Version.fromString("6.0.0")
                ));
        given(mockRepository.getAvailableVersions("com.google.guava", "guava"))
                .willReturn(List.of(Version.fromString("32.0.0")));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(3);
        var outdated = report.dependencyUpdates().stream()
                .filter(u -> !u.updateType().equals(none))
                .toList();
        then(outdated).hasSize(1);
        then(outdated.getFirst().artifactId()).isEqualTo("spring-core");
    }

    @Test
    void shouldSuggestNonMilestoneVersionWhenMilestoneIsLatest() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter"))
                .willReturn(List.of(
                        Version.fromString("3.0.0"),
                        Version.fromString("3.1.0"),
                        Version.fromString("3.2.0-M1")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.latestVersion()).isEqualTo(Version.fromString("3.1.0"));
    }

    @Test
    void shouldIncludeAllDependenciesIncludingUpToDate() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.3.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.1")));
        given(mockRepository.getAvailableVersions("org.springframework", "spring-core"))
                .willReturn(List.of(
                        Version.fromString("5.3.0"),
                        Version.fromString("6.0.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(2);
        var upToDate = report.dependencyUpdates().stream()
                .filter(u -> u.artifactId().equals("junit-jupiter"))
                .findFirst()
                .orElseThrow();
        then(upToDate.updateType()).isEqualTo(none);
    }

    @Test
    void shouldIncludeScopeInDependencyUpdate() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(
                        Version.fromString("5.10.0"),
                        Version.fromString("5.10.1")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var update = reports.getFirst().dependencyUpdates().getFirst();
        then(update.scope()).isEqualTo(test);
    }

    @Test
    void shouldAnalyzeMultipleProjects() {
        var pom1 = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>project1</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """, "pom1.xml");
        var pom2 = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>project2</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.3.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """, "pom2.xml");
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")));
        given(mockRepository.getAvailableVersions("org.springframework", "spring-core"))
                .willReturn(List.of(Version.fromString("5.3.0"), Version.fromString("6.0.0")));

        var reports = new DependencyAnalyzer(mockRepository, pom1, pom2).run();

        then(reports).hasSize(2);
        then(reports.get(0).pom().path()).isIn(pom1, pom2);
        then(reports.get(1).pom().path()).isIn(pom1, pom2);
        then(reports.stream().filter(r -> r.pom().path().equals(pom1)).findFirst().orElseThrow().dependencyUpdates()).hasSize(1);
        then(reports.stream().filter(r -> r.pom().path().equals(pom2)).findFirst().orElseThrow().dependencyUpdates()).hasSize(1);
    }

    private Path writePom(String pomContent, String filename) {
        var pomFile = tempDir.resolve(filename);
        try {
            writeString(pomFile, pomContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pomFile;
    }

    @Test
    void shouldReportManagedDependenciesWithNullCurrentVersionAndLatestVersion() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(
                        Version.fromString("5.10.0"),
                        Version.fromString("5.10.1"),
                        Version.fromString("5.11.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.groupId()).isEqualTo("org.junit.jupiter");
        then(update.artifactId()).isEqualTo("junit-jupiter");
        then(update.currentVersion()).isNull();
        then(update.latestVersion()).isEqualTo(Version.fromString("5.11.0"));
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldReportInvalidDependenciesWithNullCurrentVersion() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.groupId()).isEmpty();
        then(update.artifactId()).isEqualTo("junit-jupiter");
        then(update.currentVersion()).isEqualTo(Version.fromString("5.10.0"));
        then(update.latestVersion()).isNull();
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldReportDependenciesEvenWhenAllAvailableVersionsAreNonReleased() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.0.0-SNAPSHOT</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter"))
                .willReturn(List.of(
                        Version.fromString("3.0.0-SNAPSHOT"),
                        Version.fromString("3.1.0-M1"),
                        Version.fromString("3.1.0-SNAPSHOT")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        then(reports.getFirst().dependencyUpdates()).hasSize(1);
        var update = reports.getFirst().dependencyUpdates().getFirst();
        then(update.latestVersion()).isNull();
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldReportManagedDependenciesWithNullVersionAndNoneType() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter"))
                .willReturn(List.of(
                        Version.fromString("3.1.0"),
                        Version.fromString("3.2.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.groupId()).isEqualTo("org.springframework.boot");
        then(update.artifactId()).isEqualTo("spring-boot-starter");
        then(update.currentVersion()).isNull();
        then(update.latestVersion()).isEqualTo(Version.fromString("3.2.0"));
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldContinueProcessingWhenPomFileNotFound() {
        var validPomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var validPomFile = writePom(validPomContent);
        var nonExistentPomFile = tempDir.resolve("non-existent-pom.xml");
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(
                        Version.fromString("5.10.0"),
                        Version.fromString("5.10.1")
                ));

        var reports = new DependencyAnalyzer(mockRepository, nonExistentPomFile, validPomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.pom().path()).isEqualTo(validPomFile);
        then(report.dependencyUpdates()).hasSize(1);
    }

    @Test
    void shouldAnalyzeParent() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter-parent"))
                .willReturn(List.of(
                        Version.fromString("3.1.0"),
                        Version.fromString("3.2.0")
                ));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.parentUpdate()).isPresent();
        var parent = report.parentUpdate().get();
        then(parent.groupId()).isEqualTo("org.springframework.boot");
        then(parent.artifactId()).isEqualTo("spring-boot-starter-parent");
        then(parent.currentVersion()).isEqualTo(Version.fromString("3.1.0"));
        then(parent.latestVersion()).isEqualTo(Version.fromString("3.2.0"));
        then(parent.updateType()).isEqualTo(UpdateType.minor);
    }

    @Test
    void shouldReturnNullParentUpdateWhenParentMissing() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        then(reports.getFirst().parentUpdate()).isEmpty();
    }

    @Test
    void shouldIncludeUpToDateParent() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.0</version>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("org.springframework.boot", "spring-boot-starter-parent"))
                .willReturn(List.of(Version.fromString("3.2.0")));

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.parentUpdate()).isPresent();
        var parent = report.parentUpdate().get();
        then(parent.currentVersion()).isEqualTo(Version.fromString("3.2.0"));
        then(parent.latestVersion()).isEqualTo(Version.fromString("3.2.0"));
        then(parent.updateType()).isEqualTo(none);
    }

    @Test
    void shouldReportNullLatestVersionWhenNoVersionsAvailable() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>unknown-artifact</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("com.example", "unknown-artifact"))
                .willReturn(List.of());

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.dependencyUpdates()).hasSize(1);
        var update = report.dependencyUpdates().getFirst();
        then(update.groupId()).isEqualTo("com.example");
        then(update.artifactId()).isEqualTo("unknown-artifact");
        then(update.currentVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(update.latestVersion()).isNull();
        then(update.updateType()).isEqualTo(none);
    }

    @Test
    void shouldShowParentWhenNoVersionsAvailable() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>private-parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
        var pomFile = writePom(pomContent);
        given(mockRepository.getAvailableVersions("com.example", "private-parent"))
                .willReturn(List.of());

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.parentUpdate()).isPresent();
        var parent = report.parentUpdate().get();
        then(parent.groupId()).isEqualTo("com.example");
        then(parent.artifactId()).isEqualTo("private-parent");
        then(parent.currentVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(parent.latestVersion()).isNull();
        then(parent.updateType()).isEqualTo(none);
    }

    @Test
    void shouldShowInvalidParent() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>invalid-parent</artifactId>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
        var pomFile = writePom(pomContent);

        var reports = new DependencyAnalyzer(mockRepository, pomFile).run();

        then(reports).hasSize(1);
        var report = reports.getFirst();
        then(report.parentUpdate()).isNotNull();
        then(report.parentUpdate()).isPresent();
        var parent = report.parentUpdate().get();
        then(parent.groupId()).isEqualTo("com.example");
        then(parent.artifactId()).isEqualTo("invalid-parent");
        then(parent.currentVersion()).isNull();
        then(parent.latestVersion()).isNull();
        then(parent.updateType()).isEqualTo(none);
    }

    @Test
    void shouldNotFetchMetadataForParentAlreadyInPomList() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var moduleDir = parentDir.resolve("module-a");
        createDirectories(moduleDir);
        var parentPomPath = parentDir.resolve("pom.xml");
        writeString(parentPomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                    </modules>
                </project>
                """);
        writeString(moduleDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                
                    <artifactId>module-a</artifactId>
                </project>
                """);

        new DependencyAnalyzer(mockRepository, parentPomPath).run();

        verify(mockRepository, never()).getAvailableVersions("com.example", "parent-project");
    }

    @Test
    void shouldIncludeLocalParentInReport() throws IOException {
        var parentDir = tempDir.resolve("parent2");
        var moduleDir = parentDir.resolve("module-a");
        createDirectories(moduleDir);
        var parentPomPath = parentDir.resolve("pom.xml");
        writeString(parentPomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                    </modules>
                </project>
                """);
        writeString(moduleDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                
                    <artifactId>module-a</artifactId>
                </project>
                """);

        var reports = new DependencyAnalyzer(mockRepository, parentPomPath).run();

        var moduleReport = reports.stream()
                .filter(r -> r.pom().coordinates().artifactId().equals("module-a"))
                .findFirst().orElseThrow();
        then(moduleReport.parentUpdate()).isPresent();
        var parent = moduleReport.parentUpdate().get();
        then(parent.groupId()).isEqualTo("com.example");
        then(parent.artifactId()).isEqualTo("parent-project");
        then(parent.currentVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(parent.latestVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(parent.updateType()).isEqualTo(none);
    }

    @Test
    void shouldNotFetchMetadataForInterModuleDependency() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var moduleADir = parentDir.resolve("module-a");
        var moduleBDir = parentDir.resolve("module-b");
        createDirectories(moduleADir);
        createDirectories(moduleBDir);
        var parentPomPath = parentDir.resolve("pom.xml");
        writeString(parentPomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                        <module>module-b</module>
                    </modules>
                </project>
                """);
        writeString(moduleADir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-a</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>module-b</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        writeString(moduleBDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-b</artifactId>
                    <version>1.0.0</version>
                </project>
                """);

        new DependencyAnalyzer(mockRepository, parentPomPath).run();

        verify(mockRepository, never()).getAvailableVersions("com.example", "module-b");
    }

    @Test
    void shouldFetchMetadataForInterModuleDependencyWithDifferentVersion() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var moduleADir = parentDir.resolve("module-a");
        var moduleBDir = parentDir.resolve("module-b");
        createDirectories(moduleADir);
        createDirectories(moduleBDir);
        var parentPomPath = parentDir.resolve("pom.xml");
        writeString(parentPomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                        <module>module-b</module>
                    </modules>
                </project>
                """);
        writeString(moduleADir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-a</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>module-b</artifactId>
                            <version>2.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        writeString(moduleBDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-b</artifactId>
                    <version>1.0.0</version>
                </project>
                """);
        given(mockRepository.getAvailableVersions("com.example", "module-b"))
                .willReturn(List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")));

        new DependencyAnalyzer(mockRepository, parentPomPath).run();

        verify(mockRepository).getAvailableVersions("com.example", "module-b");
    }

    private record MultiModuleFixture(Path parentPomPath, Path modulePomPath) {}

    private MultiModuleFixture createMultiModuleProjectWithParentProperty() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var moduleDir = parentDir.resolve("module-a");
        createDirectories(moduleDir);
        var parentPomPath = parentDir.resolve("pom.xml");
        writeString(parentPomPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <properties>
                        <junit.version>5.10.0</junit.version>
                    </properties>
                
                    <modules>
                        <module>module-a</module>
                    </modules>
                </project>
                """);
        writeString(moduleDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                
                    <artifactId>module-a</artifactId>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.0"), Version.fromString("5.11.0")));
        return new MultiModuleFixture(parentPomPath, moduleDir.resolve("pom.xml"));
    }

    @Test
    void shouldResolveVersionPropertyFromParentPom() throws IOException {
        var fixture = createMultiModuleProjectWithParentProperty();

        var reports = new DependencyAnalyzer(mockRepository, fixture.parentPomPath()).run();

        var moduleReport = reports.stream()
                .filter(r -> r.pom().path().equals(fixture.modulePomPath()))
                .findFirst()
                .orElseThrow();
        then(moduleReport.dependencyUpdates()).hasSize(1);
        var update = moduleReport.dependencyUpdates().getFirst();
        then(update.currentVersion()).isEqualTo(Version.fromString("5.10.0"));
        then(update.latestVersion()).isEqualTo(Version.fromString("5.11.0"));
        then(update.updateType()).isEqualTo(UpdateType.minor);
    }

    @Test
    void shouldApplyPropertyUpdateToParentPom() throws IOException {
        var fixture = createMultiModuleProjectWithParentProperty();

        var reports = new DependencyAnalyzer(mockRepository, fixture.parentPomPath()).run();
        reports.stream()
                .filter(ProjectReport::hasUpdates)
                .forEach(report -> report.pom().apply(report.updates()));
        reports.stream()
                .map(ProjectReport::pom)
                .filter(Pom::isDirty)
                .forEach(Pom::writeToDisk);

        then(readString(fixture.parentPomPath())).contains("<junit.version>5.11.0</junit.version>");
    }

    @Test
    void shouldResolveHomeDirInDirectoryPath() throws IOException {
        var projectDir = tempDir.resolve("my-project");
        createDirectories(projectDir);
        writeString(projectDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>

                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")));
        var originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());

        try {
            var reports = new DependencyAnalyzer(mockRepository, Path.of("~/my-project")).run();

            then(reports).hasSize(1);
            then(reports.getFirst().dependencyUpdates()).hasSize(1);
            then(reports.getFirst().dependencyUpdates().getFirst().artifactId()).isEqualTo("junit-jupiter");
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void shouldResolveDirectoryToPomXml() throws IOException {
        var subDir = tempDir.resolve("my-project");
        createDirectories(subDir);
        writeString(subDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")));

        var reports = new DependencyAnalyzer(mockRepository, subDir).run();

        then(reports).hasSize(1);
        then(reports.getFirst().dependencyUpdates()).hasSize(1);
        then(reports.getFirst().dependencyUpdates().getFirst().artifactId()).isEqualTo("junit-jupiter");
    }

    @Test
    void shouldAnalyzeMultiModuleProject() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var moduleADir = parentDir.resolve("module-a");
        var moduleBDir = parentDir.resolve("module-b");
        createDirectories(moduleADir);
        createDirectories(moduleBDir);
        var parentPomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                        <module>module-b</module>
                    </modules>
                </project>
                """;
        var moduleAPomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-a</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var moduleBPomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>module-b</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.3.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var parentPomFile = parentDir.resolve("pom.xml");
        writeString(parentPomFile, parentPomContent);
        writeString(moduleADir.resolve("pom.xml"), moduleAPomContent);
        writeString(moduleBDir.resolve("pom.xml"), moduleBPomContent);
        given(mockRepository.getAvailableVersions("org.junit.jupiter", "junit-jupiter"))
                .willReturn(List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")));
        given(mockRepository.getAvailableVersions("org.springframework", "spring-core"))
                .willReturn(List.of(Version.fromString("5.3.0"), Version.fromString("6.0.0")));

        var reports = new DependencyAnalyzer(mockRepository, parentPomFile).run();

        then(reports).hasSize(3);
        var parentReport = reports.stream()
                .filter(r -> r.pom().path().equals(parentPomFile))
                .findFirst()
                .orElseThrow();
        then(parentReport.dependencyUpdates()).isEmpty();
        var moduleAReport = reports.stream()
                .filter(r -> r.pom().path().equals(moduleADir.resolve("pom.xml")))
                .findFirst()
                .orElseThrow();
        then(moduleAReport.dependencyUpdates()).hasSize(1);
        then(moduleAReport.dependencyUpdates().getFirst().artifactId()).isEqualTo("junit-jupiter");
        var moduleBReport = reports.stream()
                .filter(r -> r.pom().path().equals(moduleBDir.resolve("pom.xml")))
                .findFirst()
                .orElseThrow();
        then(moduleBReport.dependencyUpdates()).hasSize(1);
        then(moduleBReport.dependencyUpdates().getFirst().artifactId()).isEqualTo("spring-core");
    }
}
