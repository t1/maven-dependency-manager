package com.github.t1.mavendep.domain;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType;
import static java.util.Objects.requireNonNull;

public record DependencyUpdate(
        Dependency dependency,
        Version latestVersion,
        List<Version> availableVersions,
        UpdateType updateType
) {
    public DependencyUpdate {
        requireNonNull(dependency);
        requireNonNull(updateType);
    }

    public DependencyType type() {return dependency.type();}

    public String groupId() {return dependency.groupId();}

    public String artifactId() {return dependency.artifactId();}

    public ArtifactRef artifactRef() {return dependency.artifactRef();}

    public Version currentVersion() {return dependency.version();}

    public Scope scope() {return dependency.scope();}

    public String versionProperty() {return dependency.versionProperty();}

    public boolean isChange() {
        return currentVersion() != null && latestVersion() != null && latestVersion().compareTo(currentVersion()) != 0;
    }
}
