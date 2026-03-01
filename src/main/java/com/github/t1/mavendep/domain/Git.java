package com.github.t1.mavendep.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/// Reads file content from the git HEAD commit.
class Git {

    static Optional<String> readCommitted(Path file) {
        try {
            var dir = file.getParent();
            if (dir == null) dir = Path.of(".");
            var relativePath = dir.relativize(file).toString();

            // First check if we're in a git repo and file is tracked
            var process = new ProcessBuilder("git", "show", "HEAD:" + relativePath)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            var content = new String(process.getInputStream().readAllBytes());
            var exitCode = process.waitFor();
            if (exitCode != 0) return Optional.empty();
            return Optional.of(content);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
