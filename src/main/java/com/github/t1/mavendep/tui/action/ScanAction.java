package com.github.t1.mavendep.tui.action;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.Logger;
import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.tui.DashboardModel;
import com.github.t1.mavendep.tui.DashboardModel.Phase;
import dev.tamboui.tui.TuiRunner;

import java.nio.file.Path;
import java.util.List;

import static com.github.t1.mavendep.domain.Logger.with;

/// Runs [DependencyAnalyzer] on a virtual thread with progress reporting.
public class ScanAction {

    private final DashboardModel model;
    private final MavenRepository repository;
    private final List<Path> pomFiles;
    private final TuiRunner runner;

    public ScanAction(DashboardModel model, MavenRepository repository, List<Path> pomFiles, TuiRunner runner) {
        this.model = model;
        this.repository = repository;
        this.pomFiles = pomFiles;
        this.runner = runner;
    }

    public void start() {
        model.setPhase(Phase.SCANNING);
        Thread.startVirtualThread(this::scan);
    }

    private void scan() {
        with(new Logger() {
            @Override public void log(String message) {runner.runOnRenderThread(() -> model.addLogMessage(message));}

            @Override public void log(String message, Exception e) {log(message);}
        }).run(this::doScan);
    }

    private void doScan() {
        var analyzer = new DependencyAnalyzer(repository, pomFiles, (completed, total, artifact) ->
                runner.runOnRenderThread(() -> model.updateScanProgress(completed, total, artifact)));
        var reports = analyzer.run();
        runner.runOnRenderThread(() -> {
            model.setReports(reports);
            model.setPhase(Phase.READY);
        });
    }
}
