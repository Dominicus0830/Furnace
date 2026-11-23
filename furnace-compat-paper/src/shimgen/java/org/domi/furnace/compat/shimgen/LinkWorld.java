// T-263: 연결 줄이 보는 세계 — 전체 껍데기 생성기(FullShells)의 서명표·채우기 계획·이미 있는 클래스를 Links.World 로 내준다.
// "살아 있다"는 껍데기(던지는 몸통)가 아니라는 뜻: 손 shim·JDK·라이브러리의 구체 멤버, T-262 채움, 상수·열거형 상수. 연결된 멤버는 넣지 않는다(연결끼리 잇지 않음 — 고리 방지).
package org.domi.furnace.compat.shimgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class LinkWorld implements Links.World {
    private final FullShells g;
    private final Map<String, Head> heads = new HashMap<>();
    private List<String> compat;

    /** 이미 있는 클래스 하나의 머리: 상위 형식, 메서드(이름+서술자 → 접근), 필드(이름+서술자 → 접근). */
    private record Head(List<String> supers, Map<String, Integer> methods, Map<String, Integer> fields) {}

    LinkWorld(FullShells g) { this.g = g; }

    @Override
    public SigTable.Cls paperClass(String n) { return g.decl(n); }

    @Override
    public List<SigTable.Member> paperMembers(String n) { return g.table(n).members(n); }

    @Override
    public byte[] classBytes(String n) { return g.existing.bytesOf(n); }

    @Override
    public List<String> supertypes(String n) {
        if (!g.shells.contains(n)) {
            Head h = head(n);
            if (h != null) return h.supers();
        }
        SigTable.Cls c = g.decl(n);
        if (c == null) return List.of();
        List<String> out = new ArrayList<>();
        if (c.superName() != null) out.add(c.superName());
        for (String i : c.interfaces()) if (!i.startsWith(FullShells.DROPPED_SUPER_PREFIX)) out.add(i);
        return out;
    }

    @Override
    public Links.Live liveMethod(String owner, String name, String desc) {
        String key = name + desc;
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String s = todo.poll();
            if (!seen.add(s)) continue;
            if (g.shells.contains(s)) {
                SigTable.Member m = member(s, 'M', name, desc);
                if (m != null) return liveShellMethod(s, m) ? new Links.Live(s, m.access()) : null;
            } else {
                Head h = head(s);
                if (h != null) {
                    Integer acc = h.methods().get(key);
                    if (acc != null) return (acc & Opcodes.ACC_ABSTRACT) != 0 ? null : new Links.Live(s, acc);
                    // 손 shim 에 없고 표에 있는 멤버는 합치기(ShellMerge)가 던지는 몸통으로 덧붙인다 — 살아 있지 않다
                    if (g.scope.containsKey(s) && member(s, 'M', name, desc) != null && g.existing.has(s)) return null;
                }
            }
            todo.addAll(supertypes(s));
        }
        return null;
    }

    private boolean liveShellMethod(String s, SigTable.Member m) {
        if ((m.access() & Opcodes.ACC_ABSTRACT) != 0) return false;
        boolean isEnum = Fills.isEnumClass(g, s);
        if (isEnum && (m.access() & Opcodes.ACC_STATIC) != 0
                && (m.name().equals("values") && m.desc().equals("()[L" + s + ";") || m.name().equals("valueOf") && m.desc().equals("(Ljava/lang/String;)L" + s + ";"))) return true;
        if (g.conversions.match(s, m) != null) return true;   // T-264 P4 변환 몸통
        Fills.ClassFill cf = g.fills.of(s);
        if (cf == null || g.initTraps(s)) return false;
        if (cf.getters.containsKey(m.name() + m.desc())) return true;
        return switch (m.name() + m.desc()) {
            case "toString()Ljava/lang/String;" -> cf.objectMethods.contains("toString");
            case "hashCode()I" -> cf.objectMethods.contains("hashCode");
            case "equals(Ljava/lang/Object;)Z" -> cf.objectMethods.contains("equals");
            default -> false;
        };
    }

    @Override
    public boolean liveStaticField(String owner, String name, String desc) {
        if (g.shells.contains(owner)) {
            SigTable.Member m = member(owner, 'F', name, desc);
            if (m == null || (m.access() & Opcodes.ACC_STATIC) == 0 || g.initTraps(owner)) return false;
            if ((m.access() & Opcodes.ACC_FINAL) != 0 && g.constant(owner, name) != null) return true;
            if (Fills.isEnumClass(g, owner) && (m.access() & Opcodes.ACC_ENUM) != 0) return true;
            Fills.ClassFill cf = g.fills.of(owner);
            return cf != null && cf.statics.containsKey(name);
        }
        Head h = head(owner);
        if (h == null) return false;
        Integer acc = h.fields().get(name + ":" + desc);
        return acc != null && (acc & Opcodes.ACC_STATIC) != 0;
    }

    @Override
    public Iterable<String> compatClasses() {
        if (compat == null) {
            compat = new ArrayList<>();
            for (String n : g.existing.dirClasses()) if (Links.targetAllowed(n)) compat.add(n);
            compat.sort(null);
        }
        return compat;
    }

    private SigTable.Member member(String cls, char kind, String name, String desc) {
        for (SigTable.Member m : g.table(cls).members(cls)) {
            if (m.kind() == kind && m.name().equals(name) && m.desc().equals(desc)) return m;
        }
        return null;
    }

    private Head head(String n) {
        if (heads.containsKey(n)) return heads.get(n);
        byte[] b = g.existing.bytesOf(n);
        Head h = null;
        if (b != null) {
            List<String> supers = new ArrayList<>();
            Map<String, Integer> ms = new HashMap<>();
            Map<String, Integer> fs = new HashMap<>();
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    if (superName != null) supers.add(superName);
                    if (interfaces != null) supers.addAll(List.of(interfaces));
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    ms.put(name + desc, access);
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    fs.put(name + ":" + desc, access);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            h = new Head(supers, ms, fs);
        }
        heads.put(n, h);
        return h;
    }
}
