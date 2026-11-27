package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T-262: 채움 추출기 — 실제 javac 가 만든 클래스에서 알아보는 모양과 거절하는 모양. */
class FillExtractorTest {
    static FillTable extract(Path dir, String name, String src) throws IOException {
        Path f = dir.resolve(name + ".java");
        Files.writeString(f, src);
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, c.run(null, null, null, "-d", dir.toString(), f.toString()));
        FillTable t = new FillTable("t");
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class")).sorted().toList()) FillExtractor.extract(Files.readAllBytes(p), t);
        }
        return t;
    }

    @Test
    void recordPlainMembersAreFound(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "R", "public record R(int a, String b) {}");
        assertEquals(1, t.ctors("R").size());
        assertEquals(List.of("a", "b"), t.ctors("R").get(0).steps().stream().map(FillTable.Step::field).toList());
        assertEquals(List.of("a", "b"), t.getters("R").stream().map(FillTable.Getter::name).sorted().toList());
        assertEquals(List.of("equals", "hashCode", "toString"), t.objectMethods("R"));
    }

    @Test
    void recordWithValidationIsNotPlain(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "R", "public record R(int a) { public R { if (a < 0) throw new IllegalArgumentException(); } public int a() { return a + 1; } public String toString() { return \"x\"; } }");
        assertTrue(t.ctors("R").isEmpty());
        assertTrue(t.getters("R").isEmpty());
        assertEquals(List.of("equals", "hashCode"), t.objectMethods("R"));
    }

    @Test
    void enumConstantsWithLiteralArgs(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "E", "public enum E { A(1, \"x\", 2.5F), B(2, null, -1F); final int i; final String s; final float f;"
                + " E(int i, String s, float f) { this.i = i; this.s = s; this.f = f; } public int i() { return i; } }");
        assertEquals(2, t.enumConsts("E").size());
        assertEquals("A", t.enumConsts("E").get(0).name());
        assertEquals(3, t.enumConsts("E").get(0).args().size());
        FillTable.Ctor c = t.ctors("E").get(0);
        assertEquals("java/lang/Enum", c.superOwner());
        assertEquals(3, c.steps().size());
        assertEquals("i", t.getters("E").get(0).field());
    }

    @Test
    void enumConstantWithNonLiteralArgIsMarked(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "E", "public enum E { A(1), B(Integer.parseInt(\"2\")); final int i; E(int i) { this.i = i; } }");
        assertNotNull(t.enumConsts("E").get(0).args());
        assertNull(t.enumConsts("E").get(1).args());
    }

    @Test
    void staticLiteralsAndFieldInitializers(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "C", "public class C { public static int N = 5; public static String S = \"a\"; public static int M = 1; static { M = 2; }"
                + " public static Object O = new Object(); int x = 7; C() {} }");
        List<String> names = t.statics("C").stream().map(FillTable.Static::field).sorted().toList();
        assertEquals(List.of("N", "S"), names);   // M 은 대입이 둘, O 는 리터럴이 아님
        assertEquals(1, t.ctors("C").size());
        assertEquals("x", t.ctors("C").get(0).steps().get(0).field());
    }

    @Test
    void staticsUnderControlFlowAreNotTaken(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "C", "public class C { static int TERN = Boolean.getBoolean(\"x\") ? 1 : 2; static int COND; static { if (Boolean.getBoolean(\"y\")) COND = 7; }"
                + " static String S; static { try { Integer.parseInt(\"z\"); S = \"tried\"; } catch (Exception e) {} }"
                + " static int LOOP; static { for (int i = 0; i < 0; i++) LOOP = 9; } static int AFTER; static { AFTER = 4; } }");
        // 첫 분기 앞의 직선 구간이 없으므로(삼항이 첫 줄) 아무것도 표에 없다
        assertTrue(t.statics("C").isEmpty(), t.statics("C").toString());
        Files.createDirectories(d.resolve("u"));
        FillTable u = extract(d.resolve("u"), "D", "public class D { static int A = 5; static int T = Boolean.getBoolean(\"x\") ? 1 : 2; static int B = 6; }");
        assertEquals(List.of("A"), u.statics("D").stream().map(FillTable.Static::field).toList());   // B 는 분기 뒤라 받지 않는다
    }

    @Test
    void constructorWithLogicIsNotPlain(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "C", "public class C { int x; C(int a) { this.x = a * 2; } C(int a, int b) { this(a); } }");
        assertTrue(t.ctors("C").isEmpty());
    }

    @Test
    void intersectKeepsOnlyIdenticalLines(@TempDir Path d) throws IOException {
        FillTable a = extract(d, "R", "public record R(int a, String b) {}");
        Files.createDirectories(d.resolve("u"));
        FillTable b = extract(d.resolve("u"), "R", "public record R(int a, String b) { public int a() { return a; } }");
        FillTable both = a.intersect(b);
        assertEquals(a.ctors("R"), both.ctors("R"));
        assertEquals(2, both.getters("R").size());
        FillTable c = extract(d.resolve("u"), "R", "public record R(int a, String b) { public R { if (a < 0) throw new IllegalArgumentException(); } }");
        assertTrue(a.intersect(c).ctors("R").isEmpty());   // 한쪽이 다르거나 없으면 채우지 않는다
    }

    @Test
    void tableRoundTrips(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "E", "public enum E { A(1, \"\\u00e9\\t,\", 2.5F, 3L, 1.5D); E(int i, String s, float f, long l, double dd) { this.i = i; this.s = s; } final int i; final String s; }");
        Path out = d.resolve("fill.tsv.gz");
        t.write(out);
        FillTable r = FillTable.read(out);
        assertEquals(t.enumConsts("E").get(0).args(), r.enumConsts("E").get(0).args());
        assertEquals(t.ctors("E"), r.ctors("E"));
        Path out2 = d.resolve("fill2.tsv.gz");
        r.write(out2);
        assertEquals(java.util.Arrays.hashCode(Files.readAllBytes(out)), java.util.Arrays.hashCode(Files.readAllBytes(out2)));   // 같은 표는 같은 바이트
        assertEquals("\u00e9\t,", r.enumConsts("E").get(0).args().get(1).value());
    }

    @Test
    void shortValueBodies(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "V", "public class V { public static final Object O = new Object(); static int n;"
                + " public void hook() {} public boolean flag() { return true; } public float half() { return 0.5F; } public String s() { return \"x\"; }"
                + " public Object none() { return null; } public V self() { return this; } public long id(long a, int b) { return a; } public static int second(long a, int b) { return b; }"
                + " public static Object obj() { return O; } public int calc() { return n + 1; } public void work() { n++; } public int field() { return n; }"
                + " public String sorry() { try { return s(); } catch (RuntimeException e) { return null; } } }");
        java.util.Map<String, String> got = new java.util.TreeMap<>();
        for (FillTable.Method m : t.methods("V")) got.put(m.name(), (m.isStatic() ? "s " : "i ") + m.kind() + " " + m.value());
        assertEquals(java.util.Map.of(
                "hook", "i E -", "flag", "i K i:1", "half", "i K f:0x3f000000", "s", "i K s:x", "none", "i K n:",
                "self", "i A p:0", "id", "i A p:1", "second", "s A p:2", "obj", "s R V.O:Ljava/lang/Object;", "field", "i R V.n:I"), got);
    }

    @Test
    void interfaceDefaultsAndRoundTripOfValueBodies(@TempDir Path d) throws IOException {
        FillTable t = extract(d, "I", "public interface I { default boolean on() { return false; } default void tick() {} boolean abs(); static int k() { return 7; } }");
        assertEquals(List.of("on", "tick", "k"), t.methods("I").stream().map(FillTable.Method::name).toList());
        Path f = d.resolve("t.tsv.gz");
        t.write(f);
        assertEquals(t.methods("I"), FillTable.read(f).methods("I"));
        FillTable other = new FillTable("o");
        other.add(t.methods("I").get(0));
        assertEquals(1, t.intersect(other).methods("I").size());
    }

    /** S5: 가짜 VoxelShape 와 함께 컴파일한 클래스 S 의 표. */
    static FillTable shapeClass(Path dir, String body) throws IOException {
        Path pkg = dir.resolve("net/minecraft/world/phys/shapes");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("VoxelShape.java"), "package net.minecraft.world.phys.shapes; public class VoxelShape { public static VoxelShape box(double a) { return new VoxelShape(); } }");
        Files.writeString(dir.resolve("S.java"), "import net.minecraft.world.phys.shapes.VoxelShape; public class S { " + body + " }");
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, c.run(null, null, null, "-d", dir.toString(), "-sourcepath", dir.toString(), dir.resolve("S.java").toString()));
        FillTable t = new FillTable("t");
        FillExtractor.extract(Files.readAllBytes(dir.resolve("S.class")), t);
        return t;
    }

    @Test
    void shapeClinitHashFollowsStaticInitOnly(@TempDir Path d) throws IOException {
        String shapes = "static final VoxelShape A = VoxelShape.box(1); static final VoxelShape[] B = make(); static VoxelShape[] make() { return new VoxelShape[] {A}; }";
        FillTable t1 = shapeClass(d.resolve("1"), shapes + " S() { }");
        FillTable t2 = shapeClass(d.resolve("2"), shapes + " S() { System.out.println(); } int other() { return 3; }");
        FillTable t3 = shapeClass(d.resolve("3"), shapes.replace("{A}", "{A, A}") + " S() { }");
        FillTable t4 = shapeClass(d.resolve("4"), "static final int N = 3;");
        assertNotNull(t1.clinitHash("S"));
        assertEquals(t1.clinitHash("S"), t2.clinitHash("S"));   // 생성자·다른 메서드는 해시에 안 든다
        assertTrue(!t1.clinitHash("S").equals(t3.clinitHash("S")));   // 정적 초기화가 부르는 같은 클래스 정적 메서드는 든다
        assertNull(t4.clinitHash("S"));   // 모양 정적 필드가 없으면 H 줄 없음
    }

    @Test
    void shapeLinesSurviveOnlyWhenBothClinitHashesAgree(@TempDir Path d) throws IOException {
        FillTable van = new FillTable("v"), same = new FillTable("p"), other = new FillTable("q");
        van.addClinitHash("S", "aaaa");
        van.add(new FillTable.Shape("S", "A", "Lnet/minecraft/world/phys/shapes/VoxelShape;", "v 0:0,0,0,1,0.5,1"));
        van.add(new FillTable.Shape("S", "B", "Ljava/util/Map;", "m 1 enum e:a/Dir 2 N=E S=B"));
        same.addClinitHash("S", "aaaa");
        other.addClinitHash("S", "bbbb");
        FillTable both = van.intersect(same);
        assertEquals(van.shapes("S"), both.shapes("S"));   // X 줄은 바닐라 표에만 있어도 해시가 같으면 남는다
        assertEquals("aaaa", both.clinitHash("S"));
        assertTrue(van.intersect(other).shapes("S").isEmpty());   // Paper 가 정적 초기화를 바꾼 클래스는 안 채운다
        assertNull(van.intersect(other).clinitHash("S"));
        Path out = d.resolve("fill.tsv.gz");
        van.write(out);
        FillTable r = FillTable.read(out);
        assertEquals(van.shapes("S"), r.shapes("S"));
        assertEquals("aaaa", r.clinitHash("S"));
    }

    @Test
    void shapeNumbersRoundTrip() {
        for (double x : new double[] {0, 1, 0.5, 0.0625, 1.0 / 3, -0.0, -2, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e-7}) {
            assertEquals(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(Double.parseDouble(ShapeValues.num(x))), "x=" + x);
        }
        assertEquals("1", ShapeValues.num(1.0));
        assertEquals("0.5", ShapeValues.num(0.5));
    }
}
