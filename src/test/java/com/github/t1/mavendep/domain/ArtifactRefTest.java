package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class ArtifactRefTest {

    @Test void shouldFormatToString() {
        var ref = new ArtifactRef("org.example", "lib");

        then(ref.toString()).isEqualTo("org.example:lib");
    }

    @Test void shouldExposeGroupIdAndArtifactId() {
        var ref = new ArtifactRef("org.example", "lib");

        then(ref.groupId()).isEqualTo("org.example");
        then(ref.artifactId()).isEqualTo("lib");
    }
}
