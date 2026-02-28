/// Core domain model and services, independent of any UI or CLI framework.
///
/// Key types:
/// - `Pom` — parses and mutates `pom.xml` files (text-based, preserving formatting)
/// - `Dependency` / `DependencyUpdate` — represent a Maven dependency and its available update
/// - `Version` — parsed semantic version with qualifier classification (pre-release vs release)
/// - `MavenRepository` — fetches and caches artifact metadata from Maven Central
/// - `DependencyAnalyzer` — orchestrates scanning POMs and querying the repository
/// - `ProjectReport` — analysis result for a single POM
///
/// This package has no dependency on `cli`, `report`, or `tui`.
package com.github.t1.mavendep.domain;
