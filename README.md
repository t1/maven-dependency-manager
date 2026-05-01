# Maven Dependency Manager

A Java-based CLI tool to check for newer versions of Maven dependencies across multiple projects, with intelligent
caching using the local Maven repository, different output formats, and a Terminal UI (`tui`).

This is actually my personal playground project
for [Level 3 Agentic Development](https://www.codecentric.de/wissens-hub/blog/die-fuenf-level-der-ki-gestuetzten-softwareentwicklung)
using Claude Code and [my plugins](https://github.com/t1/tdder).

## Features

- **Dependency Version Checking**: Queries Maven Central for the latest versions of your dependencies
- **Plugin Version Checking**: Also checks and updates plugin versions
- **Parent POM Version Checking**: Also checks and updates parent POM versions
- **Smart Caching**: Leverages the local Maven repository (~/.m2) to minimize network requests, with
  `--force-cache-update` to bypass the cache. The local repo path can be overridden via the `maven.repo.local` system
  property or the `--local-repo` CLI option.
- **Multi-Project Support**: Scan single or multiple Maven projects at once, automatically recognize Maven modules
- **Multiple Output Formats**: JSON, YAML, XML, and human-readable text reports
- **Show Available Versions**: Query all available versions for a specific artifact by `groupId:artifactId`, `groupId`,
  or `artifactId` (resolving partial coordinates from your POM), displayed as a tree grouped by major/minor version
- **Automatic Updates**: Separate update command to update pom.xml files with the latest versions (supports both direct
  versions and property-based versions, including parent POMs)
- **Effective Version Detection**: Uses Maven's effective POM to report the currently effective dependency/plugin version,
  so BOM-managed and parent-managed versions are shown correctly
- **Parent Property Resolution**: Version properties defined in a parent POM are resolved and updated in the parent
  when the parent is within the working set of POMs (via parameter or module resolution)
- **Flexible Version Parsing**: Handles standard [semantic versions](https://semver.org) (`1.2.3`), multi-segment versions (`2.13.4.2`),
  qualifier suffixes (`1.0-RC1`, `5.1.5.Final`), and timestamp-based build numbers (`0.7.7.201606060606`).
  Qualifiers are classified as pre-release (`SNAPSHOT`, `alpha`, `beta`, `RC`, `M`, ...) or
  release (`Final`, `GA`, `RELEASE`, `SP`) to filter out unstable versions.
  Note that this is not 100% compatible to Maven's version handling.
- **Robust Error Handling**: Network failures and malformed metadata are logged but don't halt execution

## Requirements

- Java 25 or higher (with preview features enabled)
- Maven 3.6+ (for building from source)

## Building

```bash
mvn clean package
```

This creates an executable JAR: `target/maven-dep-manager.jar`

## Usage

This project uses Java preview features, so you must pass `--enable-preview` when running the JAR:

```bash
java --enable-preview -jar target/maven-dep-manager.jar <command> [options]
```

To avoid typing this every time, add a shell alias:

```bash
# bash (~/.bashrc or ~/.bash_profile)
alias mdm='java --enable-preview -jar /path/to/maven-dep-manager.jar'

# fish (~/.config/fish/config.fish)
alias --save mdm 'java --enable-preview -jar /path/to/maven-dep-manager.jar'
```

Then simply use `mdm check`, `mdm update`, etc.

### Basic Usage

```bash
# Launch the interactive TUI dashboard (default)
mdm

# Check current directory's pom.xml
mdm check

# Check a single project
mdm check pom.xml

# Check a project directory (looks for pom.xml within)
mdm check my-project/

# Check multiple projects
mdm check project1/pom.xml project2/pom.xml
```

If a `pom` declares `modules`, those are always included in the scan.

### Output Formats

```bash
# JSON output
mdm check pom.xml --json
mdm check pom.xml --format json

# YAML output
mdm check pom.xml --yaml
mdm check pom.xml --format yaml

# XML output
mdm check pom.xml --xml
mdm check pom.xml --format xml

# Text output (default)
mdm check pom.xml --text
mdm check pom.xml --format text

# Save to file
mdm check pom.xml --output updates.json
```

### Advanced Options

```bash
# Show all dependencies (including up-to-date ones)
mdm check pom.xml --show-all

# Force refresh of all cached Maven metadata
mdm check pom.xml --force-cache-update

# Enable verbose output with stack traces for exceptions
mdm check pom.xml --verbose
mdm check pom.xml -v
```

### Showing Available Versions

```bash
# Show all versions for a specific artifact
mdm show-available org.assertj:assertj-core

# Show versions using just the artifactId (resolved from pom.xml)
mdm show-available assertj-core

# Show versions using just the groupId (resolved from pom.xml)
mdm show-available org.assertj

# Specify a different POM file for coordinate resolution
mdm show-available assertj-core --pom other/pom.xml
```

### Interactive TUI Dashboard

```bash
# Launch the interactive TUI for the current directory
mdm tui

# Launch with specific POM files
mdm tui project1/pom.xml project2/pom.xml

# Specify custom build goals
mdm tui --build-goals "clean install"

# Force cache refresh on startup
mdm tui --force-cache-update
```

The TUI provides a full interactive dashboard where you can:

- Browse dependencies and plugins, grouped by POM and scope in the table; plugins use profile sub-groups only when present. The header shows the root project name(s), falling back to artifactId(s)
- Select/deselect updates with checkboxes (Space to toggle, `a` for all, `n` for none) — POM is updated automatically
- Pick specific target versions (Enter to open version picker), also downgrades — POM is updated on confirm
- Run a Maven build and see output in a separate tab (`b`)
- Rescan after manually applying updates (`r`)
- Switch between tabs (Tab/`]`/Right forward, Shift-Tab/`[`/Left backward, `p` for Plugins, `d` for Diff/Dependencies, `m` for Messages)
- View scan errors and warnings inline below the dependency table
- Quit (`q`)

### Updating Dependencies

```bash
# Update current directory's pom.xml (default) (alias: "up")
mdm update

# Update pom.xml files with latest versions
mdm update pom.xml

# Update multiple projects
mdm update project1/pom.xml project2/pom.xml

# Update only specific dependencies (by groupId:artifactId, groupId, or artifactId)
mdm update --only org.assertj:assertj-core
mdm update --only org.assertj
mdm update --only assertj-core
mdm update --only org.assertj:assertj-core org.junit.jupiter

# Managed dependencies/plugins are overridden locally if they have no local <version>
mdm update pom.xml --only org.junit.jupiter:junit-jupiter

# Only apply patch updates
mdm update --patch

# Only apply patch and minor updates (no major)
mdm update --minor

# Update with JSON output
mdm update pom.xml --format json

# Update and save report to file
mdm update pom.xml --output updates.json
```

## Output Examples

### JSON Output

```json
{
  "projects": [
    {
      "pomFile": "/path/to/pom.xml",
      "dependencies": [
        {
          "groupId": "org.springframework.boot",
          "artifactId": "spring-boot-starter-web",
          "effectiveVersion": "3.1.0",
          "latestVersion": "3.2.1",
          "updateType": "MINOR",
          "availableVersions": ["3.1.1", "3.1.5", "3.2.0", "3.2.1"]
        }
      ]
    }
  ],
  "summary": {
    "totalDependencies": 3,
    "outdatedDependencies": 3,
    "majorUpdates": 0,
    "minorUpdates": 2,
    "patchUpdates": 1
  }
}
```

### Text Output

```
Maven Dependency Update Report
==============================

Project: /path/to/pom.xml

Outdated Dependencies (3):
┌─────────┬──────────────────────────┬─────────────────────────┬──────────┬─────────────────┐
│ Scope   │ Group ID                 │ Artifact ID             │ Declared │ Update          │
├─────────┼──────────────────────────┼─────────────────────────┼──────────┼─────────────────┤
│ compile │ org.springframework.boot │ spring-boot-starter-web │ 3.1.0    │ 3.1.0 → 3.2.1   │
│ compile │ com.fasterxml.jackson    │ jackson-databind        │ 2.15.0   │ 2.15.0 → 2.16.1 │
│ test    │ org.junit.jupiter        │ junit-jupiter           │ 5.10.0   │ 5.10.0 → 5.10.1 │
└─────────┴──────────────────────────┴─────────────────────────┴──────────┴─────────────────┘

Summary: 3 updates available (0 major, 2 minor, 1 patch)
```

## Development

This project was developed using Test-Driven Development (TDD) with strict Red-Green-Refactor cycles.

### Running Tests

To run *all* test, i.e. unit *and* ITs: `mvn verify`

To run all unit tests (but not the ITs): `mvn test`
To run a specific unit test class: `mvn test -Dtest=VersionTest`
To run a specific unit test method: `mvn test -Dtest="DependencyTest#shouldFormatPluginDependencyToString"`

To run all unit tests: `mvn verify -Dskip.surefire.tests`
To run a specific integration test class (but no unit test): `mvn verify -Dskip.surefire.tests -Dit.test=CheckCommandIT`
To run a specific integration test method (but no unit test):
`mvn -Dskip.surefire.tests -Dit.test="CheckCommandIT#shouldHandlePropertyBasedVersions"`
**IMPORTANT** note the quotes in some of these commands, as this string contains a `#` character!

Managed dependencies and plugins are reported with their Maven-effective current version.
Human-readable outputs merge local git/POM state into a `Declared` column and merge effective/latest information into an `Update` column.
When you update one that has no local `<version>`, the tool adds a local explicit version override to the current `pom.xml`.

At the moment, effective-version detection uses Maven's default profile activation context for the current environment;
explicit profile selection is not implemented yet.

### Code Style

This project uses the **tdder** plugin for TDD, Clean Code, Java, and Maven conventions.
See `CLAUDE.md` for details.

## License

[Apache License 2.0](LICENSE.md)

## Contributing

Contributions welcome! Please ensure all tests pass before submitting a PR.
