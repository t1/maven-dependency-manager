## Clean Code Findings

### Code Smells

* telescoping constructors in `MavenRepository` (5 chained constructors) — use a builder or reduce to two
* `System.exit(1)` in `UpdateCommand.filterAndValidate` — untestable, use PicoCLI exit codes instead
* `System.exit` in `TuiCommand.run` — same issue
* duplicated `padRight` in `TextReportWriter` and `VersionTreeTableFormatter`
* duplicated `totalDependencies` calculation in `DependencyAnalyzer.run()` and `analyze()` — extract to `Pom`
* duplicated default Maven Central URL in `MavenRepository` and `RepositoryOptions`
* `Dependency.toUpdate` normalizes nulls to empty strings — fix at parse time in `Pom.DependencyParser` instead
* `DashboardModel` is ~300 lines with many responsibilities — comment sections hint at natural split points

### SOLID

* DIP/Bug: `UpdateCommand` ignores `RepositoryOptions` — its no-arg constructor creates `new MavenRepository()`
  bypassing CLI options like `--cache-ttl`, `--local-repo`

### Method Design

* `ReportOutputHandler.writeReport` has 4 parameters — group the last 3 into a config object
* `DependencyAnalyzer` has 3 telescoping constructors with nullable `progressListener` — use a no-op default
* boolean `showAll` passed through multiple layers as flag argument -- let's discuss our options

### Exception Handling

* broad `catch (Exception e)` in `MavenRepository.getAvailableVersions` — catch specific exceptions to avoid masking
  bugs
* `Pom.writeToDisk` wraps `IOException` in bare `RuntimeException` — use `UncheckedIOException`
* `MavenRepository.isCacheFresh` same issue — use `UncheckedIOException`

### Structure

* `MavenDepManagerCli.run()` manually wires `CheckCommand` via setters — use PicoCLI's `defaultCommand` instead

### Java Code Style

* setter injection in `CheckCommand` (`setCommonOptions`, `setRepositoryOptions`) used by `MavenDepManagerCli.run()` —
  prefer constructor injection
* `DashboardModelTest` uses `when().thenReturn()` instead of `given().willReturn()` (lines 274, 291)
* 4 places wrap exceptions in bare `new RuntimeException(e)` without a helpful message: `Pom.writeToDisk`,
  `MavenRepository.isCacheFresh`, `ReportOutputHandler.writeReport`, `JsonReportWriter.run`
* excessive `public` visibility on TUI internals (`ScanAction`, `ApplyUpdatesAction`, `MavenBuildAction`,
  `ScanProgressPanel`, `BuildOutputPanel`, `VersionPickerPanel`, `DependencyTablePanel`) — only used within `tui`
  package
* no `package-info.java` files anywhere in the project


* new TUI tab with git diff
* get smarter about the menu:
    * right-align
    * use a single model for handling and displaying key bindings; show only when available on a tab
* plugins doesn't display anything
* reduce flashing when scanning... don't paint everything in cyan
* more structured logging, so we can
    * have log levels `info`, `warning`, and `error` as method names, e.g. `log().warning("...")`
    * connect logs to the dependency they belong to, so we can associate them in the TUI (and maybe in the Table output)
    * ignore "Fetching metadata" logs in the TUI... those are harmless
