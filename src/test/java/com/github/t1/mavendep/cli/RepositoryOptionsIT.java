package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class RepositoryOptionsIT extends BaseCliIT {

    private static final String GROUP_ID = "org.example";
    private static final String ARTIFACT_ID = "some-lib";

    private void createPom() throws IOException {
        writeString(tempDir.resolve("pom.xml"), """
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
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(GROUP_ID, ARTIFACT_ID));
    }

    private void givenRemoteVersions(String... versions) {
        givenMavenRepoVersions(GROUP_ID, ARTIFACT_ID, List.of(versions).stream().map(Version::fromString).toList());
    }

    @Nested
    class CacheTtl {
        @Test
        void shouldRefreshCacheWhenTtlIsZero() throws IOException, InterruptedException {
            createPom();
            givenRemoteVersions("1.0.0");
            runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");
            givenRemoteVersions("1.0.0", "2.0.0");

            var output = runCli(tempDir, "check", "--cache-ttl", "0", "--format", "text", "--show-all");

            then(output).contains("2.0.0");
        }

        @Test
        void shouldUseCacheWhenTtlIsLarge() throws IOException, InterruptedException {
            createPom();
            givenRemoteVersions("1.0.0");
            runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");
            givenRemoteVersions("1.0.0", "2.0.0");

            var output = runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).doesNotContain("2.0.0");
        }
    }

    @Nested
    class ForceCacheUpdate {
        @Test
        void shouldRefreshCacheWhenForceCacheUpdateIsSet() throws IOException, InterruptedException {
            createPom();
            givenRemoteVersions("1.0.0");
            runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");
            givenRemoteVersions("1.0.0", "2.0.0");

            var output = runCli(tempDir, "check", "--force-cache-update", "--format", "text", "--show-all");

            then(output).contains("2.0.0");
        }
    }

    @Nested
    class LocalRepo {
        @Test
        void shouldUseSpecifiedLocalRepo() throws IOException, InterruptedException {
            createPom();
            var customRepo = tempDir.resolve("custom-repo");
            givenRemoteVersions("1.0.0");
            runCli(tempDir, "check", "--local-repo", customRepo.toString(),
                    "--cache-ttl", "9999", "--format", "text", "--show-all");
            givenRemoteVersions("1.0.0", "2.0.0");

            var output = runCli(tempDir, "check", "--local-repo", customRepo.toString(),
                    "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).doesNotContain("2.0.0");
        }

        @Test
        void shouldNotFindCacheInDefaultRepoWhenCustomRepoSpecified() throws IOException, InterruptedException {
            createPom();
            givenRemoteVersions("1.0.0", "2.0.0");
            runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");
            givenRemoteVersions("1.0.0");
            var emptyCustomRepo = tempDir.resolve("empty-custom-repo");
            Files.createDirectories(emptyCustomRepo);

            var output = runCli(tempDir, "check", "--local-repo", emptyCustomRepo.toString(),
                    "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).doesNotContain("2.0.0");
        }
    }
}
