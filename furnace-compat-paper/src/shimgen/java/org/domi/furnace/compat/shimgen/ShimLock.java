// T-229: 잠금 파일(src/shimspec/shim-<판>.lock). 연결 명세에 적힌 클래스·멤버와 그 상위 사슬의 "선언"(접근·상위·인터페이스·서술자·제네릭 서명)을
// 서명표에서 풀어 적은 기계 생성 파일이다. 커밋한다 — 빌드(생성기)는 이 파일만 읽어 바닐라 jar·네트워크 없이 돈다.
// 이 파일에는 shim 이 실제로 갖는 이름만 들어간다(명세 멤버·그 클래스·상위 사슬, 그리고 이미 shim 에 있는 형식). 제네릭 서명이 shim 에 없는
// net.minecraft 형식을 가리키면 그 서명은 적지 않는다(지운 서명으로 생성 — 서술자는 같다). 서명 관문 기준선과 같은 원칙(Mojang 이름은 shim 에 있는 것만).
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.domi.furnace.tools.extract.SigTable;

final class ShimLock {
    static final String MAGIC = "#furnace-shim-lock";
    static final String FORMAT = "1";

    /** 클래스 선언. source 는 어느 서명표에서 왔나(paper-server·vanilla·libs 이름표). */
    record Cls(String name, int access, String superName, List<String> interfaces, String signature, int innerAccess, String source) {
        int effectiveAccess() { return innerAccess >= 0 ? innerAccess : access; }

        boolean isInterface() { return (access & 0x0200) != 0; }

        boolean isEnum() { return (access & 0x4000) != 0; }

        boolean isRecord() { return "java/lang/Record".equals(superName); }

        boolean isAnnotation() { return (access & 0x2000) != 0; }

        boolean isAbstract() { return (access & 0x0400) != 0; }
    }

    /** 멤버 선언. kind 'F' 필드, 'M' 메서드(생성자 &lt;init&gt; 포함). */
    record Member(char kind, String owner, String name, String desc, int access, String signature) {
        boolean isStatic() { return (access & 0x0008) != 0; }

        boolean isAbstract() { return (access & 0x0400) != 0; }

        String key() { return name + " " + desc; }
    }

    final String mc;
    final Map<String, Cls> classes = new TreeMap<>();
    final Map<String, List<Member>> members = new TreeMap<>();
    /** 열거형 클래스 → 바닐라 상수 개수(이름은 담지 않는다 — 명세가 모자라게 적으면 생성기가 막는다. 서수·values() 가 바닐라와 같아야 하니까). */
    final Map<String, Integer> enumConstants = new TreeMap<>();

    ShimLock(String mc) { this.mc = mc; }

    List<Member> members(String owner) { return members.getOrDefault(owner, List.of()); }

    // ───────────────────────── 풀기(서명표 → 잠금) ─────────────────────────

    /** 문제 목록을 모아 한 번에 알린다. */
    static final class ResolveException extends RuntimeException {
        final List<String> problems;

        ResolveException(List<String> problems) {
            super("cannot resolve the shim spec against the signature tables (" + problems.size() + " problems):\n  " + String.join("\n  ", problems));
            this.problems = problems;
        }
    }

