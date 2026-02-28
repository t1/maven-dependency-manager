package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.OutputFormat;

/// Configuration for report output: format, output file, and whether to show all dependencies.
public record ReportConfig(OutputFormat format, String outputFile, boolean showAll) {}
