package com.github.t1.mavendep.domain;

import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;

public record EffectivePom(
        Optional<Version> parentVersion,
        Map<ArtifactRef, Version> dependencyVersions,
        Map<ArtifactRef, Version> pluginVersions
) {
    public static final EffectivePom EMPTY = new EffectivePom(Optional.empty(), Map.of(), Map.of());

    static EffectivePom from(Pom pom) {
        return new EffectivePom(
                pom.parent().map(Dependency::version),
                pom.dependencies().stream()
                        .filter(Dependency::isValid)
                        .collect(toMap(Dependency::artifactRef, Dependency::version, (left, _) -> left)),
                pom.plugins().stream()
                        .filter(Dependency::isValid)
                        .collect(toMap(Dependency::artifactRef, Dependency::version, (left, _) -> left))
        );
    }

    public Version versionFor(Dependency dependency) {
        if (dependency.profile() != null) return dependency.version();
        return switch (dependency.type()) {
            case parent -> parentVersion.orElse(dependency.version());
            case dependency -> dependencyVersions.getOrDefault(dependency.artifactRef(), dependency.version());
            case plugin -> pluginVersions.getOrDefault(dependency.artifactRef(), dependency.version());
        };
    }
}
