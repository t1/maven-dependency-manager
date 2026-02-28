* `TuiCommand.runDashboard` wraps exception in bare `new RuntimeException(e)` — blocked by
  `TuiRunner.create()` declaring `throws Exception`
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
