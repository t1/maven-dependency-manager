package com.github.t1.mavendep.tui.e2e;

import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

class Pty4jTuiTestDriver implements TuiTestDriver {
    private static final int COLUMNS = 100;
    private static final int ROWS = 30;

    private final PtyProcess process;
    private final OutputStream stdin;
    private final TerminalTextBuffer textBuffer;
    private final Thread readerThread;

    Pty4jTuiTestDriver(Path pomFile, String mavenCentralUrl, Path localRepo) {
        var styleState = new StyleState();
        textBuffer = new TerminalTextBuffer(COLUMNS, ROWS, styleState, 1000);
        var display = new NoOpTerminalDisplay();
        var terminal = new JediTerminal(display, textBuffer, styleState);

        var jarPath = Path.of("target/maven-dep-manager.jar").toAbsolutePath().toString();
        var env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        try {
            process = new PtyProcessBuilder(new String[]{
                    "java", "--enable-preview",
                    "-Dmaven.central.url=" + mavenCentralUrl,
                    "-Dmaven.repo.local=" + localRepo,
                    "-jar", jarPath,
                    "tui", pomFile.toAbsolutePath().toString()
            })
                    .setEnvironment(env)
                    .setInitialColumns(COLUMNS)
                    .setInitialRows(ROWS)
                    .setConsole(false)
                    .setRedirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start TUI process", e);
        }

        stdin = process.getOutputStream();

        var ttyConnector = new ProcessTtyConnector(process, StandardCharsets.UTF_8) {
            @Override public String getName() {return "tui-test";}
        };
        var dataStream = new TtyBasedArrayDataStream(ttyConnector);
        var emulator = new JediEmulator(dataStream, terminal);

        readerThread = Thread.ofVirtual().name("pty-reader").start(() -> {
            try {
                while (emulator.hasNext()) {
                    emulator.next();
                }
            } catch (IOException e) {
                if (!e.getMessage().contains("closed")) {
                    throw new RuntimeException("PTY reader error", e);
                }
            }
        });
    }

    @Override public void type(String text) {
        try {
            stdin.write(text.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to type into PTY", e);
        }
    }

    @Override public void pressKey(Key key) {
        try {
            stdin.write(key.sequence());
            stdin.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send key to PTY", e);
        }
    }

    @Override public boolean hasText(String text) {
        return textBuffer.getScreenLines().contains(text);
    }

    @Override public void awaitText(String text) {
        awaitAnyText(text);
    }

    @SuppressWarnings("BusyWait") // polling is appropriate for terminal output
    @Override public void awaitAnyText(String... texts) {
        var deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (var text : texts) {
                if (hasText(text)) return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for any of: " + Arrays.toString(texts), e);
            }
        }
        throw new AssertionError("Timed out after " + TIMEOUT + " waiting for any of: " +
                                 Arrays.toString(texts) + "\nScreen content:\n" + textBuffer.getScreenLines());
    }

    @Override public boolean waitForExit() {
        try {
            return process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override public void close() {
        try {
            type("q");
            if (!waitForExit()) {
                process.destroyForcibly();
            }
        } finally {
            readerThread.interrupt();
        }
    }
}
