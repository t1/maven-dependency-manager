package com.github.t1.mavendep.domain;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record DependencyUpdate(
        DependencyType type,
        String groupId,
        String artifactId,
        Version currentVersion,
        Scope scope,
        Version latestVersion,
        List<Version> availableVersions,
        UpdateType updateType
) {
    public DependencyUpdate {
        requireNonNull(type);
        requireNonNull(updateType);
    }

    public boolean isUpdate() {
        return currentVersion() != null && latestVersion() != null && latestVersion().compareTo(currentVersion()) > 0;
    }
}
