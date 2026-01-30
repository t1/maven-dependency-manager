package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.MavenRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static com.github.t1.mavendep.report.Logger.withVerbose;
import static com.github.t1.mavendep.report.ReportOutputHandler.writeReport;

@Command(
        name = "update",
        description = "Update Maven dependencies to their latest versions"
)
public class UpdateCommand implements Runnable {

    @Mixin
    private CommonOptions commonOptions;

    private final MavenRepository repository;

    @SuppressWarnings("unused") // Used by Picocli
    public UpdateCommand() {
        this(new MavenRepository());
    }

    UpdateCommand(MavenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run() {
        withVerbose(commonOptions.verbose).run(() -> {
            var analyzer = new DependencyAnalyzer(repository);

            var reports = analyzer.analyze(commonOptions.pomFiles);

            for (var report : reports) {
                var parentUpdateNeedsApplying = report.parentUpdate()
                        .filter(DependencyUpdate::isUpdate)
                        .isPresent();
                var dependencyUpdatesNeedApplying = report.dependencyUpdates().stream()
                        .anyMatch(DependencyUpdate::isUpdate);
                var pluginUpdatesNeedApplying = report.pluginUpdates().stream()
                        .anyMatch(DependencyUpdate::isUpdate);

                if (parentUpdateNeedsApplying || dependencyUpdatesNeedApplying || pluginUpdatesNeedApplying) {
                    var pom = report.pom();

                    if (dependencyUpdatesNeedApplying) {
                        var updates = report.dependencyUpdates().stream()
                                .filter(DependencyUpdate::isUpdate)
                                .toList();
                        pom.applyUpdates(updates);
                    }

                    if (pluginUpdatesNeedApplying) {
                        var updates = report.pluginUpdates().stream()
                                .filter(DependencyUpdate::isUpdate)
                                .toList();
                        pom.applyUpdates(updates);
                    }

                    if (parentUpdateNeedsApplying) {
                        pom.updateParentVersion(report.parentUpdate().get());
                    }

                    pom.writeToDisk();
                    System.out.println("Updated: " + report.pom().path());
                }
            }

            writeReport(reports, commonOptions.format, commonOptions.outputFile, commonOptions.showAll);
        });
    }
}
