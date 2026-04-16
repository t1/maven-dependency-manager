package com.github.t1.mavendep.domain;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType;
import static java.util.Objects.requireNonNull;

public record Update(
        Dependency dependency,
        Version latestVersion,
        List<Version> availableVersions,
        UpdateType updateType,
        Version committedVersion
) {
    public Update(Dependency dependency, Version latestVersion, List<Version> availableVersions, UpdateType updateType) {
        this(dependency, latestVersion, availableVersions, updateType, null);
    }

    public Update {
        requireNonNull(dependency);
        requireNonNull(updateType);
    }

    public Update withCommittedVersion(Version committedVersion) {
        return new Update(dependency, latestVersion, availableVersions, updateType, committedVersion);
    }

    public DependencyType type() {return dependency.type();}

    public String groupId() {return dependency.groupId();}

    public String artifactId() {return dependency.artifactId();}

    public ArtifactRef artifactRef() {return dependency.artifactRef();}

    public Version currentVersion() {return dependency.version();}

    public Scope scope() {return dependency.scope();}

    public String versionProperty() {return dependency.versionProperty();}

    public String profile() {return dependency.profile();}

    public String formatScope() {
        var base = (type() == DependencyType.dependency) ? "dependency/" + scope() : type().toString();
        return profile() != null ? base + "@" + profile() : base;
    }

    public boolean isChange() {
        return currentVersion() != null && latestVersion() != null && latestVersion().compareTo(currentVersion()) != 0;
    }
}
