* when starting, I see:
  WARNING: A restricted method in java.lang.System has been called
  WARNING: java.lang.System::load has been called by org.jline.nativ.JLineNativeLoader in an unnamed module (file:
  /Users/rdohna/.m2/repository/com/github/t1/maven-dep-manager/1.0.0-SNAPSHOT/maven-dep-manager-1.0.0-SNAPSHOT.jar)
  WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
  WARNING: Restricted methods will be blocked in a future release unless native access is enabled
* pressing `a` a second time on the dependencies tab should deselect all
* get smarter about the menu:
    * right-align
    * use a single model for handling and displaying key bindings; show only when available on a tab
* plugins doesn't display anything
* more structured logging, so we can
    * associate warnings and errors to the dependency they belong to in the TUI (and maybe in the Table output???)
    * ignore "Fetching metadata" logs... those are harmless
