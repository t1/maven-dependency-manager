package com.github.t1.mavendep.domain;

import java.util.LinkedHashSet;
import java.util.List;

public record AvailableVersion(Version version, List<String> sources) {
    public AvailableVersion {
        sources = new LinkedHashSet<>(sources).stream().sorted().toList();
    }

    public AvailableVersion withSource(String source) {
        var updated = new LinkedHashSet<>(sources);
        updated.add(source);
        return new AvailableVersion(version, updated.stream().toList());
    }
}
