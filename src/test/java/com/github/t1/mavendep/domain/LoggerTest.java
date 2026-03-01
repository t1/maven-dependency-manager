package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.domain.Logger.log;
import static com.github.t1.mavendep.domain.Logger.with;
import static org.assertj.core.api.BDDAssertions.then;

class LoggerTest {
    List<LogMessage> messages = new ArrayList<>();

    @Test void shouldLogWarningForArtifact() {
        var artifact = new ArtifactRef("org.example", "lib");

        with(collectingLogger()).run(() -> log().warning(artifact, "something odd"));

        then(messages).hasSize(1);
        then(messages.getFirst().artifact()).isEqualTo(artifact);
        then(messages.getFirst().message()).isEqualTo("something odd");
    }

    @Test void shouldNoOpWhenNoLoggerBound() {
        var artifact = new ArtifactRef("org.example", "lib");

        log().info(artifact, "should not throw");
        log().warning(artifact, "should not throw");
        log().warning("should not throw", new RuntimeException("boom"));
        log().error(artifact, "should not throw", new RuntimeException("boom"));

        then(messages).isEmpty();
    }

    private Logger collectingLogger() {
        return message -> messages.add(message);
    }
}
