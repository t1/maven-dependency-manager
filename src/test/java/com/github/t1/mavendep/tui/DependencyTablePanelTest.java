package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Update;
import org.junit.jupiter.api.Test;

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

    private static List<Update> mockUpdates(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(_ -> mock(Update.class))
                .toList();
    }
}
