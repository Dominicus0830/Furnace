// furnace.properties 의 플러그인 관련 키.
//   plugins.fail-fast=false        플러그인을 불러오거나 켜다가 하나라도 실패하면 서버 시작을 멈춘다
//   plugins.data-root=             비우면 데이터 폴더 = plugins/<이름>/, 쓰면 <data-root>/<이름>/
//   plugins.log-level.<이름>=INFO  플러그인별 로그 수준(JUL 수준 이름)
//   plugins.dev-mode=false         개발 모드 — 켜야 플러그인 단위 핫 리로드(/plugins reload <이름>, PluginManager.reload)가 된다
//   plugins.dev-mode.auto-reload=false  개발 모드에서 plugins/*.jar 가 바뀌면 알아서 다시 읽는다
//   plugins.libraries.*            라이브러리 자동 로딩(기본 꺼짐) — library/LibrarySettings 참고
//   plugins.compat.bukkit=true     Bukkit·Paper 플러그인 호환 어댑터(클래스패스에 furnace-compat-paper 가 있을 때) — false 면 plugin.yml jar 를 읽지 않는다
//   plugins.chunk-tickets.max-per-plugin=16384  플러그인 하나가 청크 티켓으로 붙잡을 수 있는 청크 수(겹침 포함, 1 이상)
//   plugins.compat.scheduler.async-threads=platform  Bukkit 비동기 작업(runTaskAsynchronously 등)을 이름 붙은 플랫폼 스레드 풀(Paper 모양)에서 돌린다 — virtual 이면 가상 스레드
//   plugins.compat.nms.enabled=true  호환 플러그인 클래스를 올릴 때 NMS·CraftBukkit 이름 변환(버전 접미사 v1_21_R1 풀기 등)을 한다 — false 면 바이트 그대로 올린다
//   plugins.compat.nms.strict=false   미지원 NMS 심볼이 있는 플러그인은 불러오지 않는다(기본: 그 호출만 실패)
//   plugins.compat.nms.version-package=plain  플러그인이 읽는 CraftBukkit 패키지 이름(plain = Paper 처럼 접미사 없음, versioned = Spigot 처럼 v1_21_R1)
//   plugins.compat.nms.spigot-mappings=off    download 면 Spigot 이름 NMS 플러그인이 있을 때만 Mojang·Spigot 매핑을 받아 Spigot → Mojang 이름으로 바꿔 올린다(H7d).
//                                             files 면 받지 않고 plugins.compat.nms.spigot-mappings.files 의 세 파일(server.txt, 클래스 csrg, 멤버 csrg)을 쓴다
//   scheduler.async-threads·nms.strict·nms.version-package·nms.spigot-mappings(.files) 는 검사해 compatOptions 로(기본값과 같으면 빠진다) — 호환 어댑터·PluginFormat#transformer 가 읽는다
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                             LibrarySettings libraries, boolean compat, boolean compatNms, int chunkTicketLimit,
                             boolean exposeCompatToNative, Map<String, String> compatOptions) {

    public static final PluginSettings DEFAULT = new PluginSettings(false, null, Map.of());

    /** 개발 모드 없이(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels) {
        this(failFast, dataRoot, logLevels, false, false);
    }

    /** 라이브러리 자동 로딩 없이(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, LibrarySettings.DEFAULT);
    }

    /** 호환 어댑터 켜짐(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, true);
    }

    /** NMS 이름 변환 켜짐(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, true);
    }

    /** 플러그인당 청크 티켓 한도 기본값(16384). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms,
                org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER);
    }

    /** 네이티브 플러그인 클래스로더에 호환 클래스를 숨김(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms, int chunkTicketLimit) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, chunkTicketLimit, false);
    }

    /** 호환 어댑터 설정 없음(모두 기본값). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms, int chunkTicketLimit, boolean exposeCompatToNative) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, chunkTicketLimit, exposeCompatToNative, Map.of());
    }

    /**
     * 시험용 문: true 면 Furnace 네이티브 플러그인에도 호환 클래스(org.bukkit.*·호환 코어 등)가 보인다. 서버 설정(furnace.properties)에는 없다 —
     * 지향점은 네이티브 플러그인이 Furnace API 만 쓰는 것이다. 호환 코어를 네이티브 시험 플러그인으로 직접 부리는 호환 모듈 시험만 켠다.
     */
    /** 시험 전용. 호환 모듈이 서버 jar 에 jar 째로 실린 경로(N-lib-1)에서는 서버 로더에 호환 클래스가 없어 효과가 없다. */
    public PluginSettings withExposeCompatToNative(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, chunkTicketLimit, on, compatOptions); }

    public PluginSettings withLibraries(LibrarySettings l) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, l, compat, compatNms, chunkTicketLimit, exposeCompatToNative, compatOptions); }

    public PluginSettings withCompat(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, on, compatNms, chunkTicketLimit, exposeCompatToNative, compatOptions); }

    public PluginSettings withCompatNms(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, on, chunkTicketLimit, exposeCompatToNative, compatOptions); }

    /** 호환 어댑터가 읽는 plugins.compat.* 값(적힌 글자 그대로, 키는 소문자 그대로). 시험이 직접 정할 때 쓴다. */
    public PluginSettings withCompatOptions(Map<String, String> options) {
        return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, chunkTicketLimit, exposeCompatToNative, options);
    }

    /** {@code Furnace#compatOption} 의 값: 적힌 글자 그대로, 안 적었으면 null. */
    public String compatOption(String key) { return compatOptions.get(key); }

    public PluginSettings withChunkTicketLimit(int n) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, n, exposeCompatToNative, compatOptions); }

    public PluginSettings {
        Map<String, Level> m = new HashMap<>();
        logLevels.forEach((k, v) -> m.put(k.toLowerCase(Locale.ROOT), v));
        logLevels = Map.copyOf(m);
        if (libraries == null) libraries = LibrarySettings.DEFAULT;
        compatOptions = compatOptions == null ? Map.of() : Map.copyOf(compatOptions);
        if (chunkTicketLimit < 1) throw new IllegalArgumentException("chunkTicketLimit 는 1 이상: " + chunkTicketLimit);
    }

    /** 이 플러그인에 지정된 로그 수준(없으면 null). */
    public Level levelFor(String plugin) { return logLevels.get(plugin.toLowerCase(Locale.ROOT)); }

    public static PluginSettings load(Path file) {
        if (!Files.exists(file)) return DEFAULT;
        Properties p;
        try {
            p = org.domi.furnace.server.config.FurnaceProps.read(file);
        } catch (RuntimeException e) {
            return DEFAULT;
        }
        return from(p, LoggerFactory.getLogger("Furnace"));
    }

    /** 호환 어댑터가 읽는 키: 모양이 틀린 값은 기동을 멈춘다(strict). 안 적은 키는 빠진다(= 어댑터의 기본값). */
    private static Map<String, String> compatOptionsFrom(Properties p) {
        Map<String, String> out = new HashMap<>();
        String threads = p.getProperty(ASYNC_THREADS_KEY);
        if (threads != null) {
            String t = threads.strip().toLowerCase(Locale.ROOT);
            if (!t.equals("platform") && !t.equals("virtual")) {
                throw new IllegalArgumentException("설정 '" + ASYNC_THREADS_KEY + "' 값 '" + threads.strip() + "' 이(가) 잘못됐습니다: platform 또는 virtual");
            }
            if (!t.equals("platform")) out.put(ASYNC_THREADS_KEY, t);   // 기본값과 같게 적은 것은 안 적은 것과 같다(DEFAULT 와 같은 설정)
        }
        // H7: NMS 변환 옵션(PluginFormat#transformer 의 settings 로도 넘어간다)
        if (org.domi.furnace.server.config.OptionKeys.strictBool(p, NMS_STRICT_KEY, false)) out.put(NMS_STRICT_KEY, "true");
        String vp = p.getProperty(NMS_VERSION_PACKAGE_KEY);
        if (vp != null) {
            String v = vp.strip().toLowerCase(Locale.ROOT);
            if (!v.equals("plain") && !v.equals("versioned")) {
                throw new IllegalArgumentException("설정 '" + NMS_VERSION_PACKAGE_KEY + "' 값 '" + vp.strip() + "' 이(가) 잘못됐습니다: plain 또는 versioned");
            }
            if (!v.equals("plain")) out.put(NMS_VERSION_PACKAGE_KEY, v);
        }
        // H7d: Spigot 이름 대응표(off 기본 — 안 적은 것과 같으면 빠진다)
        String sm = p.getProperty(SPIGOT_MAPPINGS_KEY);
        String files = p.getProperty(SPIGOT_MAPPING_FILES_KEY, "").strip();
        if (sm != null) {
            String v = sm.strip().toLowerCase(Locale.ROOT);
            if (!v.equals("off") && !v.equals("download") && !v.equals("files")) {
                throw new IllegalArgumentException("설정 '" + SPIGOT_MAPPINGS_KEY + "' 값 '" + sm.strip() + "' 이(가) 잘못됐습니다: off, download 또는 files");
            }
            if (v.equals("files") && (files.isEmpty() || files.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1).length != 3)) {
                throw new IllegalArgumentException("설정 '" + SPIGOT_MAPPINGS_KEY + "=files' 는 '" + SPIGOT_MAPPING_FILES_KEY + "' 에 파일 세 개"
                        + "(Mojang server.txt, Spigot 클래스 csrg, Spigot 멤버 csrg)를 경로 구분자 '" + java.io.File.pathSeparator + "' 로 이어 적어야 합니다");
            }
            if (!v.equals("off")) out.put(SPIGOT_MAPPINGS_KEY, v);
            if (v.equals("files")) out.put(SPIGOT_MAPPING_FILES_KEY, files);
        }
        return out;
    }

    /** Spigot 이름 NMS 플러그인의 대응표: off(기본) | download | files (H7d). */
    public static final String SPIGOT_MAPPINGS_KEY = "plugins.compat.nms.spigot-mappings";

    /** files 모드의 세 파일(Mojang server.txt, Spigot 클래스 csrg, 멤버 csrg)을 경로 구분자로 이은 값. */
    public static final String SPIGOT_MAPPING_FILES_KEY = "plugins.compat.nms.spigot-mappings.files";

    /** 미지원 NMS 심볼이 있는 호환 플러그인을 불러오지 않는다(기본 false). */
    public static final String NMS_STRICT_KEY = "plugins.compat.nms.strict";

    /** 플러그인이 읽는 CraftBukkit 패키지 이름: plain(기본) | versioned. */
    public static final String NMS_VERSION_PACKAGE_KEY = "plugins.compat.nms.version-package";

    /** Bukkit 비동기 작업을 돌릴 스레드: platform(기본, 이름 붙은 플랫폼 스레드 풀) | virtual(가상 스레드). */
    public static final String ASYNC_THREADS_KEY = "plugins.compat.scheduler.async-threads";

    static PluginSettings from(Properties p, Logger log) {
        boolean failFast = org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.fail-fast", false);
        String root = p.getProperty("plugins.data-root", "").strip();
        Map<String, Level> levels = new HashMap<>();
        String prefix = "plugins.log-level.";
        for (String k : p.stringPropertyNames()) {
            if (!k.startsWith(prefix)) continue;
            String v = p.getProperty(k).strip();
            try {
                levels.put(k.substring(prefix.length()), Level.parse(v.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warn("furnace.properties 의 '{}' 값 '{}' 은 로그 수준이 아니다 — 무시한다", k, v);
            }
        }
        boolean dev = org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.dev-mode", false);
        boolean auto = org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.dev-mode.auto-reload", false);
        if (auto && !dev) log.warn("plugins.dev-mode.auto-reload 는 plugins.dev-mode=true 일 때만 쓰인다 — 무시한다");
        boolean compat = org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.compat.bukkit", true);
        boolean nms = org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.compat.nms.enabled", true);
        return new PluginSettings(failFast, root.isEmpty() ? null : Path.of(root), levels, dev, dev && auto,
                LibrarySettings.from(p, log), compat, nms,
                org.domi.furnace.server.config.OptionKeys.strictInt(p, "plugins.chunk-tickets.max-per-plugin",
                        org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER, 1, 100_000_000),
                false, compatOptionsFrom(p));
    }
}
