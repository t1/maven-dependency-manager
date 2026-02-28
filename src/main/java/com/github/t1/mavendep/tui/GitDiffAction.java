package com.github.t1.mavendep.tui;

import dev.tamboui.tui.TuiRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Runs `git diff` and stores the output in the [DashboardModel].
class GitDiffAction {

    private final DashboardModel model;
    private final TuiRunner runner;
    private final Path workingDir;
    private final List<Path> pomFiles;

    GitDiffAction(DashboardModel model, TuiRunner runner, Path workingDir, List<Path> pomFiles) {
        this.model = model;
        this.runner = runner;
        this.workingDir = workingDir;
        this.pomFiles = pomFiles;
    }

    void refresh() {
        Thread.startVirtualThread(this::run);
    }

    private void run() {
        try {
            var command = new ArrayList<>(List.of("git", "diff", "--"));
            pomFiles.stream().map(Path::toString).forEach(command::add);

            var process = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            var lines = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            process.waitFor();
            runner.runOnRenderThread(() -> model.setDiffOutput(lines));
        } catch (IOException | InterruptedException e) {
            runner.runOnRenderThread(() -> model.setDiffOutput(List.of("Error running git diff: " + e.getMessage())));
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
