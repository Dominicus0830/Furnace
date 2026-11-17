// furnace.properties 의 모든 키 목록(키 표)과 시작 점검. 새 옵션을 만들면 여기에도 한 줄 넣는다(없으면 시험이 실패한다).
//
// 규칙(R4, docs/feature-parity/option-keys-2026-11-07.md):
//   1. 새 옵션의 기본값은 바닐라와 같은 동작이다.
//      예외: entity-activation-range.enabled(기본 켜짐 — Spigot·Paper 와 같다. 도미니코 결정 D11, 2026-11-14). 끄면 바닐라.
//      예외: collisions.max-entity-collisions(기본 8)·entity-tracking-range.enabled·item-merge.always-into-ticking·collisions.skip-unpushable(기본 켜짐)·collisions.allow-player-cramming-damage(기본 꺼짐)
//      — Paper 1.21.1 과 같다(결정 B3-1·T122-1, T-122). -1·false 면 바닐라.
//   2. 모든 새 옵션은 키로 ON/OFF 또는 수치를 바꿀 수 있다(코드 상수·-D 시스템 속성만으로 두지 않는다).
//   3. 잘못된 값(true/false 가 아닌 글자, 범위 밖 수)은 조용히 넘기지 않고 기동 때 어느 키가 문제인지 알리며 멈춘다.
//   4. 위험한 값(보안·바닐라와 다른 게임 속도 등)은 시작 로그에 경고한다 — {@link #audit}.
//   5. 모르는 키(오타)는 가장 비슷한 키를 알려 주며 경고한다.
package org.domi.furnace.server.config;

