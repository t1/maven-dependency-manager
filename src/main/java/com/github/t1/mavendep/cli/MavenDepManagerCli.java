package com.github.t1.mavendep.cli;

import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;

@Command(
        name = "maven-dep-manager",
        description = "Maven Dependency Manager - Check for or apply dependency updates",
        subcommands = {CheckCommand.class, UpdateCommand.class, ShowAvailableCommand.class, TuiCommand.class, HelpCommand.class, GenerateCompletion.class},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        version = "1.0.0"
)
public class MavenDepManagerCli implements Runnable {

    @Mixin
    private CommonOptions commonOptions;

    @Mixin
    private RepositoryOptions repositoryOptions;

    static void main(String[] args) {
        var exitCode = new CommandLine(new MavenDepManagerCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // If no subcommand is specified, run the check command with the parsed options
        var checkCommand = new CheckCommand();
        checkCommand.setCommonOptions(commonOptions);
        checkCommand.setRepositoryOptions(repositoryOptions);
        checkCommand.run();
    }
}
