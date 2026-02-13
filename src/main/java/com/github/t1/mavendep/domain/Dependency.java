package com.github.t1.mavendep.domain;


import java.util.List;

import static com.github.t1.mavendep.domain.Scope.DEFAULT;

public record Dependency(String groupId, String artifactId, Version version, Scope scope, String versionProperty) {
    public boolean isValid() {
        return groupId != null && artifactId != null && version != null;
    }

    public boolean isManaged() {
        return groupId != null && artifactId != null && version == null;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId +
               ((version == null) ? "" : ":" + version) +
               ((scope == DEFAULT) ? "" : ":" + scope);
    }

    public DependencyUpdate toUpdate(
            DependencyType dependencyType,
            Version latestVersion,
            List<Version> availableVersions,
            UpdateType updateType) {
        return new DependencyUpdate(
                dependencyType,
                groupId() != null ? groupId() : "",
                artifactId() != null ? artifactId() : "",
                version(),
                scope(),
                versionProperty(),
                latestVersion,
                availableVersions,
                updateType
        );
    }
}
