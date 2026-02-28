* Add yaml and xml output format
* more structured logging, so we can
    * have log levels `info`, `warning`, and `error` as method names, e.g. `log().warning("...")`
    * connect logs to the dependency they belong to, so we can associate them in the TUI (and maybe in the Table output)
    * ignore "Fetching metadata" logs in the TUI... those are harmless
* TUI: build output with colors?
