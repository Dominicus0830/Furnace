// furnace.properties 의 라이브러리 자동 로딩 키(plugins.libraries.*). 기본은 전부 꺼짐/보수적 값이다.
//   plugins.libraries.enabled=false          켜야 플러그인 설명 파일의 libraries 를 받아 클래스패스에 붙인다(Maven Central 만)
//   plugins.libraries.offline=false          켜면 캐시에 있는 것만 쓰고 네트워크에 나가지 않는다(enabled=true 일 때)
//   plugins.libraries.transitive=true        받은 라이브러리의 pom 이 선언한 compile/runtime 의존까지 함께 받는다
//   plugins.libraries.require-checksum=false 켜면 설명 파일의 sha256 이나 잠금 파일(libraries.lock) 기록이 없는 jar 는 받지 않는다
//   plugins.libraries.cache=       받은 파일과 libraries.lock 을 두는 폴더(비우면 plugins/.libraries)
//   plugins.libraries.timeout-seconds=30     요청 하나의 제한 시간(1~600)
//   plugins.libraries.max-size-mb=64         파일 하나의 크기 상한(1~1024)
package org.domi.furnace.server.plugin.library;

import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;

public record LibrarySettings(boolean enabled, boolean offline, boolean transitive, boolean requireChecksum, Path cache,
                              int timeoutSeconds, int maxSizeMb) {

    /** cache 가 null 이면 plugins 폴더 아래 이 이름. */
    public static final String DEFAULT_CACHE_DIR = ".libraries";
    public static final LibrarySettings DEFAULT = new LibrarySettings(false, false, true, false, null, 30, 64);

    /** furnace.properties 키(설정 점검·오타 경고용). */
    public static final java.util.Set<String> KEYS = java.util.Set.of("plugins.libraries.enabled", "plugins.libraries.offline",
            "plugins.libraries.transitive", "plugins.libraries.require-checksum", "plugins.libraries.cache",
            "plugins.libraries.timeout-seconds", "plugins.libraries.max-size-mb");

    public LibrarySettings withEnabled(boolean v) { return new LibrarySettings(v, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb); }

    public LibrarySettings withOffline(boolean v) { return new LibrarySettings(enabled, v, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb); }

    public LibrarySettings withTransitive(boolean v) { return new LibrarySettings(enabled, offline, v, requireChecksum, cache, timeoutSeconds, maxSizeMb); }

    public LibrarySettings withRequireChecksum(boolean v) { return new LibrarySettings(enabled, offline, transitive, v, cache, timeoutSeconds, maxSizeMb); }

    public LibrarySettings withCache(Path v) { return new LibrarySettings(enabled, offline, transitive, requireChecksum, v, timeoutSeconds, maxSizeMb); }

    public static LibrarySettings from(Properties p, Logger log) {
        boolean enabled = bool(p, "plugins.libraries.enabled", false, log);
        boolean offline = bool(p, "plugins.libraries.offline", false, log);
        boolean transitive = bool(p, "plugins.libraries.transitive", true, log);
        boolean require = bool(p, "plugins.libraries.require-checksum", false, log);
        String cache = p.getProperty("plugins.libraries.cache", "").strip();
        int timeout = number(p, "plugins.libraries.timeout-seconds", 30, 1, 600, log);
        int max = number(p, "plugins.libraries.max-size-mb", 64, 1, 1024, log);
        if (!enabled) {
            for (String k : new String[]{"plugins.libraries.offline", "plugins.libraries.transitive", "plugins.libraries.require-checksum"}) {
                if (p.containsKey(k)) log.warn("{} 는 plugins.libraries.enabled=true 일 때만 쓰인다 — 무시한다", k);
            }
        } else {
            if (!offline && !require) {
                log.warn("plugins.libraries.enabled=true — 플러그인이 요구하는 라이브러리를 Maven Central 에서 내려받는다. "
                        + "처음 받는 jar 는 체크섬을 서버가 기록(libraries.lock)해 두고 이후엔 그 값과 같은지만 확인한다. 엄격히 하려면 plugins.libraries.require-checksum=true");
            }
            if (offline && p.containsKey("plugins.libraries.timeout-seconds")) log.warn("plugins.libraries.offline=true 라 timeout-seconds 는 쓰이지 않는다");
        }
        return new LibrarySettings(enabled, offline, transitive, require, cache.isEmpty() ? null : Path.of(cache), timeout, max);
    }

    private static boolean bool(Properties p, String key, boolean def, Logger log) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.strip();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        log.warn("furnace.properties 의 '{}' 값 '{}' 은 true/false 가 아니다 — 기본값 {} 을 쓴다", key, v, def);
        return def;
    }

    private static int number(Properties p, String key, int def, int min, int max, Logger log) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            int n = Integer.parseInt(v.strip());
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {
            // 아래 경고
        }
        log.warn("furnace.properties 의 '{}' 값 '{}' 은 {}~{} 의 정수가 아니다 — 기본값 {} 을 쓴다", key, v, min, max, def);
        return def;
    }
}
