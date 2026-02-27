package com.github.t1.mavendep.tui.e2e;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;

class NoOpTerminalDisplay implements TerminalDisplay {
    @Override public void setCursor(int x, int y) {}

    @Override public void setCursorShape(CursorShape cursorShape) {}

    @Override public void beep() {}

    @Override public void onResize(TermSize termSize, RequestOrigin requestOrigin) {}

    @Override public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {}

    @Override public void setCursorVisible(boolean visible) {}

    @Override public void useAlternateScreenBuffer(boolean enabled) {}

    @Override public String getWindowTitle() {return "";}

    @Override public void setWindowTitle(String title) {}

    @Override public TerminalSelection getSelection() {return null;}

    @Override public void terminalMouseModeSet(MouseMode mouseMode) {}

    @Override public void setMouseFormat(MouseFormat mouseFormat) {}

    @Override public boolean ambiguousCharsAreDoubleWidth() {return false;}
}
