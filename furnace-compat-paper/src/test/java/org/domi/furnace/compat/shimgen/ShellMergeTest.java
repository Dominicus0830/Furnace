package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.tools.extract.SigTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-261: 이미 있는 손 shim 에 빠진 멤버를 껍데기 멤버로 합치기. 작은 가짜 서명표와 ASM 으로 만든 손 shim → 합친 바이트 → 실제 JVM 에 올려 본다.
 * 원래 멤버는 그대로 돌고, 빠진 메서드·생성자·정적 필드는 표지 달린 던지는 몸통·선언, 상수는 값, 인스턴스 필드는 색인만,
 * 상위에서 이미 찾아지는 것·손 하위가 다른 인터페이스 default 로 받는 것은 넣지 않는다(지금 잘 도는 상속을 가리지 않는다).
 */
class ShellMergeTest {

    @TempDir
    Path tmp;

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\tnet/minecraft/zz/Base\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Base\tinh\t()I\t-",
            "C\t1057\tnet/minecraft/zz/Hand\tnet/minecraft/zz/Base\t-\t-\t-",
            "F\t25\tnet/minecraft/zz/Hand\tLIMIT\tI\t-",
            "F\t25\tnet/minecraft/zz/Hand\tINSTANCE\tLnet/minecraft/zz/Hand;\t-",
            "F\t2\tnet/minecraft/zz/Hand\tcount\tI\t-",
            "M\t1\tnet/minecraft/zz/Hand\t<init>\t()V\t-",
            "M\t1\tnet/minecraft/zz/Hand\t<init>\t(IJ)V\t-",
            "M\t1\tnet/minecraft/zz/Hand\tkept\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tadded\t(Ljava/lang/String;J)I\t-",
            "M\t1025\tnet/minecraft/zz/Hand\tabs\t()V\t-",
            "M\t9\tnet/minecraft/zz/Hand\tmake\t(D)Lnet/minecraft/zz/Hand;\t-",
            "M\t17\tnet/minecraft/zz/Hand\tfin\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tinh\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tdm\t()I\t-",
            "M\t4097\tnet/minecraft/zz/Hand\tlambda$x$0\t()V\t-",
            "K\tnet/minecraft/zz/Hand\tLIMIT\tI\t7",
            "C\t1537\tnet/minecraft/zz/HItf\tjava/lang/Object\t-\t-\t-",
            "M\t1025\tnet/minecraft/zz/HItf\tneed\t()I\t-",
            "M\t9\tnet/minecraft/zz/HItf\tof\t()Lnet/minecraft/zz/HItf;\t-",
            "C\t33\tnet/minecraft/zz/Complete\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Complete\t<init>\t()V\t-",
            "");

    static final String EMPTY = "#furnace-sig-table\t2\tempty\n";

    /** 손 shim 들: Base(inh), Hand extends Base(kept), Itf(default dm), Sub extends Hand implements Itf(fin 을 덮음), HItf(빈 인터페이스), Complete. */
    private Path hand() throws Exception {
        Path dir = tmp.resolve("compat");
        write(dir, "net/minecraft/zz/Base", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> {
            ctor(cw, "java/lang/Object");
            ret(cw, Opcodes.ACC_PUBLIC, "inh", 11);
        });
        write(dir, "net/minecraft/zz/Hand", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Base", null, cw -> {
            ctor(cw, "net/minecraft/zz/Base");
            ret(cw, Opcodes.ACC_PUBLIC, "kept", 5);
        });
        write(dir, "net/minecraft/zz/Itf", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object", null,
                cw -> ret(cw, Opcodes.ACC_PUBLIC, "dm", 42));
        write(dir, "net/minecraft/zz/Sub", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Hand", new String[]{"net/minecraft/zz/Itf"}, cw -> {
            ctor(cw, "net/minecraft/zz/Hand");
            ret(cw, Opcodes.ACC_PUBLIC, "fin", 9);
        });
        write(dir, "net/minecraft/zz/HItf", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object", null, cw -> {});
        write(dir, "net/minecraft/zz/Complete", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> ctor(cw, "java/lang/Object"));
        return dir;
    }

    private record Run(FullShells g, ShellMerge merge, Map<String, byte[]> merged, Path compat) {}

    private Run run() throws Exception {
        Path compat = hand();
        FullShells g = new FullShells(table(PAPER), table(EMPTY), table(EMPTY), new FullShells.Existing(List.of(compat)));
        g.generate();
        ShellMerge m = new ShellMerge(g, FullShells.Existing.classesIn(compat));
        return new Run(g, m, m.merge(), compat);
    }

    @Test
    void mergesMissingMembersAndKeepsTheRest() throws Exception {
        Run r = run();
        assertEquals(Set.of("net/minecraft/zz/Hand", "net/minecraft/zz/HItf"), r.merged().keySet(), "Base·Complete 는 빠진 것이 없고 Itf·Sub 는 표에 없다");
        assertEquals(2, r.merge().completeClasses);
        // 상위에서 찾아지는 inh, 손 하위 Sub 가 Itf 의 default 로 받는 dm, 합성 람다는 넣지 않는다
        assertTrue(r.merge().notAddedList.get("method already resolves through a supertype").contains("net/minecraft/zz/Hand#inh()I"));
        assertTrue(r.merge().notAddedList.get("a hand subtype inherits it as a default from another interface").contains("net/minecraft/zz/Hand#dm()I"));
        assertTrue(r.merge().notAddedList.get("synthetic (compiler-made: lambdas, bridges)").contains("net/minecraft/zz/Hand#lambda$x$0()V"));
        // 인스턴스 필드는 색인만
        assertEquals(List.of("net/minecraft/zz/Hand\tcount\tI\t2"), r.merge().instanceFields);

        // 원래 메서드 바이트는 그대로(복사) — 원래 클래스의 메서드가 합친 클래스에 같은 순서로 먼저 있다
        byte[] original = Files.readAllBytes(r.compat().resolve("net/minecraft/zz/Hand.class"));
        assertEquals(methods(original), methods(r.merged().get("net/minecraft/zz/Hand")).subList(0, methods(original).size()));

        // 같은 입력이면 같은 바이트
        Run again = run();
        for (String k : r.merged().keySet()) assertArrayEquals(r.merged().get(k), again.merged().get(k), k);
    }

    @Test
    void mergedClassesLoadAndBehave() throws Exception {
        Run r = run();
        Path out = tmp.resolve("shells");
        for (Map.Entry<String, byte[]> e : r.merged().entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        // 껍데기 폴더가 앞 — 합친 클래스가 원래 클래스를 이긴다(서버의 jar 순서와 같다)
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), r.compat().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> hand = Class.forName("net.minecraft.zz.Hand", true, cl);
            Object h = hand.getConstructor().newInstance();
            assertEquals(5, hand.getMethod("kept").invoke(h), "원래 멤버는 그대로 돈다");
            assertEquals(11, hand.getMethod("inh").invoke(h), "상위의 inh 를 가리지 않는다");
            assertEquals(7, hand.getField("LIMIT").getInt(null), "상수는 값 그대로");
            assertFalse(hasField(hand, "count"), "인스턴스 필드는 넣지 않는다(색인만)");

            InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("added", String.class, long.class).invoke(h, "a", 1L));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("net.minecraft.zz.Hand#added(Ljava/lang/String;J)I"), ite.getCause().getMessage());
            ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("make", double.class).invoke(null, 1.0));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            ite = assertThrows(InvocationTargetException.class, () -> hand.getConstructor(int.class, long.class).newInstance(1, 2L));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            // 추상은 떼어 던지는 몸통(손 하위가 구현하지 않았을 수 있다)
            assertFalse(Modifier.isAbstract(hand.getMethod("abs").getModifiers()));
            ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("abs").invoke(h));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());

            // 손 하위 Sub 는 그대로 올라가고(final 이 아니게 넣었으므로 fin 덮기가 된다) Itf 의 default dm 을 계속 받는다
            Class<?> sub = Class.forName("net.minecraft.zz.Sub", true, cl);
            assertFalse(Modifier.isFinal(hand.getMethod("fin").getModifiers()));
            Object s = sub.getConstructor().newInstance();
            assertEquals(9, sub.getMethod("fin").invoke(s));
            assertEquals(42, sub.getMethod("dm").invoke(s));
            assertTrue(r.g().deviations.get("merged method made non-final: a hand subtype overrides it").contains("net/minecraft/zz/Hand#fin()I"));

            // 인터페이스: 빠진 추상 메서드는 default 던지는 몸통, static 도 던진다
            Class<?> hitf = Class.forName("net.minecraft.zz.HItf", true, cl);
            assertTrue(hitf.getMethod("need").isDefault());
            ite = assertThrows(InvocationTargetException.class, () -> hitf.getMethod("of").invoke(null));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
        }
    }

    @Test
    void addedMembersCarryTheMarkerOriginalsDoNot() throws Exception {
        Run r = run();
        java.util.List<String> marked = new java.util.ArrayList<>();
        java.util.List<String> unmarked = new java.util.ArrayList<>();
        new ClassReader(r.merged().get("net/minecraft/zz/Hand")).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                String k = name;
                unmarked.add(k);
                return new org.objectweb.asm.FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) {
                            unmarked.remove(k);
                            marked.add(k);
                        }
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String k = name + descriptor;
                unmarked.add(k);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) {
                            unmarked.remove(k);
                            marked.add(k);
                        }
                        return null;
                    }
                };
            }
        }, 0);
        assertEquals(Set.of("INSTANCE", "<init>(IJ)V", "added(Ljava/lang/String;J)I", "abs()V", "make(D)Lnet/minecraft/zz/Hand;", "fin()I"), Set.copyOf(marked));
        assertEquals(Set.of("LIMIT", "<init>()V", "kept()I"), Set.copyOf(unmarked), "원래 멤버와 값 있는 상수에는 표지가 없다");
    }

    // ───────────────────────── 도우미 ─────────────────────────

    static SigTable table(String text) throws Exception {
        return SigTable.read(new BufferedReader(new StringReader(text)));
    }

    private static boolean hasField(Class<?> c, String name) {
        for (var f : c.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }

    private static List<String> methods(byte[] b) {
        List<String> out = new java.util.ArrayList<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                out.add(access + " " + name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return out;
    }

    private static void ctor(ClassWriter cw, String sup) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, sup, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void ret(ClassWriter cw, int access, String name, int value) {
        MethodVisitor mv = cw.visitMethod(access, name, "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void write(Path dir, String name, int access, String sup, String[] itfs, Consumer<ClassWriter> body) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, access | ((access & Opcodes.ACC_INTERFACE) == 0 ? Opcodes.ACC_SUPER : 0), name, null, sup, itfs);
        body.accept(cw);
        cw.visitEnd();
        Path p = dir.resolve(name + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, cw.toByteArray());
    }
}
