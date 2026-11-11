// A06-03: 생성 단계 유체 틱 — 샘(SpringFeature)·구조물 조각의 물(StructurePiece.placeBlock)·대수층 경계(노이즈 채우기 후처리 표시)가
// 갓 생성된 청크의 fluid_ticks(바닐라 ProtoChunk 저장 형식, t=0)로 남고, 청크가 월드에 붙으면 예약 틱이 돼 흐른다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationFluidTicksTest {
    @TempDir Path tmp;

    private static NoiseWorldSource overworld() {
        return NoiseWorldSource.overworld(1234L, BlockRegistry.load("blocks/1.21.1.txt"), RegistrySet.load("registries/1.21.1.txt"));
    }

    /** 샘 모양(서·동·북·남·아래 중 구멍 하나, 나머지 넷이 고체, 위는 고체) 원천 블록 {x, y, z, 구멍 x, y, z}. */
    private static List<int[]> springs(ChunkColumn col, int cx, int cz) {
        int water = B.defaultState("water"), lava = B.defaultState("lava");
        List<int[]> out = new ArrayList<>();
        int[][] d = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) for (int y = -63; y < 250; y++) {
            int s = col.getBlock(x, y, z);
            if (s != water && s != lava) continue;
            int air = 0, solid = 0;
            int[] hole = null;
            for (int[] o : d) {
                int n = col.getBlock(x + o[0], y + o[1], z + o[2]);
                if (B.is(n, "air") || B.is(n, "cave_air")) { air++; hole = o; }
                else if (!B.isLiquid(n)) solid++;
            }
            int up = col.getBlock(x, y + 1, z);
            if (air == 1 && solid == 4 && !B.isLiquid(up) && !B.is(up, "air") && !B.is(up, "cave_air")) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                out.add(new int[] {wx, y, wz, wx + hole[0], y + hole[1], wz + hole[2]});
            }
        }
        return out;
    }

    private static Set<String> tickKeys(ChunkColumn col) {
        Set<String> keys = new HashSet<>();
        if (col.extraNbt() == null) return keys;
        for (Object o : col.extraNbt().getList("fluid_ticks").items()) {
            NbtCompound e = (NbtCompound) o;
            assertEquals(0, e.getIntOr0("t"), "생성 단계 예약은 지연 0(ProtoChunkTicks)");
            keys.add(e.getString("i") + "@" + e.getIntOr0("x") + "," + e.getIntOr0("y") + "," + e.getIntOr0("z"));
        }
        return keys;
    }

    @Test
    void freshSpringsAndAquiferEdgesCarryFluidTicks() {
        NoiseWorldSource gen = overworld();
        var vil = gen.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100);
        int bx = vil.x() >> 4, bz = vil.z() >> 4;
        int springCount = 0, ticks = 0;
        for (int cx = bx - 1; cx <= bx + 1; cx++) for (int cz = bz - 1; cz <= bz + 1; cz++) {
            ChunkColumn col = gen.generateWithFeatures(cx, cz);
            Set<String> keys = tickKeys(col);
            ticks += keys.size();
            for (int[] s : springs(col, cx, cz)) {
                springCount++;
                String fluid = B.is(col.getBlock(s[0] & 15, s[1], s[2] & 15), "water") ? "minecraft:water" : "minecraft:lava";
                assertTrue(keys.contains(fluid + "@" + s[0] + "," + s[1] + "," + s[2]), "샘 " + fluid + "@" + s[0] + "," + s[1] + "," + s[2] + " 에 유체 틱이 없다");
            }
            // 모든 예약 칸에는 그 종류의 유체가 있다
            for (String k : keys) {
                String[] p = k.substring(k.indexOf('@') + 1).split(",");
                int st = col.getBlock(Integer.parseInt(p[0]) & 15, Integer.parseInt(p[1]), Integer.parseInt(p[2]) & 15);
                int ft = org.domi.furnace.server.world.gen.feature.FeatureWorldView.fluidTypeOf(st);
                assertNotEquals(0, ft, "유체 없는 칸의 예약 " + k);
            }
        }
        assertTrue(springCount > 0, "시험할 샘이 없다");
        assertTrue(ticks > springCount, "대수층 경계 후처리 유체 틱이 없다(샘 " + springCount + ", 틱 " + ticks + ")");
    }

    @Test
    void oceanMonumentWaterIsScheduled() {
        NoiseWorldSource gen = overworld();
        var m = gen.structureLocator().findNearest(List.of("minecraft:monument"), 0, 64, 0, 100);
        assertNotNull(m);
        ChunkColumn col = gen.generateWithFeatures(m.x() >> 4, m.z() >> 4);
        long water = tickKeys(col).stream().filter(k -> k.startsWith("minecraft:water@")).count();
        assertTrue(water > 0, "해저 신전 조각이 놓은 물에 유체 틱이 없다");
    }

    @Test
    void springFlowsAfterChunkIsInstalled() throws Exception {
        NoiseWorldSource probe = overworld();
        var vil = probe.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100);
        int bx = vil.x() >> 4, bz = vil.z() >> 4;
        int[] spring = null;
        int scx = 0, scz = 0;
        outer:
        for (int cx = bx - 1; cx <= bx + 1; cx++) for (int cz = bz - 1; cz <= bz + 1; cz++) {
            ChunkColumn col = probe.generateWithFeatures(cx, cz);
            for (int[] s : springs(col, cx, cz)) {
                if (B.is(col.getBlock(s[0] & 15, s[1], s[2] & 15), "water")) { spring = s; scx = cx; scz = cz; break outer; }
            }
        }
        assertNotNull(spring, "물 샘이 없다");
        // 서버 경로(AnvilWorldSource → generateWithFeatures): 생성 → 설치 → fluid_ticks 를 예약 틱으로
        ServerCore core = TestServers.core(tmp, new FurnaceSchedulerImpl(50_000_000L, 1), reg -> new AnvilWorldSource(tmp,
            new org.domi.furnace.server.world.anvil.RegionFileStore(tmp), new org.domi.furnace.server.world.anvil.AnvilChunkCodec(reg, 0),
            NoiseWorldSource.overworld(1234L, reg, RegistrySet.load("registries/1.21.1.txt"))));
        for (int x = scx - 2; x <= scx + 2; x++) for (int z = scz - 2; z <= scz + 2; z++) core.world().forcedChunks().set(x, z, true);
        for (int cx = scx - 2; cx <= scx + 2; cx++) for (int cz = scz - 2; cz <= scz + 2; cz++) {
            var cp = new ChunkPos(cx, cz);
            var loaded = new CompletableFuture<CompletableFuture<?>>();
            core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
            loaded.get(60, TimeUnit.SECONDS).get(300, TimeUnit.SECONDS);
        }
        final int[] s = spring;
        int at = blockAt(core, s[0], s[1], s[2]);
        assertTrue(B.is(at, "water"), "월드의 샘 칸이 물이 아니다: " + at);
        // 시험 서버는 스스로 틱하지 않는다 — 그 region 을 몇 틱 돌린다(물 틱 지연 5)
        var after = new CompletableFuture<Integer>();
        core.world().runAt(s[0], s[2], (Region r) -> {
            for (int i = 0; i < 20; i++) {
                core.world().clock().advanceGameTime();
                r.tick();
            }
            after.complete(r.getBlock(s[3], s[4], s[5]));
        });
        int hole = after.get(60, TimeUnit.SECONDS);
        assertTrue(B.is(hole, "water"), "샘 옆 구멍 " + s[3] + "," + s[4] + "," + s[5] + " 으로 물이 흐르지 않았다");
    }

    private static int blockAt(ServerCore core, int x, int y, int z) throws Exception {
        var fut = new CompletableFuture<Integer>();
        core.world().runAt(x, z, (Region r) -> fut.complete(r.getBlock(x, y, z)));
        return fut.get(60, TimeUnit.SECONDS);
    }
}
