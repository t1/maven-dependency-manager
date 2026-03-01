package com.github.t1.mavendep.tui.e2e;

import java.time.Duration;

interface TuiTestDriver extends AutoCloseable {
    void type(String text);

    void pressKey(Key key);

    boolean hasText(String text);

    void awaitText(String text, Duration timeout);

    void awaitAnyText(Duration timeout, String... texts);

    boolean waitForExit(Duration timeout);

    @Override void close();
}
