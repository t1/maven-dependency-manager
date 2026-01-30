package com.github.t1.mavendep.report;

public class Logger {
    private static final ScopedValue<Boolean> VERBOSE = ScopedValue.newInstance();

    public static ScopedValue.Carrier withVerbose(boolean verbose) {
        return ScopedValue.where(VERBOSE, verbose);
    }

    public static boolean isVerbose() {return VERBOSE.orElse(false);}

    public static void log(String message, Exception e) {
        log(message);
        if (isVerbose()) {
            e.printStackTrace(System.err);
        }
    }

    public static void log(String message) {
        System.err.println(message);
    }
}
