package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.t1.mavendep.domain.Logger.log;
import static com.github.t1.mavendep.domain.Logger.with;
import static org.assertj.core.api.BDDAssertions.then;

class LoggerTest {
    List<String> messages = new ArrayList<>();

    @Test void shouldDelegateMessageToBoundLogger() {
        with(collectingLogger()).run(() -> log().log("hello"));

        then(messages).containsExactly("hello");
    }

    @Test void shouldDelegateExceptionToBoundLogger() {
        var exception = new RuntimeException("boom");

        with(collectingLogger()).run(() -> log().log("oops", exception));

        then(messages).containsExactly("oops: boom");
    }

    @Test void shouldNoOpWhenNoLoggerBound() {
        log().log("should not throw");
        log().log("should not throw either", new RuntimeException("boom"));

        then(messages).isEmpty();
    }

    private Logger collectingLogger() {
        return new Logger() {
            @Override public void log(String message) {messages.add(message);}

            @Override public void log(String message, Exception e) {messages.add(message + ": " + e.getMessage());}
        };
    }
}
