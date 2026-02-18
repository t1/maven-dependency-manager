package com.github.t1.mavendep.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.api.BDDAssertions.then;

class CheckCommandIT extends BaseCliIT {

    private Path createPomWithDependency(String groupId, String artifactId, String version) {
        return writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(groupId, artifactId, version));
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
    void shouldAnalyzeSinglePomFileAndGenerateTextReport() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("check", pomFile.toString(), "-f", "text");

        then(output).contains("Maven Dependency Update Report");
        then(output).contains("assertj-core");
        then(output).contains("3.27.7");
    }

    @Test
    void shouldAnalyzeSinglePomFileAndGenerateJsonReport() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.junit.jupiter", "junit-jupiter", "5.10.0");
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.10.1")
        ));

        var output = runCli("check", pomFile.toString(), "-f", "json");

        then(output).contains("\"artifactId\" : \"junit-jupiter\"");
        then(output).contains("\"latestVersion\" : \"5.10.1\"");
    }

    @Test
    void shouldHandlePropertyBasedVersions() throws IOException, InterruptedException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <jackson.version>2.15.0</jackson.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>${jackson.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("com.fasterxml.jackson.core", "jackson-databind", List.of(
                Version.fromString("2.15.0"),
                Version.fromString("2.16.0")
        ));

        var output = runCli("check", pomFile.toString(), "-f", "text");

        then(output).contains("jackson-databind");
        then(output).contains("2.16.0");
    }

    @Test
    void shouldWriteReportToOutputFile() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.junit.jupiter", "junit-jupiter", "5.10.0");
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.10.1")
        ));
        var outputFile = tempDir.resolve("report.txt");

        runCli("check", pomFile.toString(), "-f", "text", "-o", outputFile.toString());

        then(contentOf(outputFile.toFile()))
                .contains("Maven Dependency Update Report")
                .contains("junit-jupiter");
    }

    @Test
    void shouldShowAllDependenciesWhenFlagEnabled() throws IOException, InterruptedException {
        var pomFile = writePom("""
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
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>5.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(Version.fromString("5.10.1")));
        givenMavenRepoVersions("org.mockito", "mockito-core", List.of(
                Version.fromString("5.0.0"),
                Version.fromString("5.1.0")
        ));

        var output = runCli("check", pomFile.toString(), "-f", "text", "--show-all");

        then(output).contains("junit-jupiter");
        then(output).contains("mockito-core");
    }

    @Test
    void shouldGenerateValidJsonWithAllRequiredFields() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.26.0"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("check", pomFile.toString(), "-f", "json");

        var json = readJson(output);

        // Validate projects array
        then(json.has("projects")).isTrue();
        var projects = json.get("projects");
        then(projects.isArray()).isTrue();
        then(projects.size()).isEqualTo(1);

        // Validate project structure
        var project = projects.get(0);
        then(project.has("pomFile")).isTrue();
        then(project.get("pomFile").asText()).endsWith("pom.xml");
        then(project.has("dependencies")).isTrue();

        // Validate dependencies array
        var dependencies = project.get("dependencies");
        then(dependencies.isArray()).isTrue();
        then(dependencies.size()).isEqualTo(1);

        // Validate dependency fields
        var dependency = dependencies.get(0);
        then(dependency.has("groupId")).isTrue();
        then(dependency.get("groupId").asText()).isEqualTo("org.assertj");
        then(dependency.has("artifactId")).isTrue();
        then(dependency.get("artifactId").asText()).isEqualTo("assertj-core");
        then(dependency.has("scope")).isTrue();
        then(dependency.has("currentVersion")).isTrue();
        then(dependency.get("currentVersion").asText()).isEqualTo("3.25.1");
        then(dependency.has("latestVersion")).isTrue();
        then(dependency.get("latestVersion").asText()).isEqualTo("3.27.7");
        then(dependency.has("updateType")).isTrue();
        then(dependency.get("updateType").asText()).isEqualTo("minor");
        then(dependency.has("availableVersions")).isTrue();

        // Validate availableVersions array
        var availableVersions = dependency.get("availableVersions");
        then(availableVersions.isArray()).isTrue();
        then(availableVersions.size()).isEqualTo(3);
        then(availableVersions.get(0).asText()).isEqualTo("3.25.1");
        then(availableVersions.get(1).asText()).isEqualTo("3.26.0");
        then(availableVersions.get(2).asText()).isEqualTo("3.27.7");

        // Validate summary object
        then(json.has("summary")).isTrue();
        var summary = json.get("summary");
        then(summary.has("totalDependencies")).isTrue();
        then(summary.get("totalDependencies").asInt()).isEqualTo(1);
        then(summary.has("outdatedDependencies")).isTrue();
        then(summary.get("outdatedDependencies").asInt()).isEqualTo(1);
        then(summary.has("majorUpdates")).isTrue();
        then(summary.get("majorUpdates").asInt()).isEqualTo(0);
        then(summary.has("minorUpdates")).isTrue();
        then(summary.get("minorUpdates").asInt()).isEqualTo(1);
        then(summary.has("patchUpdates")).isTrue();
        then(summary.get("patchUpdates").asInt()).isEqualTo(0);
    }

    private static JsonNode readJson(String output) {
        var objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(output);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldUsePomXmlWhenNoFilesSpecified() throws IOException, InterruptedException {
        createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "check", "-f", "text");

        then(output).contains("Maven Dependency Update Report");
        then(output).contains("assertj-core");
    }

    @Test
    void shouldUseCheckAsDefaultCommand() throws IOException, InterruptedException {
        createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "-f", "text");

        then(output).contains("Maven Dependency Update Report");
        then(output).contains("assertj-core");
    }

    @Test
    void shouldAcceptMavenCentralUrlOption() throws IOException, InterruptedException {
        createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "check", "--maven-central-url", fakeMavenRepo(), "-f", "text");

        then(output).contains("Maven Dependency Update Report");
        then(output).contains("assertj-core");
    }

    @Test
    void shouldAcceptJsonShortcutOption() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("check", pomFile.toString(), "--json");

        then(output).contains("\"artifactId\" : \"assertj-core\"");
    }

    @Test
    void shouldAcceptTextShortcutOption() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("check", pomFile.toString(), "--text");

        then(output).contains("Maven Dependency Update Report");
    }

    @Test
    void shouldAnalyzeMultiModuleProject() throws IOException, InterruptedException {
        var parentDir = tempDir.resolve("parent");
        var moduleADir = parentDir.resolve("module-a");
        var moduleBDir = parentDir.resolve("module-b");
        java.nio.file.Files.createDirectories(moduleADir);
        java.nio.file.Files.createDirectories(moduleBDir);
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
                            <groupId>org.assertj</groupId>
                            <artifactId>assertj-core</artifactId>
                            <version>3.25.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var parentPomFile = parentDir.resolve("pom.xml");
        writeString(parentPomFile, parentPomContent);
        writeString(moduleADir.resolve("pom.xml"), moduleAPomContent);
        writeString(moduleBDir.resolve("pom.xml"), moduleBPomContent);
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.10.1")
        ));
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.0"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("check", parentPomFile.toString(), "-f", "text");

        then(output).contains("Maven Dependency Update Report");
        then(output).contains("module-a/pom.xml");
        then(output).contains("junit-jupiter");
        then(output).contains("module-b/pom.xml");
        then(output).contains("assertj-core");
    }
}
