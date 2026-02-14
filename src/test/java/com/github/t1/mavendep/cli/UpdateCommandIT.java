package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class UpdateCommandIT extends BaseCliIT {

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
    void shouldUpdateSinglePomFileAndGenerateReport() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        then(output).contains("Updated: " + pomFile);
        then(contentOf(pomFile.toFile()))
                .contains("<version>3.27.7</version>");
    }

    @Test
    void shouldUpdatePropertyBasedVersions() throws IOException, InterruptedException {
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

        runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        then(contentOf(pomFile.toFile()))
                .contains("<jackson.version>2.16.0</jackson.version>")
                .doesNotContain("2.15.0");
    }

    @Test
    void shouldPreserveXmlFormattingAfterUpdate() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.junit.jupiter", "junit-jupiter", "5.10.0");
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.10.1")
        ));

        runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        then(contentOf(pomFile.toFile()))
                .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">")
                .contains("    <modelVersion>4.0.0</modelVersion>");
    }

    @Test
    void shouldSkipPomWithNoUpdates() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.junit.jupiter", "junit-jupiter", "5.10.1");
        var originalContent = contentOf(pomFile.toFile());
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(Version.fromString("5.10.1")));

        var output = runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        then(output).doesNotContain("Updated:");
        var unchangedContent = contentOf(pomFile.toFile());
        then(unchangedContent).isEqualTo(originalContent);
    }

    @Test
    void shouldUsePomXmlWhenNoFilesSpecified() throws IOException, InterruptedException {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "update", "-f", "text");

        then(output).contains("Updated:");
        then(contentOf(pomFile.toFile())).contains("<version>3.27.7</version>");
    }

    @Test
    void shouldUpdateVersionWithWhitespace() throws IOException, InterruptedException {
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
                            <version>
                                5.10.0
                            </version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.11.0")
        ));

        runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("5.11.0");
        then(content).doesNotContain("5.10.0");
    }

    @Test
    void shouldUpdateVersionWithComment() throws IOException, InterruptedException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.sshd</groupId>
                            <artifactId>sshd-common</artifactId>
                            <version>
                                <!--we can't go to 2.15.x, because Quarkus elytron LDAP depends on this conflicting version of sshd-common-->
                                2.12.1
                            </version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("org.apache.sshd", "sshd-common", List.of(
                Version.fromString("2.12.1"),
                Version.fromString("2.13.0")
        ));

        runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("2.13.0");
        then(content).doesNotContain("2.12.1");
        then(content).contains("<!--we can't go to 2.15.x, because Quarkus elytron LDAP depends on this conflicting version of sshd-common-->");
    }

    @Test
    void shouldUpdatePluginVersion() throws IOException, InterruptedException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.8.1</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        givenMavenRepoVersions("org.apache.maven.plugins", "maven-compiler-plugin", List.of(
                Version.fromString("3.8.1"),
                Version.fromString("3.13.0")
        ));

        var output = runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        then(output).contains("Updated: " + pomFile);
        then(contentOf(pomFile.toFile()))
                .contains("<version>3.13.0</version>")
                .doesNotContain("<version>3.8.1</version>");
    }

    @Test
    void shouldUpdatePropertyVersionWithWhitespaceAndComment() throws IOException, InterruptedException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <sshd.version>
                            <!-- Don't upgrade beyond 2.12.x due to Quarkus compatibility -->
                            2.12.1
                        </sshd.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.sshd</groupId>
                            <artifactId>sshd-common</artifactId>
                            <version>${sshd.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("org.apache.sshd", "sshd-common", List.of(
                Version.fromString("2.12.1"),
                Version.fromString("2.13.0")
        ));

        runCli(null, new String[]{"update", pomFile.toString(), "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("2.13.0");
        then(content).doesNotContain("2.12.1");
        then(content).contains("<!-- Don't upgrade beyond 2.12.x due to Quarkus compatibility -->");
    }

    // --- Dependency Filter Tests ---

    private Path createPomWithTwoDependencies() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>

                    <dependencies>
                        <dependency>
                            <groupId>org.assertj</groupId>
                            <artifactId>assertj-core</artifactId>
                            <version>3.25.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));
        givenMavenRepoVersions("org.junit.jupiter", "junit-jupiter", List.of(
                Version.fromString("5.10.0"),
                Version.fromString("5.11.0")
        ));
        return pomFile;
    }

    @Test
    void shouldFilterByGroupIdAndArtifactId() throws IOException, InterruptedException {
        var pomFile = createPomWithTwoDependencies();

        runCli(null, new String[]{"update", pomFile.toString(), "--only", "org.assertj:assertj-core", "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("<version>3.27.7</version>");
        then(content).contains("<version>5.10.0</version>");
    }

    @Test
    void shouldFilterByGroupId() throws IOException, InterruptedException {
        var pomFile = createPomWithTwoDependencies();

        runCli(null, new String[]{"update", pomFile.toString(), "--only", "org.assertj", "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("<version>3.27.7</version>");
        then(content).contains("<version>5.10.0</version>");
    }

    @Test
    void shouldFilterByArtifactId() throws IOException, InterruptedException {
        var pomFile = createPomWithTwoDependencies();

        runCli(null, new String[]{"update", pomFile.toString(), "--only", "assertj-core", "-f", "text"});

        var content = contentOf(pomFile.toFile());
        then(content).contains("<version>3.27.7</version>");
        then(content).contains("<version>5.10.0</version>");
    }

    @Test
    void shouldFailWhenFilterMatchesNoDependency() {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        var originalContent = contentOf(pomFile.toFile());
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        thenThrownBy(() -> runCli(null, true, new String[]{"update", pomFile.toString(), "--only", "nonexistent:artifact", "-f", "text"}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("exit code")
                .hasMessageContaining("No dependencies match the filter")
                .hasMessageNotContaining("Exception")
                .hasMessageNotContaining("at com.");

        then(contentOf(pomFile.toFile())).isEqualTo(originalContent);
    }
}
