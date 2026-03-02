package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class PomTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldInheritGroupIdFromParent() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>1.0.0</version>
                    </parent>
                
                    <artifactId>child-project</artifactId>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        then(pom.coordinates().groupId()).isEqualTo("com.example");
    }

    @Test
    void shouldInheritVersionFromParent() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent-project</artifactId>
                        <version>2.0.0</version>
                    </parent>
                
                    <artifactId>child-project</artifactId>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        then(pom.coordinates().version()).isEqualTo(Version.fromString("2.0.0"));
    }

    @Test
    void shouldParseFromString() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.dependencies()).hasSize(1);
        then(pom.dependencies().getFirst().groupId()).isEqualTo("org.junit.jupiter");
        then(pom.parent()).isEmpty();
    }

    @Test
    void shouldParseFromPath() {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);

        var pom = Pom.parse(pomFile).orElseThrow();

        then(pom.parent().map(Dependency::groupId)).contains("org.springframework.boot");
        then(pom.dependencies()).isEmpty();
    }

    @Test
    void shouldParseProperties() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <junit.version>5.10.0</junit.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.properties()).containsEntry("junit.version", "5.10.0");
        then(pom.dependencies()).hasSize(1);
        then(pom.dependencies().getFirst().version().toString()).isEqualTo("5.10.0");
    }

    @Test
    void shouldParseVersionPropertyFromPropertyReference() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <jackson.version>2.20.0</jackson.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>${jackson.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.dependencies().getFirst().versionProperty()).isEqualTo("jackson.version");
    }

    @Test
    void shouldNotSetVersionPropertyWhenVersionIsDirect() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.dependencies().getFirst().versionProperty()).isNull();
    }

    @Test
    void shouldParseModules() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent-project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <modules>
                        <module>module-a</module>
                        <module>module-b</module>
                    </modules>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.modules()).containsExactly("module-a", "module-b");
    }

    @Test
    void shouldReturnEmptyListWhenNoModules() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.modules()).isEmpty();
    }

    @Test
    void shouldParsePlugins() {
        var pomContent = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.8.1</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);

        var pom = Pom.parse(pomContent).orElseThrow();

        then(pom.plugins()).hasSize(1);
        then(pom.plugins().getFirst().groupId()).isEqualTo("org.apache.maven.plugins");
        then(pom.plugins().getFirst().artifactId()).isEqualTo("maven-compiler-plugin");
        then(pom.plugins().getFirst().version().toString()).isEqualTo("3.8.1");
    }

    @Test
    void shouldDefaultPluginGroupIdToApacheMavenPlugins() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.8.1</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        then(pom.plugins().getFirst().groupId()).isEqualTo("org.apache.maven.plugins");
        then(pom.plugins().getFirst().artifactId()).isEqualTo("maven-compiler-plugin");
        then(pom.plugins().getFirst().version().toString()).isEqualTo("3.8.1");
    }

    private Path writePom(String pomContent) {
        var pomFile = tempDir.resolve("pom.xml");
        try {
            writeString(pomFile, pomContent);
        } catch (IOException e) {
            throw new RuntimeException("failed to write " + pomFile, e);
        }
        return pomFile;
    }

    @Test
    void shouldParseEmptyGroupIdWhenMissing() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <artifactId>some-lib</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        var dep = pom.dependencies().getFirst();
        then(dep.groupId()).isEmpty();
        then(dep.artifactId()).isEqualTo("some-lib");
    }

    @Test
    void shouldParseEmptyArtifactIdWhenMissing() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        var dep = pom.dependencies().getFirst();
        then(dep.groupId()).isEqualTo("com.example");
        then(dep.artifactId()).isEmpty();
    }

    @Test
    void shouldParseUnresolvablePropertyVersionAsNull() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();

        var dep = pom.dependencies().getFirst();
        then(dep.groupId()).isEqualTo("org.projectlombok");
        then(dep.artifactId()).isEqualTo("lombok");
        then(dep.version()).isNull();
        then(dep.versionProperty()).isEqualTo("lombok.version");
    }

    // Update tests

    @Test
    void shouldUpdateSingleDependencyVersion() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<version>5.11.0</version>");
        then(updatedContent).doesNotContain("<version>5.10.0</version>");
    }

    @Test
    void shouldUpdateMultipleDependencyVersions() throws IOException {
        Path pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                        <dependency>
                            <groupId>info.picocli</groupId>
                            <artifactId>picocli</artifactId>
                            <version>4.7.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var updates = Stream.of(
                new Update(
                        new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                        Version.fromString("5.11.0"),
                        List.of(),
                        none
                ),
                new Update(
                        new Dependency(dependency, "info.picocli", "picocli", Version.fromString("4.7.0"), compile, null),
                        Version.fromString("4.7.7"),
                        List.of(),
                        none
                )
        );

        pom.apply(updates);
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<version>5.11.0</version>");
        then(updatedContent).contains("<version>4.7.7</version>");
        then(updatedContent).doesNotContain("<version>5.10.0</version>");
        then(updatedContent).doesNotContain("<version>4.7.0</version>");
    }

    @Test
    void shouldUpdatePropertyBasedVersion() throws IOException {
        Path pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <jackson.version>2.20.0</jackson.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>${jackson.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind", Version.fromString("2.20.0"), compile, "jackson.version"),
                Version.fromString("2.21.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<jackson.version>2.21.0</jackson.version>");
        then(updatedContent).doesNotContain("<jackson.version>2.20.0</jackson.version>");
    }

    @Test
    void shouldPreserveXmlFormatting() throws IOException {
        Path pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("    <dependencies>");
        then(updatedContent).contains("        <dependency>");
        then(updatedContent).contains("            <groupId>org.junit.jupiter</groupId>");
    }

    @Test
    void shouldHandleNoUpdatesNeeded() throws IOException {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);

        var pom = Pom.parse(pomFile).orElseThrow();

        pom.apply(Stream.of());
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).isEqualTo(pomContent);
    }

    @Test
    void shouldUpdateVersionWithLeadingWhitespace() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>
                                5.10.0
                            </version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("5.11.0");
        then(updatedContent).doesNotContain("5.10.0");
    }

    @Test
    void shouldUpdateVersionWithComment() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.sshd</groupId>
                            <artifactId>sshd-common</artifactId>
                            <version>
                                <!--we can't go to 2.15.x, because Quarkus elytron LDAP depends on this conflicting version of sshd-common-->
                                2.12.1
                            </version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.apache.sshd", "sshd-common", Version.fromString("2.12.1"), compile, null),
                Version.fromString("2.13.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("2.13.0");
        then(updatedContent).doesNotContain("2.12.1");
        then(updatedContent).contains("<!--we can't go to 2.15.x, because Quarkus elytron LDAP depends on this conflicting version of sshd-common-->");
    }

    @Test
    void shouldUpdatePropertyVersionWithWhitespace() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <jackson.version>
                            2.20.0
                        </jackson.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>${jackson.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind", Version.fromString("2.20.0"), compile, "jackson.version"),
                Version.fromString("2.21.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("2.21.0");
        then(updatedContent).doesNotContain("2.20.0");
    }

    @Test
    void shouldUpdatePropertyVersionWithComment() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <sshd.version>
                            <!-- Don't upgrade beyond 2.12.x due to Quarkus compatibility -->
                            2.12.1
                        </sshd.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.sshd</groupId>
                            <artifactId>sshd-common</artifactId>
                            <version>${sshd.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.apache.sshd", "sshd-common", Version.fromString("2.12.1"), compile, "sshd.version"),
                Version.fromString("2.13.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("2.13.0");
        then(updatedContent).doesNotContain("2.12.1");
        then(updatedContent).contains("<!-- Don't upgrade beyond 2.12.x due to Quarkus compatibility -->");
    }

    @Test
    void shouldUpdateVersionWhenGroupIdIsProperty() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <junit.groupId>org.junit.jupiter</junit.groupId>
                        <junit.version>5.10.0</junit.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>${junit.groupId}</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, "junit.version"),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<junit.version>5.11.0</junit.version>");
        then(updatedContent).doesNotContain("<junit.version>5.10.0</junit.version>");
    }

    @Test
    void shouldUpdateParentVersion() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var parentUpdate = new Update(
                new Dependency(dependency, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(parentUpdate));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<version>3.2.0</version>");
        then(updatedContent).doesNotContain("<version>3.1.0</version>");
    }

    @Test
    void shouldUpdateParentVersionWithProperty() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>${spring-boot.version}</version>
                    </parent>
                
                    <properties>
                        <spring-boot.version>3.1.0</spring-boot.version>
                    </properties>
                
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var parentUpdate = new Update(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), null, "spring-boot.version"),
                Version.fromString("3.2.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(parentUpdate));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<spring-boot.version>3.2.0</spring-boot.version>");
        then(updatedContent).doesNotContain("<spring-boot.version>3.1.0</spring-boot.version>");
    }

    @Test
    void shouldUpdatePluginVersion() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.8.1</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(plugin, "org.apache.maven.plugins", "maven-compiler-plugin", Version.fromString("3.8.1"), compile, null),
                Version.fromString("3.13.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<version>3.13.0</version>");
        then(updatedContent).doesNotContain("<version>3.8.1</version>");
    }

    @Test
    void shouldSkipNonChanges() {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.11.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var sameVersion = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.11.0"), test, null),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );
        var nullCurrentVersion = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", null, test, null),
                Version.fromString("5.11.0"),
                List.of(),
                none
        );
        var nullLatestVersion = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.11.0"), test, null),
                null,
                List.of(),
                none
        );
        var bothNull = new Update(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", null, test, null),
                null,
                List.of(),
                none
        );

        pom.apply(Stream.of(sameVersion, nullCurrentVersion, nullLatestVersion, bothNull));

        then(pom.isDirty()).isFalse();
    }

    @Test
    void shouldResetToOriginalContent() throws IOException {
        var pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """;
        var pomFile = writePom(pomContent);
        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                pom.dependencies().getFirst(),
                Version.fromString("5.11.0"),
                List.of(),
                UpdateType.minor
        );

        pom.apply(Stream.of(update));
        then(pom.isDirty()).isTrue();

        pom.reset();
        pom.writeToDisk();

        then(pom.isDirty()).isFalse();
        then(readString(pomFile)).isEqualTo(pomContent);
    }

    @Test
    void shouldUpdateCorrectPropertyWhenMultiplePropertiesHaveSameVersion() throws IOException {
        var pomFile = writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <alpha.version>2.0.0</alpha.version>
                        <beta.version>2.0.0</beta.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>alpha</artifactId>
                            <version>${alpha.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>beta</artifactId>
                            <version>${beta.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var pom = Pom.parse(pomFile).orElseThrow();
        var update = new Update(
                new Dependency(dependency, "com.example", "alpha", Version.fromString("2.0.0"), compile, "alpha.version"),
                Version.fromString("3.0.0"),
                List.of(),
                none
        );

        pom.apply(Stream.of(update));
        pom.writeToDisk();

        var updatedContent = readString(pomFile);
        then(updatedContent).contains("<alpha.version>3.0.0</alpha.version>");
        then(updatedContent).contains("<beta.version>2.0.0</beta.version>");
    }
}
