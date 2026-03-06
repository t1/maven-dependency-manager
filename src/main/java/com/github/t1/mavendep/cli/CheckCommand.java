package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.util.concurrent.Callable;

import static com.github.t1.mavendep.domain.Logger.with;
import static com.github.t1.mavendep.report.ReportOutputHandler.writeReport;

@Command(
        name = "check",
        description = "Check for dependency updates in Maven projects"
)
public class CheckCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    private RepositoryOptions repositoryOptions;

    @Override
    public Integer call() {
        with(commonOptions.logger()).run(() -> {
            var mavenRepository = repositoryOptions.createMavenRepository();
            var analyzer = new DependencyAnalyzer(mavenRepository, commonOptions.pomFiles);

            var reports = analyzer.run();

            writeReport(reports, commonOptions.reportConfig());
        });
        return 0;
    }
}
