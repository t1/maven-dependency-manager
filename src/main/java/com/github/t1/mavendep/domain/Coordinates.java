package com.github.t1.mavendep.domain;

public record Coordinates(String groupId, String artifactId, Version version) {
    @Override public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
