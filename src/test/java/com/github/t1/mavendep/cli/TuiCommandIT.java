package com.github.t1.mavendep.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.BDDAssertions.then;

class TuiCommandIT {

    @Test void shouldRecognizeTuiSubcommand() {
        var cli = new CommandLine(new MavenDepManagerCli());
        var subcommands = cli.getSubcommands();
        then(subcommands).containsKey("tui");
    }

    @Test void shouldParseBuildGoalsOption() {
        var cli = new CommandLine(new MavenDepManagerCli());
        var parseResult = cli.parseArgs("tui", "--build-goals", "clean install");
        then(parseResult.subcommand().commandSpec().name()).isEqualTo("tui");
    }
}
