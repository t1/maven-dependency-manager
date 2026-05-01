package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.compile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

class DependencyTablePanelTest {

    @Test void shouldMapFirstItemInFirstGroup() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), mockUpdates(2)),
                Map.entry(Path.of("sub/pom.xml"), mockUpdates(1)));

        then(DependencyTablePanel.toVisualIndex(0, grouped)).isEqualTo(1);
    }

    @Test void shouldMapSecondItemInFirstGroup() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), mockUpdates(2)),
                Map.entry(Path.of("sub/pom.xml"), mockUpdates(1)));

        then(DependencyTablePanel.toVisualIndex(1, grouped)).isEqualTo(2);
    }

    @Test void shouldMapFirstItemInSecondGroup() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), mockUpdates(2)),
                Map.entry(Path.of("sub/pom.xml"), mockUpdates(1)));

        // visual: [header0, dep0, dep1, header1, dep2] → dep2 is at visual index 4
        then(DependencyTablePanel.toVisualIndex(2, grouped)).isEqualTo(4);
    }

    @Test void shouldMapAcrossThreeGroups() {
        var grouped = List.of(
                Map.entry(Path.of("pom.xml"), mockUpdates(1)),
                Map.entry(Path.of("a/pom.xml"), mockUpdates(1)),
                Map.entry(Path.of("b/pom.xml"), mockUpdates(1)));

        // visual: [h0, d0, h1, d1, h2, d2] → indices 0,1,2,3,4,5
        then(DependencyTablePanel.toVisualIndex(0, grouped)).isEqualTo(1);
        then(DependencyTablePanel.toVisualIndex(1, grouped)).isEqualTo(3);
        then(DependencyTablePanel.toVisualIndex(2, grouped)).isEqualTo(5);
    }

    @Test void shouldFormatUnknownEffectiveUpdate() {
        var update = new Update(
                new Dependency(dependency, "com.example", "managed-artifact", null, compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                com.github.t1.mavendep.domain.UpdateType.none);
        var model = mock(DashboardModel.class);
        org.mockito.BDDMockito.given(model.currentVersion(update)).willReturn(null);

        then(DependencyTablePanel.formatUpdate(update, model)).isEqualTo("? → 2.0.0");
    }

    private static List<Update> mockUpdates(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(_ -> mock(Update.class))
                .toList();
    }
}
