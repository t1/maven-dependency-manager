/// Report formatting and output.
///
/// `ReportOutputHandler` is the entry point: it selects a `ReportWriter`
/// based on `ReportConfig` and writes to stdout or a file.
///
/// Implementations:
/// - `TextReportWriter` — human-readable table output (uses `TableFormat`)
/// - `JsonReportWriter` — JSON output via Jackson
/// - `YamlReportWriter` — YAML output via Jackson
/// - `XmlReportWriter` — XML output via Jackson
///
/// `ReportTreeBuilder` builds the shared Jackson ObjectNode tree used by JSON, YAML, and XML writers.
package com.github.t1.mavendep.report;
