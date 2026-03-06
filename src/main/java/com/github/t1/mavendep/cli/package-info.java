/// PicoCLI command layer.
///
/// `MavenDepManagerCli` is the entry point; it registers subcommands
/// (`CheckCommand`, `UpdateCommand`, `ShowAvailableCommand`, `TuiCommand`)
/// and defaults to `tui` when none is specified.
///
/// Shared option groups live in `CommonOptions` and `RepositoryOptions`,
/// mixed into each command via PicoCLI's `@Mixin`.
package com.github.t1.mavendep.cli;
