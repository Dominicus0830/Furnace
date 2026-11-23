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
}
