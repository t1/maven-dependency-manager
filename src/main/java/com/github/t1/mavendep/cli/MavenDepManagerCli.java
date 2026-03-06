package com.github.t1.mavendep.cli;

import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;

import java.util.concurrent.Callable;

@Command(
        name = "maven-dep-manager",
        description = "Maven Dependency Manager - Check for or apply dependency updates",
        subcommands = {CheckCommand.class, UpdateCommand.class, ShowAvailableCommand.class, TuiCommand.class, HelpCommand.class, GenerateCompletion.class},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        version = "1.0.0"
)
public class MavenDepManagerCli implements Callable<Integer> {

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    private RepositoryOptions repositoryOptions;

    static void main(String[] args) {
        var exitCode = new CommandLine(new MavenDepManagerCli()).execute(args);
        System.exit(exitCode);
    }

    /// PicoCLI has no built-in "default subcommand" feature; the parent's `call()` is called
    /// when no subcommand is specified, so we delegate to `tui` manually.
    @Override public Integer call() {return new TuiCommand(commonOptions, repositoryOptions).call();}
}
