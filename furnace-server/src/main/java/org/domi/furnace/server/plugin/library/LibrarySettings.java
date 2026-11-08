// furnace.properties 의 라이브러리 자동 로딩 키(plugins.libraries.*). 기본은 전부 꺼짐/보수적 값이다.
//   plugins.libraries.enabled=false          켜야 플러그인 설명 파일의 libraries 를 받아 클래스패스에 붙인다(Maven Central 만)
//   plugins.libraries.offline=false          켜면 캐시에 있는 것만 쓰고 네트워크에 나가지 않는다(enabled=true 일 때)
//   plugins.libraries.transitive=true        받은 라이브러리의 pom 이 선언한 compile/runtime 의존까지 함께 받는다
//   plugins.libraries.require-checksum=false 켜면 설명 파일의 sha256 이나 잠금 파일(libraries.lock) 기록이 없는 jar 는 받지 않는다
//   plugins.libraries.cache=       받은 파일과 libraries.lock 을 두는 폴더(비우면 plugins/.libraries)
//   plugins.libraries.timeout-seconds=30     요청 하나의 제한 시간(1~600)
//   plugins.libraries.max-size-mb=64         파일 하나의 크기 상한(1~1024)
// Paper/Bukkit 호환 플러그인용(H4 묶음 B) — enabled 와 따로 움직인다. offline·cache·timeout-seconds·max-size-mb 는 함께 쓴다:
//   plugins.compat.libraries=needed          Paper 가 싣는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등, ServerLibraries)를
//                                            needed: plugins/bukkit-plugins/ 의 플러그인 클래스가 쓰는 묶음만 / all: 호환 플러그인이 하나라도 있으면 전부 / off: 안 받는다
//   plugins.compat.libraries.mojang=true     Brigadier·authlib·DataFixerUpper 를 Mojang 저장소(libraries.minecraft.net)에서 받는다 — false 면 그 셋은 빼고 경고
//   plugins.compat.libraries.declared=true   호환 플러그인 plugin.yml 의 libraries 를 Paper·Spigot 처럼 받는다(plugins.libraries.enabled 가 꺼져 있어도)
package org.domi.furnace.server.plugin.library;

import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;

