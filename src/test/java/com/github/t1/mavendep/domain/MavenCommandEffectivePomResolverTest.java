package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class MavenCommandEffectivePomResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldResolveManagedDependencyVersionFromLocalParent() throws IOException {
        var parentDir = tempDir.resolve("parent");
        var childDir = parentDir.resolve("child");
        createDirectories(childDir);

        writeString(parentDir.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.junit.jupiter</groupId>
                                <artifactId>junit-jupiter</artifactId>
                                <version>5.10.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        var childPom = childDir.resolve("pom.xml");
        writeString(childPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                        <relativePath>../pom.xml</relativePath>
                    </parent>
                
                    <artifactId>child</artifactId>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        var effectivePom = new MavenCommandEffectivePomResolver().resolve(childPom);

        then(effectivePom.versionFor(new Dependency(
                Dependency.DependencyType.dependency,
                "org.junit.jupiter",
                "junit-jupiter",
                null,
                Scope.DEFAULT,
                null))).isEqualTo(Version.fromString("5.10.0"));
    }
}
