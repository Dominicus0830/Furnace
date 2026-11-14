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
        IMPORTED("옮김 — furnace.properties 에 적었다"),
        KEPT("이미 furnace.properties 에 있어 그대로 둠"),
        DEFAULT("Paper 기본값 그대로라 옮기지 않음 — 같게 하려면 표시한 줄을 furnace.properties 에 넣는다"),
        MANUAL("직접 확인 — 비슷한 기능은 있지만 자동으로 옮기지 못함"),
        UNSUPPORTED("Furnace 에 대응이 없음"),
        IGNORED("무시 — 버전 표지 등 옮길 값이 아님"),
        UNKNOWN("Furnace 가 모르는 키");

        public final String title;

        Kind(String title) { this.title = title; }
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
            log.warn("Paper/Spigot 설정을 가져오지 못했다 — 건너뛴다(서버는 그대로 켠다). furnace.properties 를 손으로 맞춰라", e);
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
            extra.add("paper.yml 은 Paper 1.19 이전 형식이라 읽지 않았다. 새 Paper 로 한 번 켜면 config/paper-global.yml 로 바뀌니 그 파일을 두고 다시 가져와라(-D" + MODE_PROPERTY + "=force).");
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
        log.info("Paper/Spigot 설정 가져오기: 옮김 {} · 이미 있음 {} · Paper 기본값 {} · 직접 확인 {} · 대응 없음 {} · 모르는 키 {} — 자세한 내용: {}",
                r.count(Kind.IMPORTED), r.count(Kind.KEPT), r.count(Kind.DEFAULT), r.count(Kind.MANUAL),
                r.count(Kind.UNSUPPORTED), r.count(Kind.UNKNOWN), report.toAbsolutePath().normalize());
        for (Entry e : r.entries()) {
            if (e.kind == Kind.IMPORTED) log.info("  {} {} → {}", e.file, e.key, e.target);
        }
        int shown = 0;
        for (Entry e : r.entries()) {
            if (e.kind != Kind.MANUAL) continue;
            if (shown++ >= 8) {
                log.warn("  … 직접 확인할 항목이 더 있다 — 보고서를 봐라");
                break;
            }
            log.warn("  직접 확인: {} {} — {}", e.file, e.key, e.note);
        }
        for (String f : r.filesFailed().keySet()) log.warn("  {} 을(를) 읽지 못했다: {}", f, r.filesFailed().get(f));
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
                        ? pe.detail() + " (줄 " + pe.line() + ", 칸 " + pe.column() + ")"
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
                add(Kind.KEPT, file, key, value, furnaceKey, "furnace.properties 에 " + furnaceKey + " 가 이미 있다 — Paper 값은 쓰지 않는다");
            } else if (toWrite.containsKey(furnaceKey)) {
                add(Kind.KEPT, file, key, value, furnaceKey, "다른 Paper/Spigot 설정이 먼저 " + furnaceKey + " 를 옮겼다");
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
            String value = hit.size() == 1 ? fmt(hit.get(0), d.get(hit.get(0))) : hit.size() + "개";
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
                            "furnace.properties 에 bungeecord=true 가 이미 있다 — Velocity 와 함께 켜면 기동이 실패해 옮기지 않았다. 쓰려면 bungeecord 를 지우고 velocity-secret 을 넣어라");
                } else if (secret.isEmpty()) {
                    manual(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "velocity-secret",
                            "Velocity 를 켰는데 secret 이 비어 있다 — 프록시의 forwarding.secret 과 같은 값을 velocity-secret 에 넣어라");
                } else {
                    imp(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "velocity-secret", secret,
                            "Velocity 모던 포워딩(enabled=true). 비밀키는 보고서에 적지 않았다");
                }
            } else if (velEnabled != null && !secret.isEmpty()) {
                ignored(PAPER_GLOBAL, "proxies.velocity.secret", secretRaw, "proxies.velocity.enabled=false 라 옮기지 않았다");
            }

            if (Boolean.TRUE.equals(bungee)) {
                String curSecret = existing.getProperty("velocity-secret", "").strip();
                boolean velocityActive = !curSecret.isEmpty() || toWrite.containsKey("velocity-secret") || velocityWanted;
                if (velocityActive) {
                    manual(SPIGOT, "settings.bungeecord", true, "bungeecord",
                            "Velocity 와 BungeeCord 포워딩은 함께 켤 수 없다(켜면 기동 실패) — Velocity 를 남기고 bungeecord 는 옮기지 않았다");
                } else {
                    imp(SPIGOT, "settings.bungeecord", true, "bungeecord", "true",
                            "BungeeCord 레거시 포워딩. 프록시 없이 직접 열린 포트가 없도록 방화벽을 확인해라(서명이 없다)");
                }
            } else if (bungee != null) {
                keptDefault(SPIGOT, "settings.bungeecord", bungee, "bungeecord", "false", "꺼져 있다 — Furnace 기본(false)과 같다");
            }

            if (Boolean.TRUE.equals(proxyProtocol)) {
                imp(PAPER_GLOBAL, "proxies.proxy-protocol", true, "proxy-protocol", "true",
                        "PROXY 프로토콜(HAProxy 등). 앞단 주소만 믿게 proxy-protocol.trusted=<주소/대역> 을 같이 넣는 것을 권한다(비면 누구나 보낼 수 있다)");
            } else if (proxyProtocol != null) {
                keptDefault(PAPER_GLOBAL, "proxies.proxy-protocol", proxyProtocol, "proxy-protocol", "false", "꺼져 있다 — Furnace 기본과 같다");
            }

            if (bungeeOnline != null) {
                boolean behindProxy = toWrite.containsKey("bungeecord") || toWrite.containsKey("velocity-secret") || existingBungee
                        || !existing.getProperty("velocity-secret", "").isBlank();
                if (behindProxy) {
                    manual(PAPER_GLOBAL, "proxies.bungee-cord.online-mode", bungeeOnline, "server.properties online-mode",
                            "프록시 뒤에서는 server.properties 의 online-mode=false 여야 한다(프록시가 인증한다). Furnace 는 server.properties 를 그대로 읽는다");
                } else {
                    ignored(PAPER_GLOBAL, "proxies.bungee-cord.online-mode", bungeeOnline, "프록시(BungeeCord/Velocity)를 쓰지 않아 해당 없다");
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
                if (bv != null) ignored(BUKKIT, "ticks-per.autosave", bv, "paper-world-defaults.yml 의 값이 우선이라 쓰이지 않는다");
            } else if (b != null && b > 0) {
                key = "ticks-per.autosave"; file = BUKKIT; raw = bv; ticks = b;
                if (pv != null && p == null) ignored(PAPER_WORLD, "chunks.auto-save-interval", pv, "default — bukkit.yml 의 ticks-per.autosave 를 따른다");
            } else {
                if (pv != null) ignored(PAPER_WORLD, "chunks.auto-save-interval", pv, "숫자(틱)가 아니거나 0 이하라 옮기지 않았다");
                if (bv != null) ignored(BUKKIT, "ticks-per.autosave", bv, "숫자(틱)가 아니거나 0 이하라 옮기지 않았다");
                return;
            }
            if (ticks > Integer.MAX_VALUE) {
                ignored(file, key, raw, "너무 커서 옮기지 않았다");
            } else if (ticks == PAPER_DEFAULT_AUTOSAVE_TICKS) {
                keptDefault(file, key, raw, "autosave-ticks", "6000", "Paper 기본값(6000틱)과 같다 — Furnace 기본도 6000이다");
            } else {
                imp(file, key, raw, "autosave-ticks", Long.toString(ticks), "자동 저장 간격(틱)");
            }
        }

        private void chunkUnload() {
            Object raw = val(PAPER_WORLD, "chunks.delay-chunk-unloads-by");
            if (raw == null) return;
            Long ticks = durationTicks(raw);
            if (ticks == null || ticks < 1 || ticks > Integer.MAX_VALUE) {
                manual(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks",
                        "시간 표기를 읽지 못했다(숫자만 또는 10s·2m·1h·1d 만 읽는다) — 틱 수로 직접 넣어라(1초 = 20틱)");
            } else if (ticks == PAPER_DEFAULT_UNLOAD_DELAY_TICKS) {
                keptDefault(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks", "200",
                        "Paper 기본(10s)과 같다 — Furnace 는 기본 100틱(5초)을 쓴다. Paper 와 같게 하려면 이 줄을 넣어라");
            } else {
                imp(PAPER_WORLD, "chunks.delay-chunk-unloads-by", raw, "chunk-eviction-grace-ticks", Long.toString(ticks),
                        "청크를 내리기 전 유예(틱 = 시간 ÷ 50ms)");
            }
        }

        private void ioThreads() {
            Object raw = val(PAPER_GLOBAL, "chunk-system.io-threads");
            if (raw == null) return;
            Long n = asLong(raw);
            if (n == null) {
                manual(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", "숫자가 아니라 옮기지 않았다");
            } else if (n <= 0) {
                keptDefault(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", "4", "자동(-1) — Furnace 는 기본 4개를 쓴다");
            } else if (n > Integer.MAX_VALUE) {
                ignored(PAPER_GLOBAL, "chunk-system.io-threads", raw, "너무 커서 옮기지 않았다");
            } else {
                imp(PAPER_GLOBAL, "chunk-system.io-threads", raw, "io-threads", Long.toString(n), "청크 입출력 스레드 수");
            }
        }

        private static final int MAX_ITEM_LIMIT = 1_000_000;

        private void itemValidation() {
            itemLimit("item-validation.display-name", "limits.item.display-name", 8192, "아이템 이름 글자 수 상한");
            itemLimit("item-validation.loc-name", "limits.item.item-name", 8192, "가장 가까운 대응(item_name 컴포넌트)");
            itemLimit("item-validation.lore-line", "limits.item.lore-line", 8192, "설명 한 줄 글자 수 상한");
            itemLimit("item-validation.book.author", "limits.item.book-author", 8192, "책 저자 글자 수 상한");
            itemLimit("item-validation.book.page", "limits.item.book-page", 16384, "책 한 쪽 글자 수 상한");
        }

        private void itemLimit(String key, String furnaceKey, long paperDefault, String note) {
            Object raw = val(PAPER_GLOBAL, key);
            if (raw == null) return;
            Long n = asLong(raw);
            if (n == null) {
                manual(PAPER_GLOBAL, key, raw, furnaceKey, "숫자가 아니라 옮기지 않았다");
            } else if (n == paperDefault) {
                keptDefault(PAPER_GLOBAL, key, raw, furnaceKey, Long.toString(n),
                        "Paper 기본값과 같다 — Furnace 는 바닐라 그대로(상한 없음)가 기본이다. Paper 처럼 막으려면 이 줄을 넣어라");
            } else if (n <= 0 || n > MAX_ITEM_LIMIT) {
                ignored(PAPER_GLOBAL, key, raw, "Furnace 는 1~" + MAX_ITEM_LIMIT + " 만 받는다(0 = 끔) — 옮기지 않았다");
            } else {
                imp(PAPER_GLOBAL, key, raw, furnaceKey, Long.toString(n), note + " (바닐라 동작보다 조이는 값)");
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
                if (n == null || n != (int) (long) n) ignored(SPIGOT, base + k[0], v, "정수가 아니라 옮기지 않는다");
                else if (String.valueOf(n).equals(k[1])) keptDefault(SPIGOT, base + k[0], v, ear + k[0], k[1], "Furnace 기본과 같다");
                else imp(SPIGOT, base + k[0], v, ear + k[0], String.valueOf(n), "엔티티 활성 범위");
            }
            // 순수 Spigot 에는 주민 종류(villagers 키)가 없어 주민이 animals 범위를 쓴다. Paper 가 만든 파일은 villagers 를 늘 적어 두므로 위에서 그대로 옮겨진다.
            Long spigotAnimals = asLong(val(SPIGOT, base + "animals"));
            if (val(SPIGOT, base + "villagers") == null && spigotAnimals != null && spigotAnimals == (int) (long) spigotAnimals
                    && spigotAnimals != EntityActivationSettings.DEFAULT.animals()) {
                imp(SPIGOT, base + "animals", spigotAnimals, ear + "villagers", String.valueOf(spigotAnimals),
                        "villagers 키가 없는 Spigot 파일 — Spigot 은 주민을 animals 범위로 활성화한다");
            }
            for (String[] k : EntityActivationSettings.BOOL_KEYS) {
                Object v = val(SPIGOT, base + k[0]);
                if (v == null) continue;
                Boolean b = asBool(v);
                if (b == null) ignored(SPIGOT, base + k[0], v, "true/false 가 아니라 옮기지 않는다");
                else if (String.valueOf(b).equals(k[1])) keptDefault(SPIGOT, base + k[0], v, ear + k[0], k[1], "Furnace 기본과 같다");
                else imp(SPIGOT, base + k[0], v, ear + k[0], String.valueOf(b), "엔티티 활성 범위");
            }
            ConfigSection ws = docs.get(SPIGOT) == null ? null : docs.get(SPIGOT).getSection("world-settings");
            if (ws == null) return;
            for (String world : ws.keys(false)) {
                if (world.equals("default")) continue;
                group(Kind.MANUAL, SPIGOT, "world-settings." + world + ".entity-activation-range", ear + "*",
                        "Furnace 의 활성 범위는 서버 전체에 한 벌이다(월드별 값 없음)");
            }
        }

        private void manualAndUnsupported() {
            // ---- 직접 확인: 비슷한 기능이 있다
            Object ip = val(PAPER_GLOBAL, "logging.log-player-ip-addresses");
            if (ip != null) {
                Boolean b = asBool(ip);
                if (Boolean.FALSE.equals(b)) {
                    manual(PAPER_GLOBAL, "logging.log-player-ip-addresses", ip, "server.properties log-ips=false",
                            "접속 로그에서 IP 를 가리는 키는 바닐라 server.properties 의 log-ips 다(Furnace 도 읽는다)");
                } else {
                    keptDefault(PAPER_GLOBAL, "logging.log-player-ip-addresses", ip, "log-ips", "true", "IP 를 남긴다 — server.properties 기본(log-ips=true)과 같다");
                }
            }
            Object timeout = val(SPIGOT, "settings.timeout-time");
            if (timeout != null) {
                Long s = asLong(timeout);
                if (s != null && s == 60) {
                    keptDefault(SPIGOT, "settings.timeout-time", timeout, "max-tick-time(server.properties)", "60000", "Spigot 기본(60초)과 같다 — 바닐라 max-tick-time 기본도 60000ms 다");
                } else manual(SPIGOT, "settings.timeout-time", timeout, s == null ? "server.properties max-tick-time" : "server.properties max-tick-time=" + s * 1000,
                        "서버가 멈췄다고 보는 시간(워치독). 바닐라 키 max-tick-time(밀리초)이 같은 역할이다");
            }
            group(Kind.MANUAL, PAPER_GLOBAL, "watchdog", "server.properties max-tick-time",
                    "서버 멈춤 감시는 바닐라 max-tick-time 과 Furnace ServerWatchdog 가 한다. 경고 주기 키는 없다");
            Object sendRate = val(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate");
            if (sendRate != null) {
                Long r = asLong(sendRate);
                if (sendRate instanceof Number nn && nn.doubleValue() == 75.0) {
                    keptDefault(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate", sendRate, "chunks-per-tick", "16", "Paper 기본(75.0)과 같다 — Furnace 는 기본 chunks-per-tick=16 을 쓴다");
                } else {
                String hint = r != null && r > 0 ? "chunks-per-tick=" + Math.max(1, Math.round(r / 20.0)) : "chunks-per-tick";
                manual(PAPER_GLOBAL, "chunk-loading-basic.player-max-chunk-send-rate", sendRate, hint,
                        "Paper 는 초당, Furnace chunks-per-tick 은 틱당 청크 수(≈ 값 ÷ 20). 새 땅은 월드 생성 속도가 더 큰 제한이라 자동 변환하지 않는다");
                }
            }
            group(Kind.MANUAL, PAPER_GLOBAL, "packet-limiter", "connection.<단계>.packets-per-second / packets-burst",
                    "Furnace 는 접속 단계별(handshake·status·login·configuration·play) 패킷 속도·크기 한도를 가진다(connection-defenses-2026-11-06.md). 단위·판정이 달라 자동 변환하지 않는다");
            unsupported(PAPER_GLOBAL, "spam-limiter", "탭 완성·레시피 패킷 도배 제한 — Furnace 에는 대응이 없다(접속 단계별 패킷 속도 한도 connection.play.* 가 비슷한 방어를 한다)");
            group(Kind.MANUAL, BUKKIT, "chunk-gc", "chunk-eviction-grace-ticks", "청크 내리기는 유예 시간 하나로 조절한다(Bukkit 의 주기·기준 방식이 아님)");
            Object container = val(BUKKIT, "settings.world-container");
            if (container != null) {
                manual(BUKKIT, "settings.world-container", container, "명령줄 --universe <폴더>", "월드를 모아 두는 폴더는 명령줄 옵션으로 정한다");
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
                                    "Furnace 는 서버 전체에 하나뿐인 값을 쓴다(월드별 값 없음)");
                        } else if (v != null) {
                            ignored(SPIGOT, path, v, "default — server.properties 값을 따른다");
                        }
                    }
                }
            }

            // ---- 대응 없음 (구체적인 것 먼저, 뭉뚱그린 접두사 나중)
            unsupported(BUKKIT, "settings.allow-end", "엔드 차원을 끄는 설정이 없다");
            unsupported(BUKKIT, "settings.connection-throttle", "같은 주소 재접속 제한은 없다 — 접속 단계별 패킷 속도 한도(connection.login.*)가 비슷한 방어를 한다");
            unsupported(BUKKIT, "settings", "Bukkit 전용 동작 설정(종료 메시지·과부하 경고·플러그인 쿼리 목록·맵 색 캐시 등) — Furnace 에 대응이 없다");
            unsupported(BUKKIT, "spawn-limits", "자연 스폰 상한은 바닐라 값으로 고정이다(키 없음)");
            unsupported(BUKKIT, "ticks-per", "스폰 시도 간격은 바닐라 값으로 고정이다(키 없음)");
            unsupported(BUKKIT, "aliases", "명령 별칭 파일 기능이 없다");
            unsupported(BUKKIT, "database", "옛 설정 — 쓰이지 않는다");

            unsupported(SPIGOT, "settings.netty-threads", "네트워크 스레드 수는 Furnace 가 스스로 정한다");
            unsupported(SPIGOT, "settings.restart-on-crash", "충돌 시 재시작 스크립트는 지원하지 않는다(프로세스 관리자를 써라)");
            unsupported(SPIGOT, "settings.restart-script", "충돌 시 재시작 스크립트는 지원하지 않는다");
            unsupported(SPIGOT, "settings.player-shuffle", "플레이어 틱 순서 섞기는 결정성을 깨서 비목표다");
            unsupported(SPIGOT, "settings.attribute", "속성 상한 변경은 바닐라 결과를 바꿔 비목표다");
            unsupported(SPIGOT, "settings", "Spigot 전용 동작 설정(사용자 캐시 크기·이동 판정 임계 등) — Furnace 에 대응이 없다");
            unsupported(SPIGOT, "messages", "접속 거부·서버 가득 참 같은 안내 문구를 바꾸는 설정이 없다(바닐라 번역 키 그대로)");
            unsupported(SPIGOT, "commands", "명령 탭 완성·로그·네임스페이스 설정이 없다");
            unsupported(SPIGOT, "stats", "통계 저장 끄기는 비목표다");
            unsupported(SPIGOT, "advancements", "발전 과제 저장 끄기는 비목표다");
            unsupported(SPIGOT, "players", "플레이어 저장 끄기는 비목표다");
            unsupported(SPIGOT, "world-settings", "월드별 규칙(스폰 범위·성장 속도·병합 거리·추적 범위 등)은 아직 Furnace 에 키가 없다 — 바닐라 값으로 동작한다");

            unsupported(PAPER_GLOBAL, "chunk-system.worker-threads", "청크 작업 스레드는 Furnace 의 region 스케줄러(region-threads)와 모델이 달라 대응이 없다");
            unsupported(PAPER_GLOBAL, "chunk-system", "Paper 청크 시스템 내부 설정 — Furnace 와 구조가 달라 대응이 없다");
            unsupported(PAPER_GLOBAL, "chunk-loading-basic", "Paper 청크 전송 조절 — Furnace 는 chunks-per-tick 하나와 자체 속도 제한을 쓴다");
            unsupported(PAPER_GLOBAL, "chunk-loading-advanced", "Paper 청크 전송 조절 — Furnace 는 chunks-per-tick 하나와 자체 속도 제한을 쓴다");
            unsupported(PAPER_GLOBAL, "item-validation", "책 제목 길이·선택자 해석 등 — Furnace 의 item-validation 대응(display-name·lore·책 쪽·저자) 밖이다");
            unsupported(PAPER_GLOBAL, "unsupported-settings", "익스플로잇 허용 스위치는 바닐라와 결과가 달라져 비목표다");
            unsupported(PAPER_GLOBAL, "anticheat", "Paper 의 부정행위 방지 훅 — Furnace 에 대응이 없다");
            unsupported(PAPER_GLOBAL, "timings", "Paper timings 는 없다 — Furnace 는 /timings 와 플러그인별 프로파일러를 쓴다");
            unsupported(PAPER_GLOBAL, "messages", "Paper 안내 문구 설정이 없다");
            unsupported(PAPER_GLOBAL, "console", "콘솔 자동 완성·하이라이트 설정이 없다");
            unsupported(PAPER_GLOBAL, "commands", "명령 처리 설정이 없다");
            unsupported(PAPER_GLOBAL, "scoreboards", "스코어보드 설정이 없다(바닐라 규칙 그대로)");
            unsupported(PAPER_GLOBAL, "player-auto-save", "플레이어 자동 저장 주기·분산 설정이 없다");
            unsupported(PAPER_GLOBAL, "collisions", "충돌 설정이 없다(바닐라 maxEntityCramming 게임 규칙을 쓴다)");
            unsupported(PAPER_GLOBAL, "misc", "Paper 기타 설정(영역 파일 캐시·접속 수 제한 등) — 대응이 없다");
            unsupported(PAPER_GLOBAL, "logging", "Paper 로그 설정이 없다");
            unsupported(PAPER_GLOBAL, "proxies", "Paper 프록시 설정 중 옮길 수 있는 것은 위에서 처리했다 — 나머지는 대응이 없다");

            unsupported(PAPER_WORLD, "chunks.max-auto-save-chunks-per-tick", "틱당 저장 청크 수 키가 없다(저장은 Furnace 가 자체 조절한다)");
            unsupported(PAPER_WORLD, "chunks", "Paper 청크 보조 설정 — 대응이 없다");
            unsupported(PAPER_WORLD, "anti-xray", "클라이언트가 받는 블록이 달라져 바닐라와 결과가 어긋나므로 비목표다");
            unsupported(PAPER_WORLD, "unsupported-settings", "익스플로잇 허용 스위치는 바닐라와 결과가 달라져 비목표다");
            unsupported(PAPER_WORLD, "entities", "몹 스폰·행동·디스폰 규칙 — 아직 Furnace 에 키가 없다(바닐라 값으로 동작)");
            unsupported(PAPER_WORLD, "environment", "환경 규칙(폭발·눈·번개 등) — 아직 Furnace 에 키가 없다");
            unsupported(PAPER_WORLD, "collisions", "충돌 규칙 — 아직 Furnace 에 키가 없다");
            unsupported(PAPER_WORLD, "hopper", "호퍼 규칙 — 아직 Furnace 에 키가 없다");
            unsupported(PAPER_WORLD, "tick-rates", "틱 간격 조절 — 결과가 달라질 수 있어 아직 키가 없다");
            unsupported(PAPER_WORLD, "misc", "월드별 기타 규칙 — 아직 Furnace 에 키가 없다");
            unsupported(PAPER_WORLD, "fixes", "Paper 가 되돌린 바닐라 버그 수정 스위치 — Furnace 는 바닐라 동작 그대로다");
            unsupported(PAPER_WORLD, "feature-seeds", "구조물 시드 재정의는 비목표다(바닐라 시드 체계 유지)");
        }

        private void versionMarkers() {
            for (String f : docs.keySet()) {
                for (String k : new String[] {"_version", "config-version", "version"}) {
                    if (has(f, k) && !isCovered(f, k)) {
                        Object v = val(f, k);
                        if (v != null) ignored(f, k, v, "설정 파일 버전 표지");
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
                        add(Kind.UNKNOWN, file, ks.get(0), d.get(ks.get(0)), "", "Furnace 도 Paper 1.21.1 표에도 없는 키 — 이 버전 이후에 생긴 설정일 수 있다");
                    } else {
                        List<String> sample = ks.subList(0, Math.min(3, ks.size()));
                        add(Kind.UNKNOWN, file, g.getKey() + ".*", ks.size() + "개", "", "Furnace 도 Paper 1.21.1 표에도 없는 키들(예: " + String.join(", ", sample) + ")");
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
        if (v == null) return "(없음)";
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
        sb.append("# --- Paper/Spigot 설정에서 가져옴 (").append(Instant.now().toString(), 0, 19).append("Z, ")
                .append(String.join(" ", labels)).append(") ---\n");
        sb.append("# 어디서 무엇을 가져왔고 무엇을 못 옮겼는지: ").append(REPORT_FILE).append('\n');
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
        sb.append("Furnace — Paper/Spigot 설정 가져오기 보고서\n");
        sb.append("만든 때: ").append(Instant.now().toString(), 0, 19).append("Z\n");
        sb.append("읽은 파일: ").append(r.filesRead().isEmpty() ? "(없음)" : String.join(", ", r.filesRead())).append('\n');
        for (var f : r.filesFailed().entrySet()) sb.append("읽지 못한 파일: ").append(f.getKey()).append(" — ").append(f.getValue()).append('\n');
        for (String n : notes) sb.append("참고: ").append(n).append('\n');
        sb.append("""

                이 파일이 있으면 다시 가져오지 않는다. 다시 하려면 이 파일을 지우고 서버를 켜거나 -Dfurnace.importConfig=force 로 켠다
                (이미 furnace.properties 에 있는 키는 건드리지 않는다). 원본 YAML 은 읽기만 했고 바꾸지 않았다.
                Paper 쪽 키 이름과 기본값은 기억 기준이라 틀릴 수 있다 — 모르는 키는 아래 "모르는 키" 에 남는다.
                """);
        for (Kind k : Kind.values()) {
            List<Entry> list = r.entries().stream().filter(e -> e.kind == k).toList();
            if (list.isEmpty()) continue;
            sb.append("\n== ").append(k.title).append(" (").append(list.size()).append(") ==\n");
            for (Entry e : list) {
                sb.append("  ").append(e.file).append("  ").append(e.key).append(" = ").append(e.value);
                if (!e.target.isEmpty()) sb.append("  →  ").append(e.target);
                sb.append('\n');
                if (!e.note.isEmpty()) sb.append("      ").append(e.note).append('\n');
            }
        }
        if (r.entries().isEmpty()) sb.append("\n옮기거나 보고할 키가 없었다.\n");
        return sb.toString();
    }
}
