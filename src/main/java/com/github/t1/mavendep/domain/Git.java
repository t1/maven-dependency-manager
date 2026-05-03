package com.github.t1.mavendep.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/// Reads file content from the git HEAD commit.
class Git {

    static Optional<String> readCommitted(Path file) {
        try {
            var absoluteFile = file.toRealPath();
            var dir = absoluteFile.getParent();
            if (dir == null) dir = Path.of(".").toAbsolutePath();

            var repoRoot = git(dir, "rev-parse", "--show-toplevel");
            if (repoRoot.isEmpty()) return Optional.empty();

            var relativePath = Path.of(repoRoot.get().trim()).toRealPath().relativize(absoluteFile)
                    .toString()
                    .replace('\\', '/');
            return git(dir, "show", "HEAD:" + relativePath);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<String> git(Path dir, String... args) throws IOException, InterruptedException {
        var command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);

        var process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes());
        return process.waitFor() == 0 ? Optional.of(output) : Optional.empty();
    }
}
