package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 실제 빌드 대조(T-229·T-232): 생성기가 만들거나 합친 클래스의 컴파일 결과가 잠금 파일(= 바닐라·Paper 선언)과 같고, 명세 멤버가 그 서술자·접근으로 있으며,
 * 소유 검사 줄(read=owner·write)은 몸통 첫 호출이 소유 규칙이고, 생성 몸통에 리플렉션·invokedynamic 이 없다. 잠금 파일이 서명표와 같은지는
 * CI vanilla-gates 의 shimLockCheck 가 본다(서명표가 필요해서).
 */
class ShimSpecBuildTest {

    static final int CLASS_MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ENUM
            | Opcodes.ACC_ANNOTATION;
    static final int INNER_MASK = CLASS_MASK | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC;
    static final int MEMBER_MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
            | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_ENUM | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT;

    record Compiled(int access, int innerAccess, String superName, List<String> interfaces, String signature,
                    Map<String, Integer> members, Map<String, List<String>> firstCalls, Map<String, List<String>> badInsns) {}

    static Compiled read(String internalName) throws IOException {
        try (InputStream in = ShimSpecBuildTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, "컴파일된 클래스가 없다: " + internalName);
            return read(internalName, in.readAllBytes());
        }
    }

    /**
     * read=owner·write 줄이면 컴파일된 몸통의 첫 호출이 소유 규칙(owner.read·write)인지 — 생성 몸통이든 손 몸통(@hand·@fast)이든 같은 규칙.
     * 문제 글, 맞거나 검사 종류가 아니면 null. ShimGeneratorTest 가 합성 손 조각으로 이 함수를 직접 돌린다.
     */
    static String ownerCheckProblem(Compiled got, String n, String key, ShimSpec.Cls sc, ShimSpec.Member sm) {
        if (sm.kind() == null || !sm.kind().checked()) return null;
        String want0 = sc.owner().replace('.', '/') + "." + (sm.kind() == ShimSpec.Kind.WRITE ? "write" : "read");
        List<String> c = got.firstCalls().get(key);
        if (c == null || c.isEmpty() || !c.get(0).equals(want0)) return n + ": " + key + " 의 첫 호출이 소유 검사(" + want0 + ")가 아니다: " + c;
        return null;
    }

    static Compiled read(String internalName, byte[] bytes) {
        {
            ClassReader r = new ClassReader(bytes);
            int[] inner = {-1};
            Map<String, Integer> members = new HashMap<>();
            Map<String, List<String>> calls = new HashMap<>();
            Map<String, List<String>> bad = new HashMap<>();
            r.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitInnerClass(String name, String outer, String innerName, int access) {
                    if (name.equals(internalName)) inner[0] = access;
                }

                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                    members.put("F " + name + " " + desc, access);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    String key = "M " + name + " " + desc;
                    members.put(key, access);
                    List<String> c = new ArrayList<>();
                    List<String> b = new ArrayList<>();
                    calls.put(key, c);
                    bad.put(key, b);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                            c.add(owner + "." + n);
                            if (owner.startsWith("java/lang/reflect/") || owner.equals("java/lang/invoke/MethodHandles") || owner.equals("java/lang/Class") && n.equals("forName")) b.add(owner + "." + n);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... a) { b.add("invokedynamic " + n); }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new Compiled(r.getAccess(), inner[0], r.getSuperName(), List.of(r.getInterfaces()), signature(r), members, calls, bad);
        }
    }

    private static String signature(ClassReader r) {
        String[] s = {null};
        r.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int v, int a, String n, String sig, String sup, String[] itf) { s[0] = sig; }
        }, ClassReader.SKIP_CODE);
        return s[0];
    }

    static ShimLock lock() throws IOException {
        Properties mc = new Properties();
        try (var r = Files.newBufferedReader(Path.of("../gradle/minecraft.properties"), StandardCharsets.UTF_8)) {
            mc.load(r);
        }
        ShimLock l = ShimLock.read(Path.of("src/shimspec/shim-" + mc.getProperty("mc.version") + ".lock"));
        assertNotNull(l, "잠금 파일이 없다");
        return l;
    }

    static List<String> generatedIndex() throws IOException {
        try (InputStream in = ShimSpecBuildTest.class.getClassLoader().getResourceAsStream(ShimGenerator.GENERATED_INDEX)) {
            assertNotNull(in, "생성기 색인이 없다: " + ShimGenerator.GENERATED_INDEX);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    @Test
    void generatedClassesMatchTheLockedDeclarations() throws IOException {
        ShimLock lock = lock();
        ShimSpec spec = ShimSpec.readDir(Path.of("src/shimspec"));
        List<String> idx = generatedIndex();
        assertFalse(idx.isEmpty(), "시범 이전 패키지가 있으니 색인이 비면 안 된다");
        List<String> problems = new ArrayList<>();
        for (String line : idx) {
            String n = line.substring(2);
            ShimLock.Cls want = lock.classes.get(n);
            if (want == null) { problems.add(n + ": 색인에 있으나 잠금 파일에 없다"); continue; }
            Compiled got = read(n);
            if ((got.access() & CLASS_MASK) != (want.access() & CLASS_MASK)) problems.add(n + ": 클래스 접근 " + got.access() + " != " + want.access());
            if (want.innerAccess() >= 0 && (got.innerAccess() & INNER_MASK) != (want.innerAccess() & INNER_MASK)) problems.add(n + ": 안쪽 접근 " + got.innerAccess() + " != " + want.innerAccess());
            if (!java.util.Objects.equals(got.superName(), want.superName())) problems.add(n + ": 상위 " + got.superName() + " != " + want.superName());
            // 조각 머리가 더한 Furnace 전용 인터페이스(org/domi/furnace/…, 예: NmsPacket)는 잠금에 없는 것이 맞다 — 견줄 때 뺀다
            List<String> gotIfaces = got.interfaces().stream().filter(i -> !i.startsWith("org/domi/furnace/")).toList();
            if (!gotIfaces.equals(want.interfaces())) problems.add(n + ": 인터페이스 " + got.interfaces() + " != " + want.interfaces());
            String gotSig = got.signature() == null ? null : got.signature().replaceAll("Lorg/domi/furnace/[A-Za-z0-9_/$]+;", "");
            if (!java.util.Objects.equals(gotSig, want.signature())) problems.add(n + ": 제네릭 서명 " + got.signature() + " != " + want.signature());
            ShimSpec.Cls sc = spec.classes.get(n.replace('/', '.'));
            for (ShimLock.Member m : lock.members(n)) {
                String key = m.kind() + " " + m.name() + " " + m.desc();
                Integer acc = got.members().get(key);
                if (acc == null) { problems.add(n + ": 명세 멤버가 없다 " + key); continue; }
                if ((acc & MEMBER_MASK) != (m.access() & MEMBER_MASK)) problems.add(n + ": 멤버 접근 " + key + " " + acc + " != " + m.access());
                ShimSpec.Member sm = sc == null ? null : sc.members().stream().filter(x -> x.name().equals(m.name()) && (x.desc() == null
                        || (m.kind() == 'F' ? x.fieldDesc() && x.desc().equals(m.desc()) : !x.fieldDesc() && m.desc().startsWith(x.desc())))).findFirst().orElse(null);
                if (sm == null || m.kind() != 'M') continue;
                boolean generatedBody = sm.body() == ShimSpec.Body.DELEGATE || sm.body() == ShimSpec.Body.SELF || sm.body() == ShimSpec.Body.EMPTY;
                // read=owner·write 는 생성 몸통이든 손 몸통(@hand·@fast)이든 첫 호출이 소유 검사여야 한다
                if (generatedBody && !got.badInsns().get(key).isEmpty()) problems.add(n + ": 생성 몸통에 리플렉션·invokedynamic " + key + " " + got.badInsns().get(key));
                String own = ownerCheckProblem(got, n, key, sc, sm);
                if (own != null) problems.add(own);
            }
        }
        assertEquals(List.of(), problems);
    }
}
