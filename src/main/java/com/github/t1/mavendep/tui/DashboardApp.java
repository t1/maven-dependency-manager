package com.github.t1.mavendep.tui;

import dev.tamboui.terminal.BackendFactory;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;

/// Wires model, view, controller, and actions, then runs the TUI event loop.
public class DashboardApp {

    private final DashboardConfig config;

    public DashboardApp(DashboardConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        var model = new DashboardModel();
        model.setShowAll(config.showAll());
        var view = new DashboardView(model);

        var backend = new BackTabBackendWrapper(BackendFactory.create());
        var tuiConfig = TuiConfig.builder().backend(backend).build();
        try (var tui = TuiRunner.create(tuiConfig)) {
            var scanAction = new ScanAction(model, config.repository(), config.pomFiles(), tui);
            var applyAction = new ApplyUpdatesAction(model);
            var workingDir = config.pomFiles().getFirst().toAbsolutePath().getParent();
            var buildAction = new MavenBuildAction(model, tui, workingDir, config.buildGoals());
            var diffAction = new GitDiffAction(model, tui, workingDir, config.pomFiles());

            var controller = new DashboardController(model,
                    () -> { applyAction.run(); diffAction.refresh(); },
                    buildAction::start,
                    scanAction::start,
                    diffAction::refresh);

            scanAction.start();
            diffAction.refresh();

            tui.run(controller::handle, view::render);
        }
    }
}
