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
//
// T-264 P3 핸들 다리: CraftBukkit 클래스(org/bukkit/craftbukkit/)의 빠진 인스턴스 메서드가 "상위 손 클래스 접근자 + NMS 반환형으로 좁힘" 꼴이면
// (상위 사슬에서 가장 가까운 클래스가 같은 이름·인자의 메서드를 반환형 R' 로 선언하고 Paper 반환형 R 이 Paper 계층에서 R' 의 하위) 던지는 몸통 대신
// 다리 몸통을 낸다: 상위 접근자를 불러 null 이거나 R 이면 R 로 돌려주고, 아니면 껍데기와 같은 UnsupportedNmsCall 로 던진다(형 변환 실패를
// ClassCastException 으로 새게 하지 않는다). 표지는 @CraftBridge(진짜 동작 — @NmsShellMember 아님). 설계: compat-craft-hierarchy-t264 3.4.
// 또 Paper 클래스의 컴파일러 다리(공변 반환의 넓은 서술자 — 서명표에는 합성 멤버가 없어 Paper 계층의 덮어쓰기 관계로 다시 계산한다)도 javac 처럼 낸다:
// 같은 클래스의 진짜 메서드나 위의 좁힘 다리를 불러 그대로 돌려준다(넓히기라 검사가 필요 없다). 이미 상위에서 찾아지면 넣지 않는다.
// 예: CraftWorld#getHandle()WorldGenLevel(Paper 상위 CraftRegionAccessor 의 서술자) → getHandle()ServerLevel.
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

    /** T-264 P3: 다리 몸통 표지(클래스 파일에만 남는다). */
    static final String CRAFT_BRIDGE = "Lorg/domi/furnace/compat/nms/CraftBridge;";
    private static final String CRAFT_PREFIX = "org/bukkit/craftbukkit/";
    /** 낸 다리(소유#이름서술자 — 역할 표의 b 칸). */
    final Set<String> bridges = new java.util.TreeSet<>();
    /** 낸 컴파일러 다리(넓히기 — 역할 표에는 합성이라 없다). */
    final Set<String> compilerBridges = new java.util.TreeSet<>();

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

    private record Add(SigTable.Member m, int access, Object value, boolean shell, Bridge bridge) {
        Add(SigTable.Member m, int access, Object value, boolean shell) { this(m, access, value, shell, null); }
    }

    /** 다리 몸통이 부를 접근자(owner 의 name+desc). widen 이면 같은 클래스의 더 좁은 메서드를 그대로 돌려주고(컴파일러 다리), 아니면 Paper 반환형으로 좁힌다. */
    private record Bridge(String owner, String desc, boolean widen) {
        Bridge(String owner, String desc) { this(owner, desc, false); }
    }

    private byte[] mergeOne(String n, FullShells.Scope scope) {
        Decl own = decl(n);
        if (own == null) return null;
        boolean nms = scope == FullShells.Scope.NMS;
        List<Add> adds = new ArrayList<>();
        boolean craftClass = n.startsWith(CRAFT_PREFIX) && !own.isInterface();
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
            Bridge bridge = isCtor || isStatic || isPrivate || own.isInterface() || !n.startsWith(CRAFT_PREFIX) ? null : narrowingTarget(n, own, m);
            if (bridge != null) {
                adds.add(new Add(m, acc, null, false, bridge));
                bridges.add(n + "#" + m.name() + m.desc());
                count("bridge (narrowing accessor, T-264 P3)");
                continue;
            }
            adds.add(new Add(m, acc, null, true));
            count(isCtor ? "constructor" : isStatic ? "static method" : "method");
        }
        if (craftClass) addCompilerBridges(n, own, adds);
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
                    } else if (a.bridge() != null) {
                        bridgeBody(cv, n, a.access(), a.m(), a.bridge());
                    } else {
                        body(cv, n, a.access(), a.m());
                    }
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    /** 던지는 몸통(FullShells.shellBody 와 같은 모양). 프레임이 필요 없는 곧은 코드라 크기를 직접 적는다. */
    private static void body(ClassVisitor cv, String owner, int acc, SigTable.Member m) {
        MethodVisitor mv = cv.visitMethod(acc, m.name(), m.desc(), m.signature(), null);
        mv.visitAnnotation(FullShells.SHELL_MEMBER, false).visitEnd();
        mv.visitCode();
        mv.visitLdcInsn(owner.replace('/', '.') + "#" + m.name() + m.desc());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, FullShells.SHELL_HELPER, "unsupported", "(Ljava/lang/String;)Ljava/lang/RuntimeException;", false);
        mv.visitInsn(Opcodes.ATHROW);
        int locals = (Type.getArgumentsAndReturnSizes(m.desc()) >> 2) - ((acc & Opcodes.ACC_STATIC) != 0 ? 1 : 0);
        mv.visitMaxs(1, locals);
        mv.visitEnd();
    }

    /**
     * 다리 몸통: {@code this.<name>(args)} 를 상위 접근자(R')로 부르고, null 이거나 R 이면 R 로 돌려준다. 아니면 껍데기와 같은 문구로 던진다.
     * 지역 변수에 아무것도 넣지 않으므로 두 프레임 모두 "들어올 때와 같은 지역 + 스택에 R' 하나"(F_SAME1)다.
     */
    private static void bridgeBody(ClassVisitor cv, String owner, int acc, SigTable.Member m, Bridge b) {
        MethodVisitor mv = cv.visitMethod(acc, m.name(), m.desc(), m.signature(), null);
        mv.visitAnnotation(CRAFT_BRIDGE, false).visitEnd();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1;
        for (Type t : Type.getArgumentTypes(m.desc())) {
            mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
            slot += t.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, b.owner(), m.name(), b.desc(), false);
        if (b.widen()) {
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(Math.max(slot, 1), slot);
            mv.visitEnd();
            return;
        }
        String wide = Type.getReturnType(b.desc()).getInternalName();
        String narrow = Type.getReturnType(m.desc()).getInternalName();
        org.objectweb.asm.Label ok = new org.objectweb.asm.Label();
        org.objectweb.asm.Label fail = new org.objectweb.asm.Label();
        mv.visitInsn(Opcodes.DUP);
        mv.visitJumpInsn(Opcodes.IFNULL, ok);
        mv.visitInsn(Opcodes.DUP);
        mv.visitTypeInsn(Opcodes.INSTANCEOF, narrow);
        mv.visitJumpInsn(Opcodes.IFEQ, fail);
        mv.visitLabel(ok);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{wide});
        mv.visitTypeInsn(Opcodes.CHECKCAST, narrow);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(fail);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{wide});
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn(owner.replace('/', '.') + "#" + m.name() + m.desc());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, FullShells.SHELL_HELPER, "unsupported", "(Ljava/lang/String;)Ljava/lang/RuntimeException;", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(Math.max(slot, 2), slot);
        mv.visitEnd();
    }

    /**
     * 좁힘 다리의 대상: 상위 클래스 사슬(호환 jar 의 원래 바이트)에서 가장 가까운, 같은 이름·인자의 인스턴스 메서드(합성 다리 아님, 이 클래스에서 부를 수
     * 있는 접근)로 반환형 R' 가 참조형이고 Paper 반환형 R 이 Paper 계층에서 R' 의 하위인 것. R 이 실행 때 없으면(만들지 못한 껍데기) 다리를 내지 않는다.
     */
    private Bridge narrowingTarget(String n, Decl own, SigTable.Member m) {
        Type ret = Type.getReturnType(m.desc());
        if (ret.getSort() != Type.OBJECT) return null;
        String narrow = ret.getInternalName();
        // 바닐라 형(net/minecraft)으로 좁히는 것만(핸들 접근자 — shim 객체는 바닐라 클래스라 맞는지 시험이 본다). API·CraftBukkit 형으로 좁히는 것(예: 블록
        // 상태의 getInventory()ChiseledBookshelfInventory)은 Furnace 가 그 형의 객체를 아직 주지 않아 다리가 늘 던질 것이다 — 살아 있다고 세지 않게 껍데기로 둔다(d 칸)
        if (!narrow.startsWith("net/minecraft/") || !available(narrow)) return null;
        String prefix = m.name() + m.desc().substring(0, m.desc().indexOf(')') + 1);
        for (String s = own.superName(); s != null; ) {
            Decl d = decl(s);
            if (d == null) return null;
            for (Map.Entry<String, Integer> e : d.methods().entrySet()) {
                String key = e.getKey();
                int a = e.getValue();
                if (!key.startsWith(prefix) || key.length() == prefix.length()) continue;
                if ((a & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;
                if ((a & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 && !samePackage(s, n)) continue;
                Type wide = Type.getType(key.substring(prefix.length()));
                if (wide.getSort() != Type.OBJECT || wide.getInternalName().equals(narrow)) continue;
                if (paperSubtype(narrow, wide.getInternalName())) return new Bridge(s, key.substring(m.name().length()));
            }
            s = d.superName();
        }
        return null;
    }

    /**
     * javac 가 Paper 클래스에 만든 공변 반환 다리를 다시 만든다. 이 클래스의 Paper 인스턴스 메서드 N(args)R 마다, Paper 상위 형식(클래스·인터페이스)이
     * 같은 이름·인자를 다른 반환형 R'(R 이 R' 의 하위)로 선언하면 N(args)R' 다리가 필요하다. 대상 N(args)R 이 이 클래스의 진짜 메서드(원래 바이트)거나
     * 이번에 낸 좁힘 다리일 때만 낸다 — 던지는 껍데기를 부르는 다리는 살아 있는 것처럼 보이면 안 된다. 이미 있거나 상위에서 찾아지는 서술자는 넣지 않는다.
     */
    private void addCompilerBridges(String n, Decl own, List<Add> adds) {
        Set<String> live = new HashSet<>();
        for (Map.Entry<String, Integer> e : own.methods().entrySet()) {
            if ((e.getValue() & (Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_PRIVATE)) == 0) live.add(e.getKey());
        }
        Set<String> taken = new HashSet<>(own.methods().keySet());
        for (Add a : adds) {
            if (a.m().kind() != 'M') continue;
            taken.add(a.m().name() + a.m().desc());
            if (a.bridge() != null) live.add(a.m().name() + a.m().desc());
        }
        List<Add> more = new ArrayList<>();
        for (SigTable.Member m : g.table(n).members(n)) {
            String key = m.name() + m.desc();
            if (m.kind() != 'M' || !live.contains(key) || m.name().equals("<init>")) continue;
            if ((m.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
            Type ret = Type.getReturnType(m.desc());
            if (ret.getSort() != Type.OBJECT) continue;
            String args = m.desc().substring(0, m.desc().indexOf(')') + 1);
            for (String wideDesc : overriddenDescs(n, m.name(), args)) {
                if (wideDesc.equals(m.desc())) continue;
                String wk = m.name() + wideDesc;
                String what = n + "#" + wk;
                if (taken.contains(wk)) continue;
                Type wr = Type.getReturnType(wideDesc);
                if (wr.getSort() != Type.OBJECT || !paperSubtype(ret.getInternalName(), wr.getInternalName())) continue;
                // 실행 때 계층에서도 하위여야 한다 — Furnace 손 클래스가 Paper 의 인터페이스를 아직 안 가지면(계층 기준선의 interfaces 줄) 다리가 그 형이 아닌 값을 돌려준다
                if (!furnaceSubtype(ret.getInternalName(), wr.getInternalName())) continue;
                if (methodInherited(own, wk) || subtypeClash(n, own, wk) == null) continue;
                int acc = (m.access() & 0xFFFF & ~(Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_FINAL)) | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC;
                more.add(new Add(new SigTable.Member('M', acc, n, m.name(), wideDesc, null), acc, null, false, new Bridge(n, m.desc(), true)));
                taken.add(wk);
                compilerBridges.add(what);
                count("compiler bridge (widening, T-264 P3)");
            }
        }
        adds.addAll(more);
    }

    /** Paper 상위 형식(자기 제외)이 선언한 같은 이름·인자의 인스턴스 메서드 서술자들. */
    private Set<String> overriddenDescs(String n, String name, String args) {
        Set<String> out = new java.util.TreeSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>();
        SigTable.Cls self = g.paper.classes().get(n);
        if (self == null) return out;
        if (self.superName() != null) todo.add(self.superName());
        todo.addAll(self.interfaces());
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String t = todo.poll();
            if (!seen.add(t)) continue;
            SigTable table = g.paper.classes().containsKey(t) ? g.paper : g.api.classes().containsKey(t) ? g.api : null;
            if (table == null) continue;
            for (SigTable.Member mm : table.members(t)) {
                if (mm.kind() == 'M' && mm.name().equals(name) && mm.desc().startsWith(args) && (mm.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) out.add(mm.desc());
            }
            SigTable.Cls c = table.classes().get(t);
            if (c.superName() != null) todo.add(c.superName());
            todo.addAll(c.interfaces());
        }
        return out;
    }

    private boolean available(String type) {
        return type.startsWith("java/") || g.shells.contains(type) || g.existing.info(type) != null;
    }

    private static boolean samePackage(String a, String b) {
        return a.substring(0, Math.max(0, a.lastIndexOf('/'))).equals(b.substring(0, Math.max(0, b.lastIndexOf('/'))));
    }

    /**
     * 실행 때 계층에서 sub 가 sup 의 하위인가: 호환 jar·라이브러리에 있는 형식은 그 바이트의 머리, 껍데기로 만든 형식은 Paper 표(껍데기는 Paper 머리대로
     * 만든다)로 본다. 모르는 형식을 지나면 아니라고 본다.
     */
    private boolean furnaceSubtype(String sub, String sup) {
        if (sup.equals("java/lang/Object")) return true;
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(sub));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String t = todo.poll();
            if (!seen.add(t)) continue;
            if (t.equals(sup)) return true;
            Decl d = g.shells.contains(t) ? null : decl(t);
            if (d != null) {
                if (d.superName() != null) todo.add(d.superName());
                todo.addAll(d.interfaces());
                continue;
            }
            if (!g.shells.contains(t)) continue;
            SigTable.Cls c = g.table(t).classes().get(t);
            if (c == null) continue;
            if (c.superName() != null) todo.add(c.superName());
            todo.addAll(c.interfaces());
        }
        return false;
    }

    /** Paper 서버 표(없으면 API 표)의 계층에서 sub 가 sup 의 하위(같음 포함)인가. Object 는 모두의 상위. */
    private boolean paperSubtype(String sub, String sup) {
        if (sup.equals("java/lang/Object")) return true;
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(sub));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String t = todo.poll();
            if (!seen.add(t)) continue;
            if (t.equals(sup)) return true;
            SigTable.Cls c = g.paper.classes().get(t);
            if (c == null) c = g.api.classes().get(t);
            if (c == null) continue;
            if (c.superName() != null) todo.add(c.superName());
            todo.addAll(c.interfaces());
        }
        return false;
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
        sb.append("\n### narrowing bridges (T-264 P3) (").append(bridges.size()).append(")\n");
        for (String x : bridges) sb.append(x).append('\n');
        sb.append("\n### compiler bridges (T-264 P3) (").append(compilerBridges.size()).append(")\n");
        for (String x : compilerBridges) sb.append(x).append('\n');
        sb.append("\n### outside the compat jar (").append(outsideOverlay.size()).append(")\n");
        for (String x : outsideOverlay) sb.append(x).append('\n');
        notAddedList.forEach((k, v) -> {
            sb.append("\n### not added: ").append(k).append(" (").append(v.size()).append(")\n");
            for (String x : v) sb.append(x).append('\n');
        });
    }
}
