package org.domi.furnace.tools.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.Opcodes;

/**
 * 서명 관문(T-228): Furnace 가 손으로 쓴 shim 클래스 선언을 기준 서명표(바닐라·Paper 서버·라이브러리)와 맞대어 "플러그인이 컴파일한 모양과 다른 곳"을
 * 찾는다. 이름이 같은 클래스마다 종류·접근·상위·인터페이스, 그리고 shim 이 선언한 공개·보호 멤버가 기준에 같은 이름·서술자·static 으로 풀리는지 본다
 * (JVM 처럼 상위 사슬을 따라 푼다 — 상위 클래스 멤버를 하위에 다시 선언한 것은 구멍으로 세지 않는다).
 *
 * <p>링크를 깨는 차이만 실패 대상이다: 클래스 종류·접근·상위·빠진 상위 타입, 같은 이름의 멤버인데 서술자·static·접근이 다른 것.
 * shim 이 더 선언한 클래스·멤버·오버로드(Furnace 내부 도우미)는 플러그인이 컴파일한 호출을 깨지 않으므로 실패 대상이 아니고 {@link #info()} 로
 * 건수만 보고한다. 거꾸로 "기준에는 있는데 shim 에 없는 멤버"는 여기서 보지 않는다 — 아직 채우지 않은 구멍이고 NmsLinker 덫이 맡는다(커버리지는 T-234 순위).
 */
public final class SigGate {

    /** 차이 한 건. key 가 기준선 파일의 줄 앞부분이다. */
    public record Diff(String code, String owner, String member, String detail) {
        public String key() {
            return code + "\t" + owner + "\t" + member + "\t" + detail;
        }
    }

    private final SigTable shim;
    private final List<SigTable> refs;
    private final List<Diff> info = new ArrayList<>();

    public SigGate(SigTable shim, List<SigTable> refs) {
        this.shim = shim;
        this.refs = refs;
    }

    private SigTable.Cls refClass(String name) {
        for (SigTable t : refs) {
            SigTable.Cls c = t.classes().get(name);
            if (c != null) return c;
        }
        return null;
    }

    /** 기준 표들을 합친 한 클래스의 멤버(같은 클래스가 여러 표에 있으면 합집합). */
    private List<SigTable.Member> refMembers(String name) {
        List<SigTable.Member> out = new ArrayList<>();
        for (SigTable t : refs) if (t.classes().containsKey(name)) out.addAll(t.members(name));
        return out;
    }

