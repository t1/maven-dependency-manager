package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.io.IOException;

import static java.nio.file.Files.deleteIfExists;
import static org.assertj.core.api.BDDAssertions.then;

@DisabledIfSystemProperty(named = "mdm.offline", matches = "true", disabledReason = "requires network access")
class MavenRepositoryIT {

    @Test
    void shouldFetchAvailableVersions() throws IOException {
        var repository = new MavenRepository();
        // force a reload of metadata:
        deleteIfExists(repository.metadataFilePath("org.junit.jupiter", "junit-jupiter"));

        var versions = repository.getAvailableVersions("org.junit.jupiter", "junit-jupiter");

        then(versions).isNotNull().contains(Version.fromString("5.4.0"));
    }
}
