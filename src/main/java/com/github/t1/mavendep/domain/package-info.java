/// Core domain model and services, independent of any UI or CLI framework.
///
/// Key types:
/// - `Pom` — parses and mutates `pom.xml` files (text-based, preserving formatting)
/// - `Dependency` / `Update` — represent a Maven dependency and its available update
/// - `Version` / `VersionStatus` — parsed semantic version and its relation to the latest released version
/// - `AvailableVersion` — a version plus the repository/source ids that exposed it (e.g. `central`, `local`)
/// - `MavenRepository` — resolves available versions through Maven Resolver using the local Maven repository cache
/// - `MavenCommandEffectivePomResolver` — asks Maven for the effective POM so managed/current versions are accurate
/// - `DependencyAnalyzer` — orchestrates scanning POMs and querying the repository;
///   detects uncommitted POM changes via `Git` and sets `Update.committedVersion`
/// - `Git` — reads file content from git HEAD for uncommitted change detection
/// - `ProjectReport` — analysis result for a single POM
///
/// This package has no dependency on `cli`, `report`, or `tui`.
package com.github.t1.mavendep.domain;
