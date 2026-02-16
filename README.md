# Maven Dependency Manager

A Java-based CLI tool to check for newer versions of Maven dependencies across multiple projects, with intelligent
caching using the local Maven repository.

## Features

- **Dependency Version Checking**: Queries Maven Central for the latest versions of your dependencies
- **Parent POM Version Checking**: Also checks and updates parent POM versions
- **Smart Caching**: Leverages the local Maven repository (~/.m2) to minimize network requests, with `--force-cache-update` to bypass the cache
- **Multi-Project Support**: Scan single or multiple Maven projects at once
- **Multiple Output Formats**: JSON and human-readable text reports
- **Show Available Versions**: Query all available versions for a specific artifact by `groupId:artifactId`, `groupId`,
  or `artifactId` (resolving partial coordinates from your POM)
- **Automatic Updates**: Separate update command to update pom.xml files with the latest versions (supports both direct
  versions and property-based versions, including parent POMs)
- **Parent Property Resolution**: Version properties defined in a parent POM are resolved and updated in the parent
  when the parent is within the working set of POMs (via parameter or module resolution)
- **Flexible Version Parsing**: Handles standard semantic versions (`1.2.3`), multi-segment versions (`2.13.4.2`),
  qualifier suffixes (`1.0-RC1`, `5.1.5.Final`), and timestamp-based build numbers (`0.7.7.201606060606`).
  Qualifiers are classified as pre-release (`SNAPSHOT`, `alpha`, `beta`, `RC`, `M`, ...) or
  release (`Final`, `GA`, `RELEASE`, `SP`) to filter out unstable versions.
  Note that this is not 100% compatible to Maven's version handling.
- **Robust Error Handling**: Network failures and malformed metadata are logged but don't halt execution
- **CI/CD Ready**: Perfect for automated pipeline integration

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
# Check current directory's pom.xml (default)
mdm check

# Check a single project
mdm check pom.xml

# Check a project directory (looks for pom.xml within)
mdm check my-project/

# Check multiple projects
mdm check project1/pom.xml project2/pom.xml

# Scan directory recursively
mdm check --recursive ./projects
```

### Output Formats

```bash
# JSON output
mdm check pom.xml --format json

# Text output (default)
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

### Updating Dependencies

```bash
# Update current directory's pom.xml (default)
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
          "currentVersion": "3.1.0",
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
┌─────────┬──────────────────────────┬─────────────────────────┬─────────┬────────┬───────┐
│ Scope   │ Group ID                 │ Artifact ID             │ Current │ Latest │ Type  │
├─────────┼──────────────────────────┼─────────────────────────┼─────────┼────────┼───────┤
│ compile │ org.springframework.boot │ spring-boot-starter-web │ 3.1.0   │ 3.2.1  │ MINOR │
│ compile │ com.fasterxml.jackson    │ jackson-databind        │ 2.15.0  │ 2.16.1 │ MINOR │
│ test    │ org.junit.jupiter        │ junit-jupiter           │ 5.10.0  │ 5.10.1 │ PATCH │
└─────────┴──────────────────────────┴─────────────────────────┴─────────┴────────┴───────┘

Summary: 3 updates available (0 major, 2 minor, 1 patch)
```

## Development

This project was developed using Test-Driven Development (TDD) with strict Red-Green-Refactor cycles.

### Running Tests

To run all unit tests: `mvn test`
To run a specific unit test: `mvn test -Dtest=VersionTest`
To run a specific integration test (but no unit test): `mvn -Dskip.surefire.tests -Dit.test=CheckCommandIT`

### Code Style

Next to the general Clean Code ideals (described in CLEAN_CODE.md)
and the general practice of Test-Driven Development (described in TDD.md),
we follow these Coding Conventions at all times:

- Use `var` for local type inference
- Use Markdown for Javadoc to improves in-IDE readability
- Add comments only if they add real value; prefer self-explanatory code. Comments should explain "why", not "what".
- Prefer static imports if they don't make the code less readable, e.g. not for `List.of(...)`
- Hide checked exceptions within a method by wrapping them in a RuntimeException
- Console output to `System.err` is acceptable for warnings and diagnostics (this is a CLI tool)
- Prefer constructor injection over using setters
- Tests:
    - BDD naming (`shouldParseSemanticVersion()`)
    - BDD assertions (`then(...).isEqualTo(...)`, not `assertThat(...).isEqualTo(...)`)
    - BDD logic when using Mockito, i.e. `given(...).willReturn(...)`
      instead of `when(...).thenReturn(...)` (and that's part of the BDD given block!)
    - Within a test method body, always use an empty line to separate the given, when, and then blocks.
      Other setup code (like `writeString()`) also belong to the given block.
    - Within the given block itself, do not add empty lines between setup statements.
      For example, `writeString()` followed by Mockito `given()` should have no empty line between them.

## License

[Apache License 2.0](LICENSE.md)

## Contributing

Contributions welcome! Please ensure all tests pass before submitting a PR.
