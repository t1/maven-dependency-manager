package com.github.t1.mavendep.domain;


import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;

public record Dependency(DependencyType type,
                         String groupId,
                         String artifactId,
                         Version version,
                         Scope scope,
                         String versionProperty) {
    public enum DependencyType {
        parent,
        dependency,
        plugin
    }

    public boolean isValid() {
        return groupId != null && artifactId != null && version != null;
    }

    public boolean isManaged() {
        return groupId != null && artifactId != null && version == null;
    }

    @Override
    public String toString() {
        return (type == dependency ? "" : type + "=") +
               groupId + ":" + artifactId +
               ((version == null) ? "" : ":" + version) +
               ((scope == DEFAULT) ? "" : ":" + scope);
    }

    public DependencyUpdate toUpdate(
            Version latestVersion,
            List<Version> availableVersions,
            UpdateType updateType) {
        var normalized = new Dependency(
                type(),
                groupId() != null ? groupId() : "",
                artifactId() != null ? artifactId() : "",
                version(),
                scope(),
                versionProperty());
        return new DependencyUpdate(normalized, latestVersion, availableVersions, updateType);
    }
}
