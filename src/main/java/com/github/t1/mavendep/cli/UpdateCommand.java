package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
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

            reports.stream()
                    .filter(ProjectReport::hasUpdates)
                    .forEach(report -> report.pom().apply(report.updates()));

            reports.stream()
                    .map(ProjectReport::pom)
                    .filter(Pom::isDirty)
                    .forEach(pom -> {
                        pom.writeToDisk();
                        System.out.println("Updated: " + pom.path());
                    });

            writeReport(reports, commonOptions.format, commonOptions.outputFile, commonOptions.showAll);
        });
    }
}
