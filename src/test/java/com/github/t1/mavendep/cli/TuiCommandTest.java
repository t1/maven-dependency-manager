package com.github.t1.mavendep.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.BDDAssertions.then;

class TuiCommandTest {

    @Test
    void shouldReturnOneOnTuiError() {
        var stderr = new StringWriter();
        var cmd = new CommandLine(new TuiCommand(_ -> { throw new RuntimeException("test error"); }));
        cmd.setErr(new PrintWriter(stderr));

        var exitCode = cmd.execute("pom.xml");

        then(exitCode).isEqualTo(1);
        then(stderr.toString()).contains("TUI error: test error");
    }

    @Test
    void shouldReturnZeroOnSuccessfulRun() {
        var cmd = new CommandLine(new TuiCommand(_ -> {}));

        var exitCode = cmd.execute("pom.xml");

        then(exitCode).isEqualTo(0);
    }
}
