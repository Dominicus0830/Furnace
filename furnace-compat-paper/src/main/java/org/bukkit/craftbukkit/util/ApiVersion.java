package org.bukkit.craftbukkit.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 플러그인 yml 의 {@code api-version} 값을 비교하는 값 클래스(주·부·패치). {@link #NONE} 은 api-version 이 없는 옛 플러그인. */
public final class ApiVersion implements Comparable<ApiVersion>, Serializable {
    private static final long serialVersionUID = 0L;

    private static final Map<String, ApiVersion> versions = new HashMap<>();

    public static final ApiVersion CURRENT = getOrCreateVersion("1.21.1");
    public static final ApiVersion FLATTENING = getOrCreateVersion("1.13");
    public static final ApiVersion FIELD_NAME_PARITY = getOrCreateVersion("1.20.5");
    public static final ApiVersion NONE = getOrCreateVersion("none");

    private final int major;
    private final int minor;
    private final int patch;
    private final boolean none;

    private ApiVersion() {
        this.none = true;
        this.major = Integer.MIN_VALUE;
        this.minor = Integer.MIN_VALUE;
        this.patch = Integer.MIN_VALUE;
    }

    private ApiVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.none = false;
    }

    public static ApiVersion getOrCreateVersion(String versionString) {
        if (versionString == null || versionString.trim().isEmpty() || versionString.equalsIgnoreCase("none")) {
            return versions.computeIfAbsent("none", k -> new ApiVersion());
        }
        ApiVersion cached = versions.get(versionString);
        if (cached != null) {
            return cached;
        }
        String[] parts = versionString.split("\\.");
        if (parts.length != 2 && parts.length != 3) {
            throw new IllegalArgumentException(String.format("API version string should be of format \"major.minor.patch\" or \"major.minor\", where \"major\", \"minor\" and \"patch\" are numbers. For example \"1.18.2\" or \"1.13\", but got '%s' instead.", versionString));
        }
        int major = parseNumber(parts[0]);
        int minor = parseNumber(parts[1]);
        int patch = parts.length == 3 ? parseNumber(parts[2]) : 0;
        String key = toVersionString(major, minor, patch);
        return versions.computeIfAbsent(key, k -> new ApiVersion(major, minor, patch));
    }

    private static int parseNumber(String s) {
        return Integer.parseInt(s);
    }

    public String getVersionString() {
        return none ? "none" : toVersionString(major, minor, patch);
    }

    private static String toVersionString(int major, int minor, int patch) {
        return major + "." + minor + "." + patch;
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
        int c = Integer.compare(major, other.major);
        if (c == 0) {
            c = Integer.compare(minor, other.minor);
        }
        if (c == 0) {
            c = Integer.compare(patch, other.patch);
        }
        return c;
    }

    @Override
    public String toString() {
        return getVersionString();
    }
}
