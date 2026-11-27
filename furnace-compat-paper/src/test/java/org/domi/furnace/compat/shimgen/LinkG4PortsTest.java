package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T-263 S6 G4 서버 이식 짝: 특징·점수와 @VanillaPort 읽기. */
class LinkG4PortsTest {

    /** 바닐라 쪽 흉내. */
    static final class Van {
        int age;
        Random random = new Random();

        boolean canGrow(int x) { return age < 25 && x > 7; }

        float spread() { return random.nextFloat() * 0.3F + 0.15F; }
    }

    /** 서버 쪽 흉내: 같은 이름·같은 상수, 다른 이름·다른 상수. */
    static final class Srv {
        int age;
        Random random = new Random();

        boolean canGrow(int x) { return age < 25 && x > 7; }

        float spread() { return random.nextFloat() * 0.3F + 0.15F; }

        String describe() { return "age " + age; }
    }

    static Map<String, LinkG4Ports.Features> features(Class<?> c) throws Exception {
        try (InputStream in = c.getResourceAsStream("/" + c.getName().replace(".", "/") + ".class")) {
            return LinkG4Ports.features(in.readAllBytes());
        }
    }

    @Test
    void sameBodyScoresAboveOtherMethods() throws Exception {
        var v = features(Van.class);
        var s = features(Srv.class);
        LinkG4Ports.Features grow = v.get("canGrow(I)Z"), spread = v.get("spread()F");
        assertTrue(grow.consts.contains(25) && grow.consts.contains(7), "정수 상수: " + grow.consts);
        assertEquals(1, spread.random, "난수 호출 수");
        double same = LinkG4Ports.score(grow, s.get("canGrow(I)Z"));
        assertTrue(same >= LinkG4Ports.MIN_SCORE, "같은 몸통: " + same);
        assertTrue(same - LinkG4Ports.score(grow, s.get("spread()F")) >= LinkG4Ports.MIN_MARGIN);
        assertTrue(same - LinkG4Ports.score(grow, s.get("describe()Ljava/lang/String;")) >= LinkG4Ports.MIN_MARGIN);
        assertTrue(LinkG4Ports.score(spread, s.get("spread()F")) > LinkG4Ports.score(spread, s.get("canGrow(I)Z")));
    }

    @Test
    void namesSplitIntoWordsWithoutAccessorPrefixes() {
        assertEquals(java.util.Set.of("pick", "up", "delay"), LinkG4Ports.tokens("setPickUpDelay"));
        assertEquals(java.util.Set.of("valid", "bonemeal", "target"), LinkG4Ports.tokens("isValidBonemealTarget"));
    }

    @Test
    void portsAreReadFromSourceIncludingNestedClasses(@TempDir Path dir) throws Exception {
        Path src = Files.createDirectories(dir.resolve("src/a/b"));
        Path cls = Files.createDirectories(dir.resolve("classes/a/b"));
        Files.writeString(src.resolve("Outer.java"), """
                package a.b;
                @VanillaPort("net.minecraft.world.Foo")
                public final class Outer {
                    @VanillaPort({"net.minecraft.world.Bar", "net.minecraft.world.Bar$Inner"})
                    static final class Nested {}
                    @VanillaPort("net.minecraft.world.Gone")
                    static final class Missing {}
                    @org.domi.furnace.server.VanillaPort("net.minecraft.world.Full")
                    static final class FullName {}
                }
                """);
        Files.write(cls.resolve("Outer.class"), new byte[0]);
        Files.write(cls.resolve("Outer$Nested.class"), new byte[0]);
        Files.write(cls.resolve("Outer$FullName.class"), new byte[0]);
        LinkG4Ports g4 = new LinkG4Ports(null, null, dir.resolve("src"), dir.resolve("classes"), List.of());
        g4.readPorts();
        assertEquals(List.of("a/b/Outer"), g4.ports.get("net/minecraft/world/Foo"));
        assertEquals(List.of("a/b/Outer$Nested"), g4.ports.get("net/minecraft/world/Bar"));
        assertEquals(List.of("a/b/Outer$Nested"), g4.ports.get("net/minecraft/world/Bar$Inner"));
        assertEquals(List.of("a/b/Outer$FullName"), g4.ports.get("net/minecraft/world/Full"), "전체 이름으로 쓴 표지도 읽는다");
        assertEquals(1, g4.unresolvedPorts, "클래스 파일이 없는 표지는 센다");
        assertEquals(4, g4.portLines);
    }
}
