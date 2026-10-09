// End 피처(end_spike 시드 파생 목록, 상태 표 구성, 배치 스모크) 단위 테스트 — 기대값은 순정 SpikeCacheLoader 를 직접 돌려 얻은 값
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class EndFeaturesTest {

    private static String fmt(List<EndSpikeFeature.Spike> spikes) {
        return spikes.stream()
            .map(s -> "[" + s.centerX() + "," + s.centerZ() + "," + s.radius() + "," + s.height() + "," + s.guarded() + "]")
            .collect(Collectors.joining(" "));
    }

    @Test
    void spikeListMatchesVanillaForSeveralSeeds() {
        assertEquals("[42,0,4,94,false] [33,24,3,91,false] [12,39,2,79,true] [-13,39,4,97,false] [-34,24,3,85,false]"
            + " [-42,-1,5,103,false] [-34,-25,2,76,false] [-13,-40,2,82,true] [12,-40,3,88,false] [33,-25,4,100,false]",
            fmt(EndSpikeFeature.spikesForSeed(0L)));
        assertEquals("[42,0,3,85,false] [33,24,4,94,false] [12,39,2,76,false] [-13,39,2,82,true] [-34,24,4,100,false]"
            + " [-42,-1,3,91,false] [-34,-25,5,103,false] [-13,-40,3,88,false] [12,-40,4,97,false] [33,-25,2,79,true]",
            fmt(EndSpikeFeature.spikesForSeed(1L)));
        assertEquals("[42,0,4,97,false] [33,24,5,103,false] [12,39,3,91,false] [-13,39,2,76,false] [-34,24,4,94,false]"
            + " [-42,-1,4,100,false] [-34,-25,2,79,true] [-13,-40,3,88,false] [12,-40,3,85,false] [33,-25,2,82,true]",
            fmt(EndSpikeFeature.spikesForSeed(12345L)));
        assertEquals("[42,0,4,100,false] [33,24,2,79,true] [12,39,3,85,false] [-13,39,3,88,false] [-34,24,2,82,true]"
            + " [-42,-1,4,94,false] [-34,-25,2,76,false] [-13,-40,3,91,false] [12,-40,5,103,false] [33,-25,4,97,false]",
            fmt(EndSpikeFeature.spikesForSeed(-987654321L)));
        assertEquals("[42,0,4,94,false] [33,24,5,103,false] [12,39,2,76,false] [-13,39,3,85,false] [-34,24,3,91,false]"
            + " [-42,-1,2,82,true] [-34,-25,3,88,false] [-13,-40,2,79,true] [12,-40,4,97,false] [33,-25,4,100,false]",
            fmt(EndSpikeFeature.spikesForSeed(424242L)));
        assertEquals("[42,0,5,103,false] [33,24,2,79,true] [12,39,4,94,false] [-13,39,2,82,true] [-34,24,3,85,false]"
            + " [-42,-1,4,100,false] [-34,-25,3,91,false] [-13,-40,4,97,false] [12,-40,2,76,false] [33,-25,3,88,false]",
            fmt(EndSpikeFeature.spikesForSeed(Long.MAX_VALUE)));
    }

    private static FeatureWorldView endView(BlockRegistry reg, long seed) {
        ChunkColumn[] grid = new ChunkColumn[9];
        int[][] biomes = new int[9][];
        int endStone = reg.defaultStateId("minecraft:end_stone");
        for (int i = 0; i < 9; i++) {
            grid[i] = new ChunkColumn(0, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, 0, 16);
            biomes[i] = new int[16 * 4 * 4 * 4];
            for (int y = 0; y <= 60; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) grid[i].setBlock(x, y, z, endStone);
        }
        return new FeatureWorldView(grid, biomes, 0, 0, 0, 0, 1, false, seed);
    }

    @Test
    void endFeaturesPlaceSmokeTest() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        BlockAttrs attrs = BlockAttrs.get();
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(7L);

        FeatureWorldView view = endView(reg, 0L);
        // seed 0 의 스파이크 중 centerX=12, centerZ=39 는 청크 (0,2) 라 원점 청크(0,0) 에서는 아무것도 하지 않는다.
        assertTrue(new EndSpikeFeature(reg, false, List.of(), null).place(view, random, 0, 0, 0));
        assertEquals(reg.defaultStateId("minecraft:end_stone"), view.getBlock(5, 5, 5));

        // 직접 지정한 가드 스파이크로 우리·기반암·불을 검사한다.
        EndSpikeFeature.Spike guarded = new EndSpikeFeature.Spike(8, 8, 2, 79, true);
        new EndSpikeFeature(reg, false, List.of(guarded), null).place(view, random, 0, 0, 0);
        assertEquals(reg.defaultStateId("minecraft:bedrock"), view.getBlock(8, 79, 8));
        assertEquals(reg.defaultStateId("minecraft:fire"), view.getBlock(8, 80, 8));
        assertEquals(reg.defaultStateId("minecraft:obsidian"), view.getBlock(8, 78, 8));
        assertTrue(reg.canonicalKeyOf(view.getBlock(10, 79, 8)).startsWith("minecraft:iron_bars"));
        assertTrue(attrs.isAir(view.getBlock(8, 90, 8)));

        FeatureWorldView v2 = endView(reg, 0L);
        assertTrue(new EndIslandFeature(reg).place(v2, random, 8, 40, 8));
        assertEquals(reg.defaultStateId("minecraft:end_stone"), v2.getBlock(8, 40, 8));

        FeatureWorldView v3 = endView(reg, 0L);
        assertTrue(new EndGatewayFeature(reg, new int[]{100, 50, 0}, true).place(v3, random, 8, 70, 8));
        assertEquals(reg.defaultStateId("minecraft:end_gateway"), v3.getBlock(8, 70, 8));
        assertEquals(reg.defaultStateId("minecraft:bedrock"), v3.getBlock(8, 72, 8));
        assertEquals(reg.defaultStateId("minecraft:bedrock"), v3.getBlock(9, 71, 8));
        assertTrue(attrs.isAir(v3.getBlock(9, 70, 8)));

        FeatureWorldView v4 = endView(reg, 0L);
        ChorusPlantFeature chorus = new ChorusPlantFeature(reg);
        assertTrue(chorus.place(v4, random, 8, 61, 8));
        assertTrue(reg.canonicalKeyOf(v4.getBlock(8, 61, 8)).startsWith("minecraft:chorus_plant"));
        // 끝돌 위가 아니거나 비어 있지 않으면 실패
        assertEquals(false, chorus.place(v4, random, 8, 61, 8));
        assertEquals(false, chorus.place(v4, random, 8, 80, 8));

        FeatureWorldView v5 = endView(reg, 0L);
        new EndPlatformFeature(reg).place(v5, random, 8, 49, 8);
        assertEquals(reg.defaultStateId("minecraft:obsidian"), v5.getBlock(8, 48, 8));
        assertTrue(attrs.isAir(v5.getBlock(8, 49, 8)));
    }
}
