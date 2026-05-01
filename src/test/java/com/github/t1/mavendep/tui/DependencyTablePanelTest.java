package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.runtime;
import static com.github.t1.mavendep.domain.Scope.test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

class DependencyTablePanelTest {

    @Test void shouldMapFirstItemInSinglePomAfterScopeHeader() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(
                        update("compile-a", compile),
                        update("test-a", test))));

        then(DependencyTablePanel.toVisualIndex(0, grouped, false)).isEqualTo(1);
    }

    @Test void shouldMapSecondItemInSinglePomAfterTwoScopeHeaders() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(
                        update("compile-a", compile),
                        update("test-a", test))));

        // visual: [compile-header, dep0, test-header, dep1]
        then(DependencyTablePanel.toVisualIndex(1, grouped, false)).isEqualTo(3);
    }

    @Test void shouldMapFirstItemInSecondPomAfterPomAndScopeHeaders() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(
                        update("compile-a", compile),
                        update("test-a", test))),
                Map.entry(Path.of("sub/pom.xml"), List.of(
                        update("runtime-a", runtime))));

        // visual: [pom0, compile, dep0, test, dep1, pom1, runtime, dep2]
        then(DependencyTablePanel.toVisualIndex(2, grouped, true)).isEqualTo(7);
    }

    @Test void shouldMapAcrossThreePomsWithScopeHeaders() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(update("compile-a", compile))),
                Map.entry(Path.of("a/pom.xml"), List.of(update("test-a", test))),
                Map.entry(Path.of("b/pom.xml"), List.of(update("runtime-a", runtime))));

        // visual: [pom0, compile, dep0, pom1, test, dep1, pom2, runtime, dep2]
        then(DependencyTablePanel.toVisualIndex(0, grouped, true)).isEqualTo(2);
        then(DependencyTablePanel.toVisualIndex(1, grouped, true)).isEqualTo(5);
        then(DependencyTablePanel.toVisualIndex(2, grouped, true)).isEqualTo(8);
    }

    @Test void shouldNotAddScopeHeaderForPluginWithoutProfile() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(pluginUpdate("compiler", null))));

        then(DependencyTablePanel.toVisualIndex(0, grouped, false)).isEqualTo(0);
    }

    @Test void shouldAddProfileHeaderForPluginWithProfile() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(pluginUpdate("failsafe", "native"))));

        then(DependencyTablePanel.toVisualIndex(0, grouped, false)).isEqualTo(1);
    }

    @Test void shouldNotAddHeaderForUnprofiledPluginBeforeProfiledOnes() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), List.of(
                        pluginUpdate("compiler", null),
                        pluginUpdate("failsafe", "native"))));

        // visual: [dep0, native, dep1]
        then(DependencyTablePanel.toVisualIndex(0, grouped, false)).isEqualTo(0);
        then(DependencyTablePanel.toVisualIndex(1, grouped, false)).isEqualTo(2);
    }

    @Test void shouldFormatUnknownEffectiveUpdate() {
        var update = new Update(
                new Dependency(dependency, "com.example", "managed-artifact", null, compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                UpdateType.none);
        var model = mock(DashboardModel.class);
        org.mockito.BDDMockito.given(model.currentVersion(update)).willReturn(null);

        then(DependencyTablePanel.formatUpdate(update, model)).isEqualTo("? → 2.0.0 ");
    }

    private static Update update(String artifactId, com.github.t1.mavendep.domain.Scope scope) {
        var dep = new Dependency(dependency, "com.example", artifactId, Version.fromString("1.0.0"), scope, null);
        return dep.toUpdate(Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.minor);
    }

    private static Update pluginUpdate(String artifactId, String profile) {
        var dep = new Dependency(plugin,
                new com.github.t1.mavendep.domain.Coordinates("org.apache.maven.plugins", artifactId, Version.fromString("1.0.0")),
                DEFAULT,
                null,
                profile);
        return dep.toUpdate(Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.minor);
    }
}
