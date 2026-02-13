* instead of duplicating all fields of a `Dependency`, the `DependencyUpdate` could just contain the `Dependency`; the
  `currentVersion` is the `version` of the `Dependency`
* if a version property is not resolvable within a pom, but a parent pom is within the list of poms being worked on (
  either via parameter or maven module resolution), then update that property in the parent
* the `update` command takes a list of arguments with `groupId:artifactId` or `groupId` or `artifactId`, so it only
  updates those; fail without updating anything, if this can't be found.
