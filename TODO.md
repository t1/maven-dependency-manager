* Add a LICENSE.md file with the Apache License and remove the section in the README.
* the `update` command takes a list of arguments with `groupId:artifactId` or `groupId` or `artifactId`, so it only
  updates those; fail without updating anything, if this can't be found.
* the `update` command takes a `--patch` or `--minor` parameter to update only path or minor (and patch) updates  
