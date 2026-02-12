package com.github.t1.mavendep.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;

import java.util.List;

import static com.github.t1.mavendep.domain.DependencyType.parent;
import static com.github.t1.mavendep.domain.DependencyType.plugin;

public class JsonReportWriter implements ReportWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String write(List<ProjectReport> reports) {
        var root = objectMapper.createObjectNode();

        var projectsArray = objectMapper.createArrayNode();
        for (var report : reports) {
            projectsArray.add(buildProjectNode(report));
        }
        root.set("projects", projectsArray);

        var summary = DependencySummary.from(reports);
        root.set("summary", buildSummaryNode(summary));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectNode buildSummaryNode(DependencySummary summary) {
        var summaryNode = objectMapper.createObjectNode();
        summaryNode.put("totalDependencies", summary.totalDependencies());
        summaryNode.put("outdatedDependencies", summary.outdatedDependencies());
        summaryNode.put("majorUpdates", summary.majorUpdates());
        summaryNode.put("minorUpdates", summary.minorUpdates());
        summaryNode.put("patchUpdates", summary.patchUpdates());
        return summaryNode;
    }

    private ObjectNode buildProjectNode(ProjectReport report) {
        var projectNode = objectMapper.createObjectNode();
        projectNode.put("pomFile", report.pom().path().toAbsolutePath().toString());

        report.parentUpdate()
                .ifPresent(parent -> projectNode.set("parent", buildDependencyNode(parent)));

        var depsArray = objectMapper.createArrayNode();
        report.dependencyUpdates().stream()
                .map(this::buildDependencyNode).forEach(depsArray::add);
        projectNode.set("dependencies", depsArray);

        var pluginsArray = objectMapper.createArrayNode();
        report.pluginUpdates().stream()
                .map(this::buildDependencyNode).forEach(pluginsArray::add);
        projectNode.set("plugins", pluginsArray);

        return projectNode;
    }

    private ObjectNode buildDependencyNode(DependencyUpdate update) {
        var depNode = objectMapper.createObjectNode();
        depNode.put("type", update.type().toString());
        depNode.put("groupId", update.groupId());
        depNode.put("artifactId", update.artifactId());
        depNode.put("scope", (update.type() == parent || update.type() == plugin) ? "" : update.scope().toString());
        if (update.currentVersion() != null) {
            depNode.put("currentVersion", update.currentVersion().toString());
        } else {
            depNode.putNull("currentVersion");
        }
        if (update.latestVersion() != null) {
            depNode.put("latestVersion", update.latestVersion().toString());
        } else {
            depNode.putNull("latestVersion");
        }
        depNode.put("updateType", update.updateType().toString());

        var versionsArray = objectMapper.createArrayNode();
        for (var version : update.availableVersions()) {
            versionsArray.add(version.toString());
        }
        depNode.set("availableVersions", versionsArray);

        return depNode;
    }
}
