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

    record Target(String cls, int iterations, Map<String, String> excludes, Map<String, String> paper) {}

    record ClassReport(String cls, boolean gate, int iterations, long millis, String error, List<MemberResult> members) {}

    private static final List<ClassReport> REPORTS = java.util.Collections.synchronizedList(new ArrayList<>());
    private static DiffRunner runner;
    private static long seed;

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
        runner = new DiffRunner(rt.loader(), VanillaDiffTest.class.getClassLoader(), seed);
        List<DynamicTest> tests = new ArrayList<>();
        for (Target t : targets) {
            if (!only.isEmpty() && !only.contains(t.cls)) continue;
            int n = forced != null ? forced : t.iterations;
            tests.add(DynamicTest.dynamicTest(t.cls, () -> check(record(runClass(rt, t.cls, null, t.excludes, t.paper, true, n)))));
        }
        if (only.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : specTargets().entrySet()) {
                if (targets.stream().anyMatch(t -> t.cls.equals(e.getKey()))) continue;
                int n = forced != null ? forced : DEFAULT_ITERATIONS;
                tests.add(DynamicTest.dynamicTest("[spec] " + e.getKey(),
                        () -> check(record(runClass(rt, e.getKey(), e.getValue(), Map.of(), Map.of(), false, n)))));
            }
        }
        assertFalse(tests.isEmpty(), "no class was compared");
        return tests.stream();
    }

    static final int DEFAULT_ITERATIONS = 20_000;

    private static ClassReport record(ClassReport r) {
        REPORTS.add(r);
        return r;
    }

    @org.junit.jupiter.api.AfterAll
    static void finish() throws IOException {
        if (runner != null) runner.close();
        NbtKit.cleanTemp();
        writeReport(new ArrayList<>(REPORTS), VanillaRuntime.get().mc(), seed);
    }

    private static void check(ClassReport r) {
        if (!r.gate) return; // 명세 줄은 보고만(관문 아님) — 보고서의 "증명 없음"이 할 일 목록
        if (r.error != null) fail(r.cls + ": " + r.error);
        List<String> bad = new ArrayList<>();
        for (MemberResult m : r.members) {
            if (m.status() == Status.DIFF) bad.add("DIFF " + m.key() + " " + (m.detail() == null ? "" : m.detail()) + " " + m.examples());
            if (m.status() == Status.UNPROVED) bad.add("UNPROVED " + m.key() + " (" + m.detail() + ") - add a TypeKit type or an exclude with a reason");
        }
        if (!bad.isEmpty()) fail(r.cls + ":\n  " + String.join("\n  ", bad));
    }

    private static ClassReport runClass(VanillaRuntime rt, String cls, Set<String> onlyNames,
                                        Map<String, String> excludes, Map<String, String> paper, boolean gate, int iterations) {
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
            MemberResult res = runner.run(vc, sc, m, iterations);
            if (paper.containsKey(key)) {
                // 다를 때만 Paper 표시를 쓴다 — 더는 다르지 않으면 줄이 낡은 것이니 오류로 알린다
                if (res.status() == Status.DIFF) {
                    unusedPaper.remove(key);
                    res = new MemberResult(key, Status.PAPER, res.calls(), res.skipped(), paper.get(key) + " / " + res.detail(), res.examples());
                }
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
                    for (int k = 1; k < parts.length; k++) {
                        if (!parts[k].startsWith("iterations=")) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": unknown option " + parts[k]);
                        n = Integer.parseInt(parts[k].substring("iterations=".length()));
                    }
                    cur = new Target(parts[0], n, new LinkedHashMap<>(), new LinkedHashMap<>());
                    out.add(cur);
                } else if (line.startsWith("exclude ") && cur != null) {
                    if (reason.isEmpty()) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": exclude needs '# reason'");
                    cur.excludes.put(line.substring(8).strip(), reason);
                } else if (line.startsWith("paper ") && cur != null) {
                    if (reason.isEmpty()) throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": paper needs '# reason'");
                    cur.paper.put(line.substring(6).strip(), reason);
                } else {
                    throw new IllegalStateException("vanilla-diff-targets.txt:" + no + ": cannot parse: " + raw);
                }
            }
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
        sb.append("| 묶음 | 같음 | 다름 | 증명 없음 | 뺌 | Paper 와 같게 | shim 에 없음 | 증명 비율 |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        row(sb, "값 클래스(관문)", gateCounts);
        row(sb, "연결 명세 pure·read=any(보고만)", specCounts);
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
