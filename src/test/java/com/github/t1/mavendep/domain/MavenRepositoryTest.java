package com.github.t1.mavendep.domain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.BDDAssertions.then;

class MavenRepositoryTest {

    @TempDir
    Path tempDir;

    private final FakeRepo fakeRepo = new FakeRepo();

    @AfterEach void tearDown() {
        fakeRepo.close();
    }

    @Test
    void shouldFetchAvailableVersions() {
        fakeRepo.givenVersions("org.springframework.boot", "spring-boot", List.of("2.0.0", "2.1.0", "3.0.0", "3.2.1"));
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions(new ArtifactRef("org.springframework.boot", "spring-boot"));

        then(versions).containsExactly(
                Version.fromString("2.0.0"),
                Version.fromString("2.1.0"),
                Version.fromString("3.0.0"),
                Version.fromString("3.2.1"));
    }

    @Test
    void shouldReturnEmptyListForInvalidMetadata() {
        fakeRepo.givenRawMetadata("org.example", "invalid", "<invalid>xml</invalid>");
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions(new ArtifactRef("org.example", "invalid"));

        then(versions).isEmpty();
    }

    @Test
    void shouldIncludeSnapshotVersionsWhenRequested() {
        fakeRepo.givenVersions("org.example", "example", List.of("1.0.0", "1.1.0-SNAPSHOT", "1.1.0"));
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getAvailableVersions(new ArtifactRef("org.example", "example"));

        then(versions).containsExactly(
                Version.fromString("1.0.0"),
                Version.fromString("1.1.0-SNAPSHOT"),
                Version.fromString("1.1.0"));
    }

    @Test
    void shouldUseResolverCacheWhenTtlIsLarge() {
        var artifact = new ArtifactRef("com.example", "some-lib");
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0"));
        repositoryWithLocalTempDir().getAvailableVersions(artifact);
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0", "2.0.0"));

        var versions = repositoryWithLocalTempDir().getAvailableVersions(artifact);

        then(versions).containsExactly(Version.fromString("1.0.0"));
        then(fakeRepo.requestCountFor(artifact)).isEqualTo(1);
    }

    @Test
    void shouldRefreshCacheWhenTtlIsZero() {
        var artifact = new ArtifactRef("com.example", "fresh-lib");
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0"));
        repositoryWithLocalTempDir().getAvailableVersions(artifact);
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0", "2.0.0"));
        var repository = MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .ttl(Duration.ZERO)
                .mavenCentralUrl(fakeRepo.baseUrl())
                .build();

        var versions = repository.getAvailableVersions(artifact);

        then(versions).containsExactly(Version.fromString("1.0.0"), Version.fromString("2.0.0"));
        then(fakeRepo.requestCountFor(artifact)).isEqualTo(2);
    }

    @Test
    void shouldBypassCacheWhenForceCacheUpdateIsEnabled() {
        var artifact = new ArtifactRef("com.example", "cached-lib");
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0"));
        repositoryWithLocalTempDir().getAvailableVersions(artifact);
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0", "2.0.0"));
        var repository = MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .ttl(Duration.ofHours(9999))
                .mavenCentralUrl(fakeRepo.baseUrl())
                .forceCacheUpdate(true)
                .build();

        var versions = repository.getAvailableVersions(artifact);

        then(versions).containsExactly(Version.fromString("1.0.0"), Version.fromString("2.0.0"));
        then(fakeRepo.requestCountFor(artifact)).isEqualTo(2);
    }

    @Test
    void shouldUseSystemPropertyForLocalRepo() {
        var customPath = tempDir.resolve("custom-repo");
        System.setProperty("maven.repo.local", customPath.toString());
        try {
            var repository = MavenRepository.builder().build();

            var path = repository.metadataFilePath(new ArtifactRef("org.example", "test"));

            then(path.toString()).startsWith(customPath.toString());
        } finally {
            System.clearProperty("maven.repo.local");
        }
    }

    @Test
    void shouldMergeVersionsFromCentralAndLocalMetadataForPicker() throws IOException {
        var artifact = new ArtifactRef("org.example", "picker-lib");
        fakeRepo.givenVersions(artifact.groupId(), artifact.artifactId(), List.of("1.0.0"));
        var metadataDir = tempDir.resolve("org/example/picker-lib");
        Files.createDirectories(metadataDir);
        Files.writeString(metadataDir.resolve("maven-metadata-local.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <versioning>
                    <versions>
                      <version>2.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        var repository = repositoryWithLocalTempDir();

        var versions = repository.getPickableVersions(artifact);

        then(versions).containsExactly(
                new AvailableVersion(Version.fromString("1.0.0"), List.of("central")),
                new AvailableVersion(Version.fromString("2.0.0"), List.of("local")));
    }

    @Test
    void shouldReturnCentralMetadataPath() {
        var repository = repositoryWithLocalTempDir();

        var path = repository.metadataFilePath(new ArtifactRef("org.example", "test"));

        then(path.toString()).endsWith("org/example/test/maven-metadata-central.xml");
    }

    private MavenRepository repositoryWithLocalTempDir() {
        return MavenRepository.builder()
                .localRepositoryDir(tempDir)
                .mavenCentralUrl(fakeRepo.baseUrl())
                .build();
    }

    private static class FakeRepo implements AutoCloseable {
        private final HttpServer httpServer;
        private final ConcurrentHashMap<String, String> responses = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

        private FakeRepo() {
            try {
                httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                httpServer.createContext("/", this::handle);
                httpServer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void givenVersions(String groupId, String artifactId, List<String> versions) {
            var versionsXml = versions.stream()
                    .map(version -> "      <version>" + version + "</version>")
                    .reduce("", (left, right) -> left + right + "\n");
            givenRawMetadata(groupId, artifactId, """
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
                    """.formatted(groupId, artifactId, versionsXml));
        }

        void givenRawMetadata(String groupId, String artifactId, String metadata) {
            responses.put(pathOf(groupId, artifactId), metadata);
        }

        int requestCountFor(ArtifactRef artifact) {
            return requestCounts.getOrDefault(pathOf(artifact.groupId(), artifact.artifactId()), new AtomicInteger()).get();
        }

        String baseUrl() {return "http://localhost:" + httpServer.getAddress().getPort();}

        @Override public void close() {
            httpServer.stop(0);
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                var path = exchange.getRequestURI().getPath();
                requestCounts.computeIfAbsent(path, _ -> new AtomicInteger()).incrementAndGet();
                var response = responses.get(path);
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                var bytes = response.getBytes(UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static String pathOf(String groupId, String artifactId) {
            return "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        }
    }
}
