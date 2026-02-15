* add an option `--force-cache-update` to refresh all maven metadata used for lookup of the latest version
* refactor the report writing: don't build a String in-memory, but use a PrintStream, which can be System.out or
  directed to a file
* add a new command `show-available` that accepts `groupId:artifactId` or `groupId` or `artifactId` and displays all
  available versions
