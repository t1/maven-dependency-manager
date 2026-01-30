package com.github.t1.mavendep.domain;

public enum UpdateType {
    none,
    patch,
    minor,
    major;

    public static UpdateType between(Version current, Version newer) {
        if (current != null && newer != null && newer.compareTo(current) > 0) {
            if (current.major() != newer.major()) return major;
            if (current.minor() != newer.minor()) return minor;
            if (current.patch() != newer.patch() || !current.qualifier().equals(newer.qualifier())) return patch;
        }
        return none;
    }
}
