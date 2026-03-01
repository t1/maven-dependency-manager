package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.report.VersionTreeTableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Optional;

@Command(
        name = "show-available",
        description = "Show all available versions for a Maven artifact"
)
public class ShowAvailableCommand implements Runnable {

    @Mixin
    private RepositoryOptions repositoryOptions;

    @Parameters(
            description = "Artifact coordinate: groupId:artifactId, groupId, or artifactId",
            arity = "1"
    )
    private String coordinate;

    @Option(
            names = {"-p", "--pom"},
            description = "POM file to resolve partial coordinates (default: pom.xml)",
            defaultValue = "pom.xml"
    )
    private Path pomFile;

    @Override
    public void run() {
        var resolved = resolveCoordinate();
        if (resolved.isEmpty()) {
            System.out.println("No dependency matching '" + coordinate + "' found in " + pomFile);
            return;
        }
        var dep = resolved.get();

        var versions = repositoryOptions.createMavenRepository()
                .getAvailableVersions(dep.artifactRef());

        if (versions.isEmpty()) {
            System.out.println("No versions found for " + coordinate);
        } else {
            System.out.println(VersionTreeTableFormatter.format(versions));
        }
    }

    private Optional<Dependency> resolveCoordinate() {
        if (coordinate.contains(":")) {
            var parts = coordinate.split(":", 2);
            return Optional.of(new Dependency(Dependency.DependencyType.dependency, parts[0], parts[1], null, null, null));
        }
        return Pom.parse(pomFile)
                .flatMap(pom -> pom.dependencies().stream()
                        .filter(dep -> coordinate.equals(dep.groupId()) || coordinate.equals(dep.artifactId()))
                        .findFirst());
    }
}
