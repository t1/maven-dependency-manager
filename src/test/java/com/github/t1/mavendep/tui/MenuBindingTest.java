package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Tab;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.github.t1.mavendep.tui.DashboardModel.Tab.BUILD;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;
import static org.assertj.core.api.BDDAssertions.then;

class MenuBindingTest {

    @Test void shouldDisplayText() {
        var binding = new MenuBinding("[Space] toggle", Set.of(DEPENDENCIES, PLUGINS));

        then(binding.display()).isEqualTo("[Space] toggle");
    }

    @Test void shouldBeAvailableOnMatchingTab() {
        var binding = new MenuBinding("[Space] toggle", Set.of(DEPENDENCIES, PLUGINS));

        then(binding.isAvailableOn(DEPENDENCIES)).isTrue();
        then(binding.isAvailableOn(BUILD)).isFalse();
    }

    @Test void shouldBeAvailableOnAllTabs() {
        var binding = new MenuBinding("[b]uild", Set.of(Tab.values()));

        then(binding.isAvailableOn(DEPENDENCIES)).isTrue();
        then(binding.isAvailableOn(BUILD)).isTrue();
        then(binding.isAvailableOn(DIFF)).isTrue();
    }
}
