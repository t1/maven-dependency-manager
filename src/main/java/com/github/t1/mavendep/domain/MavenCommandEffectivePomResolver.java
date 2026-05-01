package com.github.t1.mavendep.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.t1.mavendep.domain.Logger.log;
import static java.nio.file.Files.createDirectories;

/// Resolves the Maven effective model by invoking `mvn help:effective-pom`.
///
/// This gives us Maven's effective versions without re-implementing parent,
/// BOM, and dependency-management resolution in our own parser.
public class MavenCommandEffectivePomResolver implements EffectivePomResolver {
    private static final String EFFECTIVE_POM_DIR = "maven-dep-manager";

    @Override
    public EffectivePom resolve(Path pomPath) {
        var outputFile = effectivePomOutputFile(pomPath);
        runMaven(pomPath, outputFile);
        return Logger.with(Logger.NO_OP).call(() -> Pom.parse(outputFile)
                .map(EffectivePom::from)
                .orElse(EffectivePom.EMPTY));
    }

    private static Path effectivePomOutputFile(Path pomPath) {
        var pomDir = pomPath.toAbsolutePath().getParent();
        if (pomDir == null) pomDir = Path.of(".").toAbsolutePath();
        var targetDir = pomDir.resolve("target").resolve(EFFECTIVE_POM_DIR);
        try {
            createDirectories(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create effective POM directory " + targetDir, e);
        }
        var filename = Integer.toHexString(pomPath.toAbsolutePath().normalize().toString().hashCode()) + "-effective-pom.xml";
        return targetDir.resolve(filename);
    }

    private static void runMaven(Path pomPath, Path outputFile) {
        try {
            Files.deleteIfExists(outputFile);
            var process = new ProcessBuilder(
                    "mvn",
                    "-q",
                    "-f", pomPath.toAbsolutePath().toString(),
                    "help:effective-pom",
                    "-Doutput=" + outputFile.toAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            var output = new String(process.getInputStream().readAllBytes());
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                log().warning("could not resolve effective POM for " + pomPath + ": " + output.trim(), new RuntimeException(output.trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not resolve effective POM for " + pomPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("effective POM resolution interrupted for " + pomPath, e);
        }
    }
}
