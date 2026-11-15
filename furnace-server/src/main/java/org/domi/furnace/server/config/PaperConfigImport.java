// Paper/Spigot 설정(bukkit.yml·spigot.yml·config/paper-global.yml·config/paper-world-defaults.yml) 가져오기.
// 서버 폴더에 그 파일이 있고 보고서(furnace-config-import-report.txt)가 아직 없으면 첫 기동 때 한 번 돌아
//  - Furnace 에 같은 뜻의 키가 있는 값은 furnace.properties 끝에 적고(이미 있는 키는 건드리지 않는다),
//  - 못 옮긴 키는 이유와 함께 보고서에 남긴다. 원본 YAML 은 읽기만 한다.
// 기본 원칙: Paper 기본값과 같은 값은 옮기지 않는다 — Furnace 기본은 바닐라이고, 직접 바꾼 값만 뜻이 있다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.api.config.ConfigSection;
import org.domi.furnace.server.i18n.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperConfigImport {

    private static final Logger log = LoggerFactory.getLogger(PaperConfigImport.class);

    /** 보고서 파일. 이 파일이 있으면 다시 가져오지 않는다(지우면 다음 기동 때 다시 한다). */
    public static final String REPORT_FILE = "furnace-config-import-report.txt";
    /** -Dfurnace.importConfig=auto(기본: 보고서가 없을 때만)|force(보고서가 있어도 다시)|off(끔). */
    public static final String MODE_PROPERTY = "furnace.importConfig";

    static final String BUKKIT = "bukkit.yml";
    static final String SPIGOT = "spigot.yml";
    static final String PAPER_GLOBAL = "paper-global.yml";
    static final String PAPER_WORLD = "paper-world-defaults.yml";

    /** 서버 폴더 기준 상대 경로. 라벨은 보고서에 보이는 이름이다. */
    private static final Map<String, String> FILES = Map.of(
            BUKKIT, "bukkit.yml",
            SPIGOT, "spigot.yml",
            PAPER_GLOBAL, "config/paper-global.yml",
            PAPER_WORLD, "config/paper-world-defaults.yml");
    private static final List<String> ORDER = List.of(BUKKIT, SPIGOT, PAPER_GLOBAL, PAPER_WORLD);
    /** 1.19 이전 Paper 의 단일 설정. 형식이 달라 읽지 않고 안내만 한다. */
    private static final String LEGACY_PAPER = "paper.yml";

    // Paper 1.21.1 의 기본값(기억 기준 — docs/feature-parity/paper-config-import-2026-11-07.md 에 미확인으로 적었다).
    // 틀려도 결과는 "기본값으로 보고 안 옮김"이 "옮김"으로 바뀔 뿐이라 서버가 깨지지 않는다.
    static final long PAPER_DEFAULT_AUTOSAVE_TICKS = 6000;
    static final long PAPER_DEFAULT_UNLOAD_DELAY_TICKS = 200;   // 10s

    public enum Kind {
        IMPORTED("paperimport.kind-imported"),
        KEPT("paperimport.kind-kept"),
        DEFAULT("paperimport.kind-default"),
        MANUAL("paperimport.kind-manual"),
        UNSUPPORTED("paperimport.kind-unsupported"),
        IGNORED("paperimport.kind-ignored"),
        UNKNOWN("paperimport.kind-unknown");

        private final String titleKey;

        Kind(String titleKey) { this.titleKey = titleKey; }

        /** 보고서 구역 제목(서버 언어). */
        public String title() { return Lang.t(titleKey); }
    }

    /** value 는 원본 값(비밀은 가림), target 은 Furnace 쪽 키=값(또는 안내), note 는 이유. */
    public record Entry(Kind kind, String file, String key, String value, String target, String note) {}

    public record Result(List<String> filesRead, Map<String, String> filesFailed, List<Entry> entries, Map<String, String> toWrite) {

        public long count(Kind k) { return entries.stream().filter(e -> e.kind == k).count(); }
    }

    private PaperConfigImport() {}

    // ------------------------------------------------------------------ 기동 진입점

    /** 기동 때 한 번 부른다. 실패해도 서버 기동을 막지 않는다(경고만). */
    public static void runIfNeeded(Path serverDir, Path furnaceProps) {
        String mode = System.getProperty(MODE_PROPERTY, "auto").strip().toLowerCase(Locale.ROOT);
        if (mode.equals("off")) return;
        Path report = serverDir.resolve(REPORT_FILE);
        boolean force = mode.equals("force");
        if (!force && Files.exists(report)) return;
        try {
            Result r = analyzeAndApply(serverDir, furnaceProps, report);
            if (r != null) logSummary(r, report);
        } catch (IOException | RuntimeException e) {
            log.warn(Lang.t("paperimport.note-08"), e);
        }
    }

    /** 파일이 없으면 null(아무것도 쓰지 않는다). */
    static Result analyzeAndApply(Path serverDir, Path furnaceProps, Path report) throws IOException {
        Map<String, Path> found = detect(serverDir);
        if (found.isEmpty() && !Files.exists(serverDir.resolve(LEGACY_PAPER))) return null;
        Properties existing = new Properties();
        if (Files.isRegularFile(furnaceProps)) {
            try (InputStream in = Files.newInputStream(furnaceProps)) {
                existing.load(in);
            }
        }
        Result r = analyze(found, existing);
        List<String> extra = new ArrayList<>();
        if (Files.exists(serverDir.resolve(LEGACY_PAPER))) {
            extra.add(Lang.t("paperimport.legacy-paper-yml", MODE_PROPERTY));
        }
        // furnace.properties 먼저, 보고서는 나중 — 쓰기에 실패하면 보고서가 없어 다음 기동 때 다시 시도한다.
        if (!r.toWrite().isEmpty()) appendProperties(furnaceProps, r, found.keySet());
        Files.writeString(report, render(r, extra), StandardCharsets.UTF_8);
        return r;
    }

    /** 서버 폴더에서 읽을 수 있는 파일(라벨 → 경로). */
    static Map<String, Path> detect(Path serverDir) {
        Map<String, Path> out = new LinkedHashMap<>();
        for (String label : ORDER) {
            Path p = serverDir.resolve(FILES.get(label));
            if (Files.isRegularFile(p)) out.put(label, p);
        }
        return out;
    }

    private static void logSummary(Result r, Path report) {
        log.info(Lang.t("paperimport.summary", r.count(Kind.IMPORTED), r.count(Kind.KEPT), r.count(Kind.DEFAULT), r.count(Kind.MANUAL),
                r.count(Kind.UNSUPPORTED), r.count(Kind.UNKNOWN), report.toAbsolutePath().normalize()));
        for (Entry e : r.entries()) {
            if (e.kind == Kind.IMPORTED) log.info("  {} {} → {}", e.file, e.key, e.target);
        }
        int shown = 0;
        for (Entry e : r.entries()) {
            if (e.kind != Kind.MANUAL) continue;
            if (shown++ >= 8) {
                log.warn("  {}", Lang.t("paperimport.more-manual"));
                break;
            }
            log.warn("  {}", Lang.t("paperimport.manual-line", e.file, e.key, e.note));
        }
        for (String f : r.filesFailed().keySet()) log.warn("  {}", Lang.t("paperimport.file-unreadable", f, r.filesFailed().get(f)));
    }

    // ------------------------------------------------------------------ 분석 (파일 쓰기 없음)

    /** 읽을 파일들(라벨 → 경로)과 지금의 furnace.properties 내용으로 무엇을 옮길지 정한다. */
    static Result analyze(Map<String, Path> files, Properties existing) {
        Analyzer a = new Analyzer(existing);
        for (var e : files.entrySet()) a.load(e.getKey(), e.getValue());
        a.rules();
        return a.result();
    }

    private static final class Analyzer {
        final Properties existing;
        final Map<String, ConfigSection> docs = new LinkedHashMap<>();
        final List<String> filesRead = new ArrayList<>();
        final Map<String, String> filesFailed = new LinkedHashMap<>();
        final List<Entry> entries = new ArrayList<>();
        final Map<String, String> toWrite = new LinkedHashMap<>();
        final Map<String, Set<String>> covered = new LinkedHashMap<>();

        Analyzer(Properties existing) { this.existing = existing; }

        void load(String label, Path path) {
            try {
                docs.put(label, org.domi.furnace.api.config.Config.load(path).root());
                filesRead.add(label);
            } catch (IOException | RuntimeException e) {
                // 문법 오류 메시지는 문제 줄 원문을 담는데 그 줄이 비밀키일 수 있다 — 줄·칸 번호와 설명만 남긴다.
                filesFailed.put(label, e instanceof org.domi.furnace.api.config.ConfigParseException pe
                        ? Lang.t("paperimport.yaml-position", pe.detail(), pe.line(), pe.column())
                        : e.getClass().getSimpleName());
            }
        }

        Result result() { return new Result(filesRead, filesFailed, entries, toWrite); }

        // ---- 값 읽기 도우미

        boolean has(String file, String key) {
            ConfigSection d = docs.get(file);
            return d != null && d.contains(key, true);
        }

        /** 값을 읽고 "처리했다"고 표시한다(없으면 null). 섹션이면 null. */
        Object val(String file, String key) {
            ConfigSection d = docs.get(file);
            if (d == null || !d.contains(key, true)) return null;
            covered.computeIfAbsent(file, f -> new LinkedHashSet<>()).add(key);
            Object v = d.get(key);
            return v instanceof ConfigSection ? null : v;
        }

        static Long asLong(Object v) {
            if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) return ((Number) v).longValue();
            if ((v instanceof Double || v instanceof Float) && ((Number) v).doubleValue() == Math.rint(((Number) v).doubleValue())
                    && Math.abs(((Number) v).doubleValue()) < 9e18) return ((Number) v).longValue();
            if (v instanceof String s) {
                try {
                    return Long.parseLong(s.strip());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }

        static Boolean asBool(Object v) {
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) {
                if (s.strip().equalsIgnoreCase("true")) return true;
                if (s.strip().equalsIgnoreCase("false")) return false;
            }
            return null;
        }

        // ---- 항목 기록

        void add(Kind kind, String file, String key, Object value, String target, String note) {
            entries.add(new Entry(kind, file, key, fmt(key, value), target, note));
        }

        /** furnaceKey=furnaceValue 를 옮긴다. 이미 furnace.properties 나 앞선 규칙에 있으면 건드리지 않는다. */
        void imp(String file, String key, Object value, String furnaceKey, String furnaceValue, String note) {
            String line = furnaceKey + "=" + (isSecret(furnaceKey) ? "****" : furnaceValue);
            if (existing.containsKey(furnaceKey)) {
                add(Kind.KEPT, file, key, value, furnaceKey, Lang.t("paperimport.kept-existing", furnaceKey));
            } else if (toWrite.containsKey(furnaceKey)) {
                add(Kind.KEPT, file, key, value, furnaceKey, Lang.t("paperimport.kept-earlier", furnaceKey));
            } else {
                toWrite.put(furnaceKey, furnaceValue);
                add(Kind.IMPORTED, file, key, value, line, note);
            }
        }

        void keptDefault(String file, String key, Object value, String furnaceKey, String furnaceValue, String note) {
            add(Kind.DEFAULT, file, key, value, furnaceKey + "=" + furnaceValue, note);
        }

        void manual(String file, String key, Object value, String target, String note) {
            add(Kind.MANUAL, file, key, value, target, note);
        }

        void ignored(String file, String key, Object value, String note) {
            add(Kind.IGNORED, file, key, value, "", note);
        }

        /** 이 접두사 아래의 아직 처리 안 된 키를 한 줄로 묶어 기록한다(없으면 아무것도 안 한다). */
        void group(Kind kind, String file, String prefix, String target, String note) {
            ConfigSection d = docs.get(file);
            if (d == null) return;
            List<String> hit = new ArrayList<>();
            for (String k : leaves(d)) {
                if ((k.equals(prefix) || k.startsWith(prefix + ".")) && !isCovered(file, k)) hit.add(k);
            }
            if (hit.isEmpty()) return;
            for (String k : hit) covered.computeIfAbsent(file, f -> new LinkedHashSet<>()).add(k);
            String key = hit.size() == 1 ? hit.get(0) : prefix + ".*";
            String value = hit.size() == 1 ? fmt(hit.get(0), d.get(hit.get(0))) : Lang.t("paperimport.count-items", hit.size());
            entries.add(new Entry(kind, file, key, value, target, note));
        }

        void unsupported(String file, String prefix, String note) { group(Kind.UNSUPPORTED, file, prefix, "", note); }

        boolean isCovered(String file, String key) {
            Set<String> c = covered.get(file);
            return c != null && c.contains(key);
        }

        static List<String> leaves(ConfigSection d) {
            List<String> out = new ArrayList<>();
            for (String k : d.keys(true)) {
                Object v = d.get(k);
                if (!(v instanceof ConfigSection s) || s.isEmpty()) out.add(k);
            }
            return out;
        }

        // ---- 규칙

        void rules() {
            proxies();
            autosave();
            chunkUnload();
            ioThreads();
            itemValidation();
            activationRange();
            entityHandles();
            manualAndUnsupported();
            versionMarkers();
            unknown();
        }

        private void proxies() {
            Boolean bungee = asBool(val(SPIGOT, "settings.bungeecord"));
            Boolean velEnabled = asBool(val(PAPER_GLOBAL, "proxies.velocity.enabled"));
            Object secretRaw = val(PAPER_GLOBAL, "proxies.velocity.secret");
            String secret = secretRaw == null ? "" : String.valueOf(secretRaw).strip();
            val(PAPER_GLOBAL, "proxies.velocity.online-mode");
            Boolean proxyProtocol = asBool(val(PAPER_GLOBAL, "proxies.proxy-protocol"));
            Object bungeeOnline = val(PAPER_GLOBAL, "proxies.bungee-cord.online-mode");

            boolean velocityWanted = Boolean.TRUE.equals(velEnabled) && !secret.isEmpty();
            boolean existingBungee = "true".equalsIgnoreCase(existing.getProperty("bungeecord", "false").strip());
            if (Boolean.TRUE.equals(velEnabled)) {
                if (!secret.isEmpty() && existingBungee) {
                    manual(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "velocity-secret",
                            Lang.t("paperimport.velocity-secret"));
                } else if (secret.isEmpty()) {
                    manual(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "velocity-secret",
                            Lang.t("paperimport.velocity-secret-2"));
                } else {
                    imp(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "velocity-secret", secret,
                            Lang.t("paperimport.velocity-secret-3"));
                }
            } else if (velEnabled != null && !secret.isEmpty()) {
                ignored(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, Lang.t("paperimport.proxies-velocity-secret"));
            }

            if (Boolean.TRUE.equals(bungee)) {
                String curSecret = existing.getProperty("velocity-secret", "").strip();
                boolean velocityActive = !curSecret.isEmpty() || toWrite.containsKey("velocity-secret") || velocityWanted;
                if (velocityActive) {
                    manual(SPIGOT, "settings.bungeecord", true, "bungeecord",
                            Lang.t("paperimport.bungeecord"));
                } else {
                    imp(SPIGOT, "settings.bungeecord", true, "bungeecord", "true",
                            Lang.t("paperimport.bungeecord-2"));
                }
            } else if (bungee != null) {
                keptDefault(SPIGOT, "settings.bungeecord", bungee, "bungeecord", "false", Lang.t("paperimport.bungeecord-3"));
            }

            if (Boolean.TRUE.equals(proxyProtocol)) {
                imp(PAPER_GLOBAL, "proxies.proxy-protocol", true, "proxy-protocol", "true",
                        Lang.t("paperimport.proxy-protocol"));
            } else if (proxyProtocol != null) {
                keptDefault(PAPER_GLOBAL, "proxies.proxy-protocol", proxyProtocol, "proxy-protocol", "false", Lang.t("paperimport.proxy-protocol-2"));
            }

            if (bungeeOnline != null) {
                boolean behindProxy = toWrite.containsKey("bungeecord") || toWrite.containsKey("velocity-secret") || existingBungee
                        || !existing.getProperty("velocity-secret", "").isBlank();
                if (behindProxy) {
                    manual(PAPER_GLOBAL, "proxies.bungee-cord.online-mode", bungeeOnline, "server.properties online-mode",
                            Lang.t("paperimport.server-properties-online-mode"));
                } else {
                    ignored(PAPER_GLOBAL, "proxies.bungee-cord.online-mode", bungeeOnline, Lang.t("paperimport.proxies-bungee-cord-online-mode"));
                }
            }
        }

        private void autosave() {
            Object pv = val(PAPER_WORLD, "chunks.auto-save-interval");
            Object bv = val(BUKKIT, "ticks-per.autosave");
            Long p = asLong(pv);
            Long b = asLong(bv);
            String key;
            String file;
            Object raw;
            long ticks;
            if (p != null && p > 0) {
                key = "chunks.auto-save-interval"; file = PAPER_WORLD; raw = pv; ticks = p;
                if (bv != null) ignored(BUKKIT, "ticks-per.autosave", bv, Lang.t("paperimport.ticks-per-autosave"));
            } else if (b != null && b > 0) {
                key = "ticks-per.autosave"; file = BUKKIT; raw = bv; ticks = b;
                if (pv != null && p == null) ignored(PAPER_WORLD, "chunks.auto-save-interval", pv, Lang.t("paperimport.chunks-auto-save-interval"));
            } else {
                if (pv != null) ignored(PAPER_WORLD, "chunks.auto-save-interval", pv, Lang.t("paperimport.chunks-auto-save-interval-2"));
                if (bv != null) ignored(BUKKIT, "ticks-per.autosave", bv, Lang.t("paperimport.ticks-per-autosave-2"));
                return;
            }
            if (ticks > Integer.MAX_VALUE) {
                ignored(file, key, raw, Lang.t("paperimport.note-13"));
            } else if (ticks == PAPER_DEFAULT_AUTOSAVE_TICKS) {
                keptDefault(file, key, raw, "autosave-ticks", "6000", Lang.t("paperimport.autosave-ticks"));
            } else {
                imp(file, key, raw, "autosave-ticks", Long.toString(ticks), Lang.t("paperimport.autosave-ticks-2"));
            }
        }

        private void chunkUnload() {
            Object raw = val(PAPER_WORLD, "chunks.delay-chunk-unloads-by");
            if (raw == null) return;
            Long ticks = durationTicks(raw);
            if (ticks == null || ticks < 1 || ticks > Integer.MAX_VALUE) {
                manual(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks",
                        Lang.t("paperimport.chunk-eviction-grace-ticks"));
            } else if (ticks == PAPER_DEFAULT_UNLOAD_DELAY_TICKS) {
                keptDefault(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks", "200",
                        Lang.t("paperimport.chunk-eviction-grace-ticks-2"));
            } else {
                imp(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks", Long.toString(ticks),
                        Lang.t("paperimport.chunk-eviction-grace-ticks-3"));
            }
        }

        private void ioThreads() {
            Object raw = val(PAPER_GLOBAL, "chunk-system.io-threads");
            if (raw == null) return;
            Long n = asLong(raw);
            if (n == null) {
                manual(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", Lang.t("paperimport.io-threads"));
            } else if (n <= 0) {
                keptDefault(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", "4", Lang.t("paperimport.io-threads-2"));
            } else if (n > Integer.MAX_VALUE) {
                ignored(PAPER_GLOBAL, "chunk-system.io-threads", raw, Lang.t("paperimport.chunk-system-io-threads"));
            } else {
                imp(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", Long.toString(n), Lang.t("paperimport.io-threads-3"));
            }
        }

        private static final int MAX_ITEM_LIMIT = 1_000_000;

        private void itemValidation() {
            itemLimit("item-validation.display-name", "limits.item.display-name", 8192, Lang.t("paperimport.limits-item-display-name"));
            itemLimit("item-validation.loc-name", "limits.item.item-name", 8192, Lang.t("paperimport.limits-item-item-name"));
            itemLimit("item-validation.lore-line", "limits.item.lore-line", 8192, Lang.t("paperimport.limits-item-lore-line"));
            itemLimit("item-validation.book.author", "limits.item.book-author", 8192, Lang.t("paperimport.limits-item-book-author"));
            itemLimit("item-validation.book.page", "limits.item.book-page", 16384, Lang.t("paperimport.limits-item-book-page"));
        }

        private void itemLimit(String key, String furnaceKey, long paperDefault, String note) {
            Object raw = val(PAPER_GLOBAL, key);
            if (raw == null) return;
            Long n = asLong(raw);
            if (n == null) {
                manual(PAPER_GLOBAL, key, raw, furnaceKey, Lang.t("paperimport.note-14"));
            } else if (n == paperDefault) {
                keptDefault(PAPER_GLOBAL, key, raw, furnaceKey, Long.toString(n),
                        Lang.t("paperimport.note-15"));
            } else if (n <= 0 || n > MAX_ITEM_LIMIT) {
                ignored(PAPER_GLOBAL, key, raw, Lang.t("paperimport.item-limit-range", MAX_ITEM_LIMIT));
            } else {
                imp(PAPER_GLOBAL, key, raw, furnaceKey, Long.toString(n), note + " " + Lang.t("paperimport.tighter-than-vanilla"));
            }
        }

        /**
         * spigot.yml world-settings.default.entity-activation-range.* → furnace.properties 같은 이름(D11). 기본과 같은 값은 옮기지 않는다.
         * 다른 월드의 값은 Furnace 에 월드별 키가 없어 수동 안내만.
         */
        private void activationRange() {
            String base = "world-settings.default.entity-activation-range.";
            String ear = EntityActivationSettings.PREFIX;
            for (String[] k : EntityActivationSettings.INT_KEYS) {
                Object v = val(SPIGOT, base + k[0]);
                if (v == null) continue;
                Long n = asLong(v);
                if (n == null || n != (int) (long) n) ignored(SPIGOT, base + k[0], v, Lang.t("paperimport.note-16"));
                else if (String.valueOf(n).equals(k[1])) keptDefault(SPIGOT, base + k[0], v, ear + k[0], k[1], Lang.t("paperimport.note-17"));
                else imp(SPIGOT, base + k[0], v, ear + k[0], String.valueOf(n), Lang.t("paperimport.note-18"));
            }
            // 순수 Spigot 에는 주민·물 동물·나는 적대 몹 종류(villagers·water·flying-monsters 키)가 없다: 주민·물 동물은 animals 범위(PathfinderMob),
            // 가스트·팬텀은 misc 범위(Monster 도 PathfinderMob 도 아님)를 쓴다. Paper 가 만든 파일은 세 키를 늘 적어 두므로 위에서 그대로 옮겨진다.
            Long animals = asLong(val(SPIGOT, base + "animals")), misc = asLong(val(SPIGOT, base + "misc"));
            if (animals != null || misc != null || val(SPIGOT, base + "monsters") != null) {   // Spigot 활성 범위 절이 있는 파일만
                long a = animals != null ? animals : 32, m = misc != null ? misc : 16;   // Spigot 기본
                spigotOnly(base, "villagers", "animals", a, EntityActivationSettings.DEFAULT.villagers(), Lang.t("paperimport.animals"));
                spigotOnly(base, "water", "animals", a, EntityActivationSettings.DEFAULT.water(), Lang.t("paperimport.animals-2"));
                spigotOnly(base, "flying-monsters", "misc", m, EntityActivationSettings.DEFAULT.flyingMonsters(), Lang.t("paperimport.misc"));
            }
            for (String[] k : EntityActivationSettings.BOOL_KEYS) {
                Object v = val(SPIGOT, base + k[0]);
                if (v == null) continue;
                Boolean b = asBool(v);
                if (b == null) ignored(SPIGOT, base + k[0], v, Lang.t("paperimport.note-19"));
                else if (String.valueOf(b).equals(k[1])) keptDefault(SPIGOT, base + k[0], v, ear + k[0], k[1], Lang.t("paperimport.note-20"));
                else imp(SPIGOT, base + k[0], v, ear + k[0], String.valueOf(b), Lang.t("paperimport.note-21"));
            }
            ConfigSection ws = docs.get(SPIGOT) == null ? null : docs.get(SPIGOT).getSection("world-settings");
            if (ws == null) return;
            for (String world : ws.keys(false)) {
                if (world.equals("default")) continue;
                group(Kind.MANUAL, SPIGOT, "world-settings." + world + ".entity-activation-range", ear + "*",
                        Lang.t("paperimport.entity-activation-range"));
            }
        }

        /**
         * 엔티티 손잡이(T-122): spigot.yml merge-radius.*·entity-tracking-range.*, paper-world-defaults.yml collisions.max-entity-collisions·allow-player-cramming-damage
         * (없으면 옛 spigot.yml max-entity-collisions — Paper MaxEntityCollisionsInitializer 와 같은 순서) → furnace.properties 같은 이름.
         * Furnace 기본(= Paper 1.21.1 기본)과 같은 값은 옮기지 않는다. 다른 월드의 값은 월드별 키가 없어 수동 안내만.
         */
        private void entityHandles() {
            String base = "world-settings.default.";
            for (String k : new String[]{EntityHandleSettings.ITEM_MERGE, EntityHandleSettings.EXP_MERGE}) {
                Object v = val(SPIGOT, base + k);
                if (v == null) continue;
                Double d = asDouble(v);
                double def = k.equals(EntityHandleSettings.ITEM_MERGE) ? EntityHandleSettings.DEFAULT.itemMerge() : EntityHandleSettings.DEFAULT.expMerge();
                String defText = k.equals(EntityHandleSettings.ITEM_MERGE) ? "0.5" : "-1";
                if (d == null || (k.equals(EntityHandleSettings.ITEM_MERGE) && d < 0)) ignored(SPIGOT, base + k, v, Lang.t("paperimport.note-22"));
                else if (d == def || (k.equals(EntityHandleSettings.EXP_MERGE) && d <= 0)) keptDefault(SPIGOT, base + k, v, k, defText, Lang.t("paperimport.note-23"));
                else imp(SPIGOT, base + k, v, k, fmtNum(d), Lang.t("paperimport.note-24"));
            }
            String col = EntityHandleSettings.MAX_COLLISIONS;
            Object pv = val(PAPER_WORLD, col);
            Object sv = val(SPIGOT, base + "max-entity-collisions");
            String file = pv != null ? PAPER_WORLD : SPIGOT;
            String key = pv != null ? col : base + "max-entity-collisions";
            Object v = pv != null ? pv : sv;
            if (pv == null && sv != null && asLong(sv) != null && asLong(sv) < 0) {
                ignored(SPIGOT, key, sv, Lang.t("paperimport.note-25"));
            } else if (pv != null && sv != null) {
                ignored(SPIGOT, base + "max-entity-collisions", sv, Lang.t("paperimport.world-defaults-wins", col));
            }
            if (v != null && !(pv == null && sv != null && asLong(sv) != null && asLong(sv) < 0)) {
                Long n = asLong(v);
                if (n == null || n != (int) (long) n) ignored(file, key, v, Lang.t("paperimport.note-26"));
                else if (n == EntityHandleSettings.DEFAULT.maxEntityCollisions()) keptDefault(file, key, v, col, String.valueOf(n), Lang.t("paperimport.note-27"));
                else if (n <= 0) imp(file, key, v, col, "0", Lang.t("paperimport.note-28"));
                else imp(file, key, v, col, String.valueOf(n), Lang.t("paperimport.note-29"));
            }
            Object cv = val(PAPER_WORLD, EntityHandleSettings.PLAYER_CRAMMING);
            if (cv != null) {
                Boolean b = asBool(cv);
                if (b == null) ignored(PAPER_WORLD, EntityHandleSettings.PLAYER_CRAMMING, cv, Lang.t("paperimport.note-30"));
                else if (!b) keptDefault(PAPER_WORLD, EntityHandleSettings.PLAYER_CRAMMING, cv, EntityHandleSettings.PLAYER_CRAMMING, "false", Lang.t("paperimport.note-31"));
                else imp(PAPER_WORLD, EntityHandleSettings.PLAYER_CRAMMING, cv, EntityHandleSettings.PLAYER_CRAMMING, "true", Lang.t("paperimport.note-32"));
            }
            for (String[] k : EntityHandleSettings.TRACKING_KEYS) {
                String sk = base + "entity-tracking-range." + k[0];
                Object tv = val(SPIGOT, sk);
                if (tv == null) continue;
                Long n = asLong(tv);
                if (n == null || n != (int) (long) n || n < 0) ignored(SPIGOT, sk, tv, Lang.t("paperimport.note-33"));
                else if (String.valueOf(n).equals(k[1])) keptDefault(SPIGOT, sk, tv, EntityHandleSettings.TRACKING + k[0], k[1], Lang.t("paperimport.note-34"));
                else imp(SPIGOT, sk, tv, EntityHandleSettings.TRACKING + k[0], String.valueOf(n), Lang.t("paperimport.note-35"));
            }
            ConfigSection ws = docs.get(SPIGOT) == null ? null : docs.get(SPIGOT).getSection("world-settings");
            if (ws == null) return;
            for (String world : ws.keys(false)) {
                if (world.equals("default")) continue;
                for (String g : new String[]{"merge-radius", "entity-tracking-range", "max-entity-collisions"}) {
                    group(Kind.MANUAL, SPIGOT, "world-settings." + world + "." + g, "merge-radius.*·entity-tracking-range.*·" + col,
                            Lang.t("paperimport.merge-radius-entity-tracking-range"));
                }
            }
        }

        static Double asDouble(Object v) {
            if (v instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
            if (v instanceof String str) {
                try {
                    double d = Double.parseDouble(str.strip());
                    return Double.isFinite(d) ? d : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }

        /** 2.5 → "2.5", 3.0 → "3" (furnace.properties 에 읽기 쉽게). */
        static String fmtNum(double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }

        /** 순수 Spigot 파일에 없는 Paper 종류 키를 Spigot 이 실제로 쓰는 범위(from 키 값)로 적는다 — Furnace 기본과 같으면 적지 않는다. */
        private void spigotOnly(String base, String key, String from, long value, int furnaceDefault, String why) {
            if (val(SPIGOT, base + key) != null || value != (int) value || value == furnaceDefault) return;
            imp(SPIGOT, base + from, value, EntityActivationSettings.PREFIX + key, String.valueOf(value),
                    Lang.t("paperimport.spigot-missing-key", key, why));
        }

        private void manualAndUnsupported() {
            // ---- 직접 확인: 비슷한 기능이 있다
            Object ip = val(PAPER_GLOBAL, "logging.log-player-ip-addresses");
            if (ip != null) {
                Boolean b = asBool(ip);
                if (Boolean.FALSE.equals(b)) {
                    manual(PAPER_GLOBAL, "logging.log-player-ip-addresses", ip, "server.properties log-ips=false",
                            Lang.t("paperimport.server-properties-log-ips-false"));
                } else {
                    keptDefault(PAPER_GLOBAL, "logging.log-player-ip-addresses", ip, "log-ips", "true", Lang.t("paperimport.log-ips"));
                }
            }
            Object timeout = val(SPIGOT, "settings.timeout-time");
            if (timeout != null) {
                Long s = asLong(timeout);
                if (s != null && s == 60) {
                    keptDefault(SPIGOT, "settings.timeout-time", timeout, "max-tick-time(server.properties)", "60000", Lang.t("paperimport.max-tick-time-server-properties"));
                } else manual(SPIGOT, "settings.timeout-time", timeout, s == null ? "server.properties max-tick-time" : "server.properties max-tick-time=" + s * 1000,
                        Lang.t("paperimport.server-properties-max-tick-time"));
            }
            group(Kind.MANUAL, PAPER_GLOBAL, "watchdog", "server.properties max-tick-time",
                    Lang.t("paperimport.server-properties-max-tick-time-2"));
            Object sendRate = val(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate");
            if (sendRate != null) {
                Long r = asLong(sendRate);
                if (sendRate instanceof Number nn && nn.doubleValue() == 75.0) {
                    keptDefault(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate", sendRate, "chunks-per-tick", "16", Lang.t("paperimport.chunks-per-tick"));
                } else {
                String hint = r != null && r > 0 ? "chunks-per-tick=" + Math.max(1, Math.round(r / 20.0)) : "chunks-per-tick";
                manual(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate", sendRate, hint,
                        Lang.t("paperimport.chunk-loading-basic-player-max-chunk-send-rate"));
                }
            }
            group(Kind.MANUAL, PAPER_GLOBAL, "packet-limiter", Lang.t("paperimport.packet-limiter"),
                    Lang.t("paperimport.packet-limiter-2"));
            unsupported(PAPER_GLOBAL, "spam-limiter", Lang.t("paperimport.spam-limiter"));
            group(Kind.MANUAL, BUKKIT, "chunk-gc", "chunk-eviction-grace-ticks", Lang.t("paperimport.chunk-eviction-grace-ticks-4"));
            Object container = val(BUKKIT, "settings.world-container");
            if (container != null) {
                manual(BUKKIT, "settings.world-container", container, Lang.t("paperimport.settings-world-container"), Lang.t("paperimport.settings-world-container-2"));
            }
            // spigot world-settings 의 시야 거리: 월드별 값은 지원하지 않는다
            ConfigSection ws = docs.get(SPIGOT) == null ? null : docs.get(SPIGOT).getSection("world-settings");
            if (ws != null) {
                for (String world : ws.keys(false)) {
                    for (String k : new String[] {"view-distance", "simulation-distance"}) {
                        String path = "world-settings." + world + "." + k;
                        Object v = val(SPIGOT, path);
                        if (v != null && asLong(v) != null) {
                            manual(SPIGOT, path, v, "server.properties " + k + "=" + asLong(v),
                                    Lang.t("paperimport.server-properties"));
                        } else if (v != null) {
                            ignored(SPIGOT, path, v, Lang.t("paperimport.note-36"));
                        }
                    }
                }
            }

            // ---- 대응 없음 (구체적인 것 먼저, 뭉뚱그린 접두사 나중)
            unsupported(BUKKIT, "settings.allow-end", Lang.t("paperimport.settings-allow-end"));
            unsupported(BUKKIT, "settings.connection-throttle", Lang.t("paperimport.settings-connection-throttle"));
            unsupported(BUKKIT, "settings", Lang.t("paperimport.settings"));
            unsupported(BUKKIT, "spawn-limits", Lang.t("paperimport.spawn-limits"));
            unsupported(BUKKIT, "ticks-per", Lang.t("paperimport.ticks-per"));
            unsupported(BUKKIT, "aliases", Lang.t("paperimport.aliases"));
            unsupported(BUKKIT, "database", Lang.t("paperimport.database"));

            unsupported(SPIGOT, "settings.netty-threads", Lang.t("paperimport.settings-netty-threads"));
            unsupported(SPIGOT, "settings.restart-on-crash", Lang.t("paperimport.settings-restart-on-crash"));
            unsupported(SPIGOT, "settings.restart-script", Lang.t("paperimport.settings-restart-script"));
            unsupported(SPIGOT, "settings.player-shuffle", Lang.t("paperimport.settings-player-shuffle"));
            unsupported(SPIGOT, "settings.attribute", Lang.t("paperimport.settings-attribute"));
            unsupported(SPIGOT, "settings", Lang.t("paperimport.settings-2"));
            unsupported(SPIGOT, "messages", Lang.t("paperimport.messages"));
            unsupported(SPIGOT, "commands", Lang.t("paperimport.commands"));
            unsupported(SPIGOT, "stats", Lang.t("paperimport.stats"));
            unsupported(SPIGOT, "advancements", Lang.t("paperimport.advancements"));
            unsupported(SPIGOT, "players", Lang.t("paperimport.players"));
            unsupported(SPIGOT, "world-settings", Lang.t("paperimport.world-settings"));

            unsupported(PAPER_GLOBAL, "chunk-system.worker-threads", Lang.t("paperimport.chunk-system-worker-threads"));
            unsupported(PAPER_GLOBAL, "chunk-system", Lang.t("paperimport.chunk-system"));
            unsupported(PAPER_GLOBAL, "chunk-loading-basic", Lang.t("paperimport.chunk-loading-basic"));
            unsupported(PAPER_GLOBAL, "chunk-loading-advanced", Lang.t("paperimport.chunk-loading-advanced"));
            unsupported(PAPER_GLOBAL, "item-validation", Lang.t("paperimport.item-validation"));
            unsupported(PAPER_GLOBAL, "unsupported-settings", Lang.t("paperimport.unsupported-settings"));
            unsupported(PAPER_GLOBAL, "anticheat", Lang.t("paperimport.anticheat"));
            unsupported(PAPER_GLOBAL, "timings", Lang.t("paperimport.timings"));
            unsupported(PAPER_GLOBAL, "messages", Lang.t("paperimport.messages-2"));
            unsupported(PAPER_GLOBAL, "console", Lang.t("paperimport.console"));
            unsupported(PAPER_GLOBAL, "commands", Lang.t("paperimport.commands-2"));
            unsupported(PAPER_GLOBAL, "scoreboards", Lang.t("paperimport.scoreboards"));
            unsupported(PAPER_GLOBAL, "player-auto-save", Lang.t("paperimport.player-auto-save"));
            unsupported(PAPER_GLOBAL, "collisions", Lang.t("paperimport.collisions"));
            unsupported(PAPER_GLOBAL, "misc", Lang.t("paperimport.misc-2"));
            unsupported(PAPER_GLOBAL, "logging", Lang.t("paperimport.logging"));
            unsupported(PAPER_GLOBAL, "proxies", Lang.t("paperimport.proxies"));

            unsupported(PAPER_WORLD, "chunks.max-auto-save-chunks-per-tick", Lang.t("paperimport.chunks-max-auto-save-chunks-per-tick"));
            unsupported(PAPER_WORLD, "chunks", Lang.t("paperimport.chunks"));
            unsupported(PAPER_WORLD, "anti-xray", Lang.t("paperimport.anti-xray"));
            unsupported(PAPER_WORLD, "unsupported-settings", Lang.t("paperimport.unsupported-settings-2"));
            unsupported(PAPER_WORLD, "entities", Lang.t("paperimport.entities"));
            unsupported(PAPER_WORLD, "environment", Lang.t("paperimport.environment"));
            unsupported(PAPER_WORLD, "collisions", Lang.t("paperimport.collisions-2"));
            unsupported(PAPER_WORLD, "hopper", Lang.t("paperimport.hopper"));
            unsupported(PAPER_WORLD, "tick-rates", Lang.t("paperimport.tick-rates"));
            unsupported(PAPER_WORLD, "misc", Lang.t("paperimport.misc-3"));
            unsupported(PAPER_WORLD, "fixes", Lang.t("paperimport.fixes"));
            unsupported(PAPER_WORLD, "feature-seeds", Lang.t("paperimport.feature-seeds"));
        }

        private void versionMarkers() {
            for (String f : docs.keySet()) {
                for (String k : new String[] {"_version", "config-version", "version"}) {
                    if (has(f, k) && !isCovered(f, k)) {
                        Object v = val(f, k);
                        if (v != null) ignored(f, k, v, Lang.t("paperimport.note-37"));
                    }
                }
            }
        }

        private void unknown() {
            for (var e : docs.entrySet()) {
                String file = e.getKey();
                ConfigSection d = e.getValue();
                Map<String, List<String>> byTop = new LinkedHashMap<>();
                for (String k : leaves(d)) {
                    if (isCovered(file, k)) continue;
                    String top = k.contains(".") ? k.substring(0, k.indexOf('.')) : k;
                    byTop.computeIfAbsent(top, t -> new ArrayList<>()).add(k);
                }
                for (var g : byTop.entrySet()) {
                    List<String> ks = g.getValue();
                    if (ks.size() == 1) {
                        add(Kind.UNKNOWN, file, ks.get(0), d.get(ks.get(0)), "", Lang.t("paperimport.note-38"));
                    } else {
                        List<String> sample = ks.subList(0, Math.min(3, ks.size()));
                        add(Kind.UNKNOWN, file, g.getKey() + ".*", Lang.t("paperimport.count-items", ks.size()), "", Lang.t("paperimport.unknown-keys-sample", String.join(", ", sample)));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 값 도우미

    private static final Pattern DURATION = Pattern.compile("(\\d{1,12})\\s*([smhd]?)");

    /**
     * Paper 의 시간 표기 중 해석이 확실한 것만 읽는다: 숫자만(초) 또는 숫자 + s·m·h·d 하나("10s", "2m"). 틱(올림)으로.
     * "500ms"·"1m 30s" 같은 복합·밀리초 표기는 Paper 가 다르게 읽는다고 알려져 있어(확실하지 않음) 읽지 않고 null 을 돌려준다.
     */
    static Long durationTicks(Object raw) {
        String t;
        if (raw instanceof Integer || raw instanceof Long) t = String.valueOf(raw);
        else if (raw instanceof String s) t = s.strip().toLowerCase(Locale.ROOT);
        else return null;
        Matcher m = DURATION.matcher(t);
        if (!m.matches()) return null;
        long n = Long.parseLong(m.group(1));
        long sec = switch (m.group(2)) {
            case "m" -> n * 60;
            case "h" -> n * 3600;
            case "d" -> n * 86400;
            default -> n;
        };
        return Math.ceilDiv(sec * 1000, 50);
    }

    private static boolean isSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("secret") || k.contains("password") || k.contains("token");
    }

    private static String fmt(String key, Object v) {
        if (v == null) return Lang.t("paperimport.note-39");
        if (isSecret(key)) return "****";
        String s = v instanceof ConfigSection ? "{…}" : String.valueOf(v);
        s = s.replace('\n', ' ');
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }

    // ------------------------------------------------------------------ 쓰기

    /** furnace.properties 끝에 가져온 값을 덧붙인다(원본 바이트는 그대로, 임시 파일 후 이동). */
    private static void appendProperties(Path furnaceProps, Result r, Set<String> labels) throws IOException {
        byte[] old = Files.isRegularFile(furnaceProps) ? Files.readAllBytes(furnaceProps) : new byte[0];
        StringBuilder sb = new StringBuilder();
        if (old.length > 0 && old[old.length - 1] != '\n') sb.append('\n');
        if (old.length > 0) sb.append('\n');
        // 새로 만드는 파일이면 지금 형식 버전을 먼저 적는다 — 안 적으면 바로 다음 마이그레이션이 형식 1 로 보고 돌아 가져온 값을 기본값 바꾸기로 뒤집을 수 있다
        if (old.length == 0) sb.append(ConfigMigration.KEY).append('=').append(ConfigMigration.CURRENT).append('\n');
        sb.append(Lang.t("paperimport.header", Instant.now().toString().substring(0, 19) + "Z", String.join(" ", labels))).append('\n');
        sb.append(Lang.t("paperimport.header-report", REPORT_FILE)).append('\n');
        for (var e : r.toWrite().entrySet()) {
            sb.append(escapeKey(e.getKey())).append('=').append(escapeValue(e.getValue())).append('\n');
        }
        // 끝에 덧붙이기만 한다 — 파일 권한·심볼릭 링크·단일 파일 바인드 마운트를 그대로 둔다(임시 파일로 바꾸면 사라진다).
        Files.write(furnaceProps, sb.toString().getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    // Properties.load 는 ISO-8859-1 로 읽으므로 ASCII 밖은 \\uXXXX 로 쓴다.
    static String escapeValue(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '=', ':', '#', '!' -> sb.append('\\').append(c);
                case ' ' -> sb.append(i == 0 ? "\\ " : " ");
                default -> {
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    static String escapeKey(String k) { return k.replace(" ", "\\ ").replace("=", "\\=").replace(":", "\\:"); }

    // ------------------------------------------------------------------ 보고서

    static String render(Result r, List<String> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("paperimport.report-title")).append('\n');
        sb.append(Lang.t("paperimport.report-created", Instant.now().toString().substring(0, 19) + "Z")).append('\n');
        sb.append(Lang.t("paperimport.report-read", r.filesRead().isEmpty() ? Lang.t("paperimport.note-39") : String.join(", ", r.filesRead()))).append('\n');
        for (var f : r.filesFailed().entrySet()) sb.append(Lang.t("paperimport.report-failed", f.getKey(), f.getValue())).append('\n');
        for (String n : notes) sb.append(Lang.t("paperimport.report-note", n)).append('\n');
        sb.append('\n').append(Lang.t("paperimport.report-intro")).append('\n');
        for (Kind k : Kind.values()) {
            List<Entry> list = r.entries().stream().filter(e -> e.kind == k).toList();
            if (list.isEmpty()) continue;
            sb.append("\n== ").append(k.title()).append(" (").append(list.size()).append(") ==\n");
            for (Entry e : list) {
                sb.append("  ").append(e.file).append("  ").append(e.key).append(" = ").append(e.value);
                if (!e.target.isEmpty()) sb.append("  →  ").append(e.target);
                sb.append('\n');
                if (!e.note.isEmpty()) sb.append("      ").append(e.note).append('\n');
            }
        }
        if (r.entries().isEmpty()) sb.append('\n').append(Lang.t("paperimport.report-empty")).append('\n');
        return sb.toString();
    }
}
