package com.github.t1.mavendep.domain;

public record ArtifactRef(String groupId, String artifactId) {
    @Override public String toString() {
        return groupId + ":" + artifactId;
    }
}
