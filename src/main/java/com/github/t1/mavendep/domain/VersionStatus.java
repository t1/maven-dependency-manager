package com.github.t1.mavendep.domain;

public enum VersionStatus {
    unknownCurrentVersion,
    noReleasedVersionAvailable,
    upToDate,
    upgradeAvailable,
    aheadOfLatestRelease;

    public static VersionStatus of(Version currentVersion, Version latestReleasedVersion) {
        if (currentVersion == null) return unknownCurrentVersion;
        if (latestReleasedVersion == null) return noReleasedVersionAvailable;
        if (!currentVersion.isReleased() && currentVersion.baseVersion().compareTo(latestReleasedVersion) > 0)
            return aheadOfLatestRelease;
        var compare = currentVersion.compareTo(latestReleasedVersion);
        if (compare < 0) return upgradeAvailable;
        if (compare == 0) return upToDate;
        return aheadOfLatestRelease;
    }

    public boolean isUpdateAvailable() {return this == upgradeAvailable;}
}
