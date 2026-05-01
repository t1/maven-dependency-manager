package com.github.t1.mavendep.domain;

import java.nio.file.Path;

@FunctionalInterface
public interface EffectivePomResolver {
    EffectivePomResolver NONE = _ -> EffectivePom.EMPTY;

    EffectivePom resolve(Path pomPath);
}
