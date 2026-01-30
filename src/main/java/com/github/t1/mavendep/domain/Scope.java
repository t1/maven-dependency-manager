package com.github.t1.mavendep.domain;

/// see [Maven Scope](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)
///
/// The order is used, e.g., for the output table; we consider this to be the "natural" order of scopes.
public enum Scope {
    import_,
    provided,
    compile,
    runtime,
    system,
    test;

    public static final Scope DEFAULT = compile;

    public static Scope of(String scope) {return "import".equals(scope) ? import_ : valueOf(scope);}

    @Override
    public String toString() {return super.toString().replace("_", "");}
}
