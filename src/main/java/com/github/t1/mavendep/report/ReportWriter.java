package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.ProjectReport;

import java.util.List;

public interface ReportWriter {
    String write(List<ProjectReport> reports, boolean showAll);
}
