package com.github.t1.mavendep.domain;

import static com.github.t1.mavendep.domain.Logger.LogLevel.ERROR;
import static com.github.t1.mavendep.domain.Logger.LogLevel.INFO;
import static com.github.t1.mavendep.domain.Logger.LogLevel.WARNING;

public interface Logger {
    ScopedValue<Logger> CURRENT = ScopedValue.newInstance();

    void log(LogMessage message);

    default void info(ArtifactRef artifact, String message) {log(new LogMessage(INFO, message, artifact, null));}

    default void warning(ArtifactRef artifact, String message) {log(new LogMessage(WARNING, message, artifact, null));}

    default void warning(String message, Exception e) {log(new LogMessage(WARNING, message, e));}

    default void error(ArtifactRef artifact, String message, Exception e) {log(new LogMessage(ERROR, message, artifact, e));}

    /// Returns the [Logger] bound to the current scope, or a no-op default.
    static Logger log() {return CURRENT.orElse(NO_OP);}

    static ScopedValue.Carrier with(Logger logger) {return ScopedValue.where(CURRENT, logger);}

    Logger NO_OP = _ -> {};

    enum LogLevel {
        INFO, WARNING, ERROR
    }

    record LogMessage(LogLevel level, String message, ArtifactRef artifact, Exception exception) {

        public LogMessage(LogLevel level, String message) {this(level, message, null, null);}

        public LogMessage(LogLevel level, String message, Exception exception) {
            this(level, message, null, exception);
        }
    }
}
