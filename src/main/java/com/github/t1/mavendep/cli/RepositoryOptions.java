package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.MavenRepository;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Duration;

import static com.github.t1.mavendep.domain.MavenRepository.DEFAULT_MAVEN_CENTRAL_URL;

/// Maven repository configuration options.
class RepositoryOptions {

    @Option(
            names = {"--maven-central-url"},
            description = "Maven Central URL (default: ${DEFAULT-VALUE})",
            defaultValue = DEFAULT_MAVEN_CENTRAL_URL
    )
    String mavenCentralUrl;

    @Option(
            names = {"--cache-ttl"},
            description = "Cache TTL in hours (default: 24)"
    )
    Integer cacheTtlHours;

    @Option(
            names = {"--local-repo"},
            description = "Local repository path (default: ${maven.repo.local} or ~/.m2/repository)"
    )
    String localRepository;

    @Option(
            names = {"-f", "--force-cache-update"},
            description = "Force refresh of all cached Maven metadata"
    )
    boolean forceCacheUpdate;

    MavenRepository createMavenRepository() {
        var builder = MavenRepository.builder()
                .forceCacheUpdate(forceCacheUpdate);

        if (localRepository != null) builder.localRepositoryDir(Path.of(localRepository));
        if (cacheTtlHours != null) builder.ttl(Duration.ofHours(cacheTtlHours));
        if (mavenCentralUrl != null) builder.mavenCentralUrl(mavenCentralUrl);

        return builder.build();
    }
}
