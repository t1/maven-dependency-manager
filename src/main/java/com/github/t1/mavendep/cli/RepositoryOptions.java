package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.MavenRepository;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Duration;

/// Maven repository configuration options.
class RepositoryOptions {

    @Option(
            names = {"--maven-central-url"},
            description = "Maven Central URL (default: https://repo1.maven.org/maven2)"
    )
    String mavenCentralUrl;

    @Option(
            names = {"--cache-ttl"},
            description = "Cache TTL in hours (default: 24)"
    )
    Integer cacheTtlHours;

    @Option(
            names = {"--local-repo"},
            description = "Local repository path (default: ~/.m2/repository)"
    )
    String localRepository;

    MavenRepository createMavenRepository() {
        var localRepo = localRepository != null
                ? Path.of(localRepository)
                : Path.of(System.getProperty("user.home"), ".m2", "repository");

        var ttl = cacheTtlHours != null
                ? Duration.ofHours(cacheTtlHours)
                : Duration.ofHours(24);

        var url = mavenCentralUrl != null
                ? mavenCentralUrl
                : System.getProperty("maven.central.url", "https://repo1.maven.org/maven2");

        return new MavenRepository(localRepo, ttl, url);
    }
}
