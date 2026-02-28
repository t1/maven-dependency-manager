package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.tui.DashboardApp;
import com.github.t1.mavendep.tui.DashboardConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Command(
        name = "tui",
        description = "Launch the interactive TUI dashboard"
)
public class TuiCommand implements Callable<Integer> {

    @Spec
    @SuppressWarnings("unused") // set by Picocli
    private CommandSpec spec;

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    private RepositoryOptions repositoryOptions;

    @Option(
            names = {"--build-goals"},
            description = "Maven goals to run when building (default: clean verify)",
            defaultValue = "clean verify"
    )
    private String buildGoals;

    private final Consumer<TuiCommand> dashboardRunner;

    @SuppressWarnings("unused") // Used by Picocli
    public TuiCommand() {
        this(TuiCommand::runDashboard);
    }

    TuiCommand(Consumer<TuiCommand> dashboardRunner) {
        this.dashboardRunner = dashboardRunner;
    }

    @Override
    public Integer call() {
        try {
            dashboardRunner.accept(this);
            return 0;
        } catch (RuntimeException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            spec.commandLine().getErr().println("TUI error: " + cause.getMessage());
            if (commonOptions.verbose) cause.printStackTrace(spec.commandLine().getErr());
            return 1;
        }
    }

    private static void runDashboard(TuiCommand cmd) {
        try {
            var repository = cmd.repositoryOptions.createMavenRepository();
            var goals = Arrays.asList(cmd.buildGoals.split("\\s+"));
            var config = new DashboardConfig(repository, cmd.commonOptions.pomFiles, goals, cmd.commonOptions.showAll);
            var app = new DashboardApp(config);
            app.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
