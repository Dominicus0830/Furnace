package org.domi.furnace.compat.shimgen;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.compat.nms.NmsShellRefCheck;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.tools.extract.SigTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 껍데기 참조 틀(설계 docs/roadmap/shell-refs-and-field-redirect-2026-11-27.md 2장): 명세 ref 줄 → 컴파일용 틀(@NmsShellRef, 던지는 몸통, 정적 필드 값 없음)
 * → 손 코드가 이름으로 쓴다 → 전체 껍데기 생성기가 틀을 "이미 있음"으로 보지 않고 껍데기를 만들고(합치지 않음) 검사 1~3 → 껍데기가 앞인 로더에서 진짜 껍데기가 이긴다.
 */
class ShellRefTest {

    @TempDir
    Path tmp;

    static final String SPEC = """
            class net.minecraft.zz.Host
            ref net.minecraft.zz.Host$Color
              RED
              id
              values
            ref net.minecraft.zz.Lone
              ZERO
              make
            """;

    static final String LOCK = String.join("\n",
            "#furnace-shim-lock\t1\ttest",
            "C\tnet/minecraft/zz/Host\t33\tjava/lang/Object\t-\t-\t-\tt",
            "C\tnet/minecraft/zz/Host$Color\t16433\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Host$Color;>;\t16409\tt",
            "Q\tnet/minecraft/zz/Host$Color",
            "E\tnet/minecraft/zz/Host$Color\t2",
            "F\tnet/minecraft/zz/Host$Color\tRED\tLnet/minecraft/zz/Host$Color;\t16409\t-",
            "M\tnet/minecraft/zz/Host$Color\tid\t()I\t1\t-",
            "M\tnet/minecraft/zz/Host$Color\tvalues\t()[Lnet/minecraft/zz/Host$Color;\t9\t-",
            "C\tnet/minecraft/zz/Lone\t33\tjava/lang/Object\t-\t-\t-\tt",
            "Q\tnet/minecraft/zz/Lone",
            "F\tnet/minecraft/zz/Lone\tZERO\tLnet/minecraft/zz/Lone;\t25\t-",
            "M\tnet/minecraft/zz/Lone\tmake\t()Lnet/minecraft/zz/Lone;\t9\t-",
            "");

