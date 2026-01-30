package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class ScopeTest {

    @ParameterizedTest
    @EnumSource
    void shouldParseToString(Scope scope) {
        then(Scope.of(scope.name())).isEqualTo(scope);
    }

    @Test
    void shouldThrowException_whenScopeIsInvalid() {
        thenThrownBy(() -> Scope.of("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
