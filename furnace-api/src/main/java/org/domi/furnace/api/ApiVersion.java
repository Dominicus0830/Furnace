// 플러그인 API 버전(major.minor). Minecraft 버전과 따로 매긴다 — 설명 파일의 api-version 과 비교한다.
// 규칙(semver): major 가 같고 서버 minor 가 플러그인이 요구한 minor 이상이면 호환. 폐기(@DeprecatedApi)는 최소 두 마이너 동안 남긴다.
package org.domi.furnace.api;

public record ApiVersion(int major, int minor) implements Comparable<ApiVersion> {

    /** 이 서버가 제공하는 API 버전. */
    public static final ApiVersion CURRENT = new ApiVersion(1, 0);
    /** 이 서버가 아직 받아 주는 가장 오래된 플러그인 API 버전(이보다 낮으면 경고). */
    public static final ApiVersion OLDEST_SUPPORTED = new ApiVersion(1, 0);

    /** "1.2" 또는 "1" 형식. 읽을 수 없으면 null. */
    public static ApiVersion parse(String s) {
        if (s == null) return null;
        String[] p = s.strip().split("\\.");
        try {
            if (p.length < 1 || p.length > 3) return null;
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            return major < 0 || minor < 0 ? null : new ApiVersion(major, minor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(ApiVersion o) { return major != o.major ? Integer.compare(major, o.major) : Integer.compare(minor, o.minor); }

    /** 이 서버(CURRENT)에서 required 를 요구하는 플러그인을 돌릴 수 있는가. */
    public static boolean serverSatisfies(ApiVersion required) {
        return required.major == CURRENT.major && required.minor <= CURRENT.minor;
    }

    @Override
    public String toString() { return major + "." + minor; }
}
