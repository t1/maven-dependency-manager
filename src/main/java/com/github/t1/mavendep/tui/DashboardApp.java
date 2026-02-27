package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.tui.action.ApplyUpdatesAction;
import com.github.t1.mavendep.tui.action.MavenBuildAction;
import com.github.t1.mavendep.tui.action.ScanAction;
import dev.tamboui.terminal.BackendFactory;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;

import java.nio.file.Path;
import java.util.List;

/// Wires model, view, controller, and actions, then runs the TUI event loop.
public class DashboardApp {

    private final MavenRepository repository;
    private final List<Path> pomFiles;
    private final List<String> buildGoals;

    public DashboardApp(MavenRepository repository, List<Path> pomFiles, List<String> buildGoals) {
        this.repository = repository;
        this.pomFiles = pomFiles;
        this.buildGoals = buildGoals;
    }

    public void run() throws Exception {
        var model = new DashboardModel();
        var view = new DashboardView(model);

        var backend = new BackTabBackendWrapper(BackendFactory.create());
        var config = TuiConfig.builder().backend(backend).build();
        try (var tui = TuiRunner.create(config)) {
            var scanAction = new ScanAction(model, repository, pomFiles, tui);
            var applyAction = new ApplyUpdatesAction(model);
            var workingDir = pomFiles.getFirst().toAbsolutePath().getParent();
            var buildAction = new MavenBuildAction(model, tui, workingDir, buildGoals);

            var controller = new DashboardController(model,
                    applyAction::run,
                    buildAction::start,
                    scanAction::start);

            scanAction.start();

            tui.run(controller::handle, view::render);
        }
    }
}