    /**
     * 명세 + 서명표(우선순위 순: Paper 서버 → 바닐라 → 라이브러리) → 잠금. existing 은 명세 밖에서 이미 shim 에 있는 클래스(손 shim·패킷 생성물)의
     * 내부 이름 판정 — 멤버 서술자가 가리키는 net.minecraft 형식이 명세 사슬에도 shim 에도 없으면 오류다.
     */
    static ShimLock resolve(String mc, ShimSpec spec, List<SigTable> tables, Predicate<String> existing) {
        List<String> problems = new ArrayList<>();
        ShimLock lock = new ShimLock(mc);
        // 1) 클래스 사슬: 명세 클래스 → 바깥 클래스·상위·인터페이스(net.minecraft 만)
        Deque<String> work = new ArrayDeque<>();
        for (ShimSpec.Cls c : spec.classes.values()) work.add(c.name().replace('.', '/'));
        while (!work.isEmpty()) {
            String n = work.poll();
            if (lock.classes.containsKey(n)) continue;
            SigTable.Cls found = null;
            String src = null;
            for (SigTable t : tables) {
                found = t.classes().get(n);
                if (found != null) { src = t.label(); break; }
            }
            if (found == null) { problems.add("class not in the signature tables: " + n); continue; }
            lock.classes.put(n, new Cls(n, found.access(), found.superName(), found.interfaces(), found.signature(), found.innerAccess(), src));
            if ((found.access() & 0x4000) != 0) {
                int count = 0;
                for (SigTable t : tables) {
                    if (!t.classes().containsKey(n)) continue;
                    for (SigTable.Member sm : t.members(n)) if (sm.kind() == 'F' && (sm.access() & 0x4000) != 0) count++;
                    break;
                }
                lock.enumConstants.put(n, count);
            }
            int d = n.lastIndexOf('$');
            if (d > 0) work.add(n.substring(0, d));
            if (found.superName() != null && isNms(found.superName())) work.add(found.superName());
            for (String i : found.interfaces()) if (isNms(i)) work.add(i);
        }
        Predicate<String> inShim = n -> !isNms(n) || lock.classes.containsKey(n) || existing.test(n);
        // 2) 멤버
        for (ShimSpec.Cls c : spec.classes.values()) {
            String owner = c.name().replace('.', '/');
            if (!lock.classes.containsKey(owner)) continue;
            List<SigTable.Member> all = null;
            for (SigTable t : tables) {
                if (t.classes().containsKey(owner)) { all = t.members(owner); break; }
            }
            List<Member> out = new ArrayList<>();
            for (ShimSpec.Member m : c.members()) {
                List<SigTable.Member> cand = new ArrayList<>();
                for (SigTable.Member sm : all) {
                    if (!sm.name().equals(m.name())) continue;
                    if (m.desc() != null) {
                        boolean wantField = m.fieldDesc();
                        if (wantField != (sm.kind() == 'F')) continue;
                        if (wantField ? !sm.desc().equals(m.desc()) : !sm.desc().startsWith(m.desc())) continue;
                    }
                    cand.add(sm);
                }
                if (cand.isEmpty()) {
                    problems.add(m.where() + ": " + owner + " does not declare member " + m.name() + (m.desc() == null ? "" : m.desc())
                            + " (if a superclass declares it, list it under that class)");
                    continue;
                }
                if (cand.size() > 1) {
                    List<String> ds = cand.stream().map(SigTable.Member::desc).toList();
                    problems.add(m.where() + ": overloaded name - write the descriptor: " + m.name() + " " + ds);
                    continue;
                }
                SigTable.Member sm = cand.get(0);
                for (String r : JavaTypes.referencedClasses(sm.desc())) {
                    if (!inShim.test(r)) problems.add(m.where() + ": " + m.name() + " uses a type that is not in the shim: " + r + " (add it to the spec as a class line)");
                }
                out.add(new Member(sm.kind(), owner, sm.name(), sm.desc(), sm.access(), keepSignature(sm.signature(), inShim)));
            }
            out.sort(Comparator.comparing((Member x) -> x.kind()).thenComparing(Member::name).thenComparing(Member::desc));
            lock.members.put(owner, List.copyOf(out));
        }
        // 3) 클래스 제네릭 서명: shim 에 없는 형식을 가리키면 지운다
        for (Map.Entry<String, Cls> e : lock.classes.entrySet()) {
            Cls c = e.getValue();
            String s = keepSignature(c.signature(), inShim);
            if (s != c.signature()) e.setValue(new Cls(c.name(), c.access(), c.superName(), c.interfaces(), s, c.innerAccess(), c.source()));
        }
        if (!problems.isEmpty()) throw new ResolveException(problems);
        return lock;
    }

    private static String keepSignature(String sig, Predicate<String> inShim) {
        if (sig == null) return null;
        for (String r : JavaTypes.referencedClasses(sig)) if (!inShim.test(r)) return null;
        return sig;
    }

    static boolean isNms(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("org/bukkit/craftbukkit/");
    }

    // ───────────────────────── 쓰기·읽기 ─────────────────────────

