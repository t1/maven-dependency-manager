package com.github.t1.mavendep.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

public class Logger {
    private static final ScopedValue<Boolean> VERBOSE = ScopedValue.newInstance();
    private static final ScopedValue<Consumer<String>> LOG_OUTPUT = ScopedValue.newInstance();

    public static ScopedValue.Carrier withVerbose(boolean verbose) {
        return ScopedValue.where(VERBOSE, verbose);
    }

    public static ScopedValue<Consumer<String>> logOutput() {return LOG_OUTPUT;}

    public static ScopedValue.Carrier withLogOutput(Consumer<String> output) {
        return ScopedValue.where(LOG_OUTPUT, output);
    }

    public static boolean isVerbose() {return VERBOSE.orElse(false);}

    public static void log(String message, Exception e) {
        log(message);
        if (isVerbose()) {
            if (LOG_OUTPUT.isBound()) {
                var sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                LOG_OUTPUT.get().accept(sw.toString());
            } else {
                e.printStackTrace(System.err);
            }
        }
    }

    public static void log(String message) {
        if (LOG_OUTPUT.isBound()) {
            LOG_OUTPUT.get().accept(message);
        } else {
            System.err.println(message);
        }
    }
}