    /** 이름의 상위 사슬 전체(자기 포함). 기준에 있으면 기준 표로, 없고 shim 에만 있으면 shim 표로 따라간다. */
    private Set<String> allTypes(String name, boolean preferShim) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> todo = new LinkedHashSet<>(List.of(name));
        while (!todo.isEmpty()) {
            String n = todo.iterator().next();
            todo.remove(n);
            if (n == null || !seen.add(n)) continue;
            SigTable.Cls c = preferShim && shim.classes().containsKey(n) ? shim.classes().get(n) : refClass(n);
            if (c == null) c = shim.classes().get(n);
            if (c == null) continue;
            if (c.superName() != null) todo.add(c.superName());
            todo.addAll(c.interfaces());
        }
        return seen;
    }

    private static int vis(int a) {
        return (a & Opcodes.ACC_PUBLIC) != 0 ? 3 : (a & Opcodes.ACC_PROTECTED) != 0 ? 2 : (a & Opcodes.ACC_PRIVATE) != 0 ? 0 : 1;
    }

    private static String kind(int a, String superName) {
        if ((a & Opcodes.ACC_ANNOTATION) != 0) return "annotation";
        if ((a & Opcodes.ACC_INTERFACE) != 0) return "interface";
        if ((a & Opcodes.ACC_ENUM) != 0) return "enum";
        if ("java/lang/Record".equals(superName)) return "record";
        return "class";
    }

    /** 마지막 {@link #compare()} 가 모은 참고 건수 대상(실패 아님): extra-class·extra-member·extra-overload. */
    public List<Diff> info() {
        return info;
    }

    /** 링크를 깨는 차이(정렬). 참고용 "더 있음"은 {@link #info()}. */
    public List<Diff> compare() {
        info.clear();
        List<Diff> diffs = new ArrayList<>();
        for (SigTable.Cls s : shim.classes().values()) {
            SigTable.Cls r = refClass(s.name());
            if (r == null) {
                info.add(new Diff("extra-class", s.name(), "-", "기준 표에 없는 클래스"));
                continue;
            }
            String sk = kind(s.access(), s.superName()), rk = kind(r.access(), r.superName());
            if (!sk.equals(rk)) diffs.add(new Diff("class-kind", s.name(), "-", "shim=" + sk + " ref=" + rk));
            int sa = s.effectiveAccess(), ra = r.effectiveAccess();
            if (vis(sa) < vis(ra)) diffs.add(new Diff("class-access", s.name(), "-", "shim 이 덜 열려 있다(" + vis(sa) + " < " + vis(ra) + ")"));
            if ((sa & Opcodes.ACC_FINAL) != 0 && (ra & Opcodes.ACC_FINAL) == 0) diffs.add(new Diff("class-access", s.name(), "-", "shim 만 final"));
            if ((sa & Opcodes.ACC_ABSTRACT) != 0 && (ra & Opcodes.ACC_ABSTRACT) == 0 && (ra & Opcodes.ACC_INTERFACE) == 0)
                diffs.add(new Diff("class-access", s.name(), "-", "shim 만 abstract"));
            if (!java.util.Objects.equals(s.superName(), r.superName())) diffs.add(new Diff("super", s.name(), "-", "shim=" + s.superName() + " ref=" + r.superName()));
            Set<String> shimAll = allTypes(s.name(), true), refAll = allTypes(r.name(), false);
            // 상위가 다르면 super 한 건이 이미 말한다. JDK 타입은 양쪽 같은 JVM 이 준다.
            if (java.util.Objects.equals(s.superName(), r.superName())) {
                for (String t : refAll) if (!shimAll.contains(t) && !t.startsWith("java/")) diffs.add(new Diff("type-missing", s.name(), "-", t));
            }
            members(s, r, diffs);
        }
        diffs.sort(java.util.Comparator.comparing(Diff::key));
        return diffs;
    }

    /** 기준의 같은 이름 서술자가 모두 shim 사슬 어딘가에 있으면, shim 의 이 멤버는 서명이 틀린 것이 아니라 오버로드를 더한 것이다. */
    private boolean allSameNameCovered(String shimClass, SigTable.Member m, List<String> refDescs) {
        Set<String> have = new java.util.HashSet<>();
        for (String c : allTypes(shimClass, true)) {
            for (SigTable.Member x : shim.members(c)) if (x.kind() == m.kind() && x.name().equals(m.name())) have.add(x.desc());
        }
        return have.containsAll(refDescs);
    }

    private void members(SigTable.Cls s, SigTable.Cls r, List<Diff> diffs) {
        // 기준 사슬의 멤버: 클래스 자신 → 상위·인터페이스 순으로 이름+서술자 → 멤버
        Set<String> chain = allTypes(r.name(), false);
        for (SigTable.Member m : shim.members(s.name())) {
            if (vis(m.access()) < 2) continue;   // 공개·보호만(플러그인이 닿는 면)
            boolean ctor = m.name().equals("<init>");
            SigTable.Member hit = null;
            List<String> sameName = new ArrayList<>();
            for (String c : ctor ? List.of(r.name()) : chain) {
                for (SigTable.Member x : refMembers(c)) {
                    if (x.kind() != m.kind() || !x.name().equals(m.name())) continue;
                    if (x.desc().equals(m.desc())) {
                        if (hit == null && (vis(x.access()) >= 2 || !ctor)) hit = x;
                    } else if (vis(x.access()) >= 2) {
                        sameName.add(x.desc());
                    }
                }
            }
            String id = m.name() + (m.kind() == 'F' ? " : " : "") + m.desc();
            if (hit == null) {
                if (sameName.isEmpty()) info.add(new Diff("extra-member", s.name(), id, "기준에 없음"));
                else if (allSameNameCovered(s.name(), m, sameName)) info.add(new Diff("extra-overload", s.name(), id, "기준 오버로드는 모두 shim 에 있다"));
                else diffs.add(new Diff("descriptor", s.name(), id, "기준=" + new TreeSet<>(sameName)));
                continue;
            }
            if (((hit.access() ^ m.access()) & Opcodes.ACC_STATIC) != 0) diffs.add(new Diff("static", s.name(), id, "shim=" + ((m.access() & Opcodes.ACC_STATIC) != 0)));
            if ((m.access() & Opcodes.ACC_FINAL) != 0 && (hit.access() & Opcodes.ACC_FINAL) == 0 && m.kind() == 'M')
                diffs.add(new Diff("member-access", s.name(), id, "shim 만 final"));
            if (vis(m.access()) < vis(hit.access())) diffs.add(new Diff("member-access", s.name(), id, "shim 이 덜 열려 있다"));
        }
    }
}
