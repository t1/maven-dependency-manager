* reduce flashing when scanning... don't paint everything in cyan
* more structured logging, so we can
    * have log levels `info`, `warning`, and `error` as method names, e.g. `log().warning("...")`
    * connect logs to the dependency they belong to, so we can associate them in the TUI (and maybe in the Table output)
    * ignore "Fetching metadata" logs in the TUI... those are harmless
* TUI: put header of the version picker **into** the box, so it's easier to see; and wrap it into three lines:
  "Pick version for", "groupId", and "artifactId"
* Add yaml and xml output format
* TUI: build output with colors?