public record LibrarySettings(boolean enabled, boolean offline, boolean transitive, boolean requireChecksum, Path cache,
                              int timeoutSeconds, int maxSizeMb, CompatMode compat, boolean compatMojang, boolean compatDeclared) {

    /** 호환 플러그인용 서버 제공 라이브러리를 받는 범위. */
    public enum CompatMode {
        /** 호환 플러그인 클래스에 보이는 묶음만(기본). */
        NEEDED,
        /** 호환 플러그인이 하나라도 있으면 목록 전부. */
        ALL,
        /** 받지 않는다. */
        OFF
    }

    /** 호환 플러그인용 키는 기본값(needed·Mojang 허용·plugin.yml libraries 받기). */
    public LibrarySettings(boolean enabled, boolean offline, boolean transitive, boolean requireChecksum, Path cache, int timeoutSeconds, int maxSizeMb) {
        this(enabled, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, CompatMode.NEEDED, true, true);
    }

    public LibrarySettings {
        if (compat == null) compat = CompatMode.NEEDED;
    }

    /** cache 가 null 이면 plugins 폴더 아래 이 이름. */
    public static final String DEFAULT_CACHE_DIR = ".libraries";
    public static final LibrarySettings DEFAULT = new LibrarySettings(false, false, true, false, null, 30, 64, CompatMode.NEEDED, true, true);

    /** furnace.properties 키(설정 점검·오타 경고용). */
    public static final java.util.Set<String> KEYS = java.util.Set.of("plugins.libraries.enabled", "plugins.libraries.offline",
            "plugins.libraries.transitive", "plugins.libraries.require-checksum", "plugins.libraries.cache",
            "plugins.libraries.timeout-seconds", "plugins.libraries.max-size-mb",
            "plugins.compat.libraries", "plugins.compat.libraries.mojang", "plugins.compat.libraries.declared");

    public LibrarySettings withEnabled(boolean v) { return new LibrarySettings(v, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, compat, compatMojang, compatDeclared); }

    public LibrarySettings withOffline(boolean v) { return new LibrarySettings(enabled, v, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, compat, compatMojang, compatDeclared); }

    public LibrarySettings withTransitive(boolean v) { return new LibrarySettings(enabled, offline, v, requireChecksum, cache, timeoutSeconds, maxSizeMb, compat, compatMojang, compatDeclared); }

    public LibrarySettings withRequireChecksum(boolean v) { return new LibrarySettings(enabled, offline, transitive, v, cache, timeoutSeconds, maxSizeMb, compat, compatMojang, compatDeclared); }

    public LibrarySettings withCache(Path v) { return new LibrarySettings(enabled, offline, transitive, requireChecksum, v, timeoutSeconds, maxSizeMb, compat, compatMojang, compatDeclared); }

    public LibrarySettings withCompat(CompatMode v) { return new LibrarySettings(enabled, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, v, compatMojang, compatDeclared); }

    public LibrarySettings withCompatMojang(boolean v) { return new LibrarySettings(enabled, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, compat, v, compatDeclared); }

    public LibrarySettings withCompatDeclared(boolean v) { return new LibrarySettings(enabled, offline, transitive, requireChecksum, cache, timeoutSeconds, maxSizeMb, compat, compatMojang, v); }

    /** 네트워크·캐시를 쓰는 기능(Furnace 플러그인 libraries, 호환 플러그인 라이브러리) 중 하나라도 켜져 있나. */
    public boolean anyDownload() { return enabled || compat != CompatMode.OFF || compatDeclared; }

    public static LibrarySettings from(Properties p, Logger log) {
        boolean enabled = bool(p, "plugins.libraries.enabled", false, log);
        boolean offline = bool(p, "plugins.libraries.offline", false, log);
        boolean transitive = bool(p, "plugins.libraries.transitive", true, log);
        boolean require = bool(p, "plugins.libraries.require-checksum", false, log);
        String cache = p.getProperty("plugins.libraries.cache", "").strip();
        int timeout = number(p, "plugins.libraries.timeout-seconds", 30, 1, 600, log);
        int max = number(p, "plugins.libraries.max-size-mb", 64, 1, 1024, log);
        CompatMode compat = compatMode(p, log);
        boolean mojang = bool(p, "plugins.compat.libraries.mojang", true, log);
        boolean declared = bool(p, "plugins.compat.libraries.declared", true, log);
        if (compat == CompatMode.OFF && p.containsKey("plugins.compat.libraries.mojang")) {
            log.warn("plugins.compat.libraries.mojang 은 plugins.compat.libraries 가 off 가 아닐 때만 쓰인다 — 무시한다");
        }
        if (!enabled) {
            // transitive·require-checksum 은 libraries 선언(Furnace 플러그인, 호환 플러그인의 plugin.yml)에 쓰인다
            String[] onlyWhenEnabled = declared ? new String[0] : new String[]{"plugins.libraries.transitive", "plugins.libraries.require-checksum"};
            for (String k : onlyWhenEnabled) {
                if (p.containsKey(k)) log.warn("{} 는 plugins.libraries.enabled=true(또는 plugins.compat.libraries.declared=true)일 때만 쓰인다 — 무시한다", k);
            }
            if (compat == CompatMode.OFF && !declared && p.containsKey("plugins.libraries.offline")) {
                log.warn("plugins.libraries.offline 은 라이브러리 내려받기가 하나라도 켜져 있을 때만 쓰인다 — 무시한다");
            }
        } else {
            if (!offline && !require) {
                log.warn("plugins.libraries.enabled=true — 플러그인이 요구하는 라이브러리를 Maven Central 에서 내려받는다. "
                        + "처음 받는 jar 는 체크섬을 서버가 기록(libraries.lock)해 두고 이후엔 그 값과 같은지만 확인한다. 엄격히 하려면 plugins.libraries.require-checksum=true");
            }
            if (offline && p.containsKey("plugins.libraries.timeout-seconds")) log.warn("plugins.libraries.offline=true 라 timeout-seconds 는 쓰이지 않는다");
        }
        return new LibrarySettings(enabled, offline, transitive, require, cache.isEmpty() ? null : Path.of(cache), timeout, max, compat, mojang, declared);
    }

    private static CompatMode compatMode(Properties p, Logger log) {
        String v = p.getProperty("plugins.compat.libraries");
        if (v == null) return CompatMode.NEEDED;
        return switch (v.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "needed" -> CompatMode.NEEDED;
            case "all" -> CompatMode.ALL;
            case "off" -> CompatMode.OFF;
            default -> {
                log.warn("furnace.properties 의 'plugins.compat.libraries' 값 '{}' 은 needed|all|off 가 아니다 — 기본값 needed 를 쓴다", v);
                yield CompatMode.NEEDED;
            }
        };
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
