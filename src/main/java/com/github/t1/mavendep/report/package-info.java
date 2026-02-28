/// Report formatting and output.
///
/// `ReportOutputHandler` is the entry point: it selects a `ReportWriter`
/// based on `ReportConfig` and writes to stdout or a file.
///
/// Implementations:
/// - `TextReportWriter` — human-readable table output (uses `TableFormat`)
/// - `JsonReportWriter` — JSON output via Jackson
package com.github.t1.mavendep.report;
