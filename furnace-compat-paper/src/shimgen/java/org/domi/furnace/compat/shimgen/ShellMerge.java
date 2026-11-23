// T-261: 이미 있는 클래스(손 shim·명세 생성 shim·패킷 생성물 — 호환 jar 의 클래스)에 Paper 1.21.1 서명표에는 있으나 빠진 멤버를 껍데기 멤버로 합친다.
// 합친 클래스는 원래 클래스 바이트 그대로에 멤버만 덧붙인 것이고, 껍데기 jar 에 같은 이름으로 들어간다 — 서버는 껍데기 jar 를 호환 jar 보다 앞에 둔다
// (표가 없는 빌드는 껍데기 jar 가 없으므로 지금과 같다). 손 shim 의 원래 메서드 바이트는 한 바이트도 바꾸지 않는다.
//
// 덧붙이는 것(FullShells 의 껍데기 멤버와 같은 모양·표지)
//  - 메서드·생성자: 던지는 몸통(NmsShell.unsupported) + @NmsShellMember. 추상은 떼고(손 하위가 구현하지 않았을 수 있다), 인터페이스면 default 가 된다.
//  - 정적 필드: 상수 값이 있으면 값 그대로(표지 없음), 없으면 선언만 + 표지(연결기 덫·리플렉션 다리가 막는다). NMS 범위에서만.
//  - 인스턴스 필드: 넣지 않는다(살아 있는 객체마다 메모리가 든다). 색인(INSTANCE_FIELDS 자원)에만 적어 리플렉션 다리가 "Paper 에 있으나 아직 없음"으로 센다.
// 넣지 않는 것(이유는 보고서에)
//  - 상위 형식(클래스 사슬·인터페이스)에서 이미 찾아지는 이름+서술자 — 덧붙이면 지금 잘 도는 상속 멤버를 던지는 몸통이 가린다.
//  - 손 하위 형식이 다른 인터페이스의 default 로 그 메서드를 받고 있는 것 — 덧붙이면 그 하위에서 클래스 메서드가 이기거나(던짐) default 가 둘이 된다.
//  - 합성 멤버(람다·다리 메서드 — 컴파일러 산물), 정적 초기화.
// 바닐라·Paper 의 몸통은 읽지도 옮기지도 않는다(서명표에 몸통이 없다).
package org.domi.furnace.compat.shimgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class ShellMerge {
    /** 껍데기 jar 안의 인스턴스 필드 색인(소유\t이름\t서술자\t접근 — 이름 들어감, 커밋 안 함). 리플렉션 다리 NmsShellFields 가 읽는다. */
    static final String INSTANCE_FIELDS = "META-INF/furnace/full-shells/instance-fields.tsv";
    /** 껍데기 jar 안의 합친 클래스 목록(한 줄에 하나) — 올리기 시험이 껍데기와 구별한다. */
    static final String MERGED = "META-INF/furnace/full-shells/merged-classes.txt";

    private final FullShells g;
    private final Set<String> overlay;
    /** 클래스 → 선언(머리·멤버). 호환 계층·라이브러리·JDK 에서 읽는다. */
    private final Map<String, Decl> decls = new HashMap<>();
    /** 상위 → 바로 아래 하위(호환 jar 의 클래스만 — 손 하위). */
    private final Map<String, List<String>> subtypes = new HashMap<>();

    /** 덧붙인 멤버 수(종류별). */
    final Map<String, Integer> added = new TreeMap<>();
    /** 덧붙이지 않은 멤버 수(이유별). */
    final Map<String, Integer> notAdded = new TreeMap<>();
    /** 덧붙이지 않은 멤버(이유 → 목록, 보고서용). */
    final Map<String, List<String>> notAddedList = new TreeMap<>();
    /** 인스턴스 필드 색인 줄. */
    final List<String> instanceFields = new ArrayList<>();
    /** 합친 클래스 수 / 빠진 멤버가 없어 그대로인 클래스 수 / 호환 jar 밖이라 못 합친 클래스 수. */
    int mergedClasses;
    int completeClasses;
    final List<String> outsideOverlay = new ArrayList<>();

    record Decl(int access, String superName, List<String> interfaces, Map<String, Integer> methods, Map<String, Integer> fields) {
        boolean isInterface() { return (access & Opcodes.ACC_INTERFACE) != 0; }
    }

    /** overlay: 합칠 수 있는 클래스(호환 jar 자신의 클래스 폴더에 있는 것). */
    ShellMerge(FullShells g, Set<String> overlay) {
        this.g = g;
        this.overlay = overlay;
        for (String n : overlay) {
            Decl d = decl(n);
            if (d == null) continue;
            if (d.superName() != null) subtypes.computeIfAbsent(d.superName(), k -> new ArrayList<>()).add(n);
            for (String i : d.interfaces()) subtypes.computeIfAbsent(i, k -> new ArrayList<>()).add(n);
        }
    }

    /** 이미 있는 범위 클래스마다 빠진 멤버를 합친다. 결과: 합친 클래스 이름 → 바이트(빠진 것이 없으면 넣지 않는다). */
    Map<String, byte[]> merge() {
        Map<String, byte[]> out = new TreeMap<>();
        for (Map.Entry<String, FullShells.Scope> e : g.scope.entrySet()) {
            String n = e.getKey();
            FullShells.Scope scope = e.getValue();
            if (scope == FullShells.Scope.API) continue;   // Furnace org.bukkit 재구현의 빈칸은 API 서명 관문(T-240) 몫
            if (g.shells.contains(n) || g.skipped.containsKey(n) || !g.existing.has(n)) continue;
            if (!overlay.contains(n)) {
                outsideOverlay.add(n);
                continue;
            }
            byte[] merged = mergeOne(n, scope);
            if (merged == null) completeClasses++;
            else {
                out.put(n, merged);
                mergedClasses++;
            }
        }
        return out;
    }

    private record Add(SigTable.Member m, int access, Object value, boolean shell) {}

    private byte[] mergeOne(String n, FullShells.Scope scope) {
        Decl own = decl(n);
        if (own == null) return null;
        boolean nms = scope == FullShells.Scope.NMS;
        List<Add> adds = new ArrayList<>();
        for (SigTable.Member m : g.table(n).members(n)) {
            String key = m.name() + m.desc();
            String what = n + "#" + m.name() + (m.kind() == 'F' ? ":" : "") + m.desc();
            if ((m.access() & Opcodes.ACC_SYNTHETIC) != 0 || m.kind() == 'M' && (m.access() & Opcodes.ACC_BRIDGE) != 0) {
                skip("synthetic (compiler-made: lambdas, bridges)", what);
                continue;
            }
            if (m.name().equals("<clinit>")) continue;
            if (m.kind() == 'F') {
                if (own.fields().containsKey(key)) continue;
                if (fieldInherited(own, key)) {
                    skip("field already resolves through a supertype", what);
                    continue;
                }
                if ((m.access() & Opcodes.ACC_STATIC) == 0) {
                    instanceFields.add(n + "\t" + m.name() + "\t" + m.desc() + "\t" + (m.access() & 0xFFFF));
                    count("instance field (index only)");
                    continue;
                }
                if (!nms) {
                    // 연결기 덫은 NMS 이름만 본다 — 밖 범위의 값 없는 정적 필드는 조용히 null 을 줄 것이라 넣지 않는다
                    skip("static field outside the NMS scope (the linker trap does not see it)", what);
                    continue;
                }
                Object value = (m.access() & Opcodes.ACC_FINAL) != 0 ? g.constant(n, m.name()) : null;
                adds.add(new Add(m, m.access() & 0xFFFF, value, value == null));
                count(value == null ? "static field" : "constant field (value)");
                continue;
            }
            if (own.methods().containsKey(key)) continue;
            boolean isCtor = m.name().equals("<init>");
            boolean isStatic = (m.access() & Opcodes.ACC_STATIC) != 0;
            boolean isPrivate = (m.access() & Opcodes.ACC_PRIVATE) != 0;
            if (isCtor && own.isInterface()) continue;
            if (!isCtor && methodInherited(own, key)) {
                skip("method already resolves through a supertype", what);
                continue;
            }
            boolean subDeclares = false;
            if (!isCtor && !isStatic && !isPrivate) {
                Boolean clash = subtypeClash(n, own, key);
                if (clash == null) {
                    skip("a hand subtype inherits it as a default from another interface", what);
                    continue;
                }
                subDeclares = clash;
            }
            int acc = m.access() & 0xFFFF & ~(Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE);
            if (subDeclares && (acc & Opcodes.ACC_FINAL) != 0) {
                acc &= ~Opcodes.ACC_FINAL;   // 손 하위가 같은 메서드를 덮는다 — final 이면 그 하위가 올라가지 않는다
                g.deviationPublic("merged method made non-final: a hand subtype overrides it", what);
            }
            if ((m.access() & Opcodes.ACC_ABSTRACT) != 0) g.deviationPublic("merged abstract method given a throwing body (hand subtypes may not implement it)", what);
            adds.add(new Add(m, acc, null, true));
            count(isCtor ? "constructor" : isStatic ? "static method" : "method");
        }
        if (adds.isEmpty()) return null;
        byte[] original = g.existing.bytesOf(n);
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, 0);   // 원래 메서드는 바이트 그대로 복사된다
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                for (Add a : adds) {
                    if (a.m().kind() == 'F') {
                        FieldVisitor fv = cv.visitField(a.access(), a.m().name(), a.m().desc(), a.m().signature(), a.value());
                        if (a.shell()) fv.visitAnnotation(FullShells.SHELL_MEMBER, false).visitEnd();
                        fv.visitEnd();
                    } else {
                        if (!linkBody(cv, n, a.access(), a.m())) body(cv, n, a.access(), a.m());
                    }
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    /** T-263: 연결 줄이 있으면 연결 몸통(표지 없음 — 연결기가 "있음"으로 본다). */
    private boolean linkBody(ClassVisitor cv, String owner, int acc, SigTable.Member m) {
        if (!g.links.has(owner, m.name() + m.desc())) return false;
        MethodVisitor mv = cv.visitMethod(acc, m.name(), m.desc(), m.signature(), null);
        if (g.links.emit(mv, owner, acc, m.name(), m.desc())) {
            count("linked (T-263)");
            return true;
        }
        // 대상이 안 맞음 — 문제는 links.problems 에 있고 생성 끝에 멈춘다. 여기서는 던지는 몸통으로 메서드를 마저 쓴다.
        mv.visitAnnotation(FullShells.SHELL_MEMBER, false).visitEnd();
        throwing(mv, owner, acc, m);
        return true;
    }

    /** 던지는 몸통(FullShells.shellBody 와 같은 모양). 프레임이 필요 없는 곧은 코드라 크기를 직접 적는다. */
    private static void body(ClassVisitor cv, String owner, int acc, SigTable.Member m) {
        MethodVisitor mv = cv.visitMethod(acc, m.name(), m.desc(), m.signature(), null);
        mv.visitAnnotation(FullShells.SHELL_MEMBER, false).visitEnd();
        throwing(mv, owner, acc, m);
    }

    private static void throwing(MethodVisitor mv, String owner, int acc, SigTable.Member m) {
        mv.visitCode();
        mv.visitLdcInsn(owner.replace('/', '.') + "#" + m.name() + m.desc());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, FullShells.SHELL_HELPER, "unsupported", "(Ljava/lang/String;)Ljava/lang/RuntimeException;", false);
        mv.visitInsn(Opcodes.ATHROW);
        int locals = (Type.getArgumentsAndReturnSizes(m.desc()) >> 2) - ((acc & Opcodes.ACC_STATIC) != 0 ? 1 : 0);
        mv.visitMaxs(1, locals);
        mv.visitEnd();
    }

    // ───────────────────────── 해석 검사 ─────────────────────────

    /** 상위 사슬(클래스)·상위 인터페이스에서 이름+서술자 필드가 찾아지나(private 은 다른 클래스에서 못 쓰므로 뺀다). 모르는 상위가 있으면 찾아지는 것으로 본다. */
    private boolean fieldInherited(Decl own, String key) {
        ArrayDeque<String> todo = new ArrayDeque<>(own.interfaces());
        if (own.superName() != null) todo.add(own.superName());
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String s = todo.poll();
            if (!seen.add(s)) continue;
            Decl d = decl(s);
            if (d == null) return true;   // 모르면 덧붙이지 않는다(가릴 수 있다)
            Integer a = d.fields().get(key);
            if (a != null && (a & Opcodes.ACC_PRIVATE) == 0) return true;
            todo.addAll(d.interfaces());
            if (d.superName() != null) todo.add(d.superName());
        }
        return false;
    }

    /** 상위 클래스 사슬의 private 아닌 메서드, 상위 인터페이스의 private·static 아닌 메서드에서 찾아지나. 모르는 상위가 있으면 찾아지는 것으로 본다. */
    private boolean methodInherited(Decl own, String key) {
        for (String s = own.superName(); s != null; ) {
            Decl d = decl(s);
            if (d == null) return true;
            Integer a = d.methods().get(key);
            if (a != null && (a & Opcodes.ACC_PRIVATE) == 0) return true;
            s = d.superName();
        }
        Set<String> itfs = interfaceClosure(own, null);
        if (itfs == null) return true;
        for (String i : itfs) {
            Integer a = decl(i).methods().get(key);
            if (a != null && (a & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) return true;
        }
        return false;
    }

    /**
     * 손 하위 형식에서 덧붙일 인스턴스 메서드가 문제를 일으키나. null = 어떤 하위가 (이 형식을 거치지 않는) 인터페이스에서 그 메서드를 받고 그 사이 클래스가 선언하지
     * 않는다(덧붙이면 그 하위의 해석이 바뀐다 — 넣지 않는다). true = 문제 없고 어떤 하위가 그 메서드를 직접 선언한다(덮는다). false = 문제 없음.
     */
    private Boolean subtypeClash(String n, Decl own, String key) {
        Set<String> above = interfaceClosure(own, null);
        Set<String> exclude = new HashSet<>(above == null ? Set.of() : above);
        exclude.add(n);
        boolean[] declares = {false};
        Set<String> seen = new HashSet<>();
        if (!walkSubtypes(n, key, exclude, false, declares, seen)) return null;
        return declares[0];
    }

    /** covered: 위쪽 사이 클래스가 이미 선언해 아래는 그 클래스 메서드를 받는다. 문제 있으면 false. */
    private boolean walkSubtypes(String t, String key, Set<String> exclude, boolean covered, boolean[] declares, Set<String> seen) {
        for (String sub : subtypes.getOrDefault(t, List.of())) {
            if (!seen.add(sub + (covered ? "+" : "-"))) continue;
            Decl d = decl(sub);
            if (d == null) continue;
            boolean c = covered;
            Integer a = d.methods().get(key);
            if (a != null && (a & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
                declares[0] = true;
                if (!d.isInterface()) c = true;
            }
            if (!c) {
                Set<String> itfs = interfaceClosure(d, exclude);
                if (itfs == null) return false;
                for (String i : itfs) {
                    Integer ia = decl(i).methods().get(key);
                    if (ia != null && (ia & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) return false;
                }
            }
            if (!walkSubtypes(sub, key, exclude, c, declares, seen)) return false;
        }
        return true;
    }

    /** 이 형식이 (상위 클래스 사슬까지 포함해) 갖는 인터페이스 전부. exclude 는 건너뛴다(그 위도). 모르는 형식이 끼면 null. */
    private Set<String> interfaceClosure(Decl d0, Set<String> exclude) {
        Set<String> out = new java.util.LinkedHashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>();
        for (Decl d = d0; d != null; ) {
            todo.addAll(d.interfaces());
            if (d.superName() == null) break;
            Decl s = decl(d.superName());
            if (s == null) return null;
            d = s;
        }
        while (!todo.isEmpty()) {
            String i = todo.poll();
            if (exclude != null && exclude.contains(i)) continue;
            if (!out.add(i)) continue;
            Decl di = decl(i);
            if (di == null) return null;
            todo.addAll(di.interfaces());
        }
        return out;
    }

    Decl decl(String n) {
        if (decls.containsKey(n)) return decls.get(n);
        byte[] b = g.existing.bytesOf(n);
        Decl d = b == null ? null : read(b);
        decls.put(n, d);
        return d;
    }

    private static Decl read(byte[] b) {
        Object[] head = new Object[3];
        Map<String, Integer> methods = new HashMap<>();
        Map<String, Integer> fields = new HashMap<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                head[0] = access;
                head[1] = superName;
                head[2] = interfaces == null ? List.of() : List.of(interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.put(name + descriptor, access);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.put(name + descriptor, access);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        @SuppressWarnings("unchecked") List<String> itfs = (List<String>) head[2];
        return new Decl((Integer) head[0], (String) head[1], itfs, methods, fields);
    }

    private void count(String kind) { added.merge(kind, 1, Integer::sum); }

    private void skip(String why, String what) {
        notAdded.merge(why, 1, Integer::sum);
        notAddedList.computeIfAbsent(why, k -> new ArrayList<>()).add(what);
    }

    String summary() {
        int sum = added.values().stream().mapToInt(Integer::intValue).sum();
        return "merged " + mergedClasses + " existing classes (+" + sum + " members " + added + "), " + completeClasses + " already complete, "
                + outsideOverlay.size() + " outside the compat jar; not added " + notAdded;
    }

    void report(StringBuilder sb) {
        sb.append("\n## merged into existing classes (T-261)\n").append(summary()).append("\n");
        sb.append("\n### outside the compat jar (").append(outsideOverlay.size()).append(")\n");
        for (String x : outsideOverlay) sb.append(x).append('\n');
        notAddedList.forEach((k, v) -> {
            sb.append("\n### not added: ").append(k).append(" (").append(v.size()).append(")\n");
            for (String x : v) sb.append(x).append('\n');
        });
    }
}
