package com.github.t1.mavendep.domain;

/// Defines how log messages are output.
public interface Logger {
    ScopedValue<Logger> CURRENT = ScopedValue.newInstance();

    void log(String message);

    void log(String message, Exception e);

    /// Returns the [Logger] bound to the current scope, or a no-op default.
    static Logger log() {return CURRENT.orElse(NO_OP);}

    static ScopedValue.Carrier with(Logger logger) {return ScopedValue.where(CURRENT, logger);}

    Logger NO_OP = new Logger() {
        @Override public void log(String message) {}

        @Override public void log(String message, Exception e) {}
    };
}
