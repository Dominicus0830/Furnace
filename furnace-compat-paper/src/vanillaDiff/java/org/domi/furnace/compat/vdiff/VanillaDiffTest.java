package org.domi.furnace.compat.vdiff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.domi.furnace.compat.vdiff.DiffRunner.MemberResult;
import org.domi.furnace.compat.vdiff.DiffRunner.Status;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * T-235 차분 시험: 이름 푼 바닐라 jar(캐시)와 shim 에 같은 입력을 넣어 결과를 견준다. 손으로 쓰는 시험이 없다 —
 * 대상 파일(vanilla-diff-targets.txt)의 클래스마다 공개 멤버를 모두 훑고, 입력은 {@link TypeKit} 이 형식별로 만든다.
 * 보고서: {@code build/reports/vanilla-diff/shim-proof-report.md}(증명 비율, 차이, 증명 없음 목록).
 *
 * <pre>
 *   ./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest                       전부
 *   ./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest -PvdiffClasses=net.minecraft.util.Mth   일부(쉼표)
 *   -PvdiffIterations=200000   멤버마다 입력 수(기본 20000)
 * </pre>
 */
class VanillaDiffTest {

    record Target(String cls, int iterations, long budgetSeconds, Map<String, String> excludes, Map<String, PaperLine> paper) {}

    /** paper 줄: 허용하는 차이 모양(바닐라 예외 ↔ shim 예외 쌍)과 근거. */
    record PaperLine(String reason, List<DiffRunner.Allowance> allowed) {}

    record ClassReport(String cls, boolean gate, int iterations, long millis, String error, List<MemberResult> members) {}

    private static final List<ClassReport> REPORTS = java.util.Collections.synchronizedList(new ArrayList<>());
    private static DiffRunner runner;
    private static long seed;
    /** shim 의 레지스트리·블록·아이템은 Furnace 서버 위에서 산다(BuiltInRegistries 가 서버 데이터를 읽음) — 시험 서버 하나를 띄운다. */
    private static org.domi.furnace.test.FurnaceTestServer server;

