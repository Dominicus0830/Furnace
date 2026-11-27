package org.bukkit.craftbukkit.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 플러그인 yml 의 {@code api-version} 값을 비교하는 값 클래스(주·부·패치). {@link #NONE} 은 api-version 이 없는 옛 플러그인. */
public final class ApiVersion implements Comparable<ApiVersion>, Serializable {
    private static final long serialVersionUID = 0L;

    private static final Map<String, ApiVersion> versions = new HashMap<>();

    public static final ApiVersion NONE = new ApiVersion();
    public static final ApiVersion FLATTENING = getOrCreateVersion("1.13");
    public static final ApiVersion FIELD_NAME_PARITY = getOrCreateVersion("1.20.5");
    public static final ApiVersion CURRENT = getOrCreateVersion("1.21.1");

    private final int major;
    private final int minor;
    private final int patch;
    private final boolean none;

    private ApiVersion() {
        this.major = 0;
        this.minor = 0;
        this.patch = 0;
        this.none = true;
    }

    private ApiVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.none = false;
    }

    public static synchronized ApiVersion getOrCreateVersion(String versionString) {
        if (versionString == null) {
            return NONE;
        }
        ApiVersion cached = versions.get(versionString);
        if (cached != null) {
            return cached;
        }
        String[] parts = versionString.split(Pattern.quote("."));
        int major = parseNumber(parts[0]);
        int minor = parts.length > 1 ? parseNumber(parts[1]) : 0;
        int patch = parts.length > 2 ? parseNumber(parts[2]) : 0;
        ApiVersion created = new ApiVersion(major, minor, patch);
        versions.put(versionString, created);
        return created;
    }

    private static int parseNumber(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getVersionString() {
        return none ? "none" : toVersionString(major, minor, patch);
    }

    private static String toVersionString(int major, int minor, int patch) {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }

    public boolean isOlderThan(ApiVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) < 0;
    }

    public boolean isOlderThanOrSameAs(ApiVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) <= 0;
    }

    public boolean isNewerThan(ApiVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) > 0;
    }

    public boolean isNewerThanOrSameAs(ApiVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        if (this.none || other.none) {
            return this.none == other.none ? 0 : (this.none ? -1 : 1);
        }
        int c = Integer.compare(major, other.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, other.minor);
        if (c != 0) {
            return c;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return getVersionString();
    }
}
