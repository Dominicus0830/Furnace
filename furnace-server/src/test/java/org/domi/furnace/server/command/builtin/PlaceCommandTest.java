// /place: 노이즈 오버월드의 실제 region 에서 template(회전 포함)·feature(나무)·structure 를 놓고, 미지원(Noop) feature 는 실패로 알리는지.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaceCommandTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;
    static final List<String> console = new java.util.concurrent.CopyOnWriteArrayList<>();

    // 노이즈 청크 생성이 비싸서 한 번만 만든다. 시험마다 서로 다른 자리를 쓴다.
    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")));
        core.addConsoleListener(t -> console.add(t.getString()));
        // (0..5, 0..5) 청크를 올려 둔다
        for (int cx = 0; cx <= 5; cx++) {
            for (int cz = 0; cz <= 5; cz++) {
                var cp = new org.domi.furnace.server.world.ChunkPos(cx, cz);
                var loaded = new CompletableFuture<CompletableFuture<?>>();
                core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
                loaded.get(30, TimeUnit.SECONDS).get(120, TimeUnit.SECONDS);
            }
        }
    }

    @AfterAll
    static void tearDown() { sched.close(); }

    private static List<String> run(String cmd) {
        console.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), cmd).join();
        return List.copyOf(console);
    }

    private static <T> T onRegion(Function<RegionLevel, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region.level()));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(10, TimeUnit.SECONDS);
    }

    private static int at(int x, int y, int z) throws Exception { return onRegion(l -> l.getBlockState(new Pos(x, y, z))); }


    @Test
    void templatePlacesBlocksWithRotation() throws Exception {
        assertEquals(List.of("Loaded template \"minecraft:igloo/top\" at 20, 220, 20"), run("place template minecraft:igloo/top 20 220 20"));
        NoiseWorldSource gen = NoiseWorldSource.of(core.world().worldSource());
        StructureTemplate t = gen.structureEngine().template("minecraft:igloo/top");
        int snow = 0;
        for (StructureTemplate.BlockInfo b : t.palettes().get(0)) {
            if (!B.is(b.state(), "minecraft:snow_block")) continue;
            assertTrue(B.is(at(20 + b.x(), 220 + b.y(), 20 + b.z()), "minecraft:snow_block"), "칸 " + b);
            snow++;
        }
        assertTrue(snow > 10, "눈 블록 " + snow);
        // 시계 방향 90도: 템플릿 좌표를 (0,0) 피벗으로 돌린 자리
        assertEquals(List.of("Loaded template \"minecraft:igloo/top\" at 40, 220, 40"), run("place template minecraft:igloo/top 40 220 40 clockwise_90"));
        for (StructureTemplate.BlockInfo b : t.palettes().get(0)) {
            if (!B.is(b.state(), "minecraft:snow_block")) continue;
            int[] r = StructureTemplate.transform(b.x(), b.y(), b.z(), BlockStateTable.NONE, BlockStateTable.ROT_CW90, 0, 0);
            assertTrue(B.is(at(40 + r[0], 220 + r[1], 40 + r[2]), "minecraft:snow_block"), "회전 칸 " + b);
        }
        assertEquals(List.of("There is no template with id \"minecraft:nope\""), run("place template minecraft:nope 20 220 20"));
    }

    @Test
    void featureTreeAndUnsupportedNoop() throws Exception {
        onRegion(l -> l.setBlock(new Pos(24, 199, 24), B.defaultState("minecraft:dirt"), 2));
        assertEquals(List.of("Placed \"minecraft:oak\" at 24, 200, 24"), run("place feature minecraft:oak 24 200 24"));
        assertTrue(B.is(at(24, 200, 24), "minecraft:oak_log"));
        // 아직 구현하지 않은 feature 는 성공으로 위장하지 않는다
        assertEquals(List.of("Placing feature minecraft:bonus_chest is not supported yet"), run("place feature minecraft:bonus_chest 24 200 40"));
        assertTrue(B.isAir(at(24, 200, 40)));
    }

    @Test
    void jigsawAndStructureSucceed() throws Exception {
        assertEquals(List.of("Generated jigsaw at 48, 100, 48"),
                run("place jigsaw minecraft:village/plains/town_centers minecraft:bottom 1 48 100 48"));
        // 마을 중심 조각이 놓였다: 원점 근처 칸 중 공기가 아닌 것이 생겼다
        int solid = 0;
        for (int y = 100; y < 110; y++) for (int dx = -4; dx <= 4; dx++) if (!B.isAir(at(48 + dx, y, 48))) solid++;
        assertTrue(solid > 0, "jigsaw 블록 " + solid);
        List<String> out = run("place structure minecraft:igloo 20 100 52");
        assertEquals(List.of("Generated structure \"minecraft:igloo\" at 20, 100, 52"), out);
    }

    @Test
    void noopAnywhereInTreeIsUnsupported() {
        assertTrue(PlaceCommand.containsNoop(List.of(org.domi.furnace.server.world.gen.feature.NoopFeature.INSTANCE),
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
        assertFalse(PlaceCommand.containsNoop(List.of("x"), java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
    }

    @Test
    void jigsawWithMissingTargetFails() {
        assertEquals(List.of("Failed to generate jigsaw"), run("place jigsaw minecraft:village/plains/town_centers minecraft:nope 2 24 100 24"));
    }
}
