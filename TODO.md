* `Pom.writeToDisk` wraps `IOException` in bare `RuntimeException` — use `UncheckedIOException`
* `MavenRepository.isCacheFresh` same issue — use `UncheckedIOException`
* `MavenDepManagerCli.run()` manually wires `CheckCommand` via setters — use PicoCLI's `defaultCommand` instead
* setter injection in `CheckCommand` (`setCommonOptions`, `setRepositoryOptions`) used by `MavenDepManagerCli.run()` —
  prefer constructor injection
* `DashboardModelTest` uses `when().thenReturn()` instead of `given().willReturn()` (lines 274, 291)
* 4 places wrap exceptions in bare `new RuntimeException(e)` without a helpful message: `Pom.writeToDisk`,
  `MavenRepository.isCacheFresh`, `ReportOutputHandler.writeReport`, `JsonReportWriter.run`
* excessive `public` visibility on TUI internals (`ScanAction`, `ApplyUpdatesAction`, `MavenBuildAction`,
  `ScanProgressPanel`, `BuildOutputPanel`, `VersionPickerPanel`, `DependencyTablePanel`) — only used within `tui`
  package
* no `package-info.java` files anywhere in the project
* get smarter about the menu:
    * right-align
    * use a single model for handling and displaying key bindings; show only when available on a tab
* TUI: if there are no updates (dependencies as well as plugins), show "no updates available",
  or if showing all and there are none "no dependencies/plugins"
* reduce flashing when scanning... don't paint everything in cyan
* more structured logging, so we can
    * have log levels `info`, `warning`, and `error` as method names, e.g. `log().warning("...")`
    * connect logs to the dependency they belong to, so we can associate them in the TUI (and maybe in the Table output)
    * ignore "Fetching metadata" logs in the TUI... those are harmless
* TUI: when I'm on the DIFF tab, pressing `d` should jump to the DEPENDENCIES tab; this should be visible in the menu
* TUI: put header of the version picker **into** the box, so it's easier to see; and wrap it into three lines:
  "Pick version for", "groupId", and "artifactId"
