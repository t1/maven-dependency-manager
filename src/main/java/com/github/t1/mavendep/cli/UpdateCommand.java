package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;

import static com.github.t1.mavendep.report.Logger.withVerbose;
import static com.github.t1.mavendep.report.ReportOutputHandler.writeReport;

@Command(
        name = "update",
        description = "Update Maven dependencies to their latest versions"
)
public class UpdateCommand implements Runnable {

    @Mixin
    private CommonOptions commonOptions;

    @Option(
            names = {"--only"},
            description = "Only update dependencies matching groupId:artifactId, groupId, or artifactId",
            arity = "1..*"
    )
    private List<String> dependencyFilters;

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

            if (dependencyFilters != null) {
                reports = filterAndValidate(reports);
            }

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

    private List<ProjectReport> filterAndValidate(List<ProjectReport> reports) {
        var filtered = reports.stream()
                .map(report -> report.filterUpdates(this::matchesAnyFilter))
                .toList();

        var hasMatchingUpdates = filtered.stream().anyMatch(ProjectReport::hasUpdates);
        if (!hasMatchingUpdates) {
            System.err.println("No dependencies match the filter: " + String.join(", ", dependencyFilters));
            System.exit(1);
        }

        return filtered;
    }

    private boolean matchesAnyFilter(DependencyUpdate update) {
        return dependencyFilters.stream().anyMatch(filter -> matchesFilter(update, filter));
    }

    private static boolean matchesFilter(DependencyUpdate update, String filter) {
        var dependency = update.dependency();
        if (filter.contains(":")) {
            var parts = filter.split(":", 2);
            return parts[0].equals(dependency.groupId()) && parts[1].equals(dependency.artifactId());
        }
        return filter.equals(dependency.groupId()) || filter.equals(dependency.artifactId());
    }
}
