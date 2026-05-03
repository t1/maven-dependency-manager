package com.github.t1.mavendep.domain;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static java.nio.file.Files.list;
import static java.nio.file.Files.readString;

import static com.github.t1.mavendep.domain.Logger.log;

/// Client for resolving available Maven artifact versions through Maven Resolver.
///
/// Uses Maven's local repository manager and remote repository metadata handling
/// instead of downloading and caching `maven-metadata.xml` manually.
public class MavenRepository {
    public static final String DEFAULT_MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";
    static final String DEFAULT_REPOSITORY_ID = "central";

    private final Path localRepositoryDir;
    private final Duration ttl;
    private final String mavenCentralUrl;
    private final boolean forceCacheUpdate;
    private final RepositorySystem repositorySystem;

    private MavenRepository(Builder builder) {
        this.localRepositoryDir = builder.localRepositoryDir;
        this.ttl = builder.ttl;
        this.mavenCentralUrl = builder.mavenCentralUrl;
        this.forceCacheUpdate = builder.forceCacheUpdate;
        this.repositorySystem = newRepositorySystem();
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
    /// Uses Maven Resolver with the configured local repository and update policy.
    /// Returns empty list if the artifact cannot be found or any error occurs.
    public List<Version> getAvailableVersions(ArtifactRef artifact) {
        try {
            return resolveVersions(artifact);
        } catch (Exception e) {
            log().error(artifact, "Failed to get available versions, assuming none available: " + e, e);
            return List.of();
        }
    }

    /// Retrieves versions that should be offered in the version picker,
    /// merging Maven Resolver results with versions found in cached local metadata files.
    public List<AvailableVersion> getPickableVersions(ArtifactRef artifact) {
        try {
            var versions = resolveVersions(artifact);
            var byVersion = new LinkedHashMap<Version, AvailableVersion>();
            mergeMetadataVersions(artifact).forEach((version, sources) -> byVersion.put(
                    version,
                    new AvailableVersion(version, sources.stream().toList())));
            versions.forEach(version -> byVersion.merge(
                    version,
                    new AvailableVersion(version, List.of(DEFAULT_REPOSITORY_ID)),
                    MavenRepository::keepExplicitSources));
            return byVersion.values().stream().sorted((left, right) -> left.version().compareTo(right.version())).toList();
        } catch (Exception e) {
            log().warning(artifact, "Failed to get pickable versions from metadata files: " + e);
            return getAvailableVersions(artifact).stream()
                    .map(version -> new AvailableVersion(version, List.of(DEFAULT_REPOSITORY_ID)))
                    .toList();
        }
    }

    @SuppressWarnings("deprecation")
    private RepositorySystem newRepositorySystem() {
        var locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(org.eclipse.aether.spi.connector.RepositoryConnectorFactory.class,
                BasicRepositoryConnectorFactory.class);
        locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class,
                HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw new RuntimeException("failed to create service " + type.getName() + " via " + impl.getName(), exception);
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    private DefaultRepositorySystemSession newSession() {
        var session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session,
                new LocalRepository(localRepositoryDir.toAbsolutePath().toString())));
        session.setUpdatePolicy(updatePolicy());
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_WARN);
        return session;
    }

    private RemoteRepository remoteRepository() {
        var policy = new RepositoryPolicy(true, updatePolicy(), RepositoryPolicy.CHECKSUM_POLICY_WARN);
        return new RemoteRepository.Builder(DEFAULT_REPOSITORY_ID, "default", mavenCentralUrl)
                .setReleasePolicy(policy)
                .setSnapshotPolicy(policy)
                .build();
    }

    private List<Version> resolveVersions(ArtifactRef artifact) throws Exception {
        var request = new VersionRangeRequest();
        request.setArtifact(new DefaultArtifact(artifact.groupId(), artifact.artifactId(), "jar", "[0,)"));
        request.addRepository(remoteRepository());

        var result = repositorySystem.resolveVersionRange(newSession(), request);
        return result.getVersions().stream()
                .map(this::toVersion)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Version, LinkedHashSet<String>> mergeMetadataVersions(ArtifactRef artifact) throws IOException {
        var artifactDir = metadataFilePath(artifact).getParent();
        if (artifactDir == null) return Map.of();
        var merged = new LinkedHashMap<Version, LinkedHashSet<String>>();
        if (!artifactDir.toFile().exists()) return merged;
        try (var files = list(artifactDir)) {
            files.filter(path -> path.getFileName().toString().startsWith("maven-metadata")
                                 && path.getFileName().toString().endsWith(".xml"))
                    .forEach(path -> parseMetadataUnchecked(read(path), sourceOf(path)).forEach((version, source) ->
                            merged.computeIfAbsent(version, _ -> new LinkedHashSet<>()).add(source)));
        }
        return merged;
    }

    private Map<Version, String> parseMetadataUnchecked(String metadata, String source) {
        try {
            return parseMetadata(metadata, source);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException("failed to parse metadata", e);
        }
    }

    private Map<Version, String> parseMetadata(String metadata, String source)
            throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var document = builder.parse(new java.io.ByteArrayInputStream(metadata.getBytes()));
        var versions = new LinkedHashMap<Version, String>();
        var versionNodes = document.getElementsByTagName("version");
        for (var i = 0; i < versionNodes.getLength(); i++) {
            var versionNode = versionNodes.item(i);
            var parent = versionNode.getParentNode();
            if (!(parent instanceof Node node) || !"versions".equals(node.getNodeName())) continue;
            versions.put(Version.fromString(versionNode.getTextContent().trim()), source);
        }
        return versions;
    }

    private String read(Path path) {
        try {
            return readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AvailableVersion keepExplicitSources(AvailableVersion existing, AvailableVersion fallback) {
        return existing.sources().isEmpty() ? fallback : existing;
    }

    private static String sourceOf(Path path) {
        var fileName = path.getFileName().toString();
        if (fileName.equals("maven-metadata-local.xml")) return "local";
        if (fileName.equals("maven-metadata.xml")) return "unknown";
        return fileName.replaceFirst("^maven-metadata-", "").replaceFirst("\\.xml$", "");
    }

    private String updatePolicy() {
        if (forceCacheUpdate) return RepositoryPolicy.UPDATE_POLICY_ALWAYS;
        var minutes = ttl.toMinutes();
        if (minutes <= 0) return RepositoryPolicy.UPDATE_POLICY_ALWAYS;
        return RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":" + minutes;
    }

    private Version toVersion(org.eclipse.aether.version.Version version) {
        return Version.fromString(version.toString());
    }

    Path metadataFilePath(ArtifactRef artifact) {
        var groupPath = artifact.groupId().replace('.', '/');
        return localRepositoryDir.resolve(groupPath)
                .resolve(artifact.artifactId())
                .resolve("maven-metadata-" + DEFAULT_REPOSITORY_ID + ".xml");
    }
}
