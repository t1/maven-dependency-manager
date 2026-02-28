package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UpdateCommandTest {

    @TempDir Path tempDir;

    @Mock MavenRepository repository;

    @Test
    void shouldReturnOneWhenFilterMatchesNoDependency() throws Exception {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        given(repository.getAvailableVersions("org.assertj", "assertj-core"))
                .willReturn(List.of(Version.fromString("3.25.1"), Version.fromString("3.27.7")));
        var stderr = new StringWriter();
        var cmd = new CommandLine(new UpdateCommand(repository));
        cmd.setErr(new java.io.PrintWriter(stderr));

        var exitCode = cmd.execute(pomFile.toString(), "--only", "nonexistent:artifact", "--format", "text");

        then(exitCode).isEqualTo(1);
        then(stderr.toString()).contains("No dependencies match the filter: nonexistent:artifact");
    }

    @Test
    void shouldReturnZeroOnSuccessfulRun() throws Exception {
        var pomFile = createPomWithDependency("org.assertj", "assertj-core", "3.25.1");
        given(repository.getAvailableVersions("org.assertj", "assertj-core"))
                .willReturn(List.of(Version.fromString("3.25.1"), Version.fromString("3.27.7")));

        var exitCode = new CommandLine(new UpdateCommand(repository))
                .execute(pomFile.toString(), "--format", "text");

        then(exitCode).isEqualTo(0);
    }

    private Path createPomWithDependency(String groupId, String artifactId, String version) throws Exception {
        var pomFile = tempDir.resolve("pom.xml");
        writeString(pomFile, """
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
        return pomFile;
    }
}
