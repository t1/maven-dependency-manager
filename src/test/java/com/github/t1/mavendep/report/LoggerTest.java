package com.github.t1.mavendep.report;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.BDDAssertions.then;

class LoggerTest {

    @Test void shouldSendToCustomOutputWhenBound() throws Exception {
        var messages = new ArrayList<String>();

        Logger.withLogOutput(messages::add).run(() -> Logger.log("hello"));

        then(messages).containsExactly("hello");
    }

    @Test void shouldSendExceptionMessageToCustomOutput() throws Exception {
        var messages = new ArrayList<String>();

        Logger.withLogOutput(messages::add).run(() -> Logger.log("oops", new RuntimeException("boom")));

        then(messages).containsExactly("oops");
    }

    @Test void shouldSendExceptionStackTraceToCustomOutputWhenVerbose() throws Exception {
        var messages = new ArrayList<String>();

        Logger.withVerbose(true)
                .where(Logger.logOutput(), messages::add)
                .run(() -> Logger.log("oops", new RuntimeException("boom")));

        then(messages).hasSize(2);
        then(messages.getFirst()).isEqualTo("oops");
        then(messages.get(1)).contains("RuntimeException: boom");
    }
}
