package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.tools.extract.FillTable;
import org.domi.furnace.tools.extract.FillTable.Val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** T-262: 채운 껍데기(레코드·열거형 값·정적 리터럴·단순 생성자/읽기)가 진짜로 동작하고, 채우지 않은 것은 여전히 던지는지. */
class FullShellsFillTest {
    @TempDir
    Path tmp;

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t65585\tnet/minecraft/zz/Pos\tjava/lang/Record\t-\t-\t-",
            "F\t18\tnet/minecraft/zz/Pos\tx\tI\t-",
            "F\t18\tnet/minecraft/zz/Pos\tname\tLjava/lang/String;\t-",
            "M\t1\tnet/minecraft/zz/Pos\t<init>\t(ILjava/lang/String;)V\t-",
            "M\t1\tnet/minecraft/zz/Pos\tx\t()I\t-",
            "M\t1\tnet/minecraft/zz/Pos\tname\t()Ljava/lang/String;\t-",
            "M\t1\tnet/minecraft/zz/Pos\tother\t()I\t-",
            "M\t17\tnet/minecraft/zz/Pos\thashCode\t()I\t-",
            "M\t17\tnet/minecraft/zz/Pos\ttoString\t()Ljava/lang/String;\t-",
            "M\t17\tnet/minecraft/zz/Pos\tequals\t(Ljava/lang/Object;)Z\t-",
            "R\tnet/minecraft/zz/Pos\tx:I,name:Ljava/lang/String;",
            "C\t16433\tnet/minecraft/zz/Color\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Color;>;\t-",
            "F\t16409\tnet/minecraft/zz/Color\tBLUE\tLnet/minecraft/zz/Color;\t-",
            "F\t16409\tnet/minecraft/zz/Color\tRED\tLnet/minecraft/zz/Color;\t-",
            "F\t18\tnet/minecraft/zz/Color\trgb\tI\t-",
            "F\t18\tnet/minecraft/zz/Color\tscale\tD\t-",
            "M\t2\tnet/minecraft/zz/Color\t<init>\t(Ljava/lang/String;IID)V\t(ID)V",
            "M\t1\tnet/minecraft/zz/Color\trgb\t()I\t-",
            "M\t9\tnet/minecraft/zz/Color\tvalueOf\t(Ljava/lang/String;)Lnet/minecraft/zz/Color;\t-",
            "M\t9\tnet/minecraft/zz/Color\tvalues\t()[Lnet/minecraft/zz/Color;\t-",
            "E\tnet/minecraft/zz/Color\tRED,BLUE",
            // 상수 하나가 리터럴이 아닌 열거형: 생성자도 읽기도 채우지 않는다
            "C\t16433\tnet/minecraft/zz/Half\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Half;>;\t-",
            "F\t16409\tnet/minecraft/zz/Half\tA\tLnet/minecraft/zz/Half;\t-",
            "F\t16409\tnet/minecraft/zz/Half\tB\tLnet/minecraft/zz/Half;\t-",
            "F\t18\tnet/minecraft/zz/Half\tv\tI\t-",
            "M\t2\tnet/minecraft/zz/Half\t<init>\t(Ljava/lang/String;II)V\t(I)V",
            "M\t1\tnet/minecraft/zz/Half\tv\t()I\t-",
            "E\tnet/minecraft/zz/Half\tA,B",
            "C\t33\tnet/minecraft/zz/Box\tjava/lang/Object\t-\t-\t-",
            "F\t9\tnet/minecraft/zz/Box\tLIMIT\tI\t-",
            "F\t9\tnet/minecraft/zz/Box\tTAG\tLjava/lang/String;\t-",
            "F\t9\tnet/minecraft/zz/Box\tSTATE\tLjava/lang/Object;\t-",
            "F\t2\tnet/minecraft/zz/Box\tw\tJ\t-",
            "M\t1\tnet/minecraft/zz/Box\t<init>\t(J)V\t-",
            "M\t1\tnet/minecraft/zz/Box\tw\t()J\t-",
            "M\t1\tnet/minecraft/zz/Box\tcompute\t()I\t-",
            // 상위 생성자가 채워지지 않은 하위: 생성자도 채우지 않는다
            "C\t33\tnet/minecraft/zz/Sub\tnet/minecraft/zz/Hard\t-\t-\t-",
            "F\t2\tnet/minecraft/zz/Sub\tq\tI\t-",
            "M\t1\tnet/minecraft/zz/Sub\t<init>\t(I)V\t-",
            "M\t1\tnet/minecraft/zz/Sub\tq\t()I\t-",
            "C\t33\tnet/minecraft/zz/Hard\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Hard\t<init>\t()V\t-",
            // 짧은 값 몸통(T-263 G0)
            "C\t33\tnet/minecraft/zz/Knobs\tjava/lang/Object\t-\t-\t-",
            "F\t9\tnet/minecraft/zz/Knobs\tMISSING\tLjava/lang/Object;\t-",
            "M\t1\tnet/minecraft/zz/Knobs\t<init>\t()V\t-",
            "M\t1\tnet/minecraft/zz/Knobs\thook\t()V\t-",
            "M\t1\tnet/minecraft/zz/Knobs\ton\t()Z\t-",
            "M\t1\tnet/minecraft/zz/Knobs\thalf\t()F\t-",
            "M\t1\tnet/minecraft/zz/Knobs\tself\t()Lnet/minecraft/zz/Knobs;\t-",
            "M\t9\tnet/minecraft/zz/Knobs\tpick\t(JI)I\t-",
            "M\t9\tnet/minecraft/zz/Knobs\tlimit\t()I\t-",
            "M\t9\tnet/minecraft/zz/Knobs\tmissing\t()Ljava/lang/Object;\t-",
            "M\t1\tnet/minecraft/zz/Knobs\twasStatic\t()I\t-",
            "");

    static FullShells generator(FillTable ft) throws Exception {
        FullShells g = new FullShells(FullShellsTest.table(PAPER), FullShellsTest.table(FullShellsTest.VANILLA), FullShellsTest.table(FullShellsTest.API),
                new FullShells.Existing(List.of()));
        g.fillTable = ft;
        return g;
    }

    static FillTable fills() {
        FillTable t = new FillTable("test");
        t.add(new FillTable.Ctor("net/minecraft/zz/Pos", "(ILjava/lang/String;)V", "java/lang/Record", "()V", List.of(),
                List.of(new FillTable.Step("x", "I", Val.param(1)), new FillTable.Step("name", "Ljava/lang/String;", Val.param(2)))));
        t.add(new FillTable.Getter("net/minecraft/zz/Pos", "x", "()I", "x", "I"));
        t.add(new FillTable.Getter("net/minecraft/zz/Pos", "name", "()Ljava/lang/String;", "name", "Ljava/lang/String;"));
        t.addObjectMethods("net/minecraft/zz/Pos", List.of("equals", "hashCode", "toString"));
        t.add(new FillTable.Ctor("net/minecraft/zz/Color", "(Ljava/lang/String;IID)V", "java/lang/Enum", "(Ljava/lang/String;I)V", List.of(Val.param(1), Val.param(2)),
                List.of(new FillTable.Step("rgb", "I", Val.param(3)), new FillTable.Step("scale", "D", Val.param(4)))));
        t.add(new FillTable.Getter("net/minecraft/zz/Color", "rgb", "()I", "rgb", "I"));
        t.add(new FillTable.EnumConst("net/minecraft/zz/Color", 0, "RED", "(Ljava/lang/String;IID)V", List.of(Val.lit('i', 0xff0000), Val.lit('d', 1.5))));
        t.add(new FillTable.EnumConst("net/minecraft/zz/Color", 1, "BLUE", "(Ljava/lang/String;IID)V", List.of(Val.lit('i', 0xff), Val.lit('d', 0.25))));
        t.add(new FillTable.Ctor("net/minecraft/zz/Half", "(Ljava/lang/String;II)V", "java/lang/Enum", "(Ljava/lang/String;I)V", List.of(Val.param(1), Val.param(2)),
                List.of(new FillTable.Step("v", "I", Val.param(3)))));
        t.add(new FillTable.Getter("net/minecraft/zz/Half", "v", "()I", "v", "I"));
        t.add(new FillTable.EnumConst("net/minecraft/zz/Half", 0, "A", "(Ljava/lang/String;II)V", List.of(Val.lit('i', 1))));
        t.add(new FillTable.EnumConst("net/minecraft/zz/Half", 1, "B", "(Ljava/lang/String;II)V", null));
        t.add(new FillTable.Static("net/minecraft/zz/Box", "LIMIT", "I", Val.lit('i', 12)));
        t.add(new FillTable.Static("net/minecraft/zz/Box", "TAG", "Ljava/lang/String;", Val.lit('s', "tg")));
        t.add(new FillTable.Static("net/minecraft/zz/Box", "STATE", "Ljava/lang/Object;", Val.lit('n', null)));
        t.add(new FillTable.Ctor("net/minecraft/zz/Box", "(J)V", "java/lang/Object", "()V", List.of(), List.of(new FillTable.Step("w", "J", Val.param(1)))));
        t.add(new FillTable.Getter("net/minecraft/zz/Box", "w", "()J", "w", "J"));
        t.add(new FillTable.Ctor("net/minecraft/zz/Sub", "(I)V", "net/minecraft/zz/Hard", "()V", List.of(), List.of(new FillTable.Step("q", "I", Val.param(1)))));
        t.add(new FillTable.Getter("net/minecraft/zz/Sub", "q", "()I", "q", "I"));
        String k = "net/minecraft/zz/Knobs";
        t.add(new FillTable.Ctor(k, "()V", "java/lang/Object", "()V", List.of(), List.of()));
        t.add(new FillTable.Method(k, "hook", "()V", false, 'E', "-"));
        t.add(new FillTable.Method(k, "on", "()Z", false, 'K', "i:1"));
        t.add(new FillTable.Method(k, "half", "()F", false, 'K', Val.lit('f', 0.5F).encode()));
        t.add(new FillTable.Method(k, "self", "()Lnet/minecraft/zz/Knobs;", false, 'A', "p:0"));
        t.add(new FillTable.Method(k, "pick", "(JI)I", true, 'A', "p:2"));
        t.add(new FillTable.Method(k, "limit", "()I", true, 'R', "net/minecraft/zz/Box.LIMIT:I"));       // 정적 리터럴로 살아 있음
        t.add(new FillTable.Method(k, "missing", "()Ljava/lang/Object;", true, 'R', k + ".MISSING:Ljava/lang/Object;"));   // 값 없는 필드 → 채우지 않음
        t.add(new FillTable.Method(k, "wasStatic", "()I", true, 'K', "i:3"));                            // 정적 여부가 Paper 와 다름 → 채우지 않음
        return t;
    }

    ClassLoader load(Map<String, byte[]> classes) throws Exception {
        Path out = tmp.resolve("out");
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
    }

    /** 표지가 붙은 메서드 이름. */
    static java.util.Set<String> marked(byte[] b) {
        java.util.Set<String> r = new java.util.TreeSet<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) r.add(name);
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        return r;
    }

    @Test
    void recordWorksLikeVanilla() throws Exception {
        FullShells g = generator(fills());
        Map<String, byte[]> out = g.generate();
        ClassLoader cl = load(out);
        Class<?> pos = Class.forName("net.minecraft.zz.Pos", true, cl);
        var k = pos.getConstructor(int.class, String.class);
        Object a = k.newInstance(3, "p");
        Object b = k.newInstance(3, "p");
        Object c = k.newInstance(4, "p");
        assertEquals(3, pos.getMethod("x").invoke(a));
        assertEquals("p", pos.getMethod("name").invoke(a));
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("Pos[x=3, name=p]", a.toString());
        // 채우지 않은 멤버는 그대로 던지고 표지가 남는다
        var ex = assertThrows(InvocationTargetException.class, () -> pos.getMethod("other").invoke(a));
        assertTrue(ex.getCause() instanceof UnsupportedNmsCall);
        assertEquals(java.util.Set.of("other"), marked(out.get("net/minecraft/zz/Pos")));
    }

    @Test
    void enumConstantsCarryTheVanillaValues() throws Exception {
        FullShells g = generator(fills());
        Map<String, byte[]> out = g.generate();
        ClassLoader cl = load(out);
        Class<?> color = Class.forName("net.minecraft.zz.Color", true, cl);
        Object red = color.getField("RED").get(null);
        Object blue = color.getField("BLUE").get(null);
        assertEquals(0xff0000, color.getMethod("rgb").invoke(red));
        assertEquals(0xff, color.getMethod("rgb").invoke(blue));
        var scale = color.getDeclaredField("scale");
        scale.setAccessible(true);
        assertEquals(1.5, scale.get(red));
        assertEquals(0, ((Enum<?>) red).ordinal());
        assertEquals(1, ((Enum<?>) blue).ordinal());
        assertFalse(marked(out.get("net/minecraft/zz/Color")).contains("rgb"));
        assertFalse(marked(out.get("net/minecraft/zz/Color")).contains("<init>"));
    }

    @Test
    void partiallyFillableEnumStaysAShell() throws Exception {
        FullShells g = generator(fills());
        Map<String, byte[]> out = g.generate();
        // 상수 하나가 리터럴이 아니면 열거형 전체를 채우지 않는다 — 읽기가 0 을 진짜 값처럼 돌려주면 안 된다
        assertTrue(marked(out.get("net/minecraft/zz/Half")).containsAll(List.of("v", "<init>")));
        ClassLoader cl = load(out);
        Class<?> half = Class.forName("net.minecraft.zz.Half", true, cl);
        Object a = half.getField("A").get(null);
        assertThrows(InvocationTargetException.class, () -> half.getMethod("v").invoke(a));
    }

    @Test
    void staticLiteralsAndSimpleCtors() throws Exception {
        FullShells g = generator(fills());
        Map<String, byte[]> out = g.generate();
        ClassLoader cl = load(out);
        Class<?> box = Class.forName("net.minecraft.zz.Box", true, cl);
        assertEquals(12, box.getField("LIMIT").get(null));
        assertEquals("tg", box.getField("TAG").get(null));
        Object o = box.getConstructor(long.class).newInstance(9_000_000_000L);
        assertEquals(9_000_000_000L, box.getMethod("w").invoke(o));
        assertTrue(marked(out.get("net/minecraft/zz/Box")).contains("compute"));
        // 상위(Hard) 생성자가 던지는 껍데기이므로 하위 생성자도 던진다
        assertTrue(marked(out.get("net/minecraft/zz/Sub")).containsAll(List.of("<init>", "q")));
    }

    @Test
    void shortValueBodies() throws Exception {
        FullShells g = generator(fills());
        Map<String, byte[]> out = g.generate();
        ClassLoader cl = load(out);
        Class<?> k = Class.forName("net.minecraft.zz.Knobs", true, cl);
        Object o = k.getConstructor().newInstance();
        k.getMethod("hook").invoke(o);
        assertEquals(true, k.getMethod("on").invoke(o));
        assertEquals(0.5F, k.getMethod("half").invoke(o));
        assertTrue(o == k.getMethod("self").invoke(o));
        assertEquals(7, k.getMethod("pick", long.class, int.class).invoke(null, 1L << 40, 7));
        assertEquals(12, k.getMethod("limit").invoke(null));
        assertEquals(java.util.Set.of("missing", "wasStatic"), marked(out.get("net/minecraft/zz/Knobs")));
        assertEquals(java.util.Map.of('A', 2, 'E', 1, 'K', 2, 'R', 1), g.fills.valueCounts);
    }

    @Test
    void withoutFillTableEverythingStillThrows() throws Exception {
        FullShells g = generator(null);
        Map<String, byte[]> out = g.generate();
        assertTrue(marked(out.get("net/minecraft/zz/Pos")).containsAll(List.of("<init>", "x", "hashCode")));
    }

    @Test
    void sameInputSameBytes() throws Exception {
        Map<String, byte[]> a = generator(fills()).generate();
        Map<String, byte[]> b = generator(fills()).generate();
        for (String k : a.keySet()) org.junit.jupiter.api.Assertions.assertArrayEquals(a.get(k), b.get(k), k);
    }

    @Test
    void baselineFailsOnlyWhenAFillIsLost() throws Exception {
        FullShells g = generator(fills());
        g.generate();
        Map<String, String> now = g.baseline(List.of("1\tzz\tnet/minecraft/zz/"));
        Path dir = tmp.resolve("baseline");
        Files.createDirectories(dir);
        for (var e : now.entrySet()) Files.writeString(dir.resolve(e.getKey()), e.getValue());
        FullShells.checkBaseline(dir, now);   // 같으면 통과
        // 커밋된 쪽이 덜 채웠으면(늘어난 것) 통과, 더 채웠으면(줄어든 것) 실패
        String first = now.values().iterator().next();
        String[] lines = first.split("\n");
        StringBuilder less = new StringBuilder();
        StringBuilder more = new StringBuilder();
        for (String l : lines) {
            if (l.startsWith("c\t")) {
                String[] f = l.split("\t");
                less.append("c\t").append(f[1]).append("\t").append(f[2]).append("\t0\n");
                more.append("c\t").append(f[1]).append("\t").append(f[2]).append("\t").append(Integer.parseInt(f[3]) + 5).append("\n");
            }
        }
        String file = now.keySet().iterator().next();
        Files.writeString(dir.resolve(file), less.toString());
        FullShells.checkBaseline(dir, now);
        Files.writeString(dir.resolve(file), more.toString());
        assertThrows(IllegalStateException.class, () -> FullShells.checkBaseline(dir, now));
        // 껍데기에서 빠진 클래스(지금 표에 없음)는 괜찮다
        Files.writeString(dir.resolve(file), "c\tdeadbeef\t1\t9\n");
        FullShells.checkBaseline(dir, now);
    }
}
