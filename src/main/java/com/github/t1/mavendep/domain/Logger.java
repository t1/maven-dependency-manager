package com.github.t1.mavendep.domain;

import static com.github.t1.mavendep.domain.Logger.LogLevel.ERROR;
import static com.github.t1.mavendep.domain.Logger.LogLevel.INFO;
import static com.github.t1.mavendep.domain.Logger.LogLevel.WARNING;

public interface Logger {
    ScopedValue<Logger> CURRENT = ScopedValue.newInstance();

    void log(LogMessage message);

    default void info(String message) {log(new LogMessage(INFO, message));}

    default void warning(String message) {log(new LogMessage(WARNING, message));}

    default void warning(String message, Exception e) {log(new LogMessage(WARNING, message, e));}

    default void error(String message, Exception e) {log(new LogMessage(ERROR, message, e));}

    /// Returns the [Logger] bound to the current scope, or a no-op default.
    static Logger log() {return CURRENT.orElse(NO_OP);}

    static ScopedValue.Carrier with(Logger logger) {return ScopedValue.where(CURRENT, logger);}

    Logger NO_OP = _ -> {};

    enum LogLevel {
        INFO, WARNING, ERROR
    }

    record LogMessage(LogLevel level, String message, Exception exception) {

        public LogMessage(LogLevel level, String message) {
            this(level, message, null);
        }
    }
}
