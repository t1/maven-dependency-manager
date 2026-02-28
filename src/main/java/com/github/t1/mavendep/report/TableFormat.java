package com.github.t1.mavendep.report;

/// Shared formatting utilities for table-based report output.
class TableFormat {
    static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }
}
