package com.github.t1.mavendep.tui;

import dev.tamboui.layout.Position;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Backend;
import dev.tamboui.buffer.DiffResult;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/// Wraps a [Backend] to translate the back-tab escape sequence (`ESC [ Z`)
/// into `ESC [ 1 ; 2 Z` (CSI modified-key format with modifier code 2 = Shift).
///
/// TamboUI 0.1.0's EventParser does not handle `ESC [ Z` directly.
/// The modified form `ESC [ 1 ; 2 Z` is parsed by `parseModifiedArrow`
/// which produces `KeyCode.UNKNOWN` with `KeyModifiers.SHIFT`.
/// The controller detects this combination as a back-tab.
class BackTabBackendWrapper implements Backend {

    private final Backend delegate;
    private final Deque<Integer> injectedBytes = new ArrayDeque<>();

    BackTabBackendWrapper(Backend delegate) {
        this.delegate = delegate;
    }

    @Override public int read(int timeoutMs) throws IOException {
        if (!injectedBytes.isEmpty()) return injectedBytes.poll();

        int c = delegate.read(timeoutMs);
        if (c != 27) return c; // not ESC

        int next = delegate.peek(50);
        if (next != '[') return c; // not CSI
        delegate.read(50); // consume '['

        int after = delegate.peek(50);
        if (after != 'Z') {
            // Not back-tab — re-inject '[' and return ESC for normal CSI parsing
            injectedBytes.add((int) '[');
            return 27;
        }
        delegate.read(50); // consume 'Z'

        // Inject `ESC [ 1 ; 2 Z` so EventParser produces UNKNOWN + SHIFT modifier
        injectedBytes.add((int) '[');
        injectedBytes.add((int) '1');
        injectedBytes.add((int) ';');
        injectedBytes.add((int) '2');
        injectedBytes.add((int) 'Z');
        return 27; // ESC
    }

    @Override public int peek(int timeoutMs) throws IOException {
        if (!injectedBytes.isEmpty()) return injectedBytes.peek();
        return delegate.peek(timeoutMs);
    }

    // --- Pure delegation ---
    @Override public void draw(DiffResult diff) throws IOException {delegate.draw(diff);}
    @Override public void flush() throws IOException {delegate.flush();}
    @Override public void clear() throws IOException {delegate.clear();}
    @Override public Size size() throws IOException {return delegate.size();}
    @Override public void showCursor() throws IOException {delegate.showCursor();}
    @Override public void hideCursor() throws IOException {delegate.hideCursor();}
    @Override public Position getCursorPosition() throws IOException {return delegate.getCursorPosition();}
    @Override public void setCursorPosition(Position position) throws IOException {delegate.setCursorPosition(position);}
    @Override public void enterAlternateScreen() throws IOException {delegate.enterAlternateScreen();}
    @Override public void leaveAlternateScreen() throws IOException {delegate.leaveAlternateScreen();}
    @Override public void enableRawMode() throws IOException {delegate.enableRawMode();}
    @Override public void disableRawMode() throws IOException {delegate.disableRawMode();}
    @Override public void enableMouseCapture() throws IOException {delegate.enableMouseCapture();}
    @Override public void disableMouseCapture() throws IOException {delegate.disableMouseCapture();}
    @Override public void onResize(Runnable handler) {delegate.onResize(handler);}
    @Override public void close() throws IOException {delegate.close();}
}
