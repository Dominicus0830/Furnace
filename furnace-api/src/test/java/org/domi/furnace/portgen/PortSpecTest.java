package org.domi.furnace.portgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 이식 명세 문법·검사(설계 5·6장)와 세 생성물의 모양. */
class PortSpecTest {

    static final String HEAD = "#furnace-ports 1 1.21.1 area=1";
    static final String CROP = "class net.minecraft.world.level.block.CropBlock = block.types.CropBlock kind=block";
    static final String RANDOM_TICK = "  randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V -> randomTick check=pos";

    static PortSpec spec(String... lines) { return PortSpec.parse("1-block.ports", List.of(lines)); }

    static void problem(String expect, String... lines) {
        PortSpec s = spec(lines);
        assertTrue(s.problems.stream().anyMatch(p -> p.contains(expect)), "expected a problem containing '" + expect + "', got " + s.problems);
    }

    @Test
    void goodLinesParse() {
        PortSpec s = spec(HEAD, "# 주석", CROP, RANDOM_TICK, "  static foo(IJ)Z -> foo check=pure   # 끝 주석");
        assertEquals(List.of(), s.problems);
        assertEquals("Block", s.camel());
        assertEquals(2, s.classes.get(0).members().size());
        assertTrue(s.classes.get(0).members().get(1).isStatic());
    }

    @Test
    void headerAndFileName() {
        assertTrue(PortSpec.parse("block.ports", List.of(HEAD)).problems.get(0).contains("file name"));
        problem("first line", "#furnace-ports 1 1.21.1 area=4", CROP);
        problem("first line", "#furnace-links 1 1.21.1 area=1");
    }

    @Test
    void itemReturnNeedsLiveOrCopy() {
        problem("must say live", HEAD, "class net.minecraft.world.inventory.Foo = menu.Foo kind=menu",
                "  static getItem()Lnet/minecraft/world/item/ItemStack; -> getItem check=pure");
        problem("live and copy together", HEAD, CROP, "  static a()Lnet/minecraft/world/item/ItemStack; -> a check=pure live copy");
        problem("only go on lines returning ItemStack", HEAD, CROP, "  static a()I -> a check=pure copy");
    }

    @Test
    void checkRules() {
        problem("cannot take a level argument", HEAD, CROP, "  static a(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V -> a check=pure");
        problem("needs exactly one level", HEAD, CROP, "  a(Lnet/minecraft/core/BlockPos;)V -> a check=pos");
        problem("needs exactly one level", HEAD, CROP, "  a(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V -> a check=pos");
        problem("not available yet", HEAD, CROP, "  a()V -> a check=menu");
        problem("unknown check", HEAD, CROP, "  a()V -> a check=owner");
        problem("needs check=", HEAD, CROP, "  a()V -> a");
    }

    @Test
    void notYetKindsAreRefusedNotGuessed() {
        problem("no port kind for argument type", HEAD, CROP, "  a(Lnet/minecraft/world/entity/Entity;)V -> a check=pure");
        problem("no port kind for return type", HEAD, CROP, "  a()Lnet/minecraft/world/level/block/state/BlockState; -> a check=pure");
        problem("instance lines on kind=menu", HEAD, "class net.minecraft.world.inventory.AnvilMenu = menu.AnvilMenu kind=menu", "  a()V -> a check=pure");
        problem("not available yet", HEAD, CROP + " hook");
        problem("constructor lines", HEAD, CROP, "  <init>()V -> new() check=pure");
        problem("not available yet", HEAD, CROP, "  static a(I)V -> a check=pure inout=0");
    }

    @Test
    void namesAndDuplicates() {
        problem("duplicate line", HEAD, CROP, "  static a(I)V -> a check=pure", "  static a(I)V -> b check=pure");
        problem("clashes", HEAD, CROP, "  static a(I)V -> a check=pure", "  static b(I)V -> a check=pure");
        problem("server class must be written after", HEAD, "class net.minecraft.X = org.domi.furnace.server.X kind=block");
        problem("plain name or a #fingerprint", HEAD, CROP, "  #abc()V -> a check=pure");
        assertEquals(List.of(), spec(HEAD, CROP, "  static #0123456789(I)V -> a check=pure").problems);
    }

    @Test
    void generatedShapes() {
        PortSpec s = spec(HEAD, CROP, RANDOM_TICK, "  static #0123456789(I)I -> cost check=pure");
        Map<String, String> api = PortGen.generate("api", List.of(s));
        String iface = api.get("org/domi/furnace/api/internal/port/BlockPort.java");
        assertTrue(iface.contains("void cropBlock$randomTick(int self, int a0, org.domi.furnace.api.world.World a1, int a2x, int a2y, int a2z, org.domi.furnace.api.internal.port.PortRandom a3);"), iface);
        assertTrue(iface.contains("int cropBlock$cost(int a0);"), iface);
        Map<String, String> server = PortGen.generate("server", List.of(s));
        String impl = server.get("org/domi/furnace/server/port/gen/BlockPortImpl.java");
        assertTrue(impl.contains("if (PortGuards.ownsPos(a1, a2x, a2z))"), impl);
        assertTrue(impl.contains("PortGuards.pure();"), impl);
        assertTrue(impl.contains("@org.domi.furnace.api.internal.port.PortGenerated(\"1-block.ports\")"), impl);
        assertEquals("org.domi.furnace.server.port.gen.BlockPortImpl\n", server.get("res:META-INF/services/org.domi.furnace.api.internal.port.BlockPort"));
        Map<String, String> compat = PortGen.generate("compat", List.of(s));
        String links = compat.get("links:1-block.ports.links");
        assertTrue(links.startsWith("#furnace-links 1 1.21.1 area=1\n"), links);
        assertTrue(links.contains("  #0123456789(I)I -> org.domi.furnace.compat.nms.port.gen.BlockPorts.CropBlock$f_0123456789 port\n"), links);
        String stubs = compat.get("org/domi/furnace/compat/nms/port/gen/BlockPorts.java");
        assertTrue(stubs.contains("@LinkTarget(check = \"port\")\n    public static void CropBlock$randomTick(Object self, Object a0, Object a1, Object a2, Object a3)"), stubs);
        assertTrue(stubs.contains("throw Peers.refused(e);"), stubs);
        // 생성물에 서버 이름은 서버 쪽에만, 호환 스텁은 서버 이름을 쓰지 않는다(어댑터 경계 ①)
        assertTrue(!stubs.contains("org.domi.furnace.server."), stubs);
        assertTrue(!iface.contains("net.minecraft") && !iface.contains("net/minecraft"), iface);
    }

    @Test
    void problemsStopGeneration() {
        PortSpec bad = spec(HEAD, CROP, "  a()V -> a check=menu");
        assertThrows(IllegalStateException.class, () -> PortGen.generate("api", List.of(bad)));
    }

    /** 저장소의 실제 명세가 문제 없이 읽히고 세 모드가 모두 만들어진다. */
    @Test
    void repositorySpecsAreClean() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle.kts"))) root = root.getParent();
        Path dir = root.resolve("furnace-compat-paper/src/shimspec/ports-1.21.1");
        List<PortSpec> specs = PortSpec.readDir(dir);
        assertTrue(!specs.isEmpty(), "no .ports files in " + dir);
        List<String> problems = new ArrayList<>();
        for (PortSpec s : specs) problems.addAll(s.problems);
        assertEquals(List.of(), problems);
        for (String mode : List.of("api", "server", "compat")) assertTrue(!PortGen.generate(mode, specs).isEmpty(), mode);
    }
}
