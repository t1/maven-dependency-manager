package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.MavenRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static com.github.t1.mavendep.report.Logger.withVerbose;
import static com.github.t1.mavendep.report.ReportOutputHandler.writeReport;

@Command(
        name = "check",
        description = "Check for dependency updates in Maven projects"
)
public class CheckCommand implements Runnable {

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    private RepositoryOptions repositoryOptions;

    private final MavenRepository repository;

    public CheckCommand() {
        this(new MavenRepository());
    }

    CheckCommand(MavenRepository repository) {
        this.repository = repository;
    }

    void setCommonOptions(CommonOptions commonOptions) {
        this.commonOptions = commonOptions;
    }

    void setRepositoryOptions(RepositoryOptions repositoryOptions) {
        this.repositoryOptions = repositoryOptions;
    }

    @Override
    public void run() {
        withVerbose(commonOptions.verbose).run(() -> {
            var mavenRepository = repository != null ? repository : repositoryOptions.createMavenRepository();
            var analyzer = new DependencyAnalyzer(mavenRepository, commonOptions.pomFiles);

            var reports = analyzer.run();

            writeReport(reports, commonOptions.format, commonOptions.outputFile, commonOptions.showAll);
        });
    }
}
