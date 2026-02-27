package com.github.t1.mavendep.tui.e2e;

import java.time.Duration;

interface TuiTestDriver extends AutoCloseable {
    void type(String text);

    void pressKey(Key key);

    String getLine(int row);

    boolean hasText(String text);

    void awaitText(String text, Duration timeout);

    boolean waitForExit(Duration timeout);

    @Override void close();
}
