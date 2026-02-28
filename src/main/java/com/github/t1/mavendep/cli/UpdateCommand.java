package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.DependencyAnalyzer;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.MavenRepository;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.UpdateType;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.concurrent.Callable;

import static com.github.t1.mavendep.domain.Logger.with;
import static com.github.t1.mavendep.report.ReportOutputHandler.writeReport;

@Command(
        name = "update",
        aliases = "up",
        description = "Update Maven dependencies to their latest versions"
)
public class UpdateCommand implements Callable<Integer> {

    @Spec
    @SuppressWarnings("unused") // set by Picocli
    private CommandSpec spec;

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    @SuppressWarnings("unused") // set by Picocli
    private RepositoryOptions repositoryOptions;

    @Option(
            names = {"--only"},
            description = "Only update dependencies matching groupId:artifactId, groupId, or artifactId",
            arity = "1..*"
    )
    private List<String> dependencyFilters;

    @ArgGroup
    @SuppressWarnings("unused") // set by Picocli
    private UpdateScope updateScope;

    static class UpdateScope {
        @Option(names = "--patch", description = "Only apply patch updates")
        boolean patch;

        @Option(names = "--minor", description = "Only apply patch and minor updates")
        boolean minor;

        UpdateType maxUpdateType() {
            if (patch) return UpdateType.patch;
            if (minor) return UpdateType.minor;
            return null;
        }
    }

    private MavenRepository repository;

    @SuppressWarnings("unused") // Used by Picocli
    public UpdateCommand() {}

    UpdateCommand(MavenRepository repository) {
        this.repository = repository;
    }

    @Override
    public Integer call() {
        return with(commonOptions.logger()).call(() -> {
            var effectiveRepository = (repository != null) ? repository : repositoryOptions.createMavenRepository();
            var analyzer = new DependencyAnalyzer(effectiveRepository, commonOptions.pomFiles);

            var reports = analyzer.run();

            try {
                if (dependencyFilters != null) reports = filterByDependencyFilters(reports);
            } catch (NoMatchingFilterException e) {
                spec.commandLine().getErr().println(e.getMessage());
                return 1;
            }

            reports = filterByUpdateType(reports);

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

            writeReport(reports, commonOptions.format(), commonOptions.outputFile, commonOptions.showAll);
            return 0;
        });
    }

    private List<ProjectReport> filterByUpdateType(List<ProjectReport> reports) {
        if (updateScope == null) return reports;
        var maxType = updateScope.maxUpdateType();
        if (maxType == null) return reports;
        return reports.stream()
                .map(report -> report.filterUpdates(update -> update.updateType().ordinal() <= maxType.ordinal()))
                .toList();
    }

    private List<ProjectReport> filterByDependencyFilters(List<ProjectReport> reports) {
        var filtered = reports.stream()
                .map(report -> report.filterUpdates(this::matchesAnyFilter))
                .toList();

        if (filtered.stream().noneMatch(ProjectReport::hasUpdates)) {
            throw new NoMatchingFilterException(dependencyFilters);
        }

        return filtered;
    }

    private static class NoMatchingFilterException extends RuntimeException {
        NoMatchingFilterException(List<String> filters) {
            super("No dependencies match the filter: " + String.join(", ", filters));
        }
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
