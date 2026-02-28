package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.setLastModifiedTime;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class MavenRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseMetadataXml() {
        writeCache("org.springframework.boot", "spring-boot", """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot</artifactId>
                  <versioning>
                    <latest>3.2.1</latest>
                    <release>3.2.1</release>
                    <versions>
                      <version>2.0.0</version>
                      <version>2.1.0</version>
                      <version>3.0.0</version>
                      <version>3.2.1</version>
                    </versions>
                    <lastUpdated>20231215120000</lastUpdated>
                  </versioning>
                </metadata>
                """);
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions("org.springframework.boot", "spring-boot");

        then(versions).hasSize(4);
        then(versions.get(0)).isEqualTo(Version.fromString("2.0.0"));
        then(versions.get(3)).isEqualTo(Version.fromString("3.2.1"));
    }

    @Test
    void shouldReturnEmptyListForInvalidMetadata() {
        writeCache("org.example", "invalid", "<invalid>xml</invalid>");
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions("org.example", "invalid");

        then(versions).isEmpty();
    }

    private void writeCache(String groupId, String artifactId, String metadataXml) {
        var cacheFile = createCacheFile(groupId, artifactId);
        try {
            writeString(cacheFile, metadataXml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldIncludeSnapshotVersionsWhenRequested() {
        writeCache("org.example", "example", """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                      <version>1.1.0-SNAPSHOT</version>
                      <version>1.1.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions("org.example", "example");

        then(versions).hasSize(3);
        then(versions.get(0)).isEqualTo(Version.fromString("1.0.0"));
        then(versions.get(1)).isEqualTo(Version.fromString("1.1.0-SNAPSHOT"));
        then(versions.get(2)).isEqualTo(Version.fromString("1.1.0"));
    }

    @Test
    void shouldUseCacheWhenFresh() throws IOException {
        var cacheFile = createCacheFile("com.example", "some-lib");
        writeString(cacheFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                      <version>2.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions("com.example", "some-lib");

        then(versions).hasSize(2);
        then(versions.getFirst()).isEqualTo(Version.fromString("1.0.0"));
        then(versions.get(1)).isEqualTo(Version.fromString("2.0.0"));
    }


    private Path createCacheFile(String groupId, String artifactId) {
        var cacheFile = metadataFilePath(groupId, artifactId);
        try {
            createDirectories(cacheFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return cacheFile;
    }

    private Path metadataFilePath(String groupId, String artifactId) {
        var groupPath = groupId.replace('.', '/');
        return tempDir.resolve(groupPath).resolve(artifactId).resolve("maven-metadata.xml");
    }

    @Test
    void shouldNotUseCacheWhenStale() throws IOException {
        var cacheFile = createCacheFile("com.example", "old-lib");
        writeString(cacheFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        var twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
        setLastModifiedTime(cacheFile, FileTime.from(twoDaysAgo));
        var repository = MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .ttl(Duration.ofHours(24))
                .mavenCentralUrl("http://localhost:9999")
                .build();

        var versions = repository.getAvailableVersions("com.example", "old-lib");

        then(versions).isEmpty();
    }

    @Test
    void shouldBypassCacheWhenForceCacheUpdateIsEnabled() throws IOException {
        var cacheFile = createCacheFile("com.example", "cached-lib");
        writeString(cacheFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        var repository = MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .ttl(Duration.ofHours(9999))
                .mavenCentralUrl("http://localhost:9999")
                .forceCacheUpdate(true)
                .build();

        var versions = repository.getAvailableVersions("com.example", "cached-lib");

        then(versions).isEmpty();
    }

    @Test
    void shouldUseSystemPropertyForLocalRepo() {
        var customPath = tempDir.resolve("custom-repo");
        System.setProperty("maven.repo.local", customPath.toString());
        try {
            var repository = MavenRepository.builder().build();

            var path = repository.metadataFilePath("org.example", "test");

            then(path.toString()).startsWith(customPath.toString());
        } finally {
            System.clearProperty("maven.repo.local");
        }
    }

    @Test
    void shouldReturnEmptyListForMalformedXml() {
        writeCache("org.example", "malformed", "not xml at all <");
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions("org.example", "malformed");

        then(versions).isEmpty();
    }

    private MavenRepository repositoryWithLocalTempDir() {
        return MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .build();
    }
}