    /** Paper 서명표: 진짜 클래스(열거형 상수 둘, 바닐라 순서 RED, BLUE). */
    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\tnet/minecraft/zz/Host\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Host\t<init>\t()V\t-",
            "C\t16433\tnet/minecraft/zz/Host$Color\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Host$Color;>;\t16409",
            "F\t16409\tnet/minecraft/zz/Host$Color\tBLUE\tLnet/minecraft/zz/Host$Color;\t-",
            "F\t16409\tnet/minecraft/zz/Host$Color\tRED\tLnet/minecraft/zz/Host$Color;\t-",
            "M\t2\tnet/minecraft/zz/Host$Color\t<init>\t(Ljava/lang/String;I)V\t-",
            "M\t1\tnet/minecraft/zz/Host$Color\tid\t()I\t-",
            "M\t9\tnet/minecraft/zz/Host$Color\tvalueOf\t(Ljava/lang/String;)Lnet/minecraft/zz/Host$Color;\t-",
            "M\t9\tnet/minecraft/zz/Host$Color\tvalues\t()[Lnet/minecraft/zz/Host$Color;\t-",
            "E\tnet/minecraft/zz/Host$Color\tRED,BLUE",
            "C\t33\tnet/minecraft/zz/Lone\tjava/lang/Object\t-\t-\t-",
            "F\t25\tnet/minecraft/zz/Lone\tZERO\tLnet/minecraft/zz/Lone;\t-",
            "M\t1\tnet/minecraft/zz/Lone\t<init>\t()V\t-",
            "M\t9\tnet/minecraft/zz/Lone\tmake\t()Lnet/minecraft/zz/Lone;\t-",
            "");

    static final String EMPTY = "#furnace-sig-table\t2\tempty\n";

    /** 손 코드(살아 있는 멤버만 쓴다): 열거형 상수와 values(). */
    static final String USER_LIVE = """
            package org.domi.furnace.compat.zz;
            import net.minecraft.zz.Host;
            public final class RefUser {
                public static int count() { return Host.Color.values().length; }
                public static Object red() { return Host.Color.RED; }
                public static java.util.function.Supplier<Object[]> all() { return Host.Color::values; }
            }
            """;

    /** 손 코드(던지는 껍데기 멤버를 쓴다): 채움·연결 없는 id(), make(), 값 없는 ZERO. */
    static final String USER_DEAD = """
            package org.domi.furnace.compat.zz;
            import net.minecraft.zz.Host;
            import net.minecraft.zz.Lone;
            public final class RefUser {
                public static int id() { return Host.Color.RED.id(); }
                public static Object make() { return Lone.make(); }
                public static Object zero() { return Lone.ZERO; }
            }
            """;

    record Built(ShimGenerator.Result result, Path compat) {}

    /** 명세 + 잠금 → 생성 소스 + 손 코드 → javac → 호환 클래스 폴더(틀 목록 자원 포함). */
    Built build(String userSource) throws Exception {
        ShimSpec spec = new ShimSpec();
        spec.parse("t.spec", SPEC);
        ShimLock lock = ShimLock.parse(LOCK);
        ShimGenerator g = new ShimGenerator(spec, lock, Files.createDirectories(tmp.resolve("frag")), List.of(Files.createDirectories(tmp.resolve("hand"))), List.of());
        ShimGenerator.Result r = g.generate();
        Path src = Files.createDirectories(tmp.resolve("gen"));
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, String> e : r.files().entrySet()) files.add(write(src.resolve(e.getKey() + ".java"), e.getValue()).toString());
        files.add(write(src.resolve("org/domi/furnace/compat/zz/RefUser.java"), userSource).toString());
        Path out = Files.createDirectories(tmp.resolve("compat"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-cp", System.getProperty("java.class.path"), "-encoding", "UTF-8"));
        args.addAll(files);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        assertEquals(0, javac.run(null, null, err, args.toArray(String[]::new)), () -> "생성 소스·손 코드가 컴파일되지 않는다:\n" + err + "\n" + r.files());
        write(out.resolve(ShimGenerator.SHELL_REFS_INDEX), String.join("\n", r.refs()) + "\n");
        return new Built(r, out);
    }

    private static Path write(Path p, String text) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    static SigTable table(String text) throws Exception {
        return SigTable.read(new BufferedReader(new StringReader(text)));
    }

    record Shells(FullShells g, Map<String, byte[]> classes, ShellMerge merge, List<String> problems) {}

    Shells shells(Path compat, String paper) throws Exception {
        FullShells g = new FullShells(table(paper), table(EMPTY), table(EMPTY), new FullShells.Existing(List.of(compat)));
        Map<String, byte[]> classes = g.generate();
        ShellMerge m = g.mergeWith(FullShells.Existing.classesIn(compat), classes);
        return new Shells(g, classes, m, ShellRefChecks.check(g, classes));
    }

    @Test
    void templatesCompileAsThrowingShapesWithMarksAndNoConstants() throws Exception {
        Built b = build(USER_LIVE);
        assertEquals(List.of("net/minecraft/zz/Host$Color", "net/minecraft/zz/Lone"), b.result().refs().stream().sorted().toList());
        assertTrue(b.result().files().containsKey("net/minecraft/zz/Lone"), "최상위 틀은 자기 소스");
        assertFalse(b.result().files().containsKey("net/minecraft/zz/Host$Color"), "안쪽 틀은 바깥(생성 shim) 소스 안");
        assertTrue(b.result().unimplemented().isEmpty(), "틀 멤버는 구현 없음 색인에 적지 않는다(껍데기가 가린다): " + b.result().unimplemented());
        for (String n : b.result().refs()) {
            assertTrue(NmsShellRefCheck.isTemplate(Files.readAllBytes(b.compat().resolve(n + ".class"))), "틀 표지: " + n);
        }
        assertFalse(NmsShellRefCheck.isTemplate(Files.readAllBytes(b.compat().resolve("net/minecraft/zz/Host.class"))), "생성 shim 은 틀이 아니다");
        // 정적 필드는 final·상수값 없이(javac 가 손 코드에 값을 박지 못하게)
        List<String> fields = new ArrayList<>();
        new ClassReader(Files.readAllBytes(b.compat().resolve("net/minecraft/zz/Lone.class"))).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.add(name + " final=" + ((access & Opcodes.ACC_FINAL) != 0) + " value=" + value);
                return null;
            }
        }, 0);
        assertEquals(List.of("ZERO final=false value=null"), fields);
        try (URLClassLoader cl = new URLClassLoader(new URL[]{b.compat().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> lone = cl.loadClass("net.minecraft.zz.Lone");
            InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> lone.getMethod("make").invoke(null));
            assertInstanceOf(UnsupportedNmsCall.class, e.getCause(), "틀만 있으면(껍데기 jar 없는 축소 빌드) 정직하게 던진다");
            assertTrue(Modifier.isPublic(lone.getField("ZERO").getModifiers()));
        }
    }

    @Test
    void specRefMistakesAreRefused() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("r.spec", "ref net.minecraft.A\n  getX -> org.example.X.y pure\n"))
                .getMessage().contains("ref members are names only"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("r.spec", "ref net.minecraft.A\n  <init>\n"))
                .getMessage().contains("ref lines do not list constructors"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("r.spec", "ref net.minecraft.A owner=org.example.O\n"))
                .getMessage().contains("a ref line takes no owner="));
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("r.spec", "class net.minecraft.A\nref net.minecraft.A\n"));
    }

    @Test
    void fullShellsBuildRefsInsteadOfMergingThemAndTheShellWinsAtRunTime() throws Exception {
        Built b = build(USER_LIVE);
        Shells s = shells(b.compat(), PAPER);
        assertEquals(List.of(), s.problems());
        assertEquals(Set.of("net/minecraft/zz/Host$Color", "net/minecraft/zz/Lone"), s.g().existing.refs());
        assertFalse(s.g().existing.has("net/minecraft/zz/Lone"), "틀은 이미 있는 클래스가 아니다");
        assertTrue(s.g().shells.contains("net/minecraft/zz/Host$Color") && s.g().shells.contains("net/minecraft/zz/Lone"), "틀 클래스는 껍데기로 만든다");
        for (String n : s.g().existing.refs()) assertFalse(NmsShellRefCheck.isTemplate(s.classes().get(n)), "틀은 합치지 않는다(합쳤으면 틀 표지가 남는다): " + n);

        Path shells = tmp.resolve("shells");
        for (Map.Entry<String, byte[]> e : s.classes().entrySet()) {
            Path p = shells.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        write(shells.resolve("META-INF/furnace/full-shells/merged-classes.txt"), "");   // 껍데기 jar 표지
        // 껍데기가 앞(서버 순서): 진짜 껍데기가 이긴다 — 바닐라 순서·개수, 반사가 바깥·안쪽 관계를 그대로 본다
        try (URLClassLoader cl = new URLClassLoader(new URL[]{shells.toUri().toURL(), b.compat().toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals(List.of(), NmsShellRefCheck.templatesWinning(cl), "기동 자기 점검 통과");
            Class<?> user = cl.loadClass("org.domi.furnace.compat.zz.RefUser");
            assertEquals(2, user.getMethod("count").invoke(null), "values() 는 껍데기의 상수 둘(틀은 하나만 적었다)");
            Enum<?> red = (Enum<?>) user.getMethod("red").invoke(null);
            assertEquals(0, red.ordinal(), "바닐라 순서의 서수");
            @SuppressWarnings("unchecked")
            Object[] all = ((java.util.function.Supplier<Object[]>) user.getMethod("all").invoke(null)).get();
            assertEquals("[RED, BLUE]", java.util.Arrays.toString(all));
            Class<?> host = cl.loadClass("net.minecraft.zz.Host");
            Class<?> color = cl.loadClass("net.minecraft.zz.Host$Color");
            assertEquals(host, color.getDeclaringClass(), "InnerClasses 가 바깥과 맞는다");
            assertEquals(host, color.getNestHost(), "NestHost 가 바깥(생성 shim 의 NestMembers)과 맞는다");
            assertTrue(List.of(host.getDeclaredClasses()).contains(color));
            assertTrue(Modifier.isStatic(color.getModifiers()) && Modifier.isPublic(color.getModifiers()));
        }
        // 틀이 앞인 로더(순서가 깨진 실행): 기동 자기 점검이 틀을 잡는다
        try (URLClassLoader bad = new URLClassLoader(new URL[]{b.compat().toUri().toURL(), shells.toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals(List.of("net.minecraft.zz.Host$Color", "net.minecraft.zz.Lone"), NmsShellRefCheck.templatesWinning(bad).stream().sorted().toList());
        }
        // 껍데기 jar 가 없는 축소 빌드: 틀이 대체물로 쓰이는 것이 정상 — 점검하지 않는다
        try (URLClassLoader small = new URLClassLoader(new URL[]{b.compat().toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals(List.of(), NmsShellRefCheck.templatesWinning(small));
        }
    }

    @Test
    void usedMembersThatThrowInTheShellStopTheBuild() throws Exception {
        Built b = build(USER_DEAD);
        List<String> p = shells(b.compat(), PAPER).problems();
        assertEquals(3, p.size(), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("throws in the full shell") && x.contains("net/minecraft/zz/Host$Color#id()I")), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("throws in the full shell") && x.contains("net/minecraft/zz/Lone#make()")), String.join("\n", p));
        assertTrue(p.stream().anyMatch(x -> x.contains("throws in the full shell") && x.contains("net/minecraft/zz/Lone#ZERO:")), String.join("\n", p));
    }

    @Test
    void staleTemplatesAndUnbuildableShellsStopTheBuild() throws Exception {
        Built b = build(USER_LIVE);
        // 검사 1: 서명표에서 id 가 정적으로 바뀌었다(낡은 잠금)
        String changed = PAPER.replace("M\t1\tnet/minecraft/zz/Host$Color\tid\t()I\t-", "M\t9\tnet/minecraft/zz/Host$Color\tid\t()I\t-");
        List<String> p = shells(b.compat(), changed).problems();
        assertTrue(p.stream().anyMatch(x -> x.contains("Host$Color#id()I: template access")), String.join("\n", p));
        // 검사 3: 껍데기를 만들 수 없다(상위 형식이 없다)
        String broken = PAPER.replace("C\t33\tnet/minecraft/zz/Lone\tjava/lang/Object", "C\t33\tnet/minecraft/zz/Lone\tcom/example/zz/Missing");
        List<String> q = shells(b.compat(), broken).problems();
        assertTrue(q.stream().anyMatch(x -> x.contains("ref net/minecraft/zz/Lone: the full shell is not built (supertype not in compat")), String.join("\n", q));
        // 검사 1(안쪽): 바깥과 껍데기 안쪽의 InnerClasses 가 다르면 멈춘다 — 서명표의 안쪽 접근이 바뀌었다
        String inner = PAPER.replace("Lnet/minecraft/zz/Host$Color;>;\t16409", "Lnet/minecraft/zz/Host$Color;>;\t16401");
        List<String> r = shells(b.compat(), inner).problems();
        assertTrue(r.stream().anyMatch(x -> x.contains("InnerClasses differ")), String.join("\n", r));
    }
}
