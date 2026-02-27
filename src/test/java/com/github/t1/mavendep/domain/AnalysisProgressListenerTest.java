package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisProgressListenerTest {

    @TempDir Path tempDir;

    @Mock MavenRepository mockRepository;
    @Mock AnalysisProgressListener listener;

    @Test void shouldCallListenerForEachDependency() throws Exception {
        var pomFile = tempDir.resolve("pom.xml");
        writeString(pomFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>5.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        given(mockRepository.getAvailableVersions("org.junit", "junit"))
                .willReturn(List.of(Version.fromString("5.0.0"), Version.fromString("5.1.0")));

        var analyzer = new DependencyAnalyzer(mockRepository, List.of(pomFile), listener);
        analyzer.run();

        verify(listener, atLeastOnce()).onProgress(anyInt(), anyInt(), eq("org.junit:junit"));
    }

    @Test void shouldReportCorrectTotal() throws Exception {
        var pomFile = tempDir.resolve("pom.xml");
        writeString(pomFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.a</groupId>
                            <artifactId>a</artifactId>
                            <version>1.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.b</groupId>
                            <artifactId>b</artifactId>
                            <version>1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        given(mockRepository.getAvailableVersions(anyString(), anyString()))
                .willReturn(List.of(Version.fromString("1.0"), Version.fromString("2.0")));

        var analyzer = new DependencyAnalyzer(mockRepository, List.of(pomFile), listener);
        analyzer.run();

        verify(listener, atLeastOnce()).onProgress(anyInt(), eq(2), anyString());
    }
}
