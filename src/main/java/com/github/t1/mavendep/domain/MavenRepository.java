package com.github.t1.mavendep.domain;

import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.github.t1.mavendep.domain.Logger.log;
import static java.net.http.HttpResponse.BodyHandlers;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static java.util.Collections.sort;

/// Client for fetching Maven artifact metadata from Maven Central.
///
/// Implements local caching with configurable TTL to minimize network requests.
public class MavenRepository {
    public static final String DEFAULT_MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Path localRepositoryDir;
    private final Duration ttl;
    private final String mavenCentralUrl;
    private final boolean forceCacheUpdate;

    private MavenRepository(Builder builder) {
        this.localRepositoryDir = builder.localRepositoryDir;
        this.ttl = builder.ttl;
        this.mavenCentralUrl = builder.mavenCentralUrl;
        this.forceCacheUpdate = builder.forceCacheUpdate;
    }

    public static Builder builder() {return new Builder();}

    public static class Builder {
        private Path localRepositoryDir = Path.of(System.getProperty("maven.repo.local",
                System.getProperty("user.home") + "/.m2/repository"));
        private Duration ttl = Duration.ofHours(24);
        private String mavenCentralUrl = System.getProperty("maven.central.url", DEFAULT_MAVEN_CENTRAL_URL);
        private boolean forceCacheUpdate = false;

        public Builder localRepositoryDir(Path localRepositoryDir) {
            this.localRepositoryDir = localRepositoryDir;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder mavenCentralUrl(String mavenCentralUrl) {
            this.mavenCentralUrl = mavenCentralUrl;
            return this;
        }

        public Builder forceCacheUpdate(boolean forceCacheUpdate) {
            this.forceCacheUpdate = forceCacheUpdate;
            return this;
        }

        public MavenRepository build() {return new MavenRepository(this);}
    }

    /// Retrieves all available versions for the specified Maven artifact.
    ///
    /// Uses local cache if available and fresh, otherwise fetches from Maven Central.
    /// Returns empty list if the artifact cannot be found or any error occurs.
    ///
    /// @param groupId    the Maven group ID (e.g., "org.junit.jupiter")
    /// @param artifactId the Maven artifact ID (e.g., "junit-jupiter")
    /// @return sorted list of all available versions, or empty list if unavailable
    public List<Version> getAvailableVersions(String groupId, String artifactId) {
        try {
            var cacheFile = metadataFilePath(groupId, artifactId);

            String metadata;
            metadata = exists(cacheFile) && isCacheFresh(cacheFile)
                    ? readString(cacheFile)
                    : writeCache(cacheFile, fetchMetadata(groupId, artifactId));

            return parseMetadata(metadata);
        } catch (IOException | InterruptedException | ParserConfigurationException | SAXException e) {
            log().error(new ArtifactRef(groupId, artifactId), "Failed to get available versions, assuming none available: " + e, e);
            return List.of();
        }
    }

    private boolean isCacheFresh(Path cacheFile) {
        if (forceCacheUpdate) return false;
        try {
            var lastModified = getLastModifiedTime(cacheFile).toInstant();
            var age = Duration.between(lastModified, Instant.now());
            return age.compareTo(ttl) < 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String writeCache(Path cacheFile, String metadata) throws IOException {
        var parentDir = cacheFile.getParent();
        if (parentDir != null) {
            createDirectories(parentDir);
        }
        writeString(cacheFile, metadata);
        return metadata;
    }

    private String fetchMetadata(String groupId, String artifactId) throws IOException, InterruptedException {
        var uri = metadataUri(groupId, artifactId);
        log().info("Fetching metadata for " + groupId + ":" + artifactId + " from " + uri.getAuthority());
        var request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        var response = httpClient.send(request, BodyHandlers.ofString());
        var metadata = response.body();
        var contentType = response.headers().firstValue("Content-Type").orElse(null);

        if (response.statusCode() != 200 || !isXml(contentType)) {
            throw new IOException("Failed to fetch metadata from " + uri +
                                  ((metadata == null || "text/html".equals(contentType)) ? "" : ": " + metadata));
        }
        return metadata;
    }

    private static boolean isXml(String contentType) {
        return switch (contentType) {
            case "text/xml", "application/xml" -> true;
            case null, default -> false;
        };
    }

    private List<Version> parseMetadata(String metadataXml) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new ByteArrayInputStream(metadataXml.getBytes()));

        var versions = new ArrayList<Version>();
        var versionNodes = doc.getElementsByTagName("version");

        for (var i = 0; i < versionNodes.getLength(); i++) {
            var versionNode = versionNodes.item(i);
            var parentName = versionNode.getParentNode().getNodeName();

            if ("versions".equals(parentName)) {
                var versionString = versionNode.getTextContent();
                versions.add(Version.fromString(versionString));
            }
        }

        sort(versions);
        return versions;
    }

    private URI metadataUri(String groupId, String artifactId) {
        var groupPath = groupId.replace('.', '/');
        return URI.create(String.format("%s/%s/%s/maven-metadata.xml", mavenCentralUrl, groupPath, artifactId));
    }

    Path metadataFilePath(String groupId, String artifactId) {
        var groupPath = groupId.replace('.', '/');
        return localRepositoryDir.resolve(groupPath).resolve(artifactId).resolve("maven-metadata.xml");
    }
}
