package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class ShowAvailableCommandIT extends BaseCliIT {

    @Test void shouldDisplayAvailableVersionsForGroupIdAndArtifactId() throws IOException, InterruptedException {
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.26.0"),
                Version.fromString("3.27.7")
        ));

        var output = runCli("show-available", "org.assertj:assertj-core");

        then(output).contains("3.25.1");
        then(output).contains("3.26.0");
        then(output).contains("3.27.7");
    }

    @Test void shouldDisplayMessageWhenNoVersionsFound() throws IOException, InterruptedException {
        var output = runCli("show-available", "com.nonexistent:no-artifact");

        then(output).contains("No versions found");
    }

    @Test void shouldDisplayAvailableVersionsForArtifactIdOnly() throws IOException, InterruptedException {
        givenPomWithAssertjDependency();
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "show-available", "assertj-core");

        then(output).contains("3.25.1");
        then(output).contains("3.27.7");
    }

    @Test void shouldDisplayAvailableVersionsForGroupIdOnly() throws IOException, InterruptedException {
        givenPomWithAssertjDependency();
        givenMavenRepoVersions("org.assertj", "assertj-core", List.of(
                Version.fromString("3.25.1"),
                Version.fromString("3.27.7")
        ));

        var output = runCli(tempDir, "show-available", "org.assertj");

        then(output).contains("3.25.1");
        then(output).contains("3.27.7");
    }

    private void givenPomWithAssertjDependency() throws IOException {
        writeString(tempDir.resolve("pom.xml"), """
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
                    </dependencies>
                </project>
                """);
    }
}
