package com.github.t1.mavendep.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.github.t1.mavendep.domain.ProjectReport;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

public class XmlReportWriter implements ReportWriter {
    private static final XmlMapper MAPPER = XmlMapper.builder()
            .defaultUseWrapper(false)
            .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
            .build();

    private final PrintStream out;
    private final List<ProjectReport> reports;

    public XmlReportWriter(PrintStream out, List<ProjectReport> reports) {
        this.out = out;
        this.reports = reports;
    }

    @Override
    public void run() {
        var tree = new ReportTreeBuilder(MAPPER).build(reports);
        try {
            out.print(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
