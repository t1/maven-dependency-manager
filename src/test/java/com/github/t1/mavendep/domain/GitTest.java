package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;

class GitTest {

    @TempDir Path tempDir;

    @Test void shouldReadCommittedContent() throws Exception {
        var file = tempDir.resolve("test.txt");
        Files.writeString(file, "committed content");
        git("init");
        git("add", "test.txt");
        git("commit", "-m", "initial");
        Files.writeString(file, "modified content");

        var committed = Git.readCommitted(file);

        then(committed).hasValue("committed content");
    }

    @Test void shouldReturnEmptyForUntrackedFile() throws Exception {
        git("init");
        var file = tempDir.resolve("untracked.txt");
        Files.writeString(file, "content");

        var committed = Git.readCommitted(file);

        then(committed).isEmpty();
    }

    @Test void shouldReturnEmptyOutsideGitRepo() throws IOException {
        var file = tempDir.resolve("no-git.txt");
        Files.writeString(file, "content");

        var committed = Git.readCommitted(file);

        then(committed).isEmpty();
    }

    private void git(String... args) throws Exception {
        var command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        var pb = new ProcessBuilder(command)
                .directory(tempDir.toFile());
        pb.environment().put("GIT_AUTHOR_NAME", "test");
        pb.environment().put("GIT_AUTHOR_EMAIL", "test@test");
        pb.environment().put("GIT_COMMITTER_NAME", "test");
        pb.environment().put("GIT_COMMITTER_EMAIL", "test@test");
        pb.start().waitFor();
    }
}
