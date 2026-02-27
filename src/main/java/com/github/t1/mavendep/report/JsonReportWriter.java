package com.github.t1.mavendep.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Version;

import java.io.PrintStream;
import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;

public class JsonReportWriter implements ReportWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PrintStream out;
    private final List<ProjectReport> reports;

    public JsonReportWriter(PrintStream out, List<ProjectReport> reports) {
        this.out = out;
        this.reports = reports;
    }

    @Override
    public void run() {
        var root = OBJECT_MAPPER.createObjectNode();

        var projectsArray = OBJECT_MAPPER.createArrayNode();
        for (var report : reports) {
            projectsArray.add(buildProjectNode(report));
        }
        root.set("projects", projectsArray);

        var summary = DependencySummary.summarize(reports);
        root.set("summary", buildSummaryNode(summary));

        try {
            out.print(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectNode buildSummaryNode(DependencySummary summary) {
        var summaryNode = OBJECT_MAPPER.createObjectNode();
        summaryNode.put("totalDependencies", summary.totalDependencies());
        summaryNode.put("outdatedDependencies", summary.outdatedDependencies());
        summaryNode.put("majorUpdates", summary.majorUpdates());
        summaryNode.put("minorUpdates", summary.minorUpdates());
        summaryNode.put("patchUpdates", summary.patchUpdates());
        return summaryNode;
    }

    private ObjectNode buildProjectNode(ProjectReport report) {
        var projectNode = OBJECT_MAPPER.createObjectNode();
        projectNode.put("pomFile", report.pom().path().toAbsolutePath().toString());

        report.parentUpdate()
                .ifPresent(parent -> projectNode.set("parent", buildDependencyNode(parent)));

        var depsArray = OBJECT_MAPPER.createArrayNode();
        report.dependencyUpdates().stream()
                .map(this::buildDependencyNode).forEach(depsArray::add);
        projectNode.set("dependencies", depsArray);

        var pluginsArray = OBJECT_MAPPER.createArrayNode();
        report.pluginUpdates().stream()
                .map(this::buildDependencyNode).forEach(pluginsArray::add);
        projectNode.set("plugins", pluginsArray);

        return projectNode;
    }

    private void putVersionField(ObjectNode node, String fieldName, Version version) {
        if (version != null) {
            node.put(fieldName, version.toString());
        } else {
            node.putNull(fieldName);
        }
    }

    private ObjectNode buildDependencyNode(DependencyUpdate update) {
        var depNode = OBJECT_MAPPER.createObjectNode();
        depNode.put("type", update.type().toString());
        depNode.put("groupId", update.groupId());
        depNode.put("artifactId", update.artifactId());
        depNode.put("scope", (update.type() == parent || update.type() == plugin) ? "" : update.scope().toString());
        putVersionField(depNode, "currentVersion", update.currentVersion());
        putVersionField(depNode, "latestVersion", update.latestVersion());
        depNode.put("updateType", update.updateType().toString());

        var versionsArray = OBJECT_MAPPER.createArrayNode();
        for (var version : update.availableVersions()) {
            versionsArray.add(version.toString());
        }
        depNode.set("availableVersions", versionsArray);

        return depNode;
    }
}
