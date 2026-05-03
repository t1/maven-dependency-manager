package com.github.t1.mavendep.domain;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType;
import static java.util.Objects.requireNonNull;

public record Update(
        Dependency dependency,
        Version effectiveVersion,
        Version latestVersion,
        List<Version> availableVersions,
        List<AvailableVersion> pickableVersions,
        UpdateType updateType,
        Version committedVersion,
        VersionStatus versionStatus
) {
    public Update(Dependency dependency, Version latestVersion, List<Version> availableVersions, UpdateType updateType) {
        this(dependency, dependency.version(), latestVersion, availableVersions, defaultPickableVersions(availableVersions), updateType, null);
    }

    public Update(
            Dependency dependency,
            Version latestVersion,
            List<Version> availableVersions,
            List<AvailableVersion> pickableVersions,
            UpdateType updateType) {
        this(dependency, dependency.version(), latestVersion, availableVersions, pickableVersions, updateType, null);
    }

    public Update(
            Dependency dependency,
            Version effectiveVersion,
            Version latestVersion,
            List<Version> availableVersions,
            UpdateType updateType) {
        this(dependency, effectiveVersion, latestVersion, availableVersions, defaultPickableVersions(availableVersions), updateType, null);
    }

    public Update(
            Dependency dependency,
            Version effectiveVersion,
            Version latestVersion,
            List<Version> availableVersions,
            List<AvailableVersion> pickableVersions,
            UpdateType updateType) {
        this(dependency, effectiveVersion, latestVersion, availableVersions, pickableVersions, updateType, null);
    }

    public Update(
            Dependency dependency,
            Version effectiveVersion,
            Version latestVersion,
            List<Version> availableVersions,
            UpdateType updateType,
            Version committedVersion) {
        this(dependency, effectiveVersion, latestVersion, availableVersions, defaultPickableVersions(availableVersions), updateType, committedVersion,
                VersionStatus.of(effectiveVersion, latestVersion));
    }

    public Update(
            Dependency dependency,
            Version effectiveVersion,
            Version latestVersion,
            List<Version> availableVersions,
            List<AvailableVersion> pickableVersions,
            UpdateType updateType,
            Version committedVersion) {
        this(dependency, effectiveVersion, latestVersion, availableVersions, pickableVersions, updateType, committedVersion,
                VersionStatus.of(effectiveVersion, latestVersion));
    }

    public Update {
        requireNonNull(dependency);
        requireNonNull(updateType);
        requireNonNull(pickableVersions);
        requireNonNull(versionStatus);
    }

    private static List<AvailableVersion> defaultPickableVersions(List<Version> availableVersions) {
        return availableVersions.stream().map(version -> new AvailableVersion(version, List.of())).toList();
    }

    public Update withCommittedVersion(Version committedVersion) {
        return new Update(dependency, effectiveVersion, latestVersion, availableVersions, pickableVersions, updateType, committedVersion, versionStatus);
    }

    public DependencyType type() {return dependency.type();}

    public String groupId() {return dependency.groupId();}

    public String artifactId() {return dependency.artifactId();}

    public ArtifactRef artifactRef() {return dependency.artifactRef();}

    public Version declaredVersion() {return dependency.version();}

    public Version currentVersion() {return effectiveVersion;}

    public Scope scope() {return dependency.scope();}

    public String versionProperty() {return dependency.versionProperty();}

    public String profile() {return dependency.profile();}

    public Dependency.Declaration declaration() {return dependency.declaration();}

    public boolean isManagement() {return dependency.isManagement();}

    public String formatScope() {
        var base = (type() == DependencyType.dependency) ? "dependency/" + scope() : type().toString();
        return profile() != null ? base + "@" + profile() : base;
    }

    public boolean isUpdateAvailable() {return versionStatus.isUpdateAvailable();}

    public boolean isChange() {return isUpdateAvailable();}
}
