package com.github.t1.mavendep.tui;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;

/// Renders scrollable maven build output.
class BuildOutputPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var lines = model.buildOutputLines();
        var styledLines = lines.stream().map(this::styleLine).toList();
        var text = Text.from(styledLines);

        var title = "Build Output";
        if (model.buildExitCode() != null) {
            title += (model.buildExitCode() == 0) ? " [SUCCESS]" : " [FAILED: " + model.buildExitCode() + "]";
        }

        // Auto-scroll to bottom
        var visibleLines = Math.max(1, area.height() - 2); // account for borders
        var scroll = Math.max(0, lines.size() - visibleLines);

        var paragraph = Paragraph.builder()
                .text(text)
                .scroll(scroll)
                .block(Block.builder().borders(Borders.ALL).title(title).build())
                .build();

        frame.renderWidget(paragraph, area);
    }

    Line styleLine(String line) {
        if (line.contains("BUILD SUCCESS")) return Line.styled(line, Style.EMPTY.fg(Color.GREEN).bold());
        if (line.contains("BUILD FAILURE")) return Line.styled(line, Style.EMPTY.fg(Color.RED).bold());
        if (isSeparator(line) || line.startsWith("Downloading ") || line.startsWith("Downloaded "))
            return Line.styled(line, Style.EMPTY.fg(Color.DARK_GRAY));
        if (line.startsWith("[INFO]")) return styledPrefix(line, "[INFO]", Color.CYAN);
        if (line.startsWith("[WARNING]")) return styledPrefix(line, "[WARNING]", Color.YELLOW);
        if (line.startsWith("[ERROR]")) return styledPrefix(line, "[ERROR]", Color.RED);
        return Line.from(line);
    }

    private boolean isSeparator(String line) {
        var trimmed = line.replaceFirst("^\\[\\w+] ", "");
        return !trimmed.isEmpty() && trimmed.chars().allMatch(c -> c == '-' || c == '=' || c == '*');
    }

    private Line styledPrefix(String line, String prefix, Color color) {
        return Line.from(
                Span.styled(prefix, Style.EMPTY.fg(color)),
                Span.raw(line.substring(prefix.length())));
    }
}
