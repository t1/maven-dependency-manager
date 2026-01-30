package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.ProjectReport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;

import static com.github.t1.mavendep.domain.OutputFormat.json;
import static com.github.t1.mavendep.domain.OutputFormat.text;
import static org.assertj.core.api.BDDAssertions.then;

class ReportOutputHandlerTest {

    @Test
    void shouldWriteJsonToStdout() {
        var reports = List.<ProjectReport>of();
        var originalOut = System.out;
        var outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        ReportOutputHandler.writeReport(reports, json, null, false);

        System.setOut(originalOut);
        then(outputStream.toString()).contains("\"projects\"");
    }

    @Test
    void shouldWriteTextToStdout() {
        var reports = List.<ProjectReport>of();
        var originalOut = System.out;
        var outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        ReportOutputHandler.writeReport(reports, text, null, false);

        System.setOut(originalOut);
        then(outputStream.toString()).contains("Maven Dependency Update Report");
    }

    @Test
    void shouldWriteJsonToFile() throws IOException {
        var reports = List.<ProjectReport>of();
        var tempFile = Files.createTempFile("report", ".json");

        ReportOutputHandler.writeReport(reports, json, tempFile.toString(), false);

        var content = Files.readString(tempFile);
        then(content).contains("\"projects\"");
        Files.delete(tempFile);
    }

    @Test
    void shouldWriteTextToFile() throws IOException {
        var reports = List.<ProjectReport>of();
        var tempFile = Files.createTempFile("report", ".txt");

        ReportOutputHandler.writeReport(reports, text, tempFile.toString(), false);

        var content = Files.readString(tempFile);
        then(content).contains("Maven Dependency Update Report");
        Files.delete(tempFile);
    }
}