import org.domi.furnace.server.i18n.Lang;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class OptionKeys {

    private OptionKeys() {}

    /**
     * 키 하나: 종류(number·ON/OFF·text·path·list·key·auto|number 처럼 말이 아닌 표시 — {@link ConfigDump#differs} 가 값을 비교하는 방법을 고른다)와 기본값 글자
     * (빈 글자 = 꺼짐/없음, {@link #DEF_CPU_COUNT}·{@link #DEF_PER_STAGE} 는 글자로 비교할 수 없는 기본값). 키마다의 설명은 이 표가 아니라 처음 만들어지는
     * furnace.properties 의 설명 주석({@link FurnacePropertiesFile})이 서버 언어(en/ko)로 담는다.
     */
    public record Option(String key, String type, String def) {}

    /** 기본값이 "CPU 코어 수"인 키(글자로 비교하지 않는다). */
    public static final String DEF_CPU_COUNT = "cpu-count";
    /** 기본값이 단계마다 다른 키. */
    public static final String DEF_PER_STAGE = "per-stage";

    /** 키가 아니라 이름 접두로 받는 가족(플러그인별 로그 수준, 차원별 시드). */
    private static final List<String> PREFIXES = List.of("plugins.log-level.", "dimension-seed.", "spawn.limit.", "spawn.player-limit.");

    private static final String[] STATES = {"handshake", "status", "login", "configuration", "play"};

    /** furnace.properties 키 표(접속 방어의 단계별 키는 {@link #all()} 이 펼친다). */
    private static final List<Option> FIXED = List.of(
            // 기본 틀
            new Option("tick-nanos", "number", "50000000"),
            new Option("entity-view-distance", "number", "8"),
            new Option("region-section-shift", "number", "3"),
            new Option("region-merge-radius", "number", "1"),
            new Option("light.async", "ON/OFF", "false"),
            new Option("light.async-threads", "number", "1"),
            new Option("worldgen.tick-guard", "ON/OFF", "false"),
            new Option("region-threads", "number", DEF_CPU_COUNT),
            new Option("shared-region-pool", "ON/OFF", "true"),
            new Option("region-pool-dim-cap", "number", "0"),
            new Option("region-pool-starve-ticks", "number", "2"),
            new Option("io-threads", "number", "4"),
            new Option("autosave-ticks", "number", "6000"),
            new Option("chunk-eviction-grace-ticks", "number", "100"),
            new Option("spawn-chunk-radius", "number", "2"),
            new Option("chunks-per-tick", "auto|number", "auto"),
            new Option("velocity-secret", "text", ""),
            new Option("bungeecord", "ON/OFF", "false"),
            // 운영 옵션(O1)
            new Option("chat.strict-order", "ON/OFF", "true"),
            new Option("chat.unsigned-as-system", "ON/OFF", "false"),
            new Option("server-brand", "text", "Furnace"),
            new Option("gravity-block-portal-dupe", "ON/OFF", "true"),
            new Option("commands.send-namespaced", "ON/OFF", "true"),
            new Option("commands.region-drain", "ON/OFF", "true"),
            new Option("announce-sleep-status", "ON/OFF", "true"),
            new Option("world.tick-when-empty", "auto|vanilla|always", "auto"),
            new Option("motd-format", "plain|legacy|markup", "plain"),
            new Option("confirm-interrupt", "ON/OFF", "false"),
            new Option("confirm-interrupt-seconds", "number", "5"),
            // 접속 방어(N2)
            new Option("connection.read-timeout-seconds", "number", "30"),
            new Option("limits.chat-length", "number", "256"),
            new Option("limits.command-length", "number", "256"),
            new Option("limits.item.display-name", "number", "0"),
            new Option("limits.item.item-name", "number", "0"),
            new Option("limits.item.lore-line", "number", "0"),
            new Option("limits.item.book-page", "number", "0"),
            new Option("limits.item.book-author", "number", "0"),
            new Option("proxy-protocol", "ON/OFF", "false"),
            new Option("proxy-protocol.trusted", "list", ""),
            // 플러그인
            new Option("plugins.fail-fast", "ON/OFF", "false"),
            new Option("plugins.data-root", "path", ""),
            new Option("plugins.dev-mode", "ON/OFF", "false"),
            new Option("plugins.dev-mode.auto-reload", "ON/OFF", "false"),
            // 월드
            new Option("worlds.autoload", "list", ""),
            new Option("worldgen-hooks.overworld", "key", ""),
            new Option("worldgen-hooks.the_nether", "key", ""),
            new Option("worldgen-hooks.the_end", "key", ""),
            new Option("world-generator.overworld", "plugin[:id]", ""),
            new Option("world-generator.the_nether", "plugin[:id]", ""),
            new Option("world-generator.the_end", "plugin[:id]", ""),
            new Option("world-biome-provider.overworld", "plugin[:id]", ""),
            new Option("world-biome-provider.the_nether", "plugin[:id]", ""),
            new Option("world-biome-provider.the_end", "plugin[:id]", ""),
            new Option("enchantments.disabled", "list", ""),
            // 아래는 다른 묶음에서 로더만 생기고 키 표에 빠져 있던 키(2026-11-07 furnace.properties 자동 생성 때 채움 — 없으면 오타 경고가 났다)
            new Option("io-uring", "ON/OFF", "false"),
            new Option("rcon.max-clients", "number", "16"),
            new Option("rcon.max-clients-per-ip", "number", "4"),
            new Option("rcon.max-fails-per-connection", "number", "5"),
            new Option("rcon.max-fails-per-ip", "number", "10"),
            new Option("metrics.prometheus.enabled", "ON/OFF", "false"),
            new Option("metrics.prometheus.bind", "text", "127.0.0.1"),
            new Option("metrics.prometheus.port", "number", "9225"),
            new Option("metrics.prometheus.token", "text", ""),
            new Option("metrics.jmx.enabled", "ON/OFF", "false"),
            new Option("idle-pause.empty-seconds", "number", "0"),
            new Option("idle-pause.flush-caches", "ON/OFF", "true"),
            new Option("backup.interval-minutes", "number", "0"),
            new Option("backup.directory", "path", ""),
            new Option("backup.keep", "number", "10"),
            new Option("backup.format", "text", "zip"),
            new Option("backup.skip-if-no-players", "ON/OFF", "true"),
            new Option("backup.hold-timeout-seconds", "number", "10"),
            new Option("plugins.compat.bukkit", "ON/OFF", "true"),
            new Option("plugins.compat.nms.enabled", "ON/OFF", "true"),
            new Option("plugins.compat.nms.strict", "ON/OFF", "false"),
            new Option("plugins.compat.nms.missing-class-log", "text", "summary"),
            new Option("plugins.compat.nms.version-package", "text", "plain"),
            new Option("plugins.compat.nms.spigot-mappings", "text", "download"),
            new Option("plugins.compat.nms.spigot-mappings.files", "text", ""),
            new Option("plugins.chunk-tickets.max-per-plugin", "number", "16384"),
            new Option("plugins.compat.config.paper-default-separator", "ON/OFF", "false"),
            new Option("plugins.async-threads", "text", "platform"),
            new Option("plugins.compat.scheduler.async-threads", "text", "platform"),
            new Option("plugins.compat.libraries", "text", "needed"),
            new Option("plugins.compat.libraries.mojang", "ON/OFF", "true"),
            new Option("plugins.compat.libraries.declared", "ON/OFF", "true"),
            new Option("plugins.libraries.enabled", "ON/OFF", "false"),
            new Option("plugins.libraries.offline", "ON/OFF", "false"),
            new Option("plugins.libraries.transitive", "ON/OFF", "true"),
            new Option("plugins.libraries.require-checksum", "ON/OFF", "false"),
            new Option("plugins.libraries.cache", "path", ""),
            new Option("plugins.libraries.timeout-seconds", "number", "30"),
            new Option("plugins.libraries.max-size-mb", "number", "64"),
            // 설정 자체(O2)
            new Option(ConfigMigration.KEY, "number", Integer.toString(ConfigMigration.CURRENT)),
            new Option(ConfigPresets.KEY, "text", "none"),
            new Option(JvmCheck.KEY, "ON/OFF", "true"),
            new Option("console.jline", "ON/OFF", "true"),
            new Option("console.history-size", "number", "500"),
            new Option(org.domi.furnace.server.i18n.Lang.KEY, "text", "auto"));

    /** 접속 방어의 단계별 키는 단계 다섯 개마다 같은 모양이라 펼쳐서 만든다. */
    public static List<Option> all() {
        List<Option> out = new ArrayList<>(FIXED);
        String ear = EntityActivationSettings.PREFIX;
        out.add(new Option(ear + "enabled", "ON/OFF", "true"));
        for (String[] k : EntityActivationSettings.INT_KEYS) out.add(new Option(ear + k[0], "number", k[1]));
        for (String[] k : EntityActivationSettings.BOOL_KEYS) out.add(new Option(ear + k[0], "ON/OFF", k[1]));
        // 엔티티 손잡이(T-122) — 기본은 Paper 1.21.1 값. 밀기 상한·추적 거리는 바닐라와 다르다(B3-1 결정)
        out.add(new Option(EntityHandleSettings.ITEM_MERGE, "number", "0.5"));
        out.add(new Option(EntityHandleSettings.EXP_MERGE, "number", "-1"));
        out.add(new Option(EntityHandleSettings.MAX_COLLISIONS, "number", "8"));
        out.add(new Option(EntityHandleSettings.TRACKING + "enabled", "ON/OFF", "true"));
        for (String[] k : EntityHandleSettings.TRACKING_KEYS) {
            out.add(new Option(EntityHandleSettings.TRACKING + k[0], "number", k[1]));
        }
        out.add(new Option(EntityHandleSettings.ITEM_MERGE_INTO_TICKING, "ON/OFF", "true"));
        out.add(new Option(EntityHandleSettings.SKIP_UNPUSHABLE, "ON/OFF", "true"));
        out.add(new Option(EntityHandleSettings.PLAYER_CRAMMING, "ON/OFF", "false"));
        for (String s : STATES) {
            out.add(new Option("connection." + s + ".packets-per-second", "number", "200"));
            out.add(new Option("connection." + s + ".packets-burst", "number", "400"));
            out.add(new Option("connection." + s + ".max-frame-bytes", "number", s.equals("play") ? "2097152" : DEF_PER_STAGE));
            if (!s.equals("play")) {
                out.add(new Option("connection." + s + ".timeout-seconds", "number", s.equals("login") ? "30" : "0"));
            }
        }
        return List.copyOf(out);
    }

    /** server.properties 의 값이 이 키 대신 쓰이는 키(furnace.properties 에 써도 무시 — 따로 경고한다). */
    public static Set<String> vanillaKeys() { return Config.VANILLA_KEYS; }

    /** 알려진 키 전부(접두 가족 제외). */
    public static Set<String> keys() {
        Set<String> k = new LinkedHashSet<>();
        for (Option o : all()) k.add(o.key());
        return k;
    }

    public static boolean isKnown(String key) {
        if (Config.VANILLA_KEYS.contains(key)) return true;
        for (Option o : all()) if (o.key().equals(key)) return true;
        for (String p : PREFIXES) if (key.startsWith(p) && key.length() > p.length()) return true;
        return false;
    }

    /** 모르는 키와 가장 비슷한 키(편집 거리 3 이하), 없으면 null. */
    public static String suggest(String key) {
        String best = null;
        int bestD = 4;
        List<String> pool = new ArrayList<>(keys());
        pool.addAll(Config.VANILLA_KEYS);
        for (String p : PREFIXES) pool.add(p + "<name>");
        for (String cand : pool) {
            int d = distance(key.toLowerCase(Locale.ROOT), cand.toLowerCase(Locale.ROOT));
            if (d < bestD) {
                bestD = d;
                best = cand;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** 파일에 있는데 키 표에 없는 키(오타 후보), 정렬해서. */
    public static List<String> unknownKeys(Properties p) {
        return new TreeSet<>(p.stringPropertyNames()).stream().filter(k -> !isKnown(k)).toList();
    }

    /** furnace.properties 를 읽어 {@link #audit(Properties, Consumer)} 를 돌린다. 파일이 없으면 아무것도 하지 않는다. */
    public static void audit(Path file, Consumer<String> warn) {
        if (!Files.exists(file)) return;
        Properties p;
        try {
            p = FurnaceProps.read(file);
        } catch (RuntimeException e) {
            return;   // 읽지 못하는 것은 각 로더가 이미 알린다
        }
        audit(p, ConfigDump.lineNumbers(file), warn);
    }

    /**
     * 시작 때 한 번: 오타 키와 위험한 값을 경고한다(값 자체의 형식·범위 검사는 각 로더가 한다 — 여기는 형식이 맞는 값만 본다).
     * 동작은 바꾸지 않는다.
     */
    public static void audit(Properties p, Consumer<String> warn) {
        audit(p, java.util.Map.of(), warn);
    }

    /** lines: 키 → 파일 줄 번호(1부터). 있으면 오타 경고에 줄 번호를 붙인다. */
    public static void audit(Properties p, java.util.Map<String, Integer> lines, Consumer<String> warn) {
        for (String k : unknownKeys(p)) {
            String near = suggest(k);
            Integer line = lines.get(k);
            warn.accept(Lang.t(line == null ? "cfg.unknown-key" : "cfg.unknown-key-line", k, line)
                    + (near == null ? "" : " " + Lang.t("cfg.unknown-key-suggest", near)));
        }
        auditValues(p, warn);
    }

    /** 모르는 키를 뺀 위험한 값 경고(/furnace config 가 모르는 키를 따로 보여 주므로 따로 뗐다). */
    public static void auditValues(Properties p, Consumer<String> warn) {
        if (isTrue(p, "proxy-protocol") && p.getProperty("proxy-protocol.trusted", "").isBlank()) {
            warn.accept(Lang.t("cfg.proxy-trusted-empty"));
        }
        if (isTrue(p, "plugins.dev-mode")) {
            warn.accept(Lang.t("cfg.dev-mode"));
        }
        String tick = p.getProperty("tick-nanos");
        if (tick != null && !tick.strip().equals("50000000")) {
            warn.accept(Lang.t("cfg.tick-nanos", tick.strip()));
        }
        for (String s : STATES) {
            String k = "connection." + s + ".packets-per-second";
            if ("0".equals(p.getProperty(k, "").strip())) {
                warn.accept(Lang.t("cfg.rate-limit-off", k, s));
            }
        }
        if ("0".equals(p.getProperty("connection.login.timeout-seconds", "").strip())) {
            warn.accept(Lang.t("cfg.login-timeout-off"));
        }
    }

    private static boolean isTrue(Properties p, String key) {
        return "true".equalsIgnoreCase(p.getProperty(key, "").strip());
    }

    /** 엄격한 정수 읽기: 없으면 def, 숫자가 아니거나 min~max 밖이면 어느 키가 문제인지 알리며 거부한다. */
    public static int strictInt(Properties p, String key, int def, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        int n;
        try {
            n = Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(Lang.t("cfg.not-number", key, v), e);
        }
        if (n < min || n > max) throw new IllegalArgumentException(Lang.t("cfg.out-of-range", key, n, min, max));
        return n;
    }

    /** 엄격한 ON/OFF 읽기: 없으면 def, true/false 외의 글자는 어느 키가 문제인지 알리며 거부한다. */
    public static boolean strictBool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        String t = v.strip();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(Lang.t("cfg.invalid-bool", key, t));
    }
}
