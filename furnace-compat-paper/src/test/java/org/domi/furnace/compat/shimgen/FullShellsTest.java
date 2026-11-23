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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.tools.extract.SigTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 전체 껍데기 생성기(T-260) 시험: 작은 가짜 서명표 → 바이트코드 → 실제 JVM 에 올려 모양·동작을 본다.
 * 열거형 상수 순서·서수, 상수 값, 레코드 성분, 안쪽 클래스, 인터페이스 default·static, 던지는 몸통(생성자 포함), 표지,
 * 손 shim 위에 서는 껍데기(final 메서드 빼기, final 상위면 만들지 않음), 없는 상위 형식, 범위(이미 있음·API·Paper 내부).
 */
class FullShellsTest {

    @TempDir
    Path tmp;

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t1057\tnet/minecraft/zz/Shape\tjava/lang/Object\t-\t-\t-",
            "F\t25\tnet/minecraft/zz/Shape\tLIMIT\tI\t-",
            "F\t25\tnet/minecraft/zz/Shape\tNAME\tLjava/lang/String;\t-",
            "F\t25\tnet/minecraft/zz/Shape\tDEFAULT\tLnet/minecraft/zz/Shape;\t-",
            "F\t1\tnet/minecraft/zz/Shape\tcount\tI\t-",
            "M\t1\tnet/minecraft/zz/Shape\t<init>\t()V\t-",
            "M\t1025\tnet/minecraft/zz/Shape\tdraw\t()V\t-",
            "M\t9\tnet/minecraft/zz/Shape\tof\t(Ljava/lang/String;)Lnet/minecraft/zz/Shape;\t-",
            "M\t1\tnet/minecraft/zz/Shape\tsize\t()I\t-",
            "K\tnet/minecraft/zz/Shape\tLIMIT\tI\t7",
            "K\tnet/minecraft/zz/Shape\tNAME\tLjava/lang/String;\ta\\tb",
            "C\t9\tnet/minecraft/zz/Shape$Inner\tnet/minecraft/zz/Shape\t-\t-\t9",
            "M\t1\tnet/minecraft/zz/Shape$Inner\t<init>\t()V\t-",
            "M\t1\tnet/minecraft/zz/Shape$Inner\tdraw\t()V\t-",
            "C\t16433\tnet/minecraft/zz/Color\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Color;>;\t-",
            "F\t16409\tnet/minecraft/zz/Color\tBLUE\tLnet/minecraft/zz/Color;\t-",
            "F\t16409\tnet/minecraft/zz/Color\tGREEN\tLnet/minecraft/zz/Color;\t-",
            "F\t16409\tnet/minecraft/zz/Color\tRED\tLnet/minecraft/zz/Color;\t-",
            "M\t2\tnet/minecraft/zz/Color\t<init>\t(Ljava/lang/String;ILjava/lang/String;)V\t(Ljava/lang/String;)V",
            "M\t1\tnet/minecraft/zz/Color\thex\t()Ljava/lang/String;\t-",
            "M\t9\tnet/minecraft/zz/Color\tvalueOf\t(Ljava/lang/String;)Lnet/minecraft/zz/Color;\t-",
            "M\t9\tnet/minecraft/zz/Color\tvalues\t()[Lnet/minecraft/zz/Color;\t-",
            "E\tnet/minecraft/zz/Color\tRED,GREEN,BLUE",
            "C\t65585\tnet/minecraft/zz/Pos\tjava/lang/Record\t-\t-\t-",
            "F\t18\tnet/minecraft/zz/Pos\tx\tI\t-",
            "F\t18\tnet/minecraft/zz/Pos\ty\tI\t-",
            "M\t1\tnet/minecraft/zz/Pos\t<init>\t(II)V\t-",
            "M\t1\tnet/minecraft/zz/Pos\tx\t()I\t-",
            "M\t1\tnet/minecraft/zz/Pos\ty\t()I\t-",
            "M\t17\tnet/minecraft/zz/Pos\thashCode\t()I\t-",
            "R\tnet/minecraft/zz/Pos\ty:I,x:I",
            "C\t1537\tnet/minecraft/zz/Drawable\tjava/lang/Object\t-\t-\t-",
            "M\t1025\tnet/minecraft/zz/Drawable\tdraw\t()V\t-",
            "M\t1\tnet/minecraft/zz/Drawable\thint\t()V\t-",
            "M\t9\tnet/minecraft/zz/Drawable\tnone\t()Lnet/minecraft/zz/Drawable;\t-",
            "C\t33\tnet/minecraft/zz/Chunky\tjava/lang/Object\tca/spottedleaf/zz/Patched,net/minecraft/zz/Drawable\t-\t-",
            "M\t1\tnet/minecraft/zz/Chunky\tdraw\t()V\t-",
            "C\t33\tnet/minecraft/zz/OnHand\tnet/minecraft/zz/Hand\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/OnHand\tlock\t()V\t-",
            "M\t1\tnet/minecraft/zz/OnHand\tother\t()V\t-",
            "C\t33\tnet/minecraft/zz/OnFinal\tnet/minecraft/zz/HandFinal\t-\t-\t-",
            "C\t33\tnet/minecraft/zz/UnderFinal\tnet/minecraft/zz/OnFinal\t-\t-\t-",
            "C\t33\tnet/minecraft/zz/Lost\tcom/example/zz/Missing\t-\t-\t-",
            "C\t33\tnet/minecraft/zz/Hand\tjava/lang/Object\t-\t-\t-",
            "C\t33\tnet/minecraft/zz/Present\tjava/lang/Object\t-\t-\t-",
            "C\t33\tio/papermc/paper/zz/Internal\tjava/lang/Object\t-\t-\t-",
            "M\t1\tio/papermc/paper/zz/Internal\t<init>\t()V\t-",
            "C\t1537\tio/papermc/paper/zz/ApiThing\tjava/lang/Object\t-\t-\t-",
            "C\t33\tcom/example/zz/Library\tjava/lang/Object\t-\t-\t-",
            // 상수별 몸통이 있는 추상 열거형: 자기 추상 메서드 apply, 인터페이스 Named 의 label 은 상수 몸통에만 있다
            "C\t17441\tnet/minecraft/zz/Mode\tjava/lang/Enum\tnet/minecraft/zz/Named\t-\t-",
            "F\t16409\tnet/minecraft/zz/Mode\tA\tLnet/minecraft/zz/Mode;\t-",
            "F\t16409\tnet/minecraft/zz/Mode\tB\tLnet/minecraft/zz/Mode;\t-",
            "M\t2\tnet/minecraft/zz/Mode\t<init>\t(Ljava/lang/String;I)V\t-",
            "M\t1025\tnet/minecraft/zz/Mode\tapply\t()V\t-",
            "E\tnet/minecraft/zz/Mode\tA,B",
            "C\t1537\tnet/minecraft/zz/Named\tjava/lang/Object\t-\t-\t-",
            "M\t1025\tnet/minecraft/zz/Named\tlabel\t()Ljava/lang/String;\t-",
            // NMS 밖 범위의 값 없는 정적 필드: 연결기 덫이 안 보므로 클래스 초기화가 던져야 한다
            "C\t33\tio/papermc/paper/zz/Holder\tjava/lang/Object\t-\t-\t-",
            "F\t25\tio/papermc/paper/zz/Holder\tINSTANCE\tLio/papermc/paper/zz/Holder;\t-",
            "F\t25\tio/papermc/paper/zz/Holder\tSIZE\tI\t-",
            "K\tio/papermc/paper/zz/Holder\tSIZE\tI\t3",
            "");

    static final String VANILLA = "#furnace-sig-table\t2\tvanilla-test\n";

    static final String API = String.join("\n",
            "#furnace-sig-table\t2\tpaper-api-test",
            "C\t1537\tio/papermc/paper/zz/ApiThing\tjava/lang/Object\t-\t-\t-",
            "M\t1025\tio/papermc/paper/zz/ApiThing\tname\t()Ljava/lang/String;\t-",
            "");

    static SigTable table(String text) throws Exception {
        return SigTable.read(new BufferedReader(new StringReader(text)));
    }

    /** 이미 있는 손 shim(Hand: final 메서드 lock, HandFinal: final 클래스, Present: 그냥 있음). */
    private Path existing() throws Exception {
        Path dir = tmp.resolve("existing");
        write(dir, "net/minecraft/zz/Hand", Opcodes.ACC_PUBLIC, cw -> {
            ctor(cw, "java/lang/Object");
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "lock", "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        });
        write(dir, "net/minecraft/zz/HandFinal", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, cw -> ctor(cw, "java/lang/Object"));
        write(dir, "net/minecraft/zz/Present", Opcodes.ACC_PUBLIC, cw -> ctor(cw, "java/lang/Object"));
        return dir;
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

    private static void write(Path dir, String name, int access, java.util.function.Consumer<ClassWriter> body) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, access | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        body.accept(cw);
        cw.visitEnd();
        Path p = dir.resolve(name + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, cw.toByteArray());
    }

    private FullShells generator(Path existing) throws Exception {
        return new FullShells(table(PAPER), table(VANILLA), table(API), new FullShells.Existing(List.of(existing)));
    }

    private ClassLoader load(Path existing, Map<String, byte[]> classes) throws Exception {
        Path out = tmp.resolve("out");
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return new URLClassLoader(new URL[]{out.toUri().toURL(), existing.toUri().toURL()}, getClass().getClassLoader());
    }

    @Test
    void scopeAndSkips() throws Exception {
        Path ex = existing();
        FullShells g = generator(ex);
        Map<String, byte[]> out = g.generate();
        // 이미 있는 것은 만들지 않는다. 범위 밖(라이브러리 이름공간)도 만들지 않는다.
        assertFalse(out.containsKey("net/minecraft/zz/Present"));
        assertFalse(out.containsKey("net/minecraft/zz/Hand"));
        assertFalse(out.containsKey("com/example/zz/Library"));
        // final 손 shim 위, 그 하위, 없는 상위 형식은 이유와 함께 뺀다
        assertTrue(g.skipped.get("net/minecraft/zz/OnFinal").startsWith("hand shim superclass is final"), g.skipped.toString());
        assertTrue(g.skipped.get("net/minecraft/zz/UnderFinal").startsWith("supertype not generated"), g.skipped.toString());
        assertTrue(g.skipped.get("net/minecraft/zz/Lost").startsWith("supertype not in compat"), g.skipped.toString());
        // Paper 서버 전용 내부 클래스와 API 범위(선언은 paper-api 표)
        assertEquals(FullShells.Scope.PAPER_INTERNAL, g.scope.get("io/papermc/paper/zz/Internal"));
        assertEquals(FullShells.Scope.API, g.scope.get("io/papermc/paper/zz/ApiThing"));
        assertTrue(out.containsKey("io/papermc/paper/zz/Internal"));
        assertTrue(out.containsKey("io/papermc/paper/zz/ApiThing"));
        // 같은 입력이면 같은 바이트
        Map<String, byte[]> again = generator(ex).generate();
        assertEquals(out.keySet(), again.keySet());
        for (String k : out.keySet()) assertArrayEquals(out.get(k), again.get(k), k);
    }

    @Test
    void shapesLoadAndBehave() throws Exception {
        Path ex = existing();
        FullShells g = generator(ex);
        ClassLoader cl = load(ex, g.generate());

        Class<?> shape = Class.forName("net.minecraft.zz.Shape", true, cl);
        assertTrue(Modifier.isAbstract(shape.getModifiers()));
        assertEquals(7, shape.getField("LIMIT").getInt(null));
        assertEquals("a\tb", shape.getField("NAME").get(null));
        assertTrue(Modifier.isAbstract(shape.getMethod("draw").getModifiers()));
        InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> shape.getMethod("of", String.class).invoke(null, "x"));
        assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
        assertTrue(ite.getCause().getMessage().contains("net.minecraft.zz.Shape#of(Ljava/lang/String;)Lnet/minecraft/zz/Shape;"), ite.getCause().getMessage());

        // 안쪽 클래스: 모양 그대로, 생성자는 상위 생성자를 부르지 않고 던진다(검증기를 통과해야 한다)
        Class<?> inner = Class.forName("net.minecraft.zz.Shape$Inner", true, cl);
        assertEquals(shape, inner.getEnclosingClass());
        assertEquals("Inner", inner.getSimpleName());
        assertTrue(Modifier.isStatic(inner.getModifiers()));
        ite = assertThrows(InvocationTargetException.class, () -> inner.getConstructor().newInstance());
        assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());

        // 열거형: 상수 순서·서수·values·valueOf 가 진짜와 같다(서명표는 이름순이지만 E 줄이 순서를 준다)
        Class<?> color = Class.forName("net.minecraft.zz.Color", true, cl);
        assertTrue(color.isEnum());
        Object[] values = (Object[]) color.getMethod("values").invoke(null);
        assertEquals(List.of("RED", "GREEN", "BLUE"), Arrays.stream(values).map(v -> ((Enum<?>) v).name()).toList());
        assertEquals(1, ((Enum<?>) color.getMethod("valueOf", String.class).invoke(null, "GREEN")).ordinal());
        assertEquals(values[2], color.getField("BLUE").get(null));
        ite = assertThrows(InvocationTargetException.class, () -> color.getMethod("hex").invoke(values[0]));
        assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());

        // 레코드: 성분 순서는 R 줄(선언 순서)
        Class<?> pos = Class.forName("net.minecraft.zz.Pos", true, cl);
        assertTrue(pos.isRecord());
        assertEquals(List.of("y", "x"), Arrays.stream(pos.getRecordComponents()).map(RecordComponent::getName).toList());

        // 인터페이스: 추상은 추상, default·static 은 던지는 몸통
        Class<?> drawable = Class.forName("net.minecraft.zz.Drawable", true, cl);
        assertTrue(drawable.isInterface());
        assertTrue(drawable.getMethod("hint").isDefault());
        ite = assertThrows(InvocationTargetException.class, () -> drawable.getMethod("none").invoke(null));
        assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());

        // Paper 내부 패치 인터페이스(ca/spottedleaf)는 뺀다
        Class<?> chunky = Class.forName("net.minecraft.zz.Chunky", true, cl);
        assertEquals(List.of(drawable), List.of(chunky.getInterfaces()));

        // 손 shim 위: final 메서드는 다시 선언하지 않는다(선언하면 올릴 때 VerifyError)
        Class<?> onHand = Class.forName("net.minecraft.zz.OnHand", true, cl);
        List<String> declared = new ArrayList<>();
        for (Method m : onHand.getDeclaredMethods()) declared.add(m.getName());
        assertEquals(List.of("other"), declared);
        assertTrue(g.deviations.get("method left out: a hand shim superclass declares it final").contains("net/minecraft/zz/OnHand#lock()V"));
    }

    @Test
    void shellMembersCarryTheMarker() throws Exception {
        Map<String, byte[]> out = generator(existing()).generate();
        List<String> marked = new ArrayList<>();
        List<String> unmarked = new ArrayList<>();
        for (String n : List.of("net/minecraft/zz/Shape", "net/minecraft/zz/Color")) {
            new ClassReader(out.get(n)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    String key = n + "." + name;
                    unmarked.add(key);
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                            assertFalse(visible);
                            if (d.equals(FullShells.SHELL_MEMBER)) { marked.add(key); unmarked.remove(key); }
                            return null;
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    String key = n + "." + name;
                    unmarked.add(key);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                            assertFalse(visible);
                            if (d.equals(FullShells.SHELL_MEMBER)) { marked.add(key); unmarked.remove(key); }
                            return null;
                        }
                    };
                }
            }, 0);
        }
        // 동작 없는 것(표지)과 모양 그대로 쓰는 것(상수·추상·열거형 상수와 도우미)
        assertTrue(marked.containsAll(List.of("net/minecraft/zz/Shape.count", "net/minecraft/zz/Shape.DEFAULT", "net/minecraft/zz/Shape.<init>",
                "net/minecraft/zz/Shape.of", "net/minecraft/zz/Shape.size", "net/minecraft/zz/Color.hex")), marked.toString());
        assertTrue(unmarked.containsAll(List.of("net/minecraft/zz/Shape.LIMIT", "net/minecraft/zz/Shape.NAME", "net/minecraft/zz/Shape.draw",
                "net/minecraft/zz/Color.RED", "net/minecraft/zz/Color.values", "net/minecraft/zz/Color.valueOf", "net/minecraft/zz/Color.<clinit>")), unmarked.toString());
    }

    @Test
    void abstractEnumMethodsThrowWithTheMarker() throws Exception {
        Path ex = existing();
        ClassLoader cl = load(ex, generator(ex).generate());
        Class<?> mode = Class.forName("net.minecraft.zz.Mode", true, cl);
        assertFalse(Modifier.isAbstract(mode.getModifiers()));
        Object a = mode.getField("A").get(null);
        // 추상으로 두면 AbstractMethodError(덫·계측 없음) — 같은 오류를 던지는 몸통이어야 한다
        for (String m : List.of("apply", "label")) {
            Method mm = mode.getMethod(m);
            assertFalse(Modifier.isAbstract(mm.getModifiers()), m);
            InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> mm.invoke(a), m);
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause(), m);
        }
        List<String> marked = new ArrayList<>();
        try (var in = cl.getResourceAsStream("net/minecraft/zz/Mode.class")) {
            new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                            if (d.equals(FullShells.SHELL_MEMBER)) marked.add(name);
                            return null;
                        }
                    };
                }
            }, 0);
        }
        assertTrue(marked.containsAll(List.of("apply", "label")), marked.toString());
    }

    @Test
    void nonNmsShellWithValuelessFieldsFailsClassInit() throws Exception {
        Path ex = existing();
        FullShells g = generator(ex);
        ClassLoader cl = load(ex, g.generate());
        // 올리기(초기화 없이)와 instanceof 용 클래스 객체는 된다
        Class<?> holder = Class.forName("io.papermc.paper.zz.Holder", false, cl);
        // 값 없는 정적 필드를 읽으면 null 대신 초기화 실패(예전 "클래스 없음"과 같은 꼴) + 계측
        ExceptionInInitializerError e = assertThrows(ExceptionInInitializerError.class, () -> holder.getField("INSTANCE").get(null));
        assertInstanceOf(UnsupportedNmsCall.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("io.papermc.paper.zz.Holder"), e.getCause().getMessage());
        assertThrows(NoClassDefFoundError.class, () -> holder.getField("INSTANCE").get(null));
        // 값 없는 필드가 없는 NMS 밖 껍데기는 그대로 초기화된다
        Class.forName("io.papermc.paper.zz.Internal", true, cl);
        assertEquals(1, g.initTrapped);
    }

    @Test
    void enumOrderIsRequired() throws Exception {
        String noOrder = PAPER.replace("E\tnet/minecraft/zz/Color\tRED,GREEN,BLUE\n", "");
        FullShells g = new FullShells(table(noOrder), table(VANILLA), table(API), new FullShells.Existing(List.of(existing())));
        IllegalStateException e = assertThrows(IllegalStateException.class, g::generate);
        assertTrue(e.getMessage().contains("no enum constant order"), e.getMessage());
    }

    @Test
    void constantEncodingRoundTrips() throws Exception {
        // 서명표 형식 2 의 K 줄: 실수는 비트 그대로, 문자열은 탈출 문자로(되읽으면 같은 값)
        String t = String.join("\n", "#furnace-sig-table\t2\tx",
                "C\t33\ta/K\tjava/lang/Object\t-\t-\t-",
                "F\t25\ta/K\tF\tF\t-", "F\t25\ta/K\tD\tD\t-", "F\t25\ta/K\tJ\tJ\t-", "F\t25\ta/K\tS\tLjava/lang/String;\t-",
                "K\ta/K\tF\tF\t0x7fc00001", "K\ta/K\tD\tD\t0x8000000000000000", "K\ta/K\tJ\tJ\t-9007199254740993", "K\ta/K\tS\tLjava/lang/String;\tq\\\\\\n\\u0001", "");
        SigTable s = table(t);
        assertEquals(0x7fc00001, Float.floatToRawIntBits((Float) s.constantValue("a/K", "F")));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits((Double) s.constantValue("a/K", "D")));
        assertEquals(-9007199254740993L, s.constantValue("a/K", "J"));
        assertEquals("q\\\n\u0001", s.constantValue("a/K", "S"));
    }
}
