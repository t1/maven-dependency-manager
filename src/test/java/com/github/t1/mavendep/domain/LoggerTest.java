package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.t1.mavendep.domain.Logger.LogLevel.ERROR;
import static com.github.t1.mavendep.domain.Logger.LogLevel.INFO;
import static com.github.t1.mavendep.domain.Logger.LogLevel.WARNING;
import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.domain.Logger.log;
import static com.github.t1.mavendep.domain.Logger.with;
import static org.assertj.core.api.BDDAssertions.then;

class LoggerTest {
    List<LogMessage> messages = new ArrayList<>();

    @Test void shouldLogInfoMessage() {
        with(collectingLogger()).run(() -> log().info("fetching"));

        then(messages).hasSize(1);
        then(messages.getFirst().level()).isEqualTo(INFO);
        then(messages.getFirst().message()).isEqualTo("fetching");
    }

    @Test void shouldLogWarningMessage() {
        with(collectingLogger()).run(() -> log().warning("something odd"));

        then(messages).hasSize(1);
        then(messages.getFirst().level()).isEqualTo(WARNING);
        then(messages.getFirst().message()).isEqualTo("something odd");
    }

    @Test void shouldLogErrorWithException() {
        var exception = new RuntimeException("boom");

        with(collectingLogger()).run(() -> log().error("oops", exception));

        then(messages).hasSize(1);
        then(messages.getFirst().level()).isEqualTo(ERROR);
        then(messages.getFirst().message()).isEqualTo("oops");
        then(messages.getFirst().exception()).isEqualTo(exception);
    }

    @Test void shouldNoOpWhenNoLoggerBound() {
        log().info("should not throw");
        log().warning("should not throw");
        log().error("should not throw", new RuntimeException("boom"));

        then(messages).isEmpty();
    }

    private Logger collectingLogger() {
        return message -> messages.add(message);
    }
}
