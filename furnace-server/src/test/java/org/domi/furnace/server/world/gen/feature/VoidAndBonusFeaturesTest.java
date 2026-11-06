// void_start_platform / bonus_chest 단위 테스트 — 기대값은 디컴파일(VoidStartPlatformFeature, BonusChestFeature)을 손으로 옮겨 독립 계산.
// 바닐라 oracle 대조는 아니다(가정: oracle 미검증).
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class VoidAndBonusFeaturesTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 3x3 격자(청크 -1..1), y 0..filledTo 는 fill 로 채움. origin 청크는 (ocx, 0). */
    private static FeatureWorldView view(int ocx, String fill, int filledTo) {
        ChunkColumn[] grid = new ChunkColumn[9];
        int[][] biomes = new int[9][];
        for (int i = 0; i < 9; i++) {
            grid[i] = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, 0, 16);
            biomes[i] = new int[16 * 4 * 4 * 4];
            if (fill != null) {
                int st = REG.defaultStateId(fill);
                for (int y = 0; y <= filledTo; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) grid[i].setBlock(x, y, z, st);
            }
        }
        return new FeatureWorldView(grid, biomes, ocx, 0, 0, 0, 1, false, 7L);
    }

    @Test
    void voidStartPlatform_originChunk() {
        FeatureWorldView v = view(0, null, -1);
        assertTrue(new VoidStartPlatformFeature(REG).place(v, new WorldgenRandom(), 0, 0, 0));
        int stone = REG.defaultStateId("minecraft:stone"), cobble = REG.defaultStateId("minecraft:cobblestone");
        // 중심 (8, origin.y + 3, 8) 만 조약돌, 청크 (0,0) 나머지는 돌
        assertEquals(cobble, v.getBlock(8, 3, 8));
        assertEquals(stone, v.getBlock(0, 3, 0));
        assertEquals(stone, v.getBlock(15, 3, 15));
        assertEquals(stone, v.getBlock(8, 3, 9));
        BlockAttrs a = BlockAttrs.get();
        assertTrue(a.isAir(v.getBlock(8, 2, 8)));
        assertTrue(a.isAir(v.getBlock(8, 4, 8)));
    }

    @Test
    void voidStartPlatform_neighborChunkRadius16_andFarChunkNoop() {
        // 청크 (1,0): x 16..31 중 |x-8| <= 16 인 16..24 만
        FeatureWorldView v = view(1, null, -1);
        assertTrue(new VoidStartPlatformFeature(REG).place(v, new WorldgenRandom(), 16, 0, 0));
        int stone = REG.defaultStateId("minecraft:stone");
        BlockAttrs a = BlockAttrs.get();
        assertEquals(stone, v.getBlock(16, 3, 8));
        assertEquals(stone, v.getBlock(24, 3, 0));
        assertEquals(stone, v.getBlock(24, 3, 15));
        assertTrue(a.isAir(v.getBlock(25, 3, 8)));
        // z 방향: 청크 (1,0) 의 z 0..15 는 |z-8| <= 8 이라 전부 포함
        assertEquals(stone, v.getBlock(20, 3, 15));
        // 청크 (2,0) 은 체커보드 거리 2 > 1 → 아무것도 하지 않고 true
        FeatureWorldView v2 = view(1, null, -1);
        assertTrue(new VoidStartPlatformFeature(REG).place(v2, new WorldgenRandom(), 32, 0, 0));
        assertTrue(a.isAir(v2.getBlock(32, 3, 8)));
    }

    @Test
    void voidStartPlatform_usesOriginY() {
        FeatureWorldView v = view(0, null, -1);
        new VoidStartPlatformFeature(REG).place(v, new WorldgenRandom(), 0, 10, 0);
        assertEquals(REG.defaultStateId("minecraft:cobblestone"), v.getBlock(8, 13, 8));
    }

    @Test
    void bonusChest_shuffleIsFisherYatesFromTheBack() {
        WorldgenRandom r = new WorldgenRandom();
        r.setSeed(99L);
        int[] got = BonusChestFeature.shuffledRange(10, 15, r);
        // 독립 계산: i = 6..2 에서 j = nextInt(i), swap(i-1, j)
        WorldgenRandom r2 = new WorldgenRandom();
        r2.setSeed(99L);
        int[] want = {10, 11, 12, 13, 14, 15};
        for (int i = 6; i > 1; i--) {
            int j = r2.nextInt(i);
            int t = want[i - 1]; want[i - 1] = want[j]; want[j] = t;
        }
        assertArrayEquals(want, got);
        assertEquals(r2.nextLong(), r.nextLong(), "소비한 난수 개수가 같아야 한다");
        int[] sorted = got.clone();
        java.util.Arrays.sort(sorted);
        assertArrayEquals(new int[] {10, 11, 12, 13, 14, 15}, sorted);
    }

    @Test
    void bonusChest_placesChestFirstShuffledColumn_withLootSeedAndTorches() {
        FeatureWorldView v = view(0, "minecraft:stone", 60);
        WorldgenRandom r = new WorldgenRandom();
        r.setSeed(1234L);
        WorldgenRandom ref = new WorldgenRandom();
        ref.setSeed(1234L);
        int[] xs = BonusChestFeature.shuffledRange(0, 15, ref);
        int[] zs = BonusChestFeature.shuffledRange(0, 15, ref);
        long lootSeed = ref.nextLong();

        assertTrue(new BonusChestFeature(REG).place(v, r, 5, 0, 5));
        // 모든 열이 같은 높이(돌 y<=60)라 첫 (x, z) 조합 위 y=61 에 상자
        int x = xs[0], z = zs[0];
        assertEquals(REG.defaultStateId("minecraft:chest"), v.getBlock(x, 61, z));
        NbtCompound be = v.generatedBlockEntity(x, 61, z);
        assertNotNull(be);
        assertEquals("minecraft:chests/spawn_bonus_chest", be.getString("LootTable"));
        assertEquals(lootSeed, be.getLong("LootTableSeed"));
        // 사방 이웃의 아래가 돌(단단함)이라 횃불 4개
        int torch = REG.defaultStateId("minecraft:torch");
        int[][] d = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] o : d) assertEquals(torch, v.getBlock(x + o[0], 61, z + o[1]));
        // 상자는 한 개뿐
        int chests = 0;
        int chest = REG.defaultStateId("minecraft:chest");
        for (int px = 0; px < 16; px++) for (int pz = 0; pz < 16; pz++) if (v.getBlock(px, 61, pz) == chest) chests++;
        assertEquals(1, chests);
    }
}
