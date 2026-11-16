package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 도구별 컴파일: 한 도구가 깨져도 다른 도구는 돌고, 실패는 도구별로 남는다(U-02 의 재료). */
class RunnerCompileTest {
    private static final String VANILLA = "package furnacetools; public class Vanilla { public static String mc() { return \"x\"; } }";
    private static final String OK = """
            package furnacetools;
            public class OkTool {
                public static void main(String[] a) throws Exception {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), "ok " + Vanilla.mc());
                }
            }
            """;
    private static final String BROKEN = "package furnacetools; public class BrokenTool { void f() { net.minecraft.NoSuchClass.go(); } }";

    private static InputStream src(Map<String, String> m, String name) {
        String s = m.get(name);
        return s == null ? null : new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static Tool tool(String id, String cls) {
        return new Tool(id, Tool.Kind.JAVA, "furnacetools." + cls, List.of(Tool.Output.of("out/" + id + "-{mc}.txt")), List.of(), true, id);
    }

    private static Runner runner(Path dir, Map<String, String> sources) {
        Options o = Options.parse(new String[] {"--mc", "9.9", "--cache", dir.resolve("cache").toString(), "--repo", dir.toString(), "--out", dir.resolve("out").toString()});
        Prepared p = new Prepared("9.9", dir.resolve("none.jar"), dir.resolve("none.jar"), null, List.of(), dir.resolve("gen"), 1, false);
        return new Runner(o, p, name -> src(sources, name));
    }

    @Test
    void brokenToolDoesNotStopTheOthers(@TempDir Path dir) throws Exception {
        Runner r = runner(dir, Map.of("Vanilla", VANILLA, "OkTool", OK, "BrokenTool", BROKEN));
        Tool ok = tool("ok", "OkTool");
        Tool broken = tool("broken", "BrokenTool");
        r.compile(List.of(broken, ok));
        assertEquals(List.of("broken"), List.copyOf(r.compileFailures().keySet()));
        assertTrue(r.compileFailures().get("broken").contains("NoSuchClass"), r.compileFailures().get("broken"));
        assertTrue(Files.isRegularFile(dir.resolve("cache/9.9/exporter-compile-BrokenTool.log")));

        Runner.Result rb = r.run(broken);
        assertEquals(Runner.Status.COMPILE_FAILED, rb.status());
        assertFalse(rb.ok());
        Runner.Result ro = r.run(ok);
        assertEquals(Runner.Status.OK, ro.status(), ro.error());
        assertEquals("ok x", Files.readString(ro.outputs().get(0)));
    }

    @Test
    void brokenHelperFailsEveryToolWithTheSameReason(@TempDir Path dir) throws Exception {
        Runner r = runner(dir, Map.of("Vanilla", "package furnacetools; public class Vanilla { Missing m; }", "OkTool", OK));
        r.compile(List.of(tool("ok", "OkTool")));
        assertTrue(r.compileFailures().get("ok").startsWith("공용 도우미 Vanilla 컴파일 실패"));
        assertEquals(Runner.Status.COMPILE_FAILED, r.run(tool("ok", "OkTool")).status());
    }

    @Test
    void runFailureIsReportedSeparatelyFromCompileFailure(@TempDir Path dir) throws Exception {
        String boom = "package furnacetools; public class Boom { public static void main(String[] a) { throw new RuntimeException(\"boom\"); } }";
        Runner r = runner(dir, Map.of("Vanilla", VANILLA, "Boom", boom));
        Tool t = tool("boom", "Boom");
        r.compile(List.of(t));
        assertTrue(r.compileFailures().isEmpty());
        assertEquals(Runner.Status.RUN_FAILED, r.run(t).status());
    }

    @Test
    void toolWithScratchOutputGetsItsFolder(@TempDir Path dir) throws Exception {
        // shape-vectors 처럼 결과 목록이 없고 출력 파일을 {scratch} 아래에 두는 도구 — 폴더가 없으면 도구가 FileNotFoundException 으로 죽는다.
        String writer = """
                package furnacetools;
                public class Scratchy {
                    public static void main(String[] a) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), String.join(",", a));
                    }
                }
                """;
        Runner r = runner(dir, Map.of("Vanilla", VANILLA, "Scratchy", writer));
        Tool t = new Tool("shape-vectors", Tool.Kind.JAVA, "furnacetools.Scratchy", List.of(), List.of("{scratch}/shape-vectors-{mc}.txt", "{tool-args}"), false, "x");
        r.compile(List.of(t));
        Runner.Result res = r.run(t);
        assertEquals(Runner.Status.OK, res.status(), res.error());
        assertTrue(Files.isRegularFile(dir.resolve("out/scratch/shape-vectors-9.9.txt")));
    }

    @Test
    void shapeVectorsNeedsFourArgsAndOnlyThenIsBaselineable() {
        Options none = Options.parse(new String[] {"--mc", "1.21.1", "--tools", "shape-vectors"});
        assertThrows(IllegalArgumentException.class, () -> Main.select(none));
        Tool sv = Tools.all().get("shape-vectors");
        assertFalse(Main.baselineable(sv, none));
        Options full = Options.parse(new String[] {"--mc", "1.21.1", "--tools", "shape-vectors", "--tool-arg", "shape-vectors=a", "--tool-arg", "shape-vectors=b",
                "--tool-arg", "shape-vectors=2", "--tool-arg", "shape-vectors=3"});
        assertEquals(List.of(sv), Main.select(full));
        assertTrue(Main.baselineable(sv, full));
        assertTrue(Main.javaTools().contains(sv));
        assertFalse(Main.baselineable(Tools.all().get("tags"), full));
    }
}
