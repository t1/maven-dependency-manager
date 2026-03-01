* TUI: `p` to go to the plugins tab
* TUI: add color to the logs tab
* TUI: rename the logs tab to messages
* TUI: remove the "log messages" count
* TUI: when rescanning, clear the messages first
* TUI: when there are multiple poms involved, group dependencies by the pom
* TUI-tests: too many timeout parameters; always use the same timeout of 10 secs
* recognize an uncommited change to a pom, so we can have "was" and "is" as well as downgrades not only in the TUI,
  but in the DependencyUpdate
* rename DependencyUpdate to Update