    String text() {
        StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\t').append(FORMAT).append('\t').append(mc).append('\n');
        sb.append("# Generated by ./gradlew -PskipOracle=true :furnace-compat-paper:shimLock - do not edit. Declarations of the spec (src/shimspec/*.spec)\n");
        sb.append("# classes, members and their supertype chain, resolved from the signature tables (Paper server, then vanilla, then libraries). Only names the shim has.\n");
        sb.append("# C class access super interfaces signature innerAccess source / F,M owner name descriptor access signature (- = none) / E enum constantCount\n");
        for (Cls c : classes.values()) {
            sb.append(String.join("\t", "C", c.name(), Integer.toString(c.access()), dash(c.superName()),
                    c.interfaces().isEmpty() ? "-" : String.join(",", c.interfaces()), dash(c.signature()),
                    c.innerAccess() < 0 ? "-" : Integer.toString(c.innerAccess()), c.source())).append('\n');
            Integer ec = enumConstants.get(c.name());
            if (ec != null) sb.append("E\t").append(c.name()).append('\t').append(ec).append('\n');
            for (Member m : members(c.name())) {
                sb.append(String.join("\t", Character.toString(m.kind()), m.owner(), m.name(), m.desc(), Integer.toString(m.access()), dash(m.signature()))).append('\n');
            }
        }
        return sb.toString();
    }

    void write(Path p) throws IOException {
        Files.createDirectories(p.toAbsolutePath().getParent());
        Files.writeString(p, text(), StandardCharsets.UTF_8);
    }

    static ShimLock read(Path p) throws IOException {
        if (!Files.exists(p)) return null;
        return parse(Files.readString(p, StandardCharsets.UTF_8));
    }

    static ShimLock parse(String text) {
        String[] lines = text.split("\r?\n");
        if (lines.length == 0 || !lines[0].startsWith(MAGIC + "\t")) throw new IllegalArgumentException("not a shim lock file (no " + MAGIC + " header)");
        String[] h = lines[0].split("\t", -1);
        if (!h[1].equals(FORMAT)) throw new IllegalArgumentException("unsupported shim lock format: " + h[1]);
        ShimLock lock = new ShimLock(h[2]);
        Map<String, List<Member>> acc = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split("\t", -1);
            switch (f[0]) {
                case "C" -> lock.classes.put(f[1], new Cls(f[1], Integer.parseInt(f[2]), undash(f[3]),
                        f[4].equals("-") ? List.of() : List.of(f[4].split(",")), undash(f[5]), f[6].equals("-") ? -1 : Integer.parseInt(f[6]), f[7]));
                case "E" -> lock.enumConstants.put(f[1], Integer.parseInt(f[2]));
                case "F", "M" -> acc.computeIfAbsent(f[1], k -> new ArrayList<>())
                        .add(new Member(f[0].charAt(0), f[1], f[2], f[3], Integer.parseInt(f[4]), undash(f[5])));
                default -> throw new IllegalArgumentException("shim lock line " + (i + 1) + ": unknown line: " + l);
            }
        }
        acc.forEach((k, v) -> lock.members.put(k, List.copyOf(v)));
        return lock;
    }

    /** 줄바꿈을 \n 으로(Windows core.autocrlf 체크아웃이 CRLF 로 내려도 같은 파일로 본다). */
    static String normalize(String text) { return text.replace("\r\n", "\n"); }

    /** 두 잠금이 같은가(줄바꿈 무시). */
    static boolean same(String expected, String actual) { return normalize(expected).equals(normalize(actual)); }

    /** 두 잠금의 다른 줄(낡음 보고용). */
    static List<String> diff(String expected, String actual) {
        Set<String> e = new TreeSet<>(List.of(normalize(expected).split("\n")));
        Set<String> a = new TreeSet<>(List.of(normalize(actual).split("\n")));
        List<String> out = new ArrayList<>();
        for (String l : e) if (!a.contains(l)) out.add("+ " + l);
        for (String l : a) if (!e.contains(l)) out.add("- " + l);
        return out;
    }

    private static String dash(String s) { return s == null ? "-" : s; }

    private static String undash(String s) { return s.equals("-") ? null : s; }
}
