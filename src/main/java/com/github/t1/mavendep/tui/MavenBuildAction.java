package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Phase;
import com.github.t1.mavendep.tui.DashboardModel.Tab;
import dev.tamboui.tui.TuiRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

/// Runs a Maven build subprocess, streaming output to the [DashboardModel].
class MavenBuildAction {

    private final DashboardModel model;
    private final TuiRunner runner;
    private final Path workingDir;
    private final List<String> goals;

    MavenBuildAction(DashboardModel model, TuiRunner runner, Path workingDir, List<String> goals) {
        this.model = model;
        this.runner = runner;
        this.workingDir = workingDir;
        this.goals = goals;
    }

    void start() {
        model.setPhase(Phase.BUILDING);
        model.setActiveTab(Tab.BUILD);
        model.clearBuildOutput();
        Thread.startVirtualThread(this::build);
    }

    private void build() {
        try {
            var command = new java.util.ArrayList<>(List.of("mvn"));
            command.addAll(goals);

            var process = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var outputLine = line;
                    runner.runOnRenderThread(() -> model.addBuildOutputLine(outputLine));
                }
            }

            var exitCode = process.waitFor();
            runner.runOnRenderThread(() -> {
                model.setBuildExitCode(exitCode);
                model.setPhase(Phase.READY);
            });
        } catch (IOException | InterruptedException e) {
            runner.runOnRenderThread(() -> {
                model.addBuildOutputLine("BUILD ERROR: " + e.getMessage());
                model.setBuildExitCode(-1);
                model.setPhase(Phase.READY);
            });
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
