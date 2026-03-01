package com.github.t1.mavendep.domain;

public record Coordinates(ArtifactRef artifactRef, Version version) {
    public Coordinates(String groupId, String artifactId, Version version) {
        this(new ArtifactRef(groupId, artifactId), version);
    }

    public String groupId() {return artifactRef.groupId();}

    public String artifactId() {return artifactRef.artifactId();}

    @Override public String toString() {return artifactRef + ":" + version;}
}
