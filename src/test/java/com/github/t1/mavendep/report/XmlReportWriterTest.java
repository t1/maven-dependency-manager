package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Dependency;
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
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class XmlReportWriterTest {

    @TempDir Path tempDir;

    @Test void shouldGenerateValidXml() {
        var update = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), compile, null)
                .toUpdate(Version.fromString("5.10.1"),
                        List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")), patch);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update), List.of(), 1);

        var output = write(report.onlyUpdates());

        then(output).contains("<groupId>org.junit.jupiter</groupId>");
        then(output).contains("<artifactId>junit-jupiter</artifactId>");
        then(output).contains("<effectiveVersion>5.10.0</effectiveVersion>");
        then(output).contains("<latestVersion>5.10.1</latestVersion>");
        then(output).contains("<summary>");
    }

    private String write(ProjectReport... reports) {
        var buffer = new ByteArrayOutputStream();
        new XmlReportWriter(new PrintStream(buffer), List.of(reports)).run();
        return buffer.toString();
    }

    private Pom pom() {
        var pomFile = tempDir.resolve("pom.xml");
        if (!pomFile.toFile().exists()) {
            try {
                writeString(pomFile, """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>test-project</artifactId>
                            <version>1.0.0</version>
                        </project>
                        """);
            } catch (IOException e) {
                throw new RuntimeException("failed to write " + pomFile, e);
            }
        }
        return Pom.parse(pomFile).orElseThrow();
    }
}
