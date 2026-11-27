package org.domi.furnace.compat.shimgen.o3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-263 S4 블록 상태 정답기(O2 첫판, 설계 3.3). 연결 줄 중 블록 상태 관용구(@idiom(block-state-…))가 단 멤버마다, 바닐라(부트스트랩한 블록 등록)와
 * shim(실행 때와 같은 로더 + 시험 서버의 블록 표)에서 그 멤버가 소유 클래스의 선언에 닿는 블록을 받는 쪽으로 두고, 그 블록의 **모든 상태**를 넣어
 * 결과를 견준다(정적 멤버는 모든 블록의 모든 상태). 상태가 아닌 인자는 정수 몇 값·참거짓 둘·null(관용구 몸통은 상태 말고는 인자를 쓰지 않는다).
 *
 * <p>센다: 증명(다른 적 없음 + 바닐라의 던지지 않은 답이 둘 이상), 구분력 없음, 비교할 수 없는 값, 받는 쪽 없음, shim "아직 지원 안 함". 다름은 실패.
 * 돌연변이 카나리: 서술자가 같은 증명된 멤버 A·B 에서 바닐라는 A, shim 은 B 의 연결 몸통을 A 의 상태로 부른다 — 바닐라 A·B 가 갈리면 늘 잡아야 한다.
 * 이 첫판은 BlockGetter 류 인자를 다루지 않는다(중립 지도 Proxy 는 뒤에 — 그런 인자를 읽는 몸통은 관용구가 아니다). CI 로그에는 숫자만.
 */
@Tag("full-shells")
class BlockStateOracleTest {
    static final String UNSUPPORTED = "THREW:org.domi.furnace.compat.nms.UnsupportedNmsCall";
    static final String OPAQUE = "<?>";

    record Member(String owner, String name, String desc) {}

    record Proved(Member m, String name, String receiver) {}

