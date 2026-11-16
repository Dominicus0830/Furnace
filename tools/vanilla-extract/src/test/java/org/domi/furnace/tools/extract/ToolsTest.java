package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 도구 목록이 실제 소스·결과 이름과 어긋나지 않는지(등록만 하고 소스를 빠뜨리는 실수) 본다. */
class ToolsTest {
    @Test
    void everyToolHasItsSourceResource() throws IOException {
        assertTrue(Tools.all().size() >= 25);
        for (Tool t : Tools.all().values()) {
            String res = t.kind() == Tool.Kind.JAVA
                    ? "/exporters/furnacetools/" + t.entry().substring("furnacetools.".length()) + ".java"
                    : "/python/" + t.entry();
            try (InputStream in = ToolsTest.class.getResourceAsStream(res)) {
                assertNotNull(in, t.id() + " 의 소스가 없다: " + res);
            }
        }
        try (InputStream in = ToolsTest.class.getResourceAsStream("/exporters/furnacetools/Vanilla.java")) {
            assertNotNull(in, "공용 도우미 Vanilla.java");
        }
    }

    @Test
    void outputsAreUniqueAndVersioned() {
        Set<String> seen = new HashSet<>();
        for (Tool t : Tools.all().values()) {
            for (Tool.Output o : t.outputs()) {
                assertTrue(seen.add(o.dest()), "결과 경로 중복: " + o.dest());
                assertTrue(o.dest().contains("{mc}") || o.dest().contains("{protocol}"), "판 이름 자리가 없다: " + o.dest());
                if (o.gzipAfter()) assertTrue(o.dest().endsWith(".gz"));
            }
        }
    }

    @Test
    void fillsVersionPlaceholders() {
        assertEquals("a/1.21.1.txt", Tools.fill("a/{mc}.txt", "1.21.1", 767));
        assertEquals("protocol-767-ids.txt", Tools.fill("protocol-{protocol}-ids.txt", "1.21.1", 767));
        assertEquals("protocol-unknown-ids.txt", Tools.fill("protocol-{protocol}-ids.txt", "x", -1));
    }

    @Test
    void javaToolsSourceDeclaresTheirClass() throws IOException {
        for (Tool t : Tools.all().values()) {
            if (t.kind() != Tool.Kind.JAVA) continue;
            String simple = t.entry().substring("furnacetools.".length());
            try (InputStream in = ToolsTest.class.getResourceAsStream("/exporters/furnacetools/" + simple + ".java")) {
                String src = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(src.startsWith("package furnacetools;") || src.contains("\npackage furnacetools;"), simple + " 패키지");
                assertTrue(src.contains("class " + simple), simple + " 클래스 이름");
            }
        }
    }
}
