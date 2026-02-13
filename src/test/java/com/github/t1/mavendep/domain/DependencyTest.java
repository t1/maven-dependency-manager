package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.t1.mavendep.domain.DependencyType.dependency;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class DependencyTest {

    @Test
    void shouldBeValid_whenAllFieldsArePresent() {
        var dependency = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.compile, null);

        then(dependency.isValid()).isTrue();
    }

    @Test
    void shouldBeInvalid_whenGroupIdIsNull() {
        var dependency = new Dependency(null, "lib", Version.fromString("1.0.0"), Scope.compile, null);

        then(dependency.isValid()).isFalse();
    }

    @Test
    void shouldBeInvalid_whenArtifactIdIsNull() {
        var dependency = new Dependency("com.example", null, Version.fromString("1.0.0"), Scope.compile, null);

        then(dependency.isValid()).isFalse();
    }

    @Test
    void shouldBeInvalid_whenVersionIsNull() {
        var dependency = new Dependency("com.example", "lib", null, Scope.compile, null);

        then(dependency.isValid()).isFalse();
    }

    @Test
    void shouldBeManaged_whenVersionIsNull() {
        var dependency = new Dependency("com.example", "lib", null, Scope.compile, null);

        then(dependency.isManaged()).isTrue();
    }

    @Test
    void shouldNotBeManaged_whenVersionIsPresent() {
        var dependency = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.compile, null);

        then(dependency.isManaged()).isFalse();
    }

    @Test
    void shouldNotBeManaged_whenGroupIdIsNull() {
        var dependency = new Dependency(null, "lib", null, Scope.compile, null);

        then(dependency.isManaged()).isFalse();
    }

    @Test
    void shouldNotBeManaged_whenArtifactIdIsNull() {
        var dependency = new Dependency("com.example", null, null, Scope.compile, null);

        then(dependency.isManaged()).isFalse();
    }

    @Test
    void shouldFormatToString_whenAllFieldsArePresent() {
        var dependency = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.test, null);

        then(dependency.toString()).isEqualTo("com.example:lib:1.0.0:test");
    }

    @Test
    void shouldFormatToStringWithoutVersion_whenVersionIsNull() {
        var dependency = new Dependency("com.example", "lib", null, Scope.compile, null);

        then(dependency.toString()).isEqualTo("com.example:lib");
    }

    @Test
    void shouldFormatToStringWithoutScope_whenScopeIsDefault() {
        var dependency = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.DEFAULT, null);

        then(dependency.toString()).isEqualTo("com.example:lib:1.0.0");
    }

    @Test
    void shouldFormatToStringWithVersionAndScope_whenBothArePresent() {
        var dependency = new Dependency("com.example", "lib", Version.fromString("2.5.1"), Scope.provided, null);

        then(dependency.toString()).isEqualTo("com.example:lib:2.5.1:provided");
    }

    @Test
    void shouldStoreVersionProperty() {
        var dep = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.compile, "lib.version");

        then(dep.versionProperty()).isEqualTo("lib.version");
    }

    @Test
    void shouldCreateUpdate_whenCalled() {
        var dep = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.compile, null);
        var availableVersions = List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0"));
        var latestVersion = Version.fromString("2.0.0");

        var update = dep.toUpdate(dependency, latestVersion, availableVersions, major);

        then(update.groupId()).isEqualTo("com.example");
        then(update.artifactId()).isEqualTo("lib");
        then(update.currentVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(update.latestVersion()).isEqualTo(latestVersion);
        then(update.updateType()).isEqualTo(major);
        then(update.availableVersions()).isEqualTo(availableVersions);
        then(update.scope()).isEqualTo(Scope.compile);
    }

    @Test
    void shouldPassVersionPropertyToUpdate() {
        var dep = new Dependency("com.example", "lib", Version.fromString("1.0.0"), Scope.compile, "lib.version");
        var latestVersion = Version.fromString("2.0.0");

        var update = dep.toUpdate(dependency, latestVersion, List.of(), major);

        then(update.versionProperty()).isEqualTo("lib.version");
    }

    @Test
    void shouldCreateUpdateWithTestScope_whenScopeIsTest() {
        var dep = new Dependency("org.junit", "junit", Version.fromString("4.12"), Scope.test, null);
        var availableVersions = List.of(Version.fromString("4.12"), Version.fromString("4.13"));
        var latestVersion = Version.fromString("4.13");

        var update = dep.toUpdate(dependency, latestVersion, availableVersions, patch);

        then(update.scope()).isEqualTo(Scope.test);
    }
}
