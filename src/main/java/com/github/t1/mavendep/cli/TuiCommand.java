package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.tui.DashboardApp;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Arrays;

@Command(
        name = "tui",
        description = "Launch the interactive TUI dashboard"
)
public class TuiCommand implements Runnable {

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

    @Override
    public void run() {
        try {
            var repository = repositoryOptions.createMavenRepository();
            var goals = Arrays.asList(buildGoals.split("\\s+"));
            var app = new DashboardApp(repository, commonOptions.pomFiles, goals, commonOptions.showAll);
            app.run();
        } catch (Exception e) {
            System.err.println("TUI error: " + e.getMessage());
            if (commonOptions.verbose) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @SuppressWarnings("unused") // Used by Picocli
    public TuiCommand() {}
}
