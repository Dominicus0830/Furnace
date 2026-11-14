package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.domi.furnace.server.net.ConnectionLimits;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.domi.furnace.server.remote.RconLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** O2: ${ENV} 치환, 프리셋, 버전 마이그레이션, /furnace config 덤프, JVM 점검. */
class ConfigToolsTest {

    @TempDir Path dir;

    @AfterEach
    void reset() {
        FurnaceProps.setEnvForTest(null);
        ConfigDump.clearForTest();
    }

    private Path write(String text) throws IOException {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return f;
    }

    // ------------------------------------------------------------------ ${ENV}

    @Test
    void envIsSubstituted() throws IOException {
        FurnaceProps.setEnvForTest(Map.of("FURNACE_SECRET", "abcdefghijklmnop")::get);
        Properties p = FurnaceProps.read(write("velocity-secret=${FURNACE_SECRET}\nserver-brand=a-${FURNACE_SECRET}-b\n"));
        assertEquals("abcdefghijklmnop", p.getProperty("velocity-secret"));
        assertEquals("a-abcdefghijklmnop-b", p.getProperty("server-brand"));
    }

    @Test
    void defaultIsUsedWhenEnvMissingOrEmpty() throws IOException {
        FurnaceProps.setEnvForTest(Map.of("EMPTY", "")::get);
        Properties p = FurnaceProps.read(write("a=${NOPE:-fallback}\nb=${EMPTY:-other}\nc=${NOPE:-}\n"));
        assertEquals("fallback", p.getProperty("a"));
        assertEquals("other", p.getProperty("b"));
        assertEquals("", p.getProperty("c"));
    }

    @Test
    void missingEnvWithoutDefaultStopsStartupNamingTheKey() throws IOException {
        FurnaceProps.setEnvForTest(k -> null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FurnaceProps.read(write("velocity-secret=${MISSING_ONE}\n")));
        assertTrue(e.getMessage().contains("velocity-secret"), e.getMessage());
        assertTrue(e.getMessage().contains("MISSING_ONE"), e.getMessage());
    }

    @Test
    void escapeKeepsLiteralDollarBrace() throws IOException {
        FurnaceProps.setEnvForTest(k -> null);
        Properties p = FurnaceProps.read(write("server-brand=$${NOT_A_VAR}\n"));
        assertEquals("${NOT_A_VAR}", p.getProperty("server-brand"));
    }

    @Test
    void unterminatedPlaceholderStaysLiteral() throws IOException {
        assertEquals("pa${ss", FurnaceProps.read(write("server-brand=pa${ss\n")).getProperty("server-brand"));
    }

    @Test
    void lenientModeKeepsUnknownPlaceholderAndWarns() {
        Properties in = new Properties();
        in.setProperty("motd", "hello ${who}");
        List<String> warns = new ArrayList<>();
        Properties out = FurnaceProps.substitute(in, k -> null, false, warns::add);
        assertEquals("hello ${who}", out.getProperty("motd"));
        assertEquals(1, warns.size());
    }

    @Test
    void everyLoaderSeesSubstitutedValues() throws IOException {
        // 같은 입구를 쓰는지: 속도 제한·RCON·ConfigLoader 가 치환된 값을 읽는다
        FurnaceProps.setEnvForTest(Map.of("PER_IP", "7", "SECRET", "0123456789abcdef")::get);
        Path f = write("rcon.max-clients-per-ip=${PER_IP}\nvelocity-secret=${SECRET}\nconnection.read-timeout-seconds=${NOPE:-45}\n");
        assertEquals(7, RconLimits.load(f).maxClientsPerIp());
        assertEquals("0123456789abcdef", ConfigLoader.load(f).velocitySecret());
        assertEquals(45, ConnectionLimits.load(f, w -> { }).readTimeoutSeconds());
    }

    // ------------------------------------------------------------------ 프리셋

    @Test
    void presetFillsOnlyMissingKeys() throws IOException {
        Properties p = FurnaceProps.read(write("config.preset=performance\nentity-view-distance=10\n"));
        assertEquals("10", p.getProperty("entity-view-distance"), "파일에 적은 키가 이긴다");
        assertEquals("40", p.getProperty("chunk-eviction-grace-ticks"), "없는 키는 프리셋이 채운다");
    }

