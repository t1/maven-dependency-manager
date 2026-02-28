package com.github.t1.mavendep.domain;

/// Called by [DependencyAnalyzer] after each dependency is analyzed,
/// reporting current progress as completed count and total count.
@FunctionalInterface
public interface AnalysisProgressListener {

    AnalysisProgressListener NONE = (completed, total, artifactName) -> {};

    void onProgress(int completed, int total, String artifactName);
}
