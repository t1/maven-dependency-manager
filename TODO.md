* the `update` command takes a list of arguments with `groupId:artifactId` or `groupId` or `artifactId`, so it only
  updates those; fail without updating anything, if this can't be found.
* the `update` command takes a `--patch` or `--minor` parameter to update only path or minor (and patch) updates.
* add a new command `show-available` that accepts `groupId:artifactId` or `groupId` or `artifactId` and displays all
  available versions (forces a
  refresh of the cache)