    /** 클래스마다 시험 하나 — 기본 제한시간(5분)은 공장 메서드에만 걸리므로 무거운 일은 각 동적 시험 안에서 한다(멤버마다 120초 감시는 DiffRunner). */
    @TestFactory
    Stream<DynamicTest> vanillaMatchesShim() throws IOException {
        VanillaRuntime rt = VanillaRuntime.get();
        Integer forced = Integer.getInteger("furnace.vdiff.iterations");
        seed = Long.getLong("furnace.vdiff.seed", 0x7235L);
        Set<String> only = parseList(System.getProperty("furnace.vdiff.classes", ""));
        List<Target> targets = readTargets();
        for (String o : only) {
            if (targets.stream().noneMatch(t -> t.cls.equals(o))) fail("-PvdiffClasses names a class not in vanilla-diff-targets.txt: " + o);
        }
        server = org.domi.furnace.test.FurnaceTestServer.builder().start();
        runner = new DiffRunner(rt.loader(), VanillaDiffTest.class.getClassLoader(), seed);
        List<DynamicTest> tests = new ArrayList<>();
        for (Target t : targets) {
            if (!only.isEmpty() && !only.contains(t.cls)) continue;
            int n = forced != null ? forced : t.iterations;
            tests.add(DynamicTest.dynamicTest(t.cls, () -> check(record(runClass(rt, t.cls, null, t.excludes, t.paper, true, n, budgetOf(t))))));
        }
        if (only.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : specTargets().entrySet()) {
                if (targets.stream().anyMatch(t -> t.cls.equals(e.getKey()))) continue;
                int n = forced != null ? forced : DEFAULT_ITERATIONS;
                tests.add(DynamicTest.dynamicTest("[spec] " + e.getKey(),
                        () -> check(record(runClass(rt, e.getKey(), e.getValue(), Map.of(), Map.of(), false, n, 0)))));
            }
        }
        assertFalse(tests.isEmpty(), "no class was compared");
        return tests.stream();
    }

    static final int DEFAULT_ITERATIONS = 20_000;

    /** 대상 파일 budget=<초>, 켜진 클래스만 -PvdiffBudget 으로 바꿀 수 있다. */
    private static long budgetOf(Target t) {
        if (t.budgetSeconds <= 0) return 0;
        return Long.getLong("furnace.vdiff.memberBudgetSeconds", t.budgetSeconds);
    }

    private static ClassReport record(ClassReport r) {
        REPORTS.add(r);
        return r;
    }

    @org.junit.jupiter.api.AfterAll
    static void finish() throws IOException {
        if (runner != null) runner.close();
        if (server != null) server.close();
        NbtKit.cleanTemp();
        writeReport(new ArrayList<>(REPORTS), VanillaRuntime.get().mc(), seed);
    }

    /**
     * 관문 클래스: 다름·오류·이유 없는 증명 없음이 실패. 연결 명세 줄: 다름·오류는 실패, 증명 없음만 보고(할 일 목록) —
     * 꾸러미에 형식이 들어와 증명되는 순간부터 틀린 읽기 연결이 초록으로 지나가지 않게.
     */
    private static void check(ClassReport r) {
        List<String> bad = new ArrayList<>();
        if (r.error != null) bad.add("ERROR " + r.error);
        for (MemberResult m : r.members) {
            if (m.status() == Status.DIFF) bad.add("DIFF " + m.key() + " " + (m.detail() == null ? "" : m.detail()) + " " + m.examples());
            if (r.gate && m.status() == Status.UNPROVED) bad.add("UNPROVED " + m.key() + " (" + m.detail() + ") - add a TypeKit type or an exclude with a reason");
        }
        if (!bad.isEmpty()) fail(r.cls + ":\n  " + String.join("\n  ", bad));
    }

    private static ClassReport runClass(VanillaRuntime rt, String cls, Set<String> onlyNames,
                                        Map<String, String> excludes, Map<String, PaperLine> paper, boolean gate, int iterations, long budgetSeconds) {
        long t0 = System.nanoTime();
        Class<?> vc, sc;
        try {
            vc = Class.forName(cls, true, rt.loader());
        } catch (Throwable e) {
            return new ClassReport(cls, gate, iterations, 0, "vanilla class not loadable: " + e, List.of());
        }
        try {
            sc = Class.forName(cls, true, VanillaDiffTest.class.getClassLoader());
        } catch (Throwable e) {
            return new ClassReport(cls, gate, iterations, 0, "shim class not loadable: " + e, List.of());
        }
        if (sc.getClassLoader() == rt.loader()) return new ClassReport(cls, gate, iterations, 0, "shim resolved to vanilla loader", List.of());
        String origin = shimOrigin(sc);
        if (origin != null) return new ClassReport(cls, gate, iterations, 0, origin, List.of());
        List<MemberResult> out = new ArrayList<>();
        Set<String> unusedExcludes = new LinkedHashSet<>(excludes.keySet());
        Set<String> unusedPaper = new LinkedHashSet<>(paper.keySet());
        for (Object m : DiffRunner.members(vc)) {
            String key = DiffRunner.memberKey(m);
            if (onlyNames != null) {
                String name = key.contains("(") ? key.substring(0, key.indexOf('(')) : key;
                if (!onlyNames.contains(name)) continue;
            }
            if (excludes.containsKey(key)) {
                unusedExcludes.remove(key);
                out.add(new MemberResult(key, Status.EXCLUDED, 0, 0, excludes.get(key), List.of()));
                continue;
            }
            PaperLine pl = paper.get(key);
            MemberResult res = runner.run(vc, sc, m, iterations, pl == null ? List.of() : pl.allowed(), budgetSeconds);
            // 허용한 모양의 차이가 실제로 나왔을 때만 줄이 쓰인 것 — 안 나오면 낡은 줄(오류). 허용 밖 차이는 그대로 다름
            if (pl != null && (res.status() == Status.PAPER || (res.status() == Status.DIFF && res.detail() != null && res.detail().contains("allowed Paper-shaped")))) {
                unusedPaper.remove(key);
            }
            if (res.status() == Status.PAPER) {
                res = new MemberResult(key, Status.PAPER, res.calls(), res.skipped(), pl.reason() + " / " + res.detail(), res.examples(), res.stoppedAt(), res.planned());
            }
            out.add(res);
        }
        List<String> errors = new ArrayList<>();
        if (!unusedExcludes.isEmpty()) errors.add("exclude names no vanilla member: " + unusedExcludes);
        if (!unusedPaper.isEmpty()) errors.add("paper line names a member that does not differ (remove it): " + unusedPaper);
        String error = errors.isEmpty() ? null : String.join("; ", errors);
        return new ClassReport(cls, gate, iterations, (System.nanoTime() - t0) / 1_000_000, error, out);
    }

    // ---- 대상 파일 ----

    static List<Target> readTargets() throws IOException {
        List<Target> out = new ArrayList<>();
        try (InputStream in = VanillaDiffTest.class.getResourceAsStream("/vanilla-diff-targets.txt")) {
            if (in == null) throw new IllegalStateException("vanilla-diff-targets.txt missing");
            Target cur = null;
            int no = 0;
            for (String raw : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                no++;
                String reason = raw.contains("#") ? raw.substring(raw.indexOf('#') + 1).trim() : "";
                String line = (raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw).strip();
                if (line.isEmpty()) continue;
                if (line.startsWith("class ")) {
                    String[] parts = line.substring(6).strip().split("\\s+");
                    int n = DEFAULT_ITERATIONS;
                    long budget = 0;
                    for (int k = 1; k < parts.length; k++) {
                        if (parts[k].startsWith("iterations=")) n = Integer.parseInt(parts[k].substring("iterations=".length()));
                        else if (parts[k].startsWith("budget=")) budget = Long.parseLong(parts[k].substring("budget=".length()));
                        else throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": unknown option " + parts[k]);
                    }
                    cur = new Target(parts[0], n, budget, new LinkedHashMap<>(), new LinkedHashMap<>());
                    out.add(cur);
                } else if (line.startsWith("exclude ") && cur != null) {
                    if (reason.isEmpty()) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": exclude needs '# reason'");
                    cur.excludes.put(line.substring(8).strip(), reason);
                } else if (line.startsWith("paper ") && cur != null) {
                    if (reason.isEmpty()) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": paper needs '# reason'");
                    cur.paper.put(paperKey(line, no), new PaperLine(reason, allowances(line, no)));
                } else {
                    throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": cannot parse: " + raw);
                }
            }
        }
        return out;
    }

    /** shim 클래스가 호환 계층 main 출력 폴더에서 왔는지(바닐라 대 바닐라를 견주는 사고 방지). 맞으면 null. */
    private static String shimOrigin(Class<?> sc) {
        String dirs = System.getProperty("furnace.vdiff.shimDirs");
        if (dirs == null) return "-Dfurnace.vdiff.shimDirs missing";
        try {
            Path at = Path.of(sc.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            for (String d : dirs.split(java.io.File.pathSeparator)) {
                if (!d.isBlank() && at.startsWith(Path.of(d).toAbsolutePath().normalize())) return null;
            }
            return "shim class not from compat main output: " + at;
        } catch (Exception e) {
            return "shim class origin unknown: " + e;
        }
    }

    /** "paper <열쇠> [when=<조건>] vanilla=<예외> shim=<예외> [vanilla=… shim=…]…" 의 열쇠. */
    private static String paperKey(String line, int no) {
        String[] parts = line.substring(6).strip().split("\\s+");
        return parts[0];
    }

    private static List<DiffRunner.Allowance> allowances(String line, int no) {
        List<String> parts = new ArrayList<>(List.of(line.substring(6).strip().split("\\s+")));
        parts.remove(0);
        // when=<조건> 은 줄의 모든 쌍에 걸린다(어디에 적어도 된다)
        String when = null;
        for (var it = parts.iterator(); it.hasNext(); ) {
            String p = it.next();
            if (!p.startsWith("when=")) continue;
            if (when != null) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": only one when= per paper line");
            when = p.substring(5);
            if (!DiffRunner.CONDITIONS.contains(when)) {
                throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": unknown when=" + when + " (known: " + DiffRunner.CONDITIONS + ")");
            }
            it.remove();
        }
        List<DiffRunner.Allowance> out = new ArrayList<>();
        if (parts.size() < 2 || parts.size() % 2 != 0) {
            throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": paper needs 'vanilla=<exception> shim=<exception>' pairs");
        }
        for (int k = 0; k < parts.size(); k += 2) {
            if (!parts.get(k).startsWith("vanilla=") || !parts.get(k + 1).startsWith("shim=")) {
                throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": expected vanilla=… shim=… at " + parts.get(k));
            }
            out.add(new DiffRunner.Allowance(parts.get(k).substring(8), parts.get(k + 1).substring(5), when));
        }
        return out;
    }

    /** 연결 명세의 pure·read=any 메서드 줄 → 클래스별 멤버 이름. */
    static Map<String, Set<String>> specTargets() throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        String dir = System.getProperty("furnace.vdiff.specDir");
        if (dir == null) return out;
        Pattern member = Pattern.compile("^\\s+([A-Za-z_$][\\w$]*)(?:\\([^)]*\\))?\\s*->.*\\s(pure|read=any)(\\s|$)");
        List<Path> specs;
        try (Stream<Path> s = Files.list(Path.of(dir))) {
            specs = s.filter(p -> p.toString().endsWith(".spec")).sorted().toList();
        }
        for (Path p : specs) {
            String cls = null;
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
                if (line.startsWith("class ")) {
                    cls = line.substring(6).trim().split("\\s+")[0];
                    continue;
                }
                Matcher m = member.matcher(line);
                if (cls != null && m.find()) out.computeIfAbsent(cls, k -> new LinkedHashSet<>()).add(m.group(1));
            }
        }
        return out;
    }

    private static Set<String> parseList(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String x : s.split(",")) if (!x.isBlank()) out.add(x.strip());
        return out;
    }

    // ---- 보고서 ----

    private static void writeReport(List<ClassReport> reports, String mc, long seed) throws IOException {
        reports.sort(java.util.Comparator.comparing((ClassReport r) -> !r.gate).thenComparing(r -> r.cls));
        long ms = reports.stream().mapToLong(ClassReport::millis).sum();
        String path = System.getProperty("furnace.vdiff.report");
        if (path == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# shim 증명 보고서 — 바닐라 차분 시험 (T-235)\n\n");
        sb.append("판 ").append(mc).append(", 멤버마다 입력 수는 클래스 제목 옆, 시드 0x").append(Long.toHexString(seed))
                .append(", 걸린 시간 ").append(ms / 1000.0).append("초. 바닐라 jar 는 캐시에서 읽었다(커밋되지 않음).\n\n");
        long[] gateCounts = new long[Status.values().length];
        long[] specCounts = new long[Status.values().length];
        long calls = 0;
        for (ClassReport r : reports) {
            for (MemberResult m : r.members) {
                (r.gate ? gateCounts : specCounts)[m.status().ordinal()]++;
                calls += m.calls();
            }
        }
        // 시간 예산으로 멈춘 멤버(대상 파일 budget= 클래스만) — CI 요약(앞 12줄)에 들도록 표 앞에 적는다
        long stopped = 0, ran = 0, planned = 0;
        for (ClassReport r : reports) {
            for (MemberResult m : r.members) {
                if (m.stoppedAt() < 0) continue;
                stopped++;
                ran += m.stoppedAt();
                planned += m.planned();
            }
        }
        sb.append("예산으로 멈춘 멤버: ").append(stopped).append("개")
                .append(stopped == 0 ? "" : " (돌린 입력 " + ran + " / 계획 " + planned + " — 경계값 입력은 다 돌았다)").append("\n\n");
        sb.append("| 묶음 | 같음 | 다름 | 증명 없음 | 뺌 | Paper 와 같게 | shim 에 없음 | 증명 비율 |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        row(sb, "값 클래스(관문)", gateCounts);
        row(sb, "연결 명세 pure·read=any(다름은 실패, 증명 없음은 보고)", specCounts);
        sb.append("\n호출 수 합계(양쪽 한 쌍을 1로): ").append(calls).append("\n\n");
        sb.append("증명 비율 = 같음 / (같음 + 다름 + 증명 없음). 뺌·Paper 와 같게·shim 에 없음은 분모에 넣지 않는다.\n\n");
        for (ClassReport r : reports) {
            sb.append("## ").append(r.gate ? "" : "[명세] ").append(r.cls).append(" (입력 ").append(r.iterations).append(", ").append(r.millis / 1000.0).append("초)\n\n");
            if (r.error != null) sb.append("**오류**: ").append(r.error).append("\n\n");
            Map<Status, List<MemberResult>> by = new LinkedHashMap<>();
            for (Status s : Status.values()) by.put(s, new ArrayList<>());
            for (MemberResult m : r.members) by.get(m.status()).add(m);
            for (MemberResult m : by.get(Status.DIFF)) {
                sb.append("- **다름** `").append(m.key()).append("` ").append(m.detail() == null ? "" : m.detail()).append('\n');
                for (String e : m.examples()) sb.append("  - ").append(e.replace('\n', ' ')).append('\n');
            }
            for (MemberResult m : by.get(Status.UNPROVED)) sb.append("- 증명 없음 `").append(m.key()).append("` — ").append(m.detail()).append('\n');
            for (MemberResult m : by.get(Status.EXCLUDED)) sb.append("- 뺌 `").append(m.key()).append("` — ").append(m.detail()).append('\n');
            for (MemberResult m : by.get(Status.PAPER)) {
                sb.append("- Paper 와 같게 `").append(m.key()).append("` — ").append(m.detail()).append('\n');
                for (String e : m.examples()) sb.append("  - ").append(e.replace('\n', ' ')).append('\n');
            }
            if (!by.get(Status.SAME).isEmpty()) {
                sb.append("- 같음 ").append(by.get(Status.SAME).size()).append("개: ");
                sb.append(String.join(", ", by.get(Status.SAME).stream().map(m -> "`" + m.key() + "`").toList())).append('\n');
                for (MemberResult m : by.get(Status.SAME)) {
                    if (m.detail() != null) sb.append("  - 주의 `").append(m.key()).append("` — ").append(m.detail()).append('\n');
                }
            }
            if (!by.get(Status.NOT_IN_SHIM).isEmpty()) {
                sb.append("- shim 에 없음 ").append(by.get(Status.NOT_IN_SHIM).size()).append("개: ");
                sb.append(String.join(", ", by.get(Status.NOT_IN_SHIM).stream().map(m -> "`" + m.key() + "`").toList())).append('\n');
            }
            sb.append('\n');
        }
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[vanilla-diff] report " + p);
    }

    private static void row(StringBuilder sb, String name, long[] c) {
        long same = c[Status.SAME.ordinal()], diff = c[Status.DIFF.ordinal()], unp = c[Status.UNPROVED.ordinal()];
        long denom = same + diff + unp;
        String ratio = denom == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * same / denom);
        sb.append("| ").append(name).append(" | ").append(same).append(" | ").append(diff).append(" | ").append(unp).append(" | ")
                .append(c[Status.EXCLUDED.ordinal()]).append(" | ").append(c[Status.PAPER.ordinal()]).append(" | ").append(c[Status.NOT_IN_SHIM.ordinal()]).append(" | ").append(ratio).append(" |\n");
    }
}
