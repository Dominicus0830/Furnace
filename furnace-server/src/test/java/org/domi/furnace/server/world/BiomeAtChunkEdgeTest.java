// 게임플레이 바이옴 조회(Level.getBiome)가 확대 지터로 이웃 청크·높이 밖 quart 를 고를 때 바닐라처럼 답하는지.
// 바닐라 LevelReader.getNoiseBiome: 그 청크가 있으면 저장 바이옴(ChunkAccess.getNoiseBiome — quart 높이를 죈다), 없으면 생성기 바이옴.
// 예전에는 null 이 나와 늑대 finalizeSpawn 이 NPE 로 죽고(청크 생성 동물 스폰 통째 실패), 청크 생성 스폰이 y=319 조회에서 건너뛰어졌다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.entity.DifficultyInstance;
import org.domi.furnace.server.entity.Wolf;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BiomeAtChunkEdgeTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;

    @AfterEach
    void tearDown() {
        if (sched != null) sched.close();
        if (packs != null) packs.close();
    }

    private void start(java.util.function.Function<BlockRegistry, org.domi.furnace.server.world.gen.WorldSource> source) throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, source);
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                        GameMode.SURVIVAL, false, Difficulty.EASY, List.of("vanilla"), List.of()),
                RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        core.world().awaitChunk(new ChunkPos(0, 0));
    }

    private <T> T inRegion(java.util.function.Function<Region, T> task) {
        return core.world().submitAtChunk(new ChunkPos(0, 0), task).orTimeout(30, TimeUnit.SECONDS).join();
    }

    /** 청크 (0,0) 안 블록 중 지터 quart 가 서쪽·북쪽 이웃 청크(로드 안 됨)로 가는 칸. */
    private int[] edgeBlock(int y) {
        long seed = core.biomeZoomSeed(core.world());
        for (int x = 0; x < 2; x++) for (int z = 0; z < 16; z++) {
            int[] q = BiomeZoom.quart(seed, x, y, z);
            if (q[0] >> 2 != 0 && core.world().loadedChunk(q[0] >> 2, q[2] >> 2) == null) return new int[] {x, y, z};
        }
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) {
            int[] q = BiomeZoom.quart(seed, x, y, z);
            if (q[2] >> 2 != 0 && core.world().loadedChunk(q[0] >> 2, q[2] >> 2) == null) return new int[] {x, y, z};
        }
        return fail("지터가 이웃 청크로 가는 칸이 없다");
    }

    @Test
    void neighbourChunkNotLoadedUsesGeneratorBiome_andWolfVariantFollowsIt() throws Exception {
        int forest = RegistrySet.load("registries/1.21.1.txt").indexOf("minecraft:worldgen/biome", "minecraft:forest");
        start(reg -> new SuperflatWorldSource(reg, forest));
        int[] p = edgeBlock(-60);
        assertEquals("minecraft:forest", inRegion(r -> r.level().biomeAt(p[0], p[1], p[2])), "이웃 청크가 없으면 생성기 바이옴(평지=forest)");
        // 로그의 NPE: 청크 가장자리 늑대 finalizeSpawn 이 바이옴 null 로 죽었다 — 지금은 숲 늑대(woods)
        int variant = inRegion(r -> {
            Wolf w = new Wolf(r.level(), r.level().nextEntityId());
            w.moveTo(p[0] + 0.5, p[1], p[2] + 0.5, 0.0F, 0.0F);
            w.finalizeSpawn(r.level().random(), new DifficultyInstance(2, 0, 0, 0.0F), null);
            return w.getVariant();
        });
        assertEquals(Wolf.variantIndex("minecraft:woods"), variant);
    }

    @Test
    void quartAboveTheWorldIsClampedToTheTopSection() throws Exception {
        int forest = RegistrySet.load("registries/1.21.1.txt").indexOf("minecraft:worldgen/biome", "minecraft:forest");
        start(reg -> new SuperflatWorldSource(reg, forest));
        long seed = core.biomeZoomSeed(core.world());
        // 청크 생성 스폰은 (minX, 319, minZ) 바이옴을 본다 — 지터가 quart y 80(= y 320, 세계 밖)을 고르는 칸
        int[] found = null;
        for (int x = 2; x < 14 && found == null; x++) for (int z = 2; z < 14 && found == null; z++) {
            if (BiomeZoom.quart(seed, x, 319, z)[1] == 80) found = new int[] {x, z};
        }
        assertNotNull(found, "y 319 에서 quart y 80 을 고르는 칸이 있어야 한다");
        int[] f = found;
        int[] q = BiomeZoom.quart(seed, f[0], 319, f[1]);
        // 저장 바이옴을 맨 위 quart 만 바꿔 두면, 죈 결과는 생성기(forest)가 아니라 저장 값이어야 한다
        int plains = RegistrySet.load("registries/1.21.1.txt").indexOf("minecraft:worldgen/biome", "minecraft:plains");
        String got = inRegion(r -> {
            r.setNoiseBiomeAtBlock(q[0] << 2, 319, q[2] << 2, plains);
            return r.level().biomeAt(f[0], 319, f[1]);
        });
        assertEquals("minecraft:plains", got);
        assertEquals(plains, (int) inRegion(r -> r.biomeIdAtQuart(q[0], 80, q[2])));
        assertEquals(plains, (int) inRegion(r -> r.biomeIdAtQuart(q[0], 1000, q[2])));
    }

    @Test
    void noiseWorldEdgeMatchesTheGeneratorBiomeSource() throws Exception {
        start(reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")));
        var src = NoiseWorldSource.of(core.world().worldSource());
        long seed = core.biomeZoomSeed(core.world());
        for (int y : new int[] {-60, 64, 200}) {
            int[] p = edgeBlock(y);
            int[] q = BiomeZoom.quart(seed, p[0], p[1], p[2]);
            String expected = src.biomeAt(q[0] << 2, q[1] << 2, q[2] << 2);
            assertFalse(expected == null || expected.isEmpty());
            assertEquals(expected, inRegion(r -> r.level().biomeAt(p[0], p[1], p[2])), "y=" + y);
        }
        // 안쪽 칸은 저장 바이옴(새로 만든 청크라 생성기와 같다)
        int[] q = BiomeZoom.quart(seed, 8, 64, 8);
        assertEquals(src.biomeAt(q[0] << 2, q[1] << 2, q[2] << 2), inRegion(r -> r.level().biomeAt(8, 64, 8)));
    }
}