    @Test
    void linkedBlockStateMembersMatchVanilla() throws Exception {
        Path tmp = Files.createTempDirectory("o2-vanilla");
        List<URL> vanUrls = new ArrayList<>();
        List<String> vanPaths = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("furnace.vdiff.classpath")), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            vanUrls.add(Path.of(line.trim()).toUri().toURL());
            vanPaths.add(line.trim());
        }
        EntityOracleTest.compileVanillaProbe(tmp, vanPaths, "VanillaBlockProbe");
        vanUrls.add(0, tmp.toUri().toURL());
        List<URL> shimUrls = new ArrayList<>();
        shimUrls.add(Path.of(System.getProperty("furnace.fullShells.jar")).toUri().toURL());
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) if (!p.isBlank()) shimUrls.add(Path.of(p).toUri().toURL());

        List<Member> members = members(Path.of("src/shimspec/links-" + System.getProperty("furnace.vdiff.mc", "1.21.1")));
        int proved = 0, undiscriminating = 0, opaque = 0, noReceiver = 0, unsupported = 0, privateUnresolved = 0, canaryCaught = 0, canaryMissed = 0;
        long inputs = 0;
        List<String> failures = new ArrayList<>();
        List<Proved> provedList = new ArrayList<>();
        StringBuilder table = new StringBuilder("# T-263 O2 block state oracle (member results, names - build output only)\n\n| member | receivers | result |\n|---|---|---|\n");
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader van = new URLClassLoader("vanilla", vanUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
             URLClassLoader shim = new URLClassLoader("shim", shimUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> vp = Class.forName("furnaceo3.VanillaBlockProbe", true, van);
            Class<?> sp = Class.forName(ShimBlockProbe.class.getName(), true, shim);
            assertTrue(sp.getClassLoader() == shim, "shim probe must come from the isolated loader");
            java.io.PrintStream out = System.out, err = System.err;
            Thread.currentThread().setContextClassLoader(van);
            try {
                vp.getMethod("init").invoke(null);
            } finally {
                System.setOut(out);   // 바닐라 Bootstrap 이 표준 출력을 자기 로거로 바꾼다
                System.setErr(err);
            }
            Thread.currentThread().setContextClassLoader(shim);
            sp.getMethod("init").invoke(null);
            try {
                Probe v = new Probe(vp, van), s = new Probe(sp, shim);
                String allBlocks = v.call("allBlocks");
                assertEquals(allBlocks, s.call("allBlocks"), "block registry order differs");
                for (Member m0 : members) {
                    Class<?> vc = Class.forName(m0.owner(), false, van);
                    String name = m0.name();
                    if (name.startsWith("#")) {
                        name = EntityOracleTest.resolve(vc, name, m0.desc());
                        if (name == null) {
                            privateUnresolved++;
                            continue;
                        }
                    }
                    String label = m0.owner() + "." + name + m0.desc();
                    String recv = v.call("receivers", m0.owner(), name, m0.desc());
                    if (recv.isEmpty()) {
                        noReceiver++;
                        table.append("| ").append(label).append(" | - | no block reaches this declaration |\n");
                        continue;
                    }
                    Set<String> vanillaValues = new HashSet<>();
                    boolean anyOpaque = false, anyUnsupported = false;
                    String wrong = null;
                    List<String> receivers = recv.equals("-") ? List.of("-") : List.of(recv.split(","));
                    for (String r : receivers) {
                        String stateBlocks = r.equals("-") ? allBlocks : r;
                        Map<String, String> vr = parse(v.call("run", m0.owner(), name, m0.desc(), r, stateBlocks));
                        Map<String, String> sr = parse(s.call("run", m0.owner(), name, m0.desc(), r, stateBlocks));
                        inputs += vr.size();
                        if (!vr.keySet().equals(sr.keySet())) {
                            wrong = label + " receiver=" + r + ": the input states differ (vanilla " + vr.size() + ", shim " + sr.size() + ")";
                            break;
                        }
                        for (Map.Entry<String, String> e : vr.entrySet()) {
                            String a = e.getValue(), b = sr.get(e.getKey());
                            if (!a.startsWith("THREW")) vanillaValues.add(a);
                            if (a.contains(OPAQUE)) anyOpaque = true;
                            if (a.equals(b)) continue;
                            if (b.startsWith(UNSUPPORTED)) {
                                anyUnsupported = true;
                                continue;
                            }
                            if (wrong == null) wrong = label + " receiver=" + r + " input=" + e.getKey() + ": vanilla=" + a + " shim=" + b;
                        }
                        if (wrong != null) break;
                    }
                    String where = receivers.get(0).equals("-") ? "static" : receivers.size() + " blocks";
                    if (wrong != null) {
                        failures.add(wrong);
                        table.append("| ").append(label).append(" | ").append(where).append(" | DIFFERS |\n");
                    } else if (anyUnsupported) {
                        unsupported++;
                        table.append("| ").append(label).append(" | ").append(where).append(" | shim unsupported |\n");
                    } else if (anyOpaque) {
                        opaque++;
                        table.append("| ").append(label).append(" | ").append(where).append(" | same, but holds a value the oracle cannot compare |\n");
                    } else if (vanillaValues.size() < 2) {
                        undiscriminating++;
                        table.append("| ").append(label).append(" | ").append(where).append(" | same, undiscriminating |\n");
                    } else {
                        proved++;
                        provedList.add(new Proved(m0, name, receivers.get(0)));
                        table.append("| ").append(label).append(" | ").append(where).append(" | proved (").append(vanillaValues.size()).append(" distinct) |\n");
                    }
                }
                // 돌연변이 카나리: 같은 서술자의 증명된 인스턴스 멤버 A·B — 바닐라 A(A 의 블록·상태) 와 shim B(B 의 블록, A 의 상태)는 바닐라 A·B 가 갈리면 달라야 한다
                Map<String, List<Proved>> groups = new TreeMap<>();
                for (Proved p : provedList) if (!p.receiver().equals("-")) groups.computeIfAbsent(p.m().desc(), k -> new ArrayList<>()).add(p);
                for (List<Proved> g : groups.values()) {
                    for (int i = 0; i + 1 < g.size(); i++) {
                        Proved a = g.get(i), b = g.get(i + 1);
                        Map<String, String> va = parse(v.call("run", a.m().owner(), a.name(), a.m().desc(), a.receiver(), a.receiver()));
                        Map<String, String> vb = parse(v.call("run", b.m().owner(), b.name(), b.m().desc(), b.receiver(), a.receiver()));
                        if (va.equals(vb)) continue;
                        Map<String, String> sb = parse(s.call("run", b.m().owner(), b.name(), b.m().desc(), b.receiver(), a.receiver()));
                        if (!sb.equals(va)) canaryCaught++;
                        else {
                            canaryMissed++;
                            failures.add("mutation canary missed: " + a.m().owner() + "." + a.name() + " linked to " + b.m().owner() + "." + b.name());
                        }
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(shim);
                sp.getMethod("close").invoke(null);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
        Path report = Path.of(System.getProperty("furnace.o2.report", "build/reports/block-state-oracle/report.md"));
        Files.createDirectories(report.getParent());
        Files.writeString(report, table.toString(), StandardCharsets.UTF_8);
        System.out.println("[block-state-oracle] members=" + members.size() + " proved=" + proved + " undiscriminating=" + undiscriminating + " opaque=" + opaque
                + " noReceiver=" + noReceiver + " shimUnsupported=" + unsupported + " privateUnresolved=" + privateUnresolved + " inputs=" + inputs
                + " mutation canary caught=" + canaryCaught + " missed=" + canaryMissed);
        assertEquals(List.of(), failures, "linked block state members must match vanilla on every state");
        // 채택된 줄은 모두 증명되어야 한다(2026-11-27 측정: 42줄 모두 증명) — 하나라도 증명에서 빠지면 실패
        assertTrue(members.size() >= 42, "block state idiom lines: " + members.size());
        assertEquals(members.size(), proved, "every adopted block state line must be proved by the oracle");
        assertEquals(0, canaryMissed, "mutation canary: a wrongly linked member must not compare equal");
        assertTrue(canaryCaught >= 5, "mutation canaries that could be told apart: " + canaryCaught);
    }

    /** 다른 로더의 탐침 정적 메서드(문자열만 주고받는다). 부르는 동안 문맥 로더를 그 로더로. */
    record Probe(Class<?> cls, ClassLoader loader) {
        String call(String method, String... args) throws Exception {
            Class<?>[] ts = new Class<?>[args.length];
            java.util.Arrays.fill(ts, String.class);
            Method m = cls.getMethod(method, ts);
            Thread.currentThread().setContextClassLoader(loader);
            return (String) m.invoke(null, (Object[]) args);
        }
    }

    static Map<String, String> parse(String out) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : out.split("\n")) {
            if (line.isEmpty()) continue;
            int t = line.indexOf('\t');
            if (t < 0) throw new IllegalStateException("probe answered: " + line);
            m.put(line.substring(0, t), line.substring(t + 1));
        }
        return m;
    }

    /** 연결 파일 전부에서 블록 상태 관용구 줄의 (소유 클래스, 멤버). */
    static List<Member> members(Path dir) throws Exception {
        List<Member> out = new ArrayList<>();
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".links")).sorted().toList();
        }
        for (Path f : files) {
            String owner = null;
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                // 주석: "# " 로 시작하는 줄·줄 끝 " # …"(연결 파일 규칙). 지문(#ab12…)은 # 뒤에 빈칸이 없어 주석이 아니다
                String line = raw.contains(" # ") ? raw.substring(0, raw.indexOf(" # ")) : raw;
                if (line.trim().equals("#") || line.trim().startsWith("# ") || line.startsWith("#furnace-links") || line.isBlank()) continue;
                if (line.startsWith("class ")) {
                    owner = line.substring(6).trim();
                    continue;
                }
                String t = line.trim();
                int arrow = t.indexOf(" -> ");
                if (owner == null || arrow < 0 || !t.substring(arrow + 4).startsWith("@idiom(block-state-")) continue;
                String sig = t.substring(0, arrow);
                int paren = sig.indexOf('(');
                out.add(new Member(owner, sig.substring(0, paren), sig.substring(paren)));
            }
        }
        return out;
    }
}
