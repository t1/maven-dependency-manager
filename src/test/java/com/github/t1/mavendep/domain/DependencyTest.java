package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.provided;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class DependencyTest {

    @Test
    void shouldBeValid_whenAllFieldsArePresent() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null);

        then(dep.isValid()).isTrue();
    }

    @Test
    void shouldBeInvalid_whenGroupIdIsNull() {
        var dep = new Dependency(dependency, null, "lib", Version.fromString("1.0.0"), compile, null);

        then(dep.isValid()).isFalse();
    }

    @Test
    void shouldBeInvalid_whenArtifactIdIsNull() {
        var dep = new Dependency(dependency, "com.example", null, Version.fromString("1.0.0"), compile, null);

        then(dep.isValid()).isFalse();
    }

    @Test
    void shouldBeInvalid_whenVersionIsNull() {
        var dep = new Dependency(dependency, "com.example", "lib", null, compile, null);

        then(dep.isValid()).isFalse();
    }

    @Test
    void shouldBeManaged_whenVersionIsNull() {
        var dep = new Dependency(dependency, "com.example", "lib", null, compile, null);

        then(dep.isManaged()).isTrue();
    }

    @Test
    void shouldNotBeManaged_whenVersionIsPresent() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null);

        then(dep.isManaged()).isFalse();
    }

    @Test
    void shouldNotBeManaged_whenGroupIdIsNull() {
        var dep = new Dependency(dependency, null, "lib", null, compile, null);

        then(dep.isManaged()).isFalse();
    }

    @Test
    void shouldNotBeManaged_whenArtifactIdIsNull() {
        var dep = new Dependency(dependency, "com.example", null, null, compile, null);

        then(dep.isManaged()).isFalse();
    }

    @Test
    void shouldFormatParentDependencyToString() {
        var dep = new Dependency(parent, "com.example", "lib", Version.fromString("1.0.0"), test, null);

        then(dep.toString()).isEqualTo("parent=com.example:lib:1.0.0:test");
    }

    @Test
    void shouldFormatPluginDependencyToString() {
        var dep = new Dependency(plugin, "com.example", "lib", Version.fromString("1.0.0"), test, null);

        then(dep.toString()).isEqualTo("plugin=com.example:lib:1.0.0:test");
    }

    @Test
    void shouldFormatToString_whenAllFieldsArePresent() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), test, null);

        then(dep.toString()).isEqualTo("com.example:lib:1.0.0:test");
    }

    @Test
    void shouldFormatToStringWithoutVersion_whenVersionIsNull() {
        var dep = new Dependency(dependency, "com.example", "lib", null, compile, null);

        then(dep.toString()).isEqualTo("com.example:lib");
    }

    @Test
    void shouldFormatToStringWithoutScope_whenScopeIsDefault() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), DEFAULT, null);

        then(dep.toString()).isEqualTo("com.example:lib:1.0.0");
    }

    @Test
    void shouldFormatToStringWithVersionAndScope_whenBothArePresent() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("2.5.1"), provided, null);

        then(dep.toString()).isEqualTo("com.example:lib:2.5.1:provided");
    }

    @Test
    void shouldStoreVersionProperty() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, "lib.version");

        then(dep.versionProperty()).isEqualTo("lib.version");
    }

    @Test
    void shouldCreateUpdate_whenCalled() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null);
        var availableVersions = List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0"));
        var latestVersion = Version.fromString("2.0.0");

        var update = dep.toUpdate(latestVersion, availableVersions, major);

        then(update.groupId()).isEqualTo("com.example");
        then(update.artifactId()).isEqualTo("lib");
        then(update.currentVersion()).isEqualTo(Version.fromString("1.0.0"));
        then(update.latestVersion()).isEqualTo(latestVersion);
        then(update.updateType()).isEqualTo(major);
        then(update.availableVersions()).isEqualTo(availableVersions);
        then(update.scope()).isEqualTo(compile);
    }

    @Test
    void shouldPassVersionPropertyToUpdate() {
        var dep = new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, "lib.version");
        var latestVersion = Version.fromString("2.0.0");

        var update = dep.toUpdate(latestVersion, List.of(), major);

        then(update.versionProperty()).isEqualTo("lib.version");
    }

    @Test
    void shouldCreateUpdateWithTestScope_whenScopeIsTest() {
        var dep = new Dependency(dependency, "org.junit", "junit", Version.fromString("4.12"), test, null);
        var availableVersions = List.of(Version.fromString("4.12"), Version.fromString("4.13"));
        var latestVersion = Version.fromString("4.13");

        var update = dep.toUpdate(latestVersion, availableVersions, patch);

        then(update.scope()).isEqualTo(test);
    }
}
