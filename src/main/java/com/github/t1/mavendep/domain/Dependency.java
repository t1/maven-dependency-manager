package com.github.t1.mavendep.domain;


import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;

public record Dependency(
        DependencyType type,
        Coordinates coordinates,
        Scope scope,
        String versionProperty) {
    public enum DependencyType {
        parent,
        dependency,
        plugin
    }

    public Dependency(DependencyType type, String groupId, String artifactId, Version version, Scope scope, String versionProperty) {
        this(type, new Coordinates(groupId, artifactId, version), scope, versionProperty);
    }

    public String groupId() {return coordinates.groupId();}

    public String artifactId() {return coordinates.artifactId();}

    public ArtifactRef artifactRef() {return new ArtifactRef(groupId(), artifactId());}

    public Version version() {return coordinates.version();}

    public boolean isValid() {
        return hasGroupId() && hasArtifactId() && version() != null;
    }

    public boolean isManaged() {
        return hasGroupId() && hasArtifactId() && version() == null;
    }

    private boolean hasGroupId() {return groupId() != null && !groupId().isEmpty();}

    private boolean hasArtifactId() {return artifactId() != null && !artifactId().isEmpty();}

    @Override
    public String toString() {
        return (type == dependency ? "" : type + "=") +
               groupId() + ":" + artifactId() +
               ((version() == null) ? "" : ":" + version()) +
               ((scope == DEFAULT) ? "" : ":" + scope);
    }

    public Dependency with(Version version) {
        return new Dependency(type, groupId(), artifactId(), version, scope, versionProperty);
    }

    public DependencyUpdate toUpdate(
            Version latestVersion,
            List<Version> availableVersions,
            UpdateType updateType) {
        return new DependencyUpdate(this, latestVersion, availableVersions, updateType);
    }
}
