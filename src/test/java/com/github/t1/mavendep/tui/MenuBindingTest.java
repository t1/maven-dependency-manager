package com.github.t1.mavendep.tui;

import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.github.t1.mavendep.tui.DashboardModel.Tab.ALL_TABS;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.BUILD;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCY_TABS;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;
import static dev.tamboui.tui.event.KeyCode.ENTER;
import static dev.tamboui.tui.event.KeyCode.ESCAPE;
import static dev.tamboui.tui.event.KeyCode.UP;
import static org.assertj.core.api.BDDAssertions.then;

class MenuBindingTest {

    @Test void shouldDisplayOnMatchingTab() {
        var binding = MenuBinding.charBinding('s', "[s]how all", DEPENDENCY_TABS, _ -> true);

        then(binding.displayFor(DEPENDENCIES)).hasValue("[s]how all");
        then(binding.displayFor(BUILD)).isEmpty();
    }

    @Test void shouldDisplayOnAllTabs() {
        var binding = MenuBinding.charBinding('b', "[b]uild", ALL_TABS, _ -> true);

        then(binding.displayFor(DEPENDENCIES)).hasValue("[b]uild");
        then(binding.displayFor(BUILD)).hasValue("[b]uild");
        then(binding.displayFor(DIFF)).hasValue("[b]uild");
    }

    @Test void shouldMatchCharKey() {
        var binding = MenuBinding.charBinding('s', "[s]how all", DEPENDENCY_TABS, _ -> true);

        then(binding.matches(KeyEvent.ofChar('s'))).isTrue();
        then(binding.matches(KeyEvent.ofChar('b'))).isFalse();
        then(binding.matches(KeyEvent.ofKey(ENTER))).isFalse();
    }

    @Test void shouldMatchKeyCode() {
        var binding = MenuBinding.keyBinding(ESCAPE, "[Escape] foo", DEPENDENCY_TABS, _ -> true);

        then(binding.matches(KeyEvent.ofKey(ESCAPE))).isTrue();
        then(binding.matches(KeyEvent.ofChar('e'))).isFalse();
    }

    @Test void shouldSupportDynamicDisplay() {
        var binding = MenuBinding.charBinding('d',
                tab -> tab == DIFF ? "[d]ependencies" : "[d]iff",
                _ -> true);

        then(binding.displayFor(DIFF)).hasValue("[d]ependencies");
        then(binding.displayFor(DEPENDENCIES)).hasValue("[d]iff");
    }

    @Test void shouldHideDynamicDisplayWhenNull() {
        var binding = MenuBinding.charBinding('p',
                tab -> tab != PLUGINS ? "[p]lugins" : null,
                _ -> true);

        then(binding.displayFor(DEPENDENCIES)).hasValue("[p]lugins");
        then(binding.displayFor(PLUGINS)).isEmpty();
    }

    @Test void shouldHideHiddenCharBinding() {
        var binding = MenuBinding.charBinding('a', _ -> true);

        then(binding.displayFor(DEPENDENCIES)).isEmpty();
        then(binding.matches(KeyEvent.ofChar('a'))).isTrue();
    }

    @Test void shouldHideHiddenKeyBinding() {
        var binding = MenuBinding.hiddenBinding(key -> key.code() == UP, _ -> true);

        then(binding.displayFor(DEPENDENCIES)).isEmpty();
        then(binding.matches(KeyEvent.ofKey(UP))).isTrue();
    }

    @Test void shouldDisplayOnly() {
        var binding = MenuBinding.displayOnlyBinding("[q]uit", ALL_TABS);

        then(binding.displayFor(DEPENDENCIES)).hasValue("[q]uit");
        then(binding.matches(KeyEvent.ofChar('q'))).isFalse();
    }

    @Test void shouldDisplayOnlyOnSpecificTabs() {
        var binding = MenuBinding.displayOnlyBinding("[Space] toggle", DEPENDENCY_TABS);

        then(binding.displayFor(DEPENDENCIES)).hasValue("[Space] toggle");
        then(binding.displayFor(BUILD)).isEmpty();
    }

    @Test void shouldActivateAction() {
        var activated = new boolean[]{false};
        var binding = MenuBinding.charBinding('b', "[b]uild", ALL_TABS, _ -> {
            activated[0] = true;
            return true;
        });

        var result = binding.activate(KeyEvent.ofChar('b'));

        then(result).isTrue();
        then(activated[0]).isTrue();
    }

    @Test void shouldNotActivateDisplayOnly() {
        var binding = MenuBinding.displayOnlyBinding("[q]uit", ALL_TABS);

        then(binding.activate(KeyEvent.ofChar('q'))).isFalse();
    }

    @Test void shouldContainDependenciesAndPluginsInDependencyTabs() {
        then(DEPENDENCY_TABS).containsExactlyInAnyOrder(DEPENDENCIES, PLUGINS);
    }

    @Test void shouldContainAllTabsInAllTabs() {
        then(ALL_TABS).isEqualTo(EnumSet.allOf(DashboardModel.Tab.class));
    }
}
