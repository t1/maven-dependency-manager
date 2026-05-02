package com.github.t1.mavendep.tui.e2e;

enum Key {
    UP("\033[A"),
    DOWN("\033[B"),
    LEFT("\033[D"),
    RIGHT("\033[C"),
    TAB("\t"),
    SHIFT_TAB("\033[Z"),
    ENTER("\r"),
    ESCAPE("\033"),
    PAGE_UP("\033[5~"),
    PAGE_DOWN("\033[6~"),
    HOME("\033[H"),
    END("\033[F");

    private final byte[] sequence;

    Key(String escape) {
        this.sequence = escape.getBytes();
    }

    byte[] sequence() {return sequence;}
}
