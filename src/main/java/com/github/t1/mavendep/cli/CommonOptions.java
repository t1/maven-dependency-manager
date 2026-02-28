package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Logger;
import com.github.t1.mavendep.domain.OutputFormat;
import com.github.t1.mavendep.report.ReportConfig;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

/// Common options shared across CLI commands.
class CommonOptions {

    @Parameters(
            description = "POM file(s) or directory(ies) to analyze (default: pom.xml)",
            arity = "0..*",
            defaultValue = "pom.xml"
    )
    List<Path> pomFiles;

    @ArgGroup
    @SuppressWarnings("unused") // set by Picocli
    private FormatOption formatOption;

    static class FormatOption {
        @Option(names = {"--format"}, description = "Output format: text, json, yaml, xml (default: text)")
        OutputFormat format;

        @Option(names = "--json", description = "Shortcut for --format=json")
        boolean json;

        @Option(names = "--text", description = "Shortcut for --format=text")
        boolean text;

        @Option(names = "--yaml", description = "Shortcut for --format=yaml")
        boolean yaml;

        @Option(names = "--xml", description = "Shortcut for --format=xml")
        boolean xml;

        OutputFormat resolve() {
            if (json) return OutputFormat.json;
            if (text) return OutputFormat.text;
            if (yaml) return OutputFormat.yaml;
            if (xml) return OutputFormat.xml;
            return format;
        }
    }

    OutputFormat format() {
        if (formatOption == null) return OutputFormat.text;
        return formatOption.resolve();
    }

    @Option(
            names = {"-o", "--output"},
            description = "Output file (default: stdout)"
    )
    String outputFile;

    @Option(
            names = {"-a", "--show-all"},
            description = "Show all dependencies, even if up to date"
    )
    boolean showAll;

    @Option(
            names = {"-v", "--verbose"},
            description = "Print stack traces for exceptions"
    )
    boolean verbose;

    ReportConfig reportConfig() {
        return new ReportConfig(format(), outputFile, showAll);
    }

    Logger logger() {
        return logMessage -> {
            System.err.println(logMessage.level() + ": " + logMessage.message());
            if (verbose && logMessage.exception() != null) {
                logMessage.exception().printStackTrace(System.err);
            }
        };
    }
}
