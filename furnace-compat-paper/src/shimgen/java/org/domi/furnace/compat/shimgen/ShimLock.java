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
import java.util.Collections;
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
    /** 레코드 클래스 → 성분(바닐라 선언 순서). 이름·서술자·제네릭 서명(shim 에 없는 형식을 가리키면 지운다). */
    final Map<String, List<Component>> recordComponents = new TreeMap<>();

    record Component(String name, String desc, String signature) { }

    /**
     * 바닐라가 equals·hashCode·toString 을 레코드 자동 생성이 아니라 직접 쓴 레코드 → 그 메서드 이름들. 서명표에서 final 이 아닌 것(자동 생성은 public final)으로 가린다.
     * 생성기는 이런 레코드를 손 조각으로만 허용한다(자동 생성 몸통은 바닐라와 접근 수정자·동작이 다르다).
     */
    final Map<String, List<String>> explicitObjectMethods = new TreeMap<>();
    /** 클래스 → 런타임에 보이는 값 없는 애노테이션 서술자(허용 목록 안의 것만: {@link #COPIED_ANNOTATIONS}). 반사 결과(@FunctionalInterface 등)가 바닐라와 같아지게 복사한다. */
    final Map<String, List<String>> classAnnotations = new TreeMap<>();
    /**
     * 명세에 안 오른 인터페이스(손 shim 이 되는 것)의 추상 메서드 → H 줄. 생성 클래스가 그 인터페이스를 구현하는데 손 상위가 몸통을 안 갖추면 던지는 몸통을 붙이려고 적는다.
     * shim 에 없는 형식을 쓰는 메서드는 뺀다(몸통을 쓸 수 없다).
     */
    final Map<String, List<Member>> handAbstracts = new TreeMap<>();

    /** 복사하는 애노테이션(java.lang 의 것만 — 어느 shim 에서나 닿는다). 다른 애노테이션은 라이브러리 형식이라 복사하지 않는다. */
    static final Set<String> COPIED_ANNOTATIONS = Set.of("Ljava/lang/FunctionalInterface;", "Ljava/lang/Deprecated;");

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
            // Paper 내부 패치 인터페이스(moonrise 청크·추적 시스템 — T-232 Entity)는 플러그인이 쓰는 형식이 아니고 shim 에 없다: 선언에서 뺀다
            List<String> itfs = found.interfaces().stream().filter(i -> !i.startsWith(PAPER_INTERNAL)).toList();
            // 제네릭 서명에서도 같은 인터페이스를 글자 그대로 걸러 낸다(형식 매개변수·상위·남는 인터페이스는 원문 그대로)
            String sig = found.signature() == null || itfs.size() == found.interfaces().size() ? found.signature()
                    : JavaTypes.dropInterfaces(found.signature(), x -> x.startsWith(PAPER_INTERNAL));
            lock.classes.put(n, new Cls(n, found.access(), found.superName(), itfs, sig, found.innerAccess(), src));
            if ((found.access() & 0x4000) != 0) {
                int count = 0;
                for (SigTable t : tables) {
                    if (!t.classes().containsKey(n)) continue;
                    for (SigTable.Member sm : t.members(n)) if (sm.kind() == 'F' && (sm.access() & 0x4000) != 0) count++;
                    break;
                }
                lock.enumConstants.put(n, count);
            }
            for (SigTable t : tables) {
                if (!t.classes().containsKey(n)) continue;
                List<String> an = t.classAnnotations(n).stream().filter(COPIED_ANNOTATIONS::contains).toList();
                if (!an.isEmpty()) lock.classAnnotations.put(n, an);
                break;
            }
            if ("java/lang/Record".equals(found.superName())) {
                for (SigTable t : tables) {
                    if (!t.classes().containsKey(n)) continue;
                    List<Component> comps = new ArrayList<>();
                    for (String rc : t.recordComponents(n)) {
                        int k = rc.indexOf(':');
                        String cn = rc.substring(0, k), cd = rc.substring(k + 1), csig = null;
                        for (SigTable.Member sm : t.members(n)) if (sm.kind() == 'F' && sm.name().equals(cn) && sm.desc().equals(cd)) csig = sm.signature();
                        comps.add(new Component(cn, cd, csig));
                    }
                    if (comps.isEmpty()) problems.add("record without components in the signature table (rebuild it, format 2): " + n);
                    lock.recordComponents.put(n, comps);
                    List<String> explicit = new ArrayList<>();
                    for (SigTable.Member sm : t.members(n)) {
                        if (sm.kind() != 'M' || (sm.access() & 0x0018) != 0) continue;
                        boolean obj = sm.name().equals("equals") && sm.desc().equals("(Ljava/lang/Object;)Z")
                                || sm.name().equals("hashCode") && sm.desc().equals("()I")
                                || sm.name().equals("toString") && sm.desc().equals("()Ljava/lang/String;");
                        if (obj && (sm.access() & 0x0010) == 0) explicit.add(sm.name());
                    }
                    if (!explicit.isEmpty()) { Collections.sort(explicit); lock.explicitObjectMethods.put(n, List.copyOf(explicit)); }
                    break;
                }
            }
            int d = n.lastIndexOf('$');
            if (d > 0) work.add(n.substring(0, d));
            if (found.superName() != null && isNms(found.superName())) work.add(found.superName());
            for (String i : found.interfaces()) if (isNms(i)) work.add(i);
        }
        Predicate<String> inShim = n -> !isNms(n) || lock.classes.containsKey(n) || existing.test(n);
        for (Map.Entry<String, List<Component>> e : lock.recordComponents.entrySet()) {
            List<Component> fixed = new ArrayList<>();
            for (Component k : e.getValue()) {
                for (String r : JavaTypes.referencedClasses(k.desc())) {
                    if (!inShim.test(r)) problems.add(e.getKey() + ": record component " + k.name() + " uses a type that is not in the shim: " + r + " (add it to the spec as a class line)");
                }
                fixed.add(new Component(k.name(), k.desc(), keepSignature(k.signature(), inShim)));
            }
            e.setValue(List.copyOf(fixed));
        }
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
        // 2b) 명세에 없는 인터페이스의 추상 메서드(손 인터페이스 후보)
        for (Cls c : lock.classes.values()) {
            if (!c.isInterface() || spec.classes.containsKey(c.name().replace('/', '.'))) continue;
            List<SigTable.Member> all = null;
            for (SigTable t : tables) {
                if (t.classes().containsKey(c.name())) { all = t.members(c.name()); break; }
            }
            if (all == null) continue;
            List<Member> hs = new ArrayList<>();
            for (SigTable.Member sm : all) {
                if (sm.kind() != 'M' || (sm.access() & 0x0400) == 0 || (sm.access() & 0x0008) != 0) continue;
                boolean ok = true;
                for (String r : JavaTypes.referencedClasses(sm.desc())) if (!inShim.test(r)) ok = false;
                if (ok) hs.add(new Member('M', c.name(), sm.name(), sm.desc(), sm.access(), keepSignature(sm.signature(), inShim)));
            }
            hs.sort(Comparator.comparing(Member::name).thenComparing(Member::desc));
            if (!hs.isEmpty()) lock.handAbstracts.put(c.name(), List.copyOf(hs));
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

    /** Paper 서버 내부 구현 패키지(moonrise). 이 이름의 인터페이스는 shim 선언에 두지 않는다. */
    static final String PAPER_INTERNAL = "ca/spottedleaf/";

    static boolean isNms(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("org/bukkit/craftbukkit/");
    }

    // ───────────────────────── 쓰기·읽기 ─────────────────────────

    String text() {
        StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\t').append(FORMAT).append('\t').append(mc).append('\n');
        sb.append("# Generated by ./gradlew -PskipOracle=true :furnace-compat-paper:shimLock - do not edit. Declarations of the spec (src/shimspec/*.spec)\n");
        sb.append("# classes, members and their supertype chain, resolved from the signature tables (Paper server, then vanilla, then libraries). Only names the shim has.\n");
        sb.append("# C class access super interfaces signature innerAccess source / R record component (owner name descriptor signature, declaration order) / A class annotations copied (descriptors) / X record owner names of equals,hashCode,toString the vanilla source writes itself / H abstract method of an interface the spec does not list (hand shim candidate; owner name descriptor access signature) / F,M owner name descriptor access signature (- = none) / E enum constantCount\n");
        for (Cls c : classes.values()) {
            sb.append(String.join("\t", "C", c.name(), Integer.toString(c.access()), dash(c.superName()),
                    c.interfaces().isEmpty() ? "-" : String.join(",", c.interfaces()), dash(c.signature()),
                    c.innerAccess() < 0 ? "-" : Integer.toString(c.innerAccess()), c.source())).append('\n');
            Integer ec = enumConstants.get(c.name());
            if (ec != null) sb.append("E\t").append(c.name()).append('\t').append(ec).append('\n');
            for (Component k : recordComponents.getOrDefault(c.name(), List.of())) {
                sb.append(String.join("\t", "R", c.name(), k.name(), k.desc(), dash(k.signature()))).append('\n');
            }
            List<String> an = classAnnotations.get(c.name());
            if (an != null) sb.append("A\t").append(c.name()).append('\t').append(String.join(",", an)).append('\n');
            List<String> eo = explicitObjectMethods.get(c.name());
            if (eo != null) sb.append("X\t").append(c.name()).append('\t').append(String.join(",", eo)).append('\n');
            for (Member m : members(c.name())) {
                sb.append(String.join("\t", Character.toString(m.kind()), m.owner(), m.name(), m.desc(), Integer.toString(m.access()), dash(m.signature()))).append('\n');
            }
        }
        for (List<Member> hs : handAbstracts.values()) {
            for (Member m : hs) {
                sb.append(String.join("\t", "H", m.owner(), m.name(), m.desc(), Integer.toString(m.access()), dash(m.signature()))).append('\n');
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
        Map<String, List<Member>> hacc = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split("\t", -1);
            switch (f[0]) {
                case "C" -> lock.classes.put(f[1], new Cls(f[1], Integer.parseInt(f[2]), undash(f[3]),
                        f[4].equals("-") ? List.of() : List.of(f[4].split(",")), undash(f[5]), f[6].equals("-") ? -1 : Integer.parseInt(f[6]), f[7]));
                case "R" -> lock.recordComponents.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new Component(f[2], f[3], undash(f[4])));
                case "A" -> lock.classAnnotations.put(f[1], List.of(f[2].split(",")));
                case "X" -> lock.explicitObjectMethods.put(f[1], List.of(f[2].split(",")));
                case "E" -> lock.enumConstants.put(f[1], Integer.parseInt(f[2]));
                case "H" -> hacc.computeIfAbsent(f[1], k -> new ArrayList<>())
                        .add(new Member('M', f[1], f[2], f[3], Integer.parseInt(f[4]), undash(f[5])));
                case "F", "M" -> acc.computeIfAbsent(f[1], k -> new ArrayList<>())
                        .add(new Member(f[0].charAt(0), f[1], f[2], f[3], Integer.parseInt(f[4]), undash(f[5])));
                default -> throw new IllegalArgumentException("shim lock line " + (i + 1) + ": unknown line: " + l);
            }
        }
        acc.forEach((k, v) -> lock.members.put(k, List.copyOf(v)));
        hacc.forEach((k, v) -> lock.handAbstracts.put(k, List.copyOf(v)));
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
