package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.OutputFormat;
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

    @Option(
            names = {"-f", "--format"},
            description = "Output format: text, json (default: text)",
            defaultValue = "text"
    )
    OutputFormat format;

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
}
