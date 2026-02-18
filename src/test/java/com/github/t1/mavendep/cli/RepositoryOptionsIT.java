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

    private void preSeedCache(Path localRepo, String... versions) throws IOException {
        var groupPath = GROUP_ID.replace('.', '/');
        var cacheDir = localRepo.resolve(groupPath).resolve(ARTIFACT_ID);
        Files.createDirectories(cacheDir);
        var versionsXml = new StringBuilder();
        for (var version : versions) {
            versionsXml.append("            <version>").append(version).append("</version>\n");
        }
        writeString(cacheDir.resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <versions>
                %s\
                    </versions>
                  </versioning>
                </metadata>
                """.formatted(GROUP_ID, ARTIFACT_ID, versionsXml));
    }

    private Path defaultLocalRepo() {
        return tempDir.resolve(".m2/repository");
    }

    private void givenRemoteHasNewerVersion() {
        givenMavenRepoVersions(GROUP_ID, ARTIFACT_ID, List.of(
                Version.fromString("1.0.0"),
                Version.fromString("2.0.0")
        ));
    }

    @Nested
    class CacheTtl {
        @Test
        void shouldRefreshCacheWhenTtlIsZero() throws IOException, InterruptedException {
            createPom();
            preSeedCache(defaultLocalRepo(), "1.0.0");
            givenRemoteHasNewerVersion();

            var output = runCli(tempDir, "check", "--cache-ttl", "0", "--format", "text", "--show-all");

            then(output).contains("2.0.0");
        }

        @Test
        void shouldUseCacheWhenTtlIsLarge() throws IOException, InterruptedException {
            createPom();
            preSeedCache(defaultLocalRepo(), "1.0.0");
            givenRemoteHasNewerVersion();

            var output = runCli(tempDir, "check", "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).doesNotContain("2.0.0");
        }
    }

    @Nested
    class ForceCacheUpdate {
        @Test
        void shouldRefreshCacheWhenForceCacheUpdateIsSet() throws IOException, InterruptedException {
            createPom();
            preSeedCache(defaultLocalRepo(), "1.0.0");
            givenRemoteHasNewerVersion();

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
            preSeedCache(customRepo, "1.0.0", "2.0.0");

            var output = runCli(tempDir, "check", "--local-repo", customRepo.toString(),
                    "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).contains("2.0.0");
        }

        @Test
        void shouldNotFindCacheInDefaultRepoWhenCustomRepoSpecified() throws IOException, InterruptedException {
            createPom();
            preSeedCache(defaultLocalRepo(), "1.0.0", "2.0.0");
            givenMavenRepoVersions(GROUP_ID, ARTIFACT_ID, List.of(Version.fromString("1.0.0")));
            var emptyCustomRepo = tempDir.resolve("empty-custom-repo");
            Files.createDirectories(emptyCustomRepo);

            var output = runCli(tempDir, "check", "--local-repo", emptyCustomRepo.toString(),
                    "--cache-ttl", "9999", "--format", "text", "--show-all");

            then(output).doesNotContain("2.0.0");
        }
    }
}
