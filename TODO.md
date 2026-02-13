* Add a LICENSE.md file with the Apache License and remove the section in the README.
* Sort the pre-release qualifiers of a Version in a semantic way and use that for ordering. Maybe an enum is a good
  solution?
* If a file parameter is a directory, look for a `pom.xml` within.
* Document the fact that you must pass `--enable-preview` when calling
  `java -jar --enable-preview target/maven-dep-manager.jar`
* Document that it's a good idea to add a shell alias for the `java -jar --enable-preview`; give examples for `bash` as
  well as `fish`
* the `update` command takes a list of arguments with `groupId:artifactId` or `groupId` or `artifactId`, so it only
  updates those; fail without updating anything, if this can't be found.
* the `update` command takes a `--patch` or `--minor` parameter to update only path or minor (and patch) updates.
* add a new command `show-available` that accepts `groupId:artifactId` and displays all available versions (forces a
  refresh of the cache)
