// Paper/Spigot 설정 가져오기: 대응 키 옮기기, 이미 있는 키 보존, 기본값 건너뛰기, 못 옮긴 키 보고.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.domi.furnace.server.config.PaperConfigImport.Entry;
import org.domi.furnace.server.config.PaperConfigImport.Kind;
import org.domi.furnace.server.config.PaperConfigImport.Result;
import org.domi.furnace.server.net.ConnectionLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperConfigImportTest {

    private static void write(Path dir, String rel, String text) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private static final String BUKKIT = """
            # This is the main configuration file for Bukkit.
            settings:
              allow-end: true
              warn-on-overload: true
              permissions-file: permissions.yml
              update-folder: update
              plugin-profiling: false
              connection-throttle: 4000
              query-plugins: true
              deprecated-verbose: default
              shutdown-message: Server closed
              minimum-api: none
              use-map-color-cache: true
            spawn-limits:
              monsters: 70
              animals: 10
              water-animals: 5
              water-ambient: 20
              water-underground-creature: 5
              axolotls: 5
              ambient: 15
            chunk-gc:
              period-in-ticks: 600
            ticks-per:
              animal-spawns: 400
              monster-spawns: 1
              water-spawns: 1
              water-ambient-spawns: 1
              water-underground-creature-spawns: 1
              axolotl-spawns: 1
              ambient-spawns: 1
              autosave: 12000
            aliases: now-in-commands.yml
            """;

    private static final String SPIGOT = """
            config-version: 12
            settings:
              debug: false
              bungeecord: true
              timeout-time: 90
              restart-on-crash: true
              restart-script: ./start.sh
              netty-threads: 4
              attribute:
                maxHealth:
                  max: 2048.0
              sample-count: 12
              player-shuffle: 0
              user-cache-size: 1000
              save-user-cache-on-stop-only: false
              moved-wrongly-threshold: 0.0625
              moved-too-quickly-multiplier: 10.0
              log-villager-deaths: true
              log-named-deaths: true
            messages:
              whitelist: You are not whitelisted on this server!
              unknown-command: Unknown command. Type "/help" for help.
              server-full: The server is full!
              outdated-client: Outdated client! Please use {0}
              outdated-server: Outdated server! I'm still on {0}
              restart: Server is restarting
            commands:
              tab-complete: 0
              send-namespaced: true
              log: true
              spam-exclusions:
              - /skill
              silent-commandblock-console: false
              replace-commands:
              - setblock
            world-settings:
              default:
                view-distance: 6
                simulation-distance: default
                mob-spawn-range: 6
                growth:
                  cactus-modifier: 100
                  cane-modifier: 100
                entity-activation-range:
                  animals: 32
                  monsters: 32
            """;

    private static final String PAPER_GLOBAL = """
            _version: 29
            proxies:
              bungee-cord:
                online-mode: true
              proxy-protocol: true
              velocity:
                enabled: false
                online-mode: true
                secret: ''
            chunk-system:
              io-threads: 6
              worker-threads: -1
            item-validation:
              display-name: 4000
              loc-name: 8192
              lore-line: 8192
              resolve-selectors-in-books: false
              book:
                author: 8192
                page: 16384
                title: 8192
            packet-limiter:
              kick-message: <red><lang:disconnect.exceeded_packet_rate>
              limits:
                all:
                  interval: 7.0
                  max-packet-rate: 500.0
            logging:
              log-player-ip-addresses: false
            watchdog:
              early-warning-every: 5000
            chunk-loading-basic:
              player-max-chunk-send-rate: 60.0
              target-player-chunk-send-rate: 100.0
            unsupported-settings:
              allow-piston-duplication: false
              allow-headless-pistons: false
            some-future-feature:
              enabled: true
              mode: fast
            """;

    private static final String PAPER_WORLD = """
            _version: 30
            chunks:
              auto-save-interval: default
              delay-chunk-unloads-by: 30s
              max-auto-save-chunks-per-tick: 24
              prevent-moving-into-unloaded-chunks: false
            anti-xray:
              enabled: false
            entities:
              spawning:
                spawn-limits:
                  monsters: 70
            """;

    private static Result run(Path dir, Properties existing) {
        return PaperConfigImport.analyze(PaperConfigImport.detect(dir), existing);
    }

    private static void all(Path dir) throws Exception {
        write(dir, "bukkit.yml", BUKKIT);
        write(dir, "spigot.yml", SPIGOT);
        write(dir, "config/paper-global.yml", PAPER_GLOBAL);
        write(dir, "config/paper-world-defaults.yml", PAPER_WORLD);
    }

    private static List<Entry> of(Result r, Kind k) {
        return r.entries().stream().filter(e -> e.kind() == k).toList();
    }

    private static boolean has(Result r, Kind k, String file, String key) {
        return r.entries().stream().anyMatch(e -> e.kind() == k && e.file().equals(file) && e.key().equals(key));
    }

    @Test
    void mapsEquivalentKeysAndSkipsPaperDefaults(@TempDir Path dir) throws Exception {
        all(dir);
        Result r = run(dir, new Properties());

        assertEquals("true", r.toWrite().get("bungeecord"));
        assertEquals("true", r.toWrite().get("proxy-protocol"));
        assertEquals("6", r.toWrite().get("io-threads"));
        assertEquals("12000", r.toWrite().get("autosave-ticks"), "paper 는 default 라 bukkit.yml ticks-per.autosave 를 따른다");
        assertEquals("600", r.toWrite().get("chunk-eviction-grace-ticks"), "30s = 600틱");
        assertEquals("4000", r.toWrite().get("limits.item.display-name"));
        // Paper 기본값과 같은 것은 옮기지 않고 DEFAULT 로만 남긴다
        assertFalse(r.toWrite().containsKey("limits.item.lore-line"));
        assertFalse(r.toWrite().containsKey("limits.item.book-page"));
        assertFalse(r.toWrite().containsKey("limits.item.book-author"));
        assertFalse(r.toWrite().containsKey("limits.item.item-name"));
        assertTrue(has(r, Kind.DEFAULT, "paper-global.yml", "item-validation.lore-line"));
        assertTrue(has(r, Kind.DEFAULT, "paper-global.yml", "item-validation.book.page"));
        // 옮기지 않는 키는 쓰지 않는다
        assertEquals(List.of("autosave-ticks", "bungeecord", "chunk-eviction-grace-ticks", "io-threads", "limits.item.display-name", "proxy-protocol"),
                r.toWrite().keySet().stream().sorted().toList());
    }

    @Test
    void reportsManualUnsupportedAndUnknown(@TempDir Path dir) throws Exception {
        all(dir);
        Result r = run(dir, new Properties());

        // 직접 확인: 비슷한 기능이 있다
        assertTrue(has(r, Kind.MANUAL, "spigot.yml", "settings.timeout-time"));
        assertTrue(r.entries().stream().anyMatch(e -> e.key().equals("settings.timeout-time") && e.target().contains("max-tick-time=90000")));
        assertTrue(has(r, Kind.MANUAL, "paper-global.yml", "logging.log-player-ip-addresses"));
        assertTrue(r.entries().stream().anyMatch(e -> e.key().equals("chunk-loading-basic.player-max-chunk-send-rate") && e.target().equals("chunks-per-tick=3")));
        assertTrue(has(r, Kind.MANUAL, "spigot.yml", "world-settings.default.view-distance"));
        assertTrue(has(r, Kind.MANUAL, "paper-global.yml", "proxies.bungee-cord.online-mode"));
        assertTrue(of(r, Kind.MANUAL).stream().anyMatch(e -> e.key().startsWith("packet-limiter")));
        // 대응 없음
        assertTrue(has(r, Kind.UNSUPPORTED, "bukkit.yml", "settings.allow-end"));
        assertTrue(has(r, Kind.UNSUPPORTED, "bukkit.yml", "spawn-limits.*"));
        assertTrue(has(r, Kind.UNSUPPORTED, "spigot.yml", "messages.*"));
        assertTrue(has(r, Kind.UNSUPPORTED, "paper-global.yml", "unsupported-settings.*"));
        assertTrue(has(r, Kind.UNSUPPORTED, "paper-world-defaults.yml", "anti-xray.enabled"));
        // 버전 표지는 무시
        assertTrue(has(r, Kind.IGNORED, "spigot.yml", "config-version"));
        assertTrue(has(r, Kind.IGNORED, "paper-global.yml", "_version"));
        // 어디에도 없는 키는 모르는 키로 묶는다
        assertTrue(has(r, Kind.UNKNOWN, "paper-global.yml", "some-future-feature.*"));
    }

    @Test
    void velocityBeatsBungeeAndSecretIsMasked(@TempDir Path dir) throws Exception {
        write(dir, "spigot.yml", "settings:\n  bungeecord: true\n");
        write(dir, "config/paper-global.yml", """
                proxies:
                  velocity:
                    enabled: true
                    online-mode: true
                    secret: 's3cr3t=value: #x'
                """);
        Result r = run(dir, new Properties());
        assertEquals("s3cr3t=value: #x", r.toWrite().get("velocity-secret"));
        assertFalse(r.toWrite().containsKey("bungeecord"), "Velocity 와 BungeeCord 는 함께 켤 수 없다");
        assertTrue(has(r, Kind.MANUAL, "spigot.yml", "settings.bungeecord"));
        String report = PaperConfigImport.render(r, List.of());
        assertFalse(report.contains("s3cr3t"), "비밀키는 보고서에 적지 않는다");
        assertTrue(report.contains("velocity-secret=****"));
    }

    @Test
    void velocityEnabledWithEmptySecretIsManual(@TempDir Path dir) throws Exception {
        write(dir, "config/paper-global.yml", "proxies:\n  velocity:\n    enabled: true\n    secret: ''\n");
        Result r = run(dir, new Properties());
        assertTrue(r.toWrite().isEmpty());
        assertTrue(has(r, Kind.MANUAL, "paper-global.yml", "proxies.velocity.secret"));
    }

    @Test
    void existingFurnaceKeysAreKept(@TempDir Path dir) throws Exception {
        all(dir);
        Properties existing = new Properties();
        existing.setProperty("io-threads", "2");
        existing.setProperty("velocity-secret", "already");
        Result r = run(dir, existing);
        assertFalse(r.toWrite().containsKey("io-threads"));
        assertTrue(has(r, Kind.KEPT, "paper-global.yml", "chunk-system.io-threads"));
        // furnace.properties 에 Velocity 비밀키가 이미 있으면 BungeeCord 는 옮기지 않는다(함께 켜면 기동 실패)
        assertFalse(r.toWrite().containsKey("bungeecord"));
    }

    @Test
    void writesFurnacePropertiesThatTheLoadersAccept(@TempDir Path dir) throws Exception {
        all(dir);
        Path props = dir.resolve("furnace.properties");
        String original = "# 내 설정\nview-distance=8\nplugins.fail-fast=true";   // 줄바꿈 없이 끝난다
        Files.writeString(props, original, StandardCharsets.UTF_8);

        Result r = PaperConfigImport.analyzeAndApply(dir, props, dir.resolve(PaperConfigImport.REPORT_FILE));
        assertNotNull(r);

        String text = Files.readString(props, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(original + "\n"), "원래 내용은 그대로 두고 뒤에 덧붙인다");
        assertTrue(text.contains("# --- Paper/Spigot 설정에서 가져옴"));
        assertTrue(Files.isRegularFile(dir.resolve(PaperConfigImport.REPORT_FILE)));

        // 두 로더가 에러 없이 읽고 값이 들어간다
        Config cfg = ConfigLoader.load(props);
        assertTrue(cfg.bungeecord());
        assertEquals(6, cfg.ioThreads());
        assertEquals(12000, cfg.autosaveTicks());
        assertEquals(600, cfg.chunkEvictionGraceTicks());
        assertEquals(8, cfg.viewDistance(), "원래 있던 키는 그대로");
        ConnectionLimits limits = ConnectionLimits.load(props, s -> { });
        assertEquals(4000, limits.displayNameLength());
        assertEquals(0, limits.loreLineLength());
        assertTrue(limits.proxyProtocol());
    }

    @Test
    void escapesValuesSoPropertiesReadThemBack(@TempDir Path dir) throws Exception {
        write(dir, "config/paper-global.yml", """
                proxies:
                  velocity:
                    enabled: true
                    secret: ' a=b:c#d!e\\f 한글 '
                """);
        Path props = dir.resolve("furnace.properties");
        PaperConfigImport.analyzeAndApply(dir, props, dir.resolve(PaperConfigImport.REPORT_FILE));
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(props)) {
            p.load(in);
        }
        assertEquals("a=b:c#d!e\\f 한글", p.getProperty("velocity-secret"), "YAML 값은 앞뒤 공백을 자른 문자열이다");
    }

    @Test
    void doesNothingWithoutPaperFiles(@TempDir Path dir) throws Exception {
        Path props = dir.resolve("furnace.properties");
        assertNull(PaperConfigImport.analyzeAndApply(dir, props, dir.resolve(PaperConfigImport.REPORT_FILE)));
        assertFalse(Files.exists(props));
        assertFalse(Files.exists(dir.resolve(PaperConfigImport.REPORT_FILE)));
    }

    @Test
    void runsOnlyOnceAndForceRunsAgain(@TempDir Path dir) throws Exception {
        write(dir, "config/paper-global.yml", "chunk-system:\n  io-threads: 6\n");
        Path props = dir.resolve("furnace.properties");
        Path report = dir.resolve(PaperConfigImport.REPORT_FILE);
        PaperConfigImport.runIfNeeded(dir, props);
        assertTrue(Files.readString(props).contains("io-threads=6"));
        // 사용자가 값을 바꾼 뒤 다시 켜도 덮어쓰지 않는다(보고서가 있으니 건너뜀)
        Files.writeString(props, "io-threads=3\n");
        PaperConfigImport.runIfNeeded(dir, props);
        assertEquals("io-threads=3\n", Files.readString(props));
        // 보고서를 지우면 다시 하지만 이미 있는 키는 건드리지 않는다
        Files.delete(report);
        PaperConfigImport.runIfNeeded(dir, props);
        assertEquals("io-threads=3\n", Files.readString(props));
        assertTrue(Files.readString(report).contains("이미 furnace.properties 에 있어"));
        // off
        Files.delete(report);
        System.setProperty(PaperConfigImport.MODE_PROPERTY, "off");
        try {
            PaperConfigImport.runIfNeeded(dir, props);
            assertFalse(Files.exists(report));
            System.setProperty(PaperConfigImport.MODE_PROPERTY, "force");
            PaperConfigImport.runIfNeeded(dir, props);
            assertTrue(Files.exists(report));
            long before = Files.getLastModifiedTime(report).toMillis();
            PaperConfigImport.runIfNeeded(dir, props);   // force 는 보고서가 있어도 다시 한다
            assertTrue(Files.getLastModifiedTime(report).toMillis() >= before);
        } finally {
            System.clearProperty(PaperConfigImport.MODE_PROPERTY);
        }
    }

    @Test
    void brokenYamlIsReportedNotThrown(@TempDir Path dir) throws Exception {
        write(dir, "bukkit.yml", "settings:\n\t bad: [unclosed\n");
        write(dir, "spigot.yml", "settings:\n  bungeecord: true\n");
        Path props = dir.resolve("furnace.properties");
        PaperConfigImport.runIfNeeded(dir, props);   // 던지지 않는다
        assertTrue(Files.readString(props).contains("bungeecord=true"), "읽은 파일은 계속 처리한다");
        String report = Files.readString(dir.resolve(PaperConfigImport.REPORT_FILE));
        assertTrue(report.contains("읽지 못한 파일: bukkit.yml"));
    }

    @Test
    void legacyPaperYmlIsNoted(@TempDir Path dir) throws Exception {
        write(dir, "paper.yml", "settings:\n  velocity-support:\n    enabled: true\n");
        Path props = dir.resolve("furnace.properties");
        PaperConfigImport.runIfNeeded(dir, props);
        assertFalse(Files.exists(props));
        assertTrue(Files.readString(dir.resolve(PaperConfigImport.REPORT_FILE)).contains("1.19 이전 형식"));
    }

    @Test
    void parsesDurations() {
        assertEquals(200L, PaperConfigImport.durationTicks("10s"));
        assertEquals(10L, PaperConfigImport.durationTicks("500ms"));
        assertEquals(1800L, PaperConfigImport.durationTicks("1m 30s"));
        assertEquals(20L, PaperConfigImport.durationTicks("1"), "숫자만 있으면 초");
        assertEquals(1L, PaperConfigImport.durationTicks("1ms"), "올림");
        assertNull(PaperConfigImport.durationTicks("soon"));
        assertNull(PaperConfigImport.durationTicks("10 parsecs"));
        assertNull(PaperConfigImport.durationTicks(""));
    }
}
