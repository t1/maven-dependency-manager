package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.MavenRepository;

import java.nio.file.Path;
import java.util.List;

/// Configuration for the TUI dashboard: repository, POM files, build goals, and display options.
public record DashboardConfig(MavenRepository repository, List<Path> pomFiles, List<String> buildGoals, boolean showAll) {}
