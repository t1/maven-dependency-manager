package com.github.t1.mavendep.tui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class BuildOutputPanelTest {

    private final BuildOutputPanel panel = new BuildOutputPanel();

    @Test void shouldStyleInfoPrefixAsCyan() {
        var line = panel.styleLine("[INFO] Compiling sources");

        then(line).isEqualTo(Line.from(
                Span.styled("[INFO]", Style.EMPTY.fg(Color.CYAN)),
                Span.raw(" Compiling sources")));
    }

    @Test void shouldStyleWarningPrefixAsYellow() {
        var line = panel.styleLine("[WARNING] Deprecated API");

        then(line).isEqualTo(Line.from(
                Span.styled("[WARNING]", Style.EMPTY.fg(Color.YELLOW)),
                Span.raw(" Deprecated API")));
    }
    @Test void shouldStyleErrorPrefixAsRed() {
        var line = panel.styleLine("[ERROR] Build failed");

        then(line).isEqualTo(Line.from(
                Span.styled("[ERROR]", Style.EMPTY.fg(Color.RED)),
                Span.raw(" Build failed")));
    }
    @Test void shouldStyleBuildSuccessAsGreenBold() {
        var line = panel.styleLine("[INFO] BUILD SUCCESS");

        then(line).isEqualTo(Line.from(
                Span.styled("[INFO] BUILD SUCCESS", Style.EMPTY.fg(Color.GREEN).bold())));
    }
    @Test void shouldStyleBuildFailureAsRedBold() {
        var line = panel.styleLine("[ERROR] BUILD FAILURE");

        then(line).isEqualTo(Line.from(
                Span.styled("[ERROR] BUILD FAILURE", Style.EMPTY.fg(Color.RED).bold())));
    }
    @Test void shouldStyleSeparatorLineAsDarkGray() {
        var dashes = panel.styleLine("[INFO] -----------------------------------");
        var equals = panel.styleLine("[INFO] ===================================");
        var stars = panel.styleLine("[INFO] ***********************************");

        var dimStyle = Style.EMPTY.fg(Color.DARK_GRAY);
        then(dashes).isEqualTo(Line.styled("[INFO] -----------------------------------", dimStyle));
        then(equals).isEqualTo(Line.styled("[INFO] ===================================", dimStyle));
        then(stars).isEqualTo(Line.styled("[INFO] ***********************************", dimStyle));
    }
    @Test void shouldStyleDownloadLineAsDarkGray() {
        var downloading = panel.styleLine("Downloading from central: https://repo.maven.apache.org/...");
        var downloaded = panel.styleLine("Downloaded from central: https://repo.maven.apache.org/... (15 kB)");

        var dimStyle = Style.EMPTY.fg(Color.DARK_GRAY);
        then(downloading).isEqualTo(Line.styled("Downloading from central: https://repo.maven.apache.org/...", dimStyle));
        then(downloaded).isEqualTo(Line.styled("Downloaded from central: https://repo.maven.apache.org/... (15 kB)", dimStyle));
    }
    @Test void shouldStylePlainLineWithDefaultStyle() {
        var line = panel.styleLine("some random output");

        then(line).isEqualTo(Line.from("some random output"));
    }
}
