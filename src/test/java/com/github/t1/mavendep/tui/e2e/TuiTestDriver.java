package com.github.t1.mavendep.tui.e2e;

import java.time.Duration;

interface TuiTestDriver extends AutoCloseable {
    Duration TIMEOUT = Duration.ofSeconds(10);

    void type(String text);

    void pressKey(Key key);

    boolean hasText(String text);

    void awaitText(String text);

    void awaitAnyText(String... texts);

    boolean waitForExit();

    @Override void close();
}
