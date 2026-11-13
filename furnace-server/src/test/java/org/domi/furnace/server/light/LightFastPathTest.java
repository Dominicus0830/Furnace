// 빛 계산 빠른 경로(M12)가 결과를 바꾸지 않는지: 구역 힌트(한 상태로만 찬 구역·발광 블록 없는 구역)를 주고 계산한 빛이
// 힌트 없이(모든 칸을 훑는 예전 경로) 계산한 빛과 칸마다, 하늘빛 원천 높이까지 같은지 본다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class LightFastPathTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final int AIR = REG.defaultStateId("minecraft:air");
    private static final int BEDROCK = REG.defaultStateId("minecraft:bedrock");
    /** 구역을 통째로 채울 후보: 막는 것·안 막는 것·면만 가리는 것·빛을 내는 것을 섞는다. */
    private static final String[] FILL = {"minecraft:air", "minecraft:stone", "minecraft:glass", "minecraft:water", "minecraft:oak_leaves",
            "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:oak_slab", "minecraft:cobweb", "minecraft:ice", "minecraft:cave_air",
            "minecraft:dirt_path", "minecraft:snow", "minecraft:lava", "minecraft:torch", "minecraft:oak_stairs"};
    private static final String[] SCATTER = {"minecraft:air", "minecraft:stone", "minecraft:glowstone", "minecraft:oak_leaves", "minecraft:glass",
            "minecraft:torch", "minecraft:water", "minecraft:oak_slab", "minecraft:shroomlight", "minecraft:lantern"};

    /** 무작위 지형: 높이 지형 + 일부 구역을 한 상태로 통째로 채움(디스크 로드처럼 단일값 팔레트) + 흩뿌린 블록. */
    private static ChunkColumn randomColumn(Random r) {
        ChunkColumn col = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, -64, 24);
        int stone = REG.defaultStateId("minecraft:stone");
        int base = 20 + r.nextInt(60);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int h = base + r.nextInt(8);
            for (int y = -64; y < h; y++) col.setBlock(x, y, z, stone);
        }
        for (int i = 0; i < 24; i++) {
            if (r.nextInt(3) != 0) continue;
            int[] blocks = new int[4096];
            java.util.Arrays.fill(blocks, REG.defaultStateId(FILL[r.nextInt(FILL.length)]));
            if (r.nextInt(4) == 0) {   // 섞인 구역도(팔레트 2개 이상)
                for (int n = 0; n < 30; n++) blocks[r.nextInt(4096)] = REG.defaultStateId(SCATTER[r.nextInt(SCATTER.length)]);
            }
            col.loadSectionBlocks(i, blocks);
        }
        for (int n = 0; n < 200; n++) {
            col.setBlock(r.nextInt(16), -64 + r.nextInt(384), r.nextInt(16), REG.defaultStateId(SCATTER[r.nextInt(SCATTER.length)]));
        }
        return col;
    }

    private static void assertSameLight(ChunkLight expected, ChunkLight actual, String what) {
        assertArrayEquals(expected.lowestSource, actual.lowestSource, what + " lowestSource");
        for (int s = 0; s < ChunkLight.SECTIONS; s++) {
            assertArrayEquals(expected.sky(s), actual.sky(s), what + " sky section " + s);
            assertArrayEquals(expected.block(s), actual.block(s), what + " block section " + s);
        }
    }

    /** 생성 스레드의 미리 계산(ChunkLevel.precomputeLight — 구역 힌트를 준다) == 힌트 없는 computeIsolated. */
    @Test
    void precomputeWithSectionHintsMatchesFullScan() {
        Random r = new Random(12);
        for (int i = 0; i < 300; i++) {
            ChunkColumn col = randomColumn(r);
            boolean sky = i % 5 != 0;
            int cx = r.nextInt(64) - 32, cz = r.nextInt(64) - 32;
            ChunkLight expected = LevelLight.computeIsolated(cx, cz, (x, y, z) -> col.getBlock(x & 15, y, z & 15), BEDROCK, -64, 320, sky);
            ChunkLevel.precomputeLight(col, cx, cz, BEDROCK, sky);
            assertSameLight(expected, col.light(), "chunk " + i + " sky=" + sky);
        }
    }

    /** 이웃이 있는 세계에서 차례로 켜기(lightChunk — 하늘빛 원천을 이웃 열과 비교한다)도 힌트 유무와 상관없이 같다. */
    @Test
    void neighbourAwareLightChunkWithHintsMatchesFullScan() {
        Random r = new Random(34);
        for (int round = 0; round < 20; round++) {
            ChunkColumn[] cols = new ChunkColumn[9];
            for (int i = 0; i < 9; i++) cols[i] = randomColumn(r);
            Grid plain = new Grid(cols, false), hinted = new Grid(cols, true);
            LevelLight a = new LevelLight(plain, true), b = new LevelLight(hinted, true);
            int[] order = {4, 0, 8, 1, 3, 5, 7, 2, 6};
            for (int i : order) {
                a.lightChunk(i % 3, i / 3);
                b.lightChunk(i % 3, i / 3);
            }
            a.runUpdates();
            b.runUpdates();
            for (int i = 0; i < 9; i++) assertSameLight(plain.lights[i], hinted.lights[i], "round " + round + " chunk " + i);
        }
    }

    @Test
    void sectionHintsReflectPalette() {
        ChunkColumn col = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, -64, 24);
        assertEquals(AIR, col.sectionUniformBlock(5));
        assertFalse(col.sectionMaybeHas(5, LightTables.EMITS));
        col.setBlock(3, 4, 5, REG.defaultStateId("minecraft:glowstone"));   // y=4 → 구역 4
        assertEquals(-1, col.sectionUniformBlock(4));
        assertTrue(col.sectionMaybeHas(4, LightTables.EMITS));
        col.setBlock(3, 4, 5, AIR);   // 팔레트에는 옛 항목이 남는다 — "있을 수도 있다"(바닐라 maybeHas 와 같다)
        assertTrue(col.sectionMaybeHas(4, LightTables.EMITS));
        int[] lit = new int[4096];
        java.util.Arrays.fill(lit, REG.defaultStateId("minecraft:sea_lantern"));
        col.loadSectionBlocks(10, lit);
        assertEquals(REG.defaultStateId("minecraft:sea_lantern"), col.sectionUniformBlock(10));
        assertTrue(col.sectionMaybeHas(10, LightTables.EMITS));
    }

    /** 3×3 청크 세계. hints 면 ChunkLevel 의 빛 세계처럼 구역 힌트를 준다. */
    private static final class Grid implements LightWorld {
        final ChunkColumn[] cols;
        final ChunkLight[] lights = new ChunkLight[9];
        final boolean hints;

        Grid(ChunkColumn[] cols, boolean hints) {
            this.cols = cols;
            this.hints = hints;
            for (int i = 0; i < 9; i++) lights[i] = new ChunkLight();
        }

        private int idx(int cx, int cz) { return cx >= 0 && cx < 3 && cz >= 0 && cz < 3 ? cx + cz * 3 : -1; }

        public ChunkLight light(int cx, int cz) { int i = idx(cx, cz); return i < 0 ? null : lights[i]; }

        public int state(int x, int y, int z) {
            if (y < -64 || y >= 320) return 12958;
            return cols[idx(x >> 4, z >> 4)].getBlock(x & 15, y, z & 15);
        }

        public int uniformSectionState(int cx, int sy, int cz) {
            int i = idx(cx, cz);
            return !hints || i < 0 ? -1 : cols[i].sectionUniformBlock(sy + 4);
        }

        public boolean sectionMayEmit(int cx, int sy, int cz) {
            int i = idx(cx, cz);
            return !hints || i < 0 || cols[i].sectionMaybeHas(sy + 4, LightTables.EMITS);
        }

        public int unloadedState() { return BEDROCK; }

        public int minY() { return -64; }

        public int maxY() { return 320; }
    }
}