    @Test
    void presetOverridesExplicitDefaultsButNotCustomValues() throws IOException {
        // 자동 생성 파일처럼 기본값이 명시돼 있어도 프리셋이 적용돼야 한다
        Properties p = FurnaceProps.read(write("config.preset=performance\nentity-view-distance=8\nchunk-eviction-grace-ticks=77\n"));
        assertEquals("6", p.getProperty("entity-view-distance"));
        assertEquals("77", p.getProperty("chunk-eviction-grace-ticks"));
    }

    @Test
    void noPresetChangesNothing() throws IOException {
        Properties p = FurnaceProps.read(write("autosave-ticks=100\n"));
        assertEquals(1, p.size());
    }

    @Test
    void unknownPresetStopsStartup() throws IOException {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FurnaceProps.read(write("config.preset=turbo\n")));
        assertTrue(e.getMessage().contains("config.preset"));
    }

    @Test
    void presetsOnlyUseKnownKeysAndLegalValues() {
        for (String name : ConfigPresets.NAMES) {
            Properties p = new Properties();
            p.setProperty("config.preset", name);
            ConfigPresets.apply(p);
            for (String k : p.stringPropertyNames()) assertTrue(OptionKeys.isKnown(k), name + " 프리셋의 모르는 키: " + k);
            // 프리셋이 채운 값으로 실제 로더가 기동 가능한 설정을 만든다
            Path f = dir.resolve("preset-" + name + ".properties");
            try {
                StringBuilder sb = new StringBuilder();
                for (String k : p.stringPropertyNames()) sb.append(k).append('=').append(p.getProperty(k)).append('\n');
                Files.writeString(f, sb.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ConfigLoader.load(f);
            OperatorOptions.load(f);
            ConnectionLimits.load(f, w -> { });
        }
    }

    @Test
    void vanillaPresetMatchesVanillaDefaults() {
        Properties p = new Properties();
        p.setProperty("config.preset", "vanilla");
        ConfigPresets.apply(p);
        assertEquals(Long.toString(Config.DEFAULT.tickNanos()), p.getProperty("tick-nanos"));
        assertEquals(Boolean.toString(OperatorOptions.DEFAULT.strictChatOrder()), p.getProperty("chat.strict-order"));
        assertEquals(Integer.toString(ConnectionLimits.DEFAULT.chatLength()), p.getProperty("limits.chat-length"));
        assertEquals(Integer.toString(ConnectionLimits.DEFAULT.commandLength()), p.getProperty("limits.command-length"));
    }

    // ------------------------------------------------------------------ 마이그레이션

    private static final List<ConfigMigration.Step> STEPS = List.of(
            new ConfigMigration.Step(1, Map.of("old-name", "new-name"), List.of("gone-key")),
            new ConfigMigration.Step(2, Map.of("second-old", "second-new"), List.of()));

    @Test
    void noVersionLineMeansVersionOneAndCurrentOneTouchesNothing() throws IOException {
        Path f = write("# 내 설정\nautosave-ticks=100\n");
        String before = Files.readString(f, StandardCharsets.UTF_8);
        assertNull(ConfigMigration.run(f, 1, List.of(), warn()));
        assertEquals(before, Files.readString(f, StandardCharsets.UTF_8));
    }

    /** 형식 2(2026-11-12): 옛 기본 off 로 자동 기록된 spigot-mappings 줄은 새 기본 download 로, 다른 값·주석 줄은 그대로. */
    @Test
    void version2MovesTheOldSpigotMappingsDefaultOnly() throws IOException {
        Path f = write("# 내 설정\nplugins.compat.nms.spigot-mappings=off\nautosave-ticks=100\n");
        ConfigMigration.Result r = ConfigMigration.run(f, warn());
        assertNotNull(r);
        assertEquals(1, r.from());
        assertEquals(ConfigMigration.CURRENT, r.to());
        Properties p = FurnaceProps.readRaw(f);
        assertEquals("download", p.getProperty("plugins.compat.nms.spigot-mappings"));
        assertEquals("100", p.getProperty("autosave-ticks"));
        assertTrue(r.changes().stream().anyMatch(c -> c.contains("plugins.compat.nms.spigot-mappings")), r.changes().toString());
        assertTrue(Files.readString(r.backup(), StandardCharsets.UTF_8).contains("spigot-mappings=off"), "백업은 원본");
        assertNull(ConfigMigration.run(f, warn()), "다시 돌리면 아무 일도 없다");

        Path files = write("plugins.compat.nms.spigot-mappings = files\n#plugins.compat.nms.spigot-mappings=off\n");
        ConfigMigration.run(files, warn());
        String after = Files.readString(files, StandardCharsets.UTF_8);
        assertTrue(after.contains("plugins.compat.nms.spigot-mappings = files"), "옛 기본과 다른 값은 그대로: " + after);
        assertTrue(after.contains("#plugins.compat.nms.spigot-mappings=off"), "주석 줄은 그대로: " + after);
    }

    /** 형식 3(2026-11-14, T-101): 옛 기본 4 로 자동 기록된 region-section-shift 줄만 3 으로, 직접 고른 값은 그대로. */
    @Test
    void version3MovesTheOldRegionSectionShiftDefaultOnly() throws IOException {
        Path f = write("config-version=2\nregion-section-shift=4\nregion-merge-radius=1\n");
        ConfigMigration.Result r = ConfigMigration.run(f, warn());
        assertNotNull(r);
        assertEquals(2, r.from());
        assertEquals("3", FurnaceProps.readRaw(f).getProperty("region-section-shift"));
        assertEquals("1", FurnaceProps.readRaw(f).getProperty("region-merge-radius"));
        assertTrue(Files.readString(r.backup(), StandardCharsets.UTF_8).contains("region-section-shift=4"), "백업은 원본");

        Path chosen = write("config-version=2\nregion-section-shift=5\n");
        ConfigMigration.run(chosen, warn());
        assertEquals("5", FurnaceProps.readRaw(chosen).getProperty("region-section-shift"), "옛 기본과 다른 값은 그대로");

        Path v1 = write("region-section-shift=4\nplugins.compat.nms.spigot-mappings=off\n");   // 형식 1 → 3 두 단계
        ConfigMigration.run(v1, warn());
        assertEquals("3", FurnaceProps.readRaw(v1).getProperty("region-section-shift"));
        assertEquals("download", FurnaceProps.readRaw(v1).getProperty("plugins.compat.nms.spigot-mappings"));
    }

    @Test
    void freshlyCreatedPropertiesFileIsAlreadyCurrent() throws IOException {
        Path f = dir.resolve("fresh.properties");
        FurnacePropertiesFile.ensure(f);
        assertNull(ConfigMigration.run(f, warn()), "새로 만든 파일은 다음 기동에 마이그레이션(백업)이 돌지 않는다");
        assertEquals("download", FurnaceProps.readRaw(f).getProperty("plugins.compat.nms.spigot-mappings"));
        assertEquals("3", FurnaceProps.readRaw(f).getProperty("region-section-shift"));
    }

    @Test
    void migratesAcrossSeveralVersionsKeepingCommentsAndBacksUp() throws IOException {
        Path f = write("# 주석 보존\nold-name = 5\n\nunrelated=1\ngone-key=yes\nsecond-old: 9\n");
        List<String> w = new ArrayList<>();
        ConfigMigration.Result r = ConfigMigration.run(f, 3, STEPS, w::add);
        assertNotNull(r);
        assertEquals(1, r.from());
        assertEquals(3, r.to());
        String after = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(after.contains("# 주석 보존"));
        assertTrue(after.contains("new-name = 5"), after);
        assertTrue(after.contains("second-new: 9"), after);
        assertTrue(after.contains("unrelated=1"));
        assertTrue(after.contains("# (형식 1 의 키라 더는 쓰지 않는다) gone-key=yes"), after);
        assertTrue(after.contains("config-version=3"));
        assertTrue(Files.exists(r.backup()));
        assertTrue(Files.readString(r.backup(), StandardCharsets.UTF_8).contains("old-name = 5"), "백업은 원본");
        assertEquals("3", FurnaceProps.readRaw(f).getProperty("config-version"));
        // 다시 돌리면 아무 일도 없다
        assertNull(ConfigMigration.run(f, 3, STEPS, w::add));
    }

    @Test
    void existingNewKeyIsNotOverwritten() throws IOException {
        Path f = write("old-name=1\nnew-name=2\n");
        List<String> w = new ArrayList<>();
        ConfigMigration.run(f, 2, STEPS, w::add);
        Properties p = FurnaceProps.readRaw(f);
        assertEquals("2", p.getProperty("new-name"));
        assertNull(p.getProperty("old-name"));
        assertFalse(w.isEmpty());
    }

    @Test
    void newerFileOnlyWarns() throws IOException {
        Path f = write("config-version=9\nold-name=1\n");
        List<String> w = new ArrayList<>();
        assertNull(ConfigMigration.run(f, 2, STEPS, w::add));
        assertEquals(1, w.size());
        assertTrue(Files.readString(f).contains("old-name=1"));
    }

    @Test
    void badVersionValueIsRejected() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> ConfigMigration.run(write("config-version=abc\n"), warn()));
        assertThrows(IllegalArgumentException.class, () -> ConfigMigration.run(write("config-version=0\n"), warn()));
    }

    @Test
    void missingFileIsFine() {
        assertNull(ConfigMigration.run(dir.resolve("nope.properties"), warn()));
    }

    private static java.util.function.Consumer<String> warn() { return w -> { }; }

    // ------------------------------------------------------------------ 덤프

    @Test
    void dumpShowsModifiedKeysSourcesAndMasksSecrets() throws IOException {
        FurnaceProps.setEnvForTest(Map.of("S", "supersecretvalue")::get);
        Path f = write("# c\nautosave-ticks=100\nvelocity-secret=${S}\nbackup.keep=10\nautosve-ticks=5\nconfig.preset=performance\n");
        ConfigDump.Snapshot s = ConfigDump.take(f);
        List<String> out = ConfigDump.render(s, null, s.size(), s.modified());
        String all = String.join("\n", out);
        assertTrue(all.contains("autosave-ticks = 100"), all);
        assertTrue(all.contains("(기본 6000)"), all);
        assertTrue(all.contains("[파일 2번째 줄]"), all);
        assertTrue(all.contains("velocity-secret = ****(16자)"), all);
        assertFalse(all.contains("supersecretvalue"), "비밀값이 덤프에 나오면 안 된다");
        assertTrue(all.contains("환경 변수에서"), all);
        assertFalse(all.contains("backup.keep"), "기본값과 같은 키는 숨긴다");
        assertTrue(all.contains("entity-view-distance = 6") && all.contains("프리셋 performance"), all);
        assertTrue(all.contains("autosve-ticks (5번째 줄) — 'autosave-ticks' 를 말한 건가요?"), all);
    }

    @Test
    void secretsAreMaskedByName() {
        assertTrue(ConfigDump.isSecret("velocity-secret"));
        assertTrue(ConfigDump.isSecret("future.api-Token"));
        assertTrue(ConfigDump.isSecret("x.password"));
        assertFalse(ConfigDump.isSecret("autosave-ticks"));
    }

    @Test
    void dumpAllAndFilter() throws IOException {
        Path f = write("autosave-ticks=100\n");
        ConfigDump.Snapshot s = ConfigDump.take(f);
        String all = String.join("\n", ConfigDump.render(s, "all", s.size(), s.modified()));
        assertTrue(all.contains("backup.keep"));
        assertTrue(all.contains("(없음 → 기본값 10)"));
        String filtered = String.join("\n", ConfigDump.render(s, "BACKUP", s.size(), s.modified()));
        assertTrue(filtered.contains("backup.interval-minutes"));
        assertFalse(filtered.contains("autosave-ticks"));
    }

    @Test
    void dumpNotesFileChangedAfterStart() throws IOException {
        Path f = write("autosave-ticks=100\n");
        ConfigDump.Snapshot s = ConfigDump.take(f);
        String text = String.join("\n", ConfigDump.render(s, null, s.size() + 5, s.modified()));
        assertTrue(text.contains("시작한 뒤 파일이 바뀌었습니다"));
    }

    @Test
    void dumpWithoutFile() {
        ConfigDump.Snapshot s = ConfigDump.take(dir.resolve("none.properties"));
        String text = String.join("\n", ConfigDump.render(s, null, 0, 0));
        assertTrue(text.contains("파일 없음"));
        assertTrue(text.contains("기본값과 다른 키 0개"));
    }

    @Test
    void dumpWarnsAboutDangerousValuesButNotUnknownTwice() throws IOException {
        Path f = write("tick-nanos=40000000\nfoo=1\n");
        ConfigDump.Snapshot s = ConfigDump.take(f);
        String text = String.join("\n", ConfigDump.render(s, null, s.size(), s.modified()));
        assertTrue(text.contains("경고 1개"), text);
        assertTrue(text.contains("모르는 키 1개"), text);
    }

    @Test
    void lineNumbersSkipCommentsAndBlankLines() throws IOException {
        Map<String, Integer> n = ConfigDump.lineNumbers(write("# c\n\n  key.a = 1\n! x\nkey.b:2\nkey.c 3\n"));
        assertEquals(3, n.get("key.a"));
        assertEquals(5, n.get("key.b"));
        assertEquals(6, n.get("key.c"));
        assertEquals(3, n.size());
    }

    @Test
    void differsHandlesTypes() {
        var tick = OptionKeys.all().stream().filter(o -> o.key().equals("tick-nanos")).findFirst().orElseThrow();
        assertFalse(ConfigDump.differs(tick, "50000000"));
        assertFalse(ConfigDump.differs(tick, " 50000000 "));
        assertTrue(ConfigDump.differs(tick, "1"));
        var bool = OptionKeys.all().stream().filter(o -> o.key().equals("chat.strict-order")).findFirst().orElseThrow();
        assertFalse(ConfigDump.differs(bool, "TRUE"));
        assertTrue(ConfigDump.differs(bool, "false"));
        assertFalse(ConfigDump.differs(bool, null));
    }

    // ------------------------------------------------------------------ 키 표 완전성

    @Test
    void everyKeyAnyLoaderReadsIsInTheTable() {
        for (String k : LibrarySettings.KEYS) assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        for (String k : org.domi.furnace.server.entity.spawn.SpawnConfig.knownKeys()) assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        for (String k : RconLimits.knownKeys()) assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        for (String k : ConnectionLimits.knownKeys()) assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        for (String k : List.of("motd-format", "io-uring", "idle-pause.empty-seconds", "idle-pause.flush-caches", "backup.interval-minutes",
                "backup.directory", "backup.keep", "backup.format", "backup.skip-if-no-players", "backup.hold-timeout-seconds",
                "metrics.prometheus.enabled", "metrics.prometheus.bind", "metrics.prometheus.port", "metrics.prometheus.token",
                "metrics.jmx.enabled", "config-version", "config.preset", "jvm.check", "console.jline", "console.history-size")) {
            assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        }
    }

    @Test
    void auditAddsLineNumbersToUnknownKeys() {
        Properties p = new Properties();
        p.setProperty("autosve-ticks", "5");
        List<String> w = new ArrayList<>();
        OptionKeys.audit(p, Map.of("autosve-ticks", 12), w::add);
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("(12번째 줄)"), w.get(0));
    }

    // ------------------------------------------------------------------ JVM 점검

    @Test
    void jvmCheckQuietForGoodSetup() {
        assertEquals(List.of(), JvmCheck.check(21, 4L << 30, List.of("-Xms4G", "-Xmx4G", "-XX:+UseZGC"), 8));
        assertEquals(List.of(), JvmCheck.check(25, 8L << 30, List.of(), 4));
    }

    @Test
    void jvmCheckFlagsProblems() {
        assertEquals(1, JvmCheck.check(22, 4L << 30, List.of(), 8).size(), "비 LTS");
        assertTrue(JvmCheck.check(21, 512L << 20, List.of(), 8).get(0).contains("512MB"));
        assertTrue(JvmCheck.check(21, 4L << 30, List.of("-XX:+UseSerialGC"), 8).get(0).contains("Serial"));
        assertTrue(JvmCheck.check(21, 4L << 30, List.of("-XX:+UseParallelGC"), 8).get(0).contains("Parallel"));
        assertTrue(JvmCheck.check(21, 4L << 30, List.of("-Xms1G", "-Xmx4G"), 8).get(0).contains("-Xms1G"));
        assertTrue(JvmCheck.check(21, 4L << 30, List.of(), 1).get(0).contains("CPU"));
        assertTrue(JvmCheck.check(17, 4L << 30, List.of(), 8).get(0).contains("Java 21"));
    }

    @Test
    void jvmSizeParsing() {
        assertEquals(4L << 30, JvmCheck.sizeOf("4G"));
        assertEquals(512L << 20, JvmCheck.sizeOf("512m"));
        assertEquals(1024L, JvmCheck.sizeOf("1k"));
        assertEquals(12345L, JvmCheck.sizeOf("12345"));
        assertEquals(-1, JvmCheck.sizeOf("abc"));
    }

    @Test
    void strictIntRejectsBadValues() {
        Properties p = new Properties();
        p.setProperty("console.history-size", "-3");
        assertThrows(IllegalArgumentException.class, () -> OptionKeys.strictInt(p, "console.history-size", 500, 0, 100000));
        p.setProperty("console.history-size", "abc");
        assertThrows(IllegalArgumentException.class, () -> OptionKeys.strictInt(p, "console.history-size", 500, 0, 100000));
        p.setProperty("console.history-size", "20");
        assertEquals(20, OptionKeys.strictInt(p, "console.history-size", 500, 0, 100000));
        assertEquals(500, OptionKeys.strictInt(new Properties(), "console.history-size", 500, 0, 100000));
    }
}
