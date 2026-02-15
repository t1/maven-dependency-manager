package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class CacheTtlOptionIT extends BaseCliIT {

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

    private void preSeedCacheWithOnlyCurrentVersion() throws IOException {
        var groupPath = GROUP_ID.replace('.', '/');
        var cacheDir = tempDir.resolve(".m2/repository").resolve(groupPath).resolve(ARTIFACT_ID);
        Files.createDirectories(cacheDir);
        writeString(cacheDir.resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <versions>
                        <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """.formatted(GROUP_ID, ARTIFACT_ID));
    }

    private void givenRemoteHasNewerVersion() {
        givenMavenRepoVersions(GROUP_ID, ARTIFACT_ID, List.of(
                Version.fromString("1.0.0"),
                Version.fromString("2.0.0")
        ));
    }

    @Test
    void shouldRefreshCacheWhenTtlIsZero() throws IOException, InterruptedException {
        createPom();
        preSeedCacheWithOnlyCurrentVersion();
        givenRemoteHasNewerVersion();

        var output = runCli(tempDir, "check", "--cache-ttl", "0", "-f", "text", "--show-all");

        then(output).contains("2.0.0");
    }

    @Test
    void shouldUseCacheWhenTtlIsLarge() throws IOException, InterruptedException {
        createPom();
        preSeedCacheWithOnlyCurrentVersion();
        givenRemoteHasNewerVersion();

        var output = runCli(tempDir, "check", "--cache-ttl", "9999", "-f", "text", "--show-all");

        then(output).doesNotContain("2.0.0");
    }
}
