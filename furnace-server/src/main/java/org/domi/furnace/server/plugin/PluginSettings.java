// furnace.properties 의 플러그인 관련 키.
//   plugins.fail-fast=false        플러그인을 불러오거나 켜다가 하나라도 실패하면 서버 시작을 멈춘다
//   plugins.data-root=             비우면 데이터 폴더 = plugins/<이름>/, 쓰면 <data-root>/<이름>/
//   plugins.log-level.<이름>=INFO  플러그인별 로그 수준(JUL 수준 이름)
//   plugins.dev-mode=false         개발 모드 — 켜야 플러그인 단위 핫 리로드(/plugins reload <이름>, PluginManager.reload)가 된다
//   plugins.dev-mode.auto-reload=false  개발 모드에서 plugins/*.jar 가 바뀌면 알아서 다시 읽는다
//   plugins.libraries.*            라이브러리 자동 로딩(기본 꺼짐) — library/LibrarySettings 참고
//   plugins.compat.bukkit=true     Bukkit·Paper 플러그인 호환 어댑터(클래스패스에 furnace-compat-paper 가 있을 때) — false 면 plugin.yml jar 를 읽지 않는다
//   plugins.chunk-tickets.max-per-plugin=16384  플러그인 하나가 청크 티켓으로 붙잡을 수 있는 청크 수(겹침 포함, 1 이상)
//   plugins.compat.nms.enabled=true  호환 플러그인 클래스를 올릴 때 NMS·CraftBukkit 이름 변환(버전 접미사 v1_21_R1 풀기 등)을 한다 — false 면 바이트 그대로 올린다
//   plugins.compat.nms.strict=false   미지원 NMS 심볼이 있는 플러그인은 불러오지 않는다(기본: 그 호출만 실패)
//   plugins.compat.nms.version-package=plain  플러그인이 읽는 CraftBukkit 패키지 이름(plain = Paper 처럼 접미사 없음, versioned = Spigot 처럼 v1_21_R1)
//   bukkit·nms.enabled·libraries* 를 뺀 plugins.compat.* 키는 compatOptions 로 플러그인 형식(PluginFormat#transformer)에 그대로 넘긴다
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
                             LibrarySettings libraries, boolean compat, boolean compatNms, Map<String, String> compatOptions,
                             int chunkTicketLimit, boolean exposeCompatToNative) {

    /** plugins.compat.nms.* 기본값(빈 설정 파일을 읽은 것과 같다). */
    public static final Map<String, String> DEFAULT_COMPAT_OPTIONS =
            Map.of("plugins.compat.nms.strict", "false", "plugins.compat.nms.version-package", "plain");

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

    /** 호환 형식 설정·플러그인당 청크 티켓 한도는 기본값(16384). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, DEFAULT_COMPAT_OPTIONS);
    }

    /** 청크 티켓 한도는 기본값. */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms, Map<String, String> compatOptions) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, compatOptions,
                org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER);
    }

    /** 호환 형식 설정은 기본값, 네이티브 플러그인 클래스로더에 호환 클래스를 숨김(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms, int chunkTicketLimit) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, DEFAULT_COMPAT_OPTIONS, chunkTicketLimit, false);
    }

    /** 네이티브 플러그인 클래스로더에 호환 클래스를 숨김(기본). */
    public PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels, boolean devMode, boolean autoReload,
                          LibrarySettings libraries, boolean compat, boolean compatNms, Map<String, String> compatOptions, int chunkTicketLimit) {
        this(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, compatOptions, chunkTicketLimit, false);
    }

    /**
     * 시험용 문: true 면 Furnace 네이티브 플러그인에도 호환 클래스(org.bukkit.*·호환 코어 등)가 보인다. 서버 설정(furnace.properties)에는 없다 —
     * 지향점은 네이티브 플러그인이 Furnace API 만 쓰는 것이다. 호환 코어를 네이티브 시험 플러그인으로 직접 부리는 호환 모듈 시험만 켠다.
     */
    public PluginSettings withExposeCompatToNative(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, compatOptions, chunkTicketLimit, on); }

    public PluginSettings withLibraries(LibrarySettings l) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, l, compat, compatNms, compatOptions, chunkTicketLimit, exposeCompatToNative); }

    public PluginSettings withCompat(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, on, compatNms, compatOptions, chunkTicketLimit, exposeCompatToNative); }

    public PluginSettings withCompatNms(boolean on) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, on, compatOptions, chunkTicketLimit, exposeCompatToNative); }

    /** plugins.compat.* 설정(접두어 포함 키)을 바꾼 사본 — 시험 서버가 쓴다. */
    public PluginSettings withCompatOptions(Map<String, String> o) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, o, chunkTicketLimit, exposeCompatToNative); }

    public PluginSettings withChunkTicketLimit(int n) { return new PluginSettings(failFast, dataRoot, logLevels, devMode, autoReload, libraries, compat, compatNms, compatOptions, n, exposeCompatToNative); }

    public PluginSettings {
        Map<String, Level> m = new HashMap<>();
        logLevels.forEach((k, v) -> m.put(k.toLowerCase(Locale.ROOT), v));
        logLevels = Map.copyOf(m);
        if (libraries == null) libraries = LibrarySettings.DEFAULT;
        compatOptions = compatOptions == null ? DEFAULT_COMPAT_OPTIONS : Map.copyOf(compatOptions);
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
        Map<String, String> compatOptions = new HashMap<>();
        for (String k : p.stringPropertyNames()) {
            // bukkit·nms.enabled 는 위에서 필드로, plugins.compat.libraries* 는 LibrarySettings 가 읽는다. 빈 값은 안 쓴 것과 같다
            if (k.startsWith("plugins.compat.") && !k.equals("plugins.compat.bukkit") && !k.equals("plugins.compat.nms.enabled")
                    && !LibrarySettings.KEYS.contains(k) && !p.getProperty(k).isBlank()) {
                compatOptions.put(k, p.getProperty(k).strip());
            }
        }
        // 값 검사는 여기서(잘못된 값은 기동을 멈춘다, OptionKeys 규칙 3) — 호환 형식은 받은 값을 믿는다
        compatOptions.put("plugins.compat.nms.strict", String.valueOf(org.domi.furnace.server.config.OptionKeys.strictBool(p, "plugins.compat.nms.strict", false)));
        String vp = compatOptions.getOrDefault("plugins.compat.nms.version-package", "plain").toLowerCase(Locale.ROOT);
        if (!vp.equals("plain") && !vp.equals("versioned")) {
            throw new IllegalArgumentException("설정 'plugins.compat.nms.version-package' 값 '" + vp + "' 이(가) 잘못됐습니다: plain 또는 versioned");
        }
        compatOptions.put("plugins.compat.nms.version-package", vp);
        return new PluginSettings(failFast, root.isEmpty() ? null : Path.of(root), levels, dev, dev && auto,
                LibrarySettings.from(p, log), compat, nms, compatOptions,
                org.domi.furnace.server.config.OptionKeys.strictInt(p, "plugins.chunk-tickets.max-per-plugin",
                        org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER, 1, 100_000_000));
    }
}
