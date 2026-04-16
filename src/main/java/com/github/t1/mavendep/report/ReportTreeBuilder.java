package com.github.t1.mavendep.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.Version;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;

/// Builds a Jackson ObjectNode tree from project reports.
/// Shared by JSON, YAML, and XML report writers.
class ReportTreeBuilder {
    private final ObjectMapper mapper;

    ReportTreeBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode build(List<ProjectReport> reports) {
        var root = mapper.createObjectNode();

        var projectsArray = mapper.createArrayNode();
        for (var report : reports) {
            projectsArray.add(buildProjectNode(report));
        }
        root.set("projects", projectsArray);

        var summary = DependencySummary.summarize(reports);
        root.set("summary", buildSummaryNode(summary));

        return root;
    }

    private ObjectNode buildSummaryNode(DependencySummary summary) {
        var summaryNode = mapper.createObjectNode();
        summaryNode.put("totalDependencies", summary.totalDependencies());
        summaryNode.put("outdatedDependencies", summary.outdatedDependencies());
        summaryNode.put("majorUpdates", summary.majorUpdates());
        summaryNode.put("minorUpdates", summary.minorUpdates());
        summaryNode.put("patchUpdates", summary.patchUpdates());
        return summaryNode;
    }

    private ObjectNode buildProjectNode(ProjectReport report) {
        var projectNode = mapper.createObjectNode();
        projectNode.put("pomFile", report.pom().path().toAbsolutePath().toString());

        report.parentUpdate()
                .ifPresent(p -> projectNode.set("parent", buildDependencyNode(p)));

        var depsArray = mapper.createArrayNode();
        report.dependencyUpdates().stream()
                .map(this::buildDependencyNode).forEach(depsArray::add);
        projectNode.set("dependencies", depsArray);

        var pluginsArray = mapper.createArrayNode();
        report.pluginUpdates().stream()
                .map(this::buildDependencyNode).forEach(pluginsArray::add);
        projectNode.set("plugins", pluginsArray);

        return projectNode;
    }

    private ObjectNode buildDependencyNode(Update update) {
        var depNode = mapper.createObjectNode();
        depNode.put("type", update.type().toString());
        depNode.put("groupId", update.groupId());
        depNode.put("artifactId", update.artifactId());
        depNode.put("scope", (update.type() == parent || update.type() == plugin) ? "" : update.scope().toString());
        if (update.profile() != null) {
            depNode.put("profile", update.profile());
        }
        if (update.committedVersion() != null) {
            putVersionField(depNode, "committedVersion", update.committedVersion());
        }
        putVersionField(depNode, "currentVersion", update.currentVersion());
        putVersionField(depNode, "latestVersion", update.latestVersion());
        depNode.put("updateType", update.updateType().toString());

        var versionsArray = mapper.createArrayNode();
        for (var version : update.availableVersions()) {
            versionsArray.add(version.toString());
        }
        depNode.set("availableVersions", versionsArray);

        return depNode;
    }

    private void putVersionField(ObjectNode node, String fieldName, Version version) {
        if (version != null) {
            node.put(fieldName, version.toString());
        } else {
            node.putNull(fieldName);
        }
    }
}
