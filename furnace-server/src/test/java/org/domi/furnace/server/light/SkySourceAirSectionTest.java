// 하늘빛 원천 처음 계산(computeSources)이 바닐라 ChunkSkyLightSources.findLowestSourceY 와 같은지 본다.
// 바닐라는 공기뿐인 구역을 만나면 위 칸 상태를 AIR 로 되돌리고 구역을 건너뛴다 — 그 바로 위 구역 맨 아래 줄 블록의 아래 면은 검사하지 않는다.
// 바닐라 서버 jar 대조(평지 기반암1·공기47·반 블록): 반 블록이 섹션 맨 아래 줄(y=-16)이면 그 아래 y=-17..-63 이 모두 15, 맨 위 줄(y=-17)이면 0.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.junit.jupiter.api.Test;

class SkySourceAirSectionTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final int AIR = REG.defaultStateId("minecraft:air");
    private static final int CAVE_AIR = REG.defaultStateId("minecraft:cave_air");
    private static final int BEDROCK = REG.defaultStateId("minecraft:bedrock");
    private static final int STONE = REG.defaultStateId("minecraft:stone");
    private static final int SLAB = REG.defaultStateId("minecraft:smooth_stone_slab");   // type=bottom: 아래 면만 꽉 참, 빛은 통과
    private static final String[] SPARSE = {"minecraft:smooth_stone_slab", "minecraft:oak_stairs", "minecraft:glass", "minecraft:stone",
            "minecraft:oak_leaves", "minecraft:water", "minecraft:cobweb", "minecraft:snow", "minecraft:dirt_path", "minecraft:cave_air",
            "minecraft:oak_slab", "minecraft:ice", "minecraft:torch"};

    private static ChunkColumn flat() {
        ChunkColumn col = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, -64, 24);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) col.setBlock(x, -64, z, BEDROCK);
        return col;
    }

    private static void layer(ChunkColumn col, int y, int state) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) col.setBlock(x, y, z, state);
    }

    /** 힌트 없는 고립 계산과 구역 힌트를 주는 생성 스레드 경로(ChunkLevel.precomputeLight) 둘 다의 원천 높이. */
    private static int[][] sources(ChunkColumn col) {
        ChunkLight plain = LevelLight.computeIsolated(0, 0, (x, y, z) -> col.getBlock(x & 15, y, z & 15), BEDROCK, -64, 320, true);
        ChunkLevel.precomputeLight(col, 0, 0, BEDROCK, true);
        return new int[][] {plain.lowestSource, col.light().lowestSource};
    }

    @Test
    void slabOnBottomRowAboveAirSections_doesNotStopSources() {
        ChunkColumn col = flat();
        layer(col, -16, SLAB);   // 섹션 -1 맨 아래 줄, 섹션 -2·-3 은 공기뿐
        for (int[] ls : sources(col)) {
            assertEquals(-63, ls[0]);   // 기반암 바로 위까지 원천(바닐라 서버: y=-17..-63 모두 15)
            assertEquals(-63, ls[255]);
        }
        ChunkLight l = col.light();
        assertEquals(15, l.skyAt(0, -32, 0));   // 바닐라 서버처럼 반 블록 아래도 하늘빛 15
        assertEquals(15, l.skyAt(8, -63, 8));
    }

    @Test
    void slabOnTopRow_stopsSources() {
        ChunkColumn col = flat();
        layer(col, -17, SLAB);   // 섹션 -2 맨 위 줄 — 그 아래 칸(같은 구역)과의 면은 바닐라도 검사한다
        for (int[] ls : sources(col)) assertEquals(-17, ls[0]);
    }

    @Test
    void sectionBelowNotOnlyAir_stopsSources() {
        ChunkColumn col = flat();
        layer(col, -16, SLAB);
        col.setBlock(15, -20, 15, STONE);   // 섹션 -2 에 비공기 한 칸 → 바닐라도 칸마다 검사해서 반 블록에서 멈춘다
        for (int[] ls : sources(col)) {
            assertEquals(-16, ls[0]);
            assertEquals(-16, ls[15 + 15 * 16 - 1]);
        }
    }

    @Test
    void caveAirOnlySection_countsAsAir() {
        ChunkColumn col = flat();
        layer(col, -16, SLAB);
        for (int y = -32; y < -16; y++) layer(col, y, CAVE_AIR);   // 섹션 -2 를 cave_air 로(바닐라 hasOnlyAir 는 isAir 로 센다)
        col.setBlock(3, -25, 3, AIR);   // 섞인 팔레트(uniform 힌트 없음)
        for (int[] ls : sources(col)) assertEquals(-63, ls[0]);
    }

    /** 바닐라 findLowestSourceY 를 그대로 옮긴 모형(fillFrom: Math.max(…, minY), 비공기 구역이 없으면 minY). */
    private static int vanillaLowest(ChunkColumn col, int x, int z) {
        int minY = -65, sections = 24;
        int highest = -1;
        for (int i = sections - 1; i >= 0; i--) if (!onlyAir(col, i)) { highest = i; break; }
        if (highest == -1) return minY;
        int aboveY = (highest - 4 + 1) * 16;
        int aboveState = AIR;
        for (int i = highest; i >= 0; i--) {
            if (onlyAir(col, i)) {
                aboveState = AIR;
                aboveY = (i - 4) * 16;
            } else {
                for (int ly = 15; ly >= 0; ly--) {
                    int y = (i - 4) * 16 + ly;
                    int here = col.getBlock(x, y, z);
                    if (edgeOccluded(aboveState, here)) return Math.max(aboveY, minY);
                    aboveState = here;
                    aboveY = y;
                }
            }
        }
        return minY;
    }

    private static boolean onlyAir(ChunkColumn col, int i) {
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (!ChunkSection.isAir(col.getBlock(x, (i - 4) * 16 + y, z))) return false;
        }
        return true;
    }

    private static boolean edgeOccluded(int above, int below) {
        if (LightTables.lightBlock(below) != 0) return true;
        return LightTables.faceShapesOcclude(LightTables.faceShape(above, LightEngine.DOWN), LightTables.faceShape(below, LightEngine.UP));
    }

    /** 무작위 열: 공기뿐인 구역·cave_air 구역·통째로 찬 구역·드문 블록(맨 아래·맨 위 줄에 자주)을 섞는다. */
    private static ChunkColumn randomColumn(Random r) {
        ChunkColumn col = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, -64, 24);
        for (int i = 0; i < 24; i++) {
            int kind = r.nextInt(6);
            if (kind <= 1) continue;   // 공기뿐
            int[] blocks = new int[4096];
            if (kind == 2) java.util.Arrays.fill(blocks, CAVE_AIR);
            if (kind == 3 && r.nextInt(3) == 0) java.util.Arrays.fill(blocks, REG.defaultStateId(SPARSE[r.nextInt(SPARSE.length)]));
            if (kind >= 4) {
                int row = r.nextBoolean() ? 0 : r.nextBoolean() ? 15 : r.nextInt(16);
                int s = REG.defaultStateId(SPARSE[r.nextInt(SPARSE.length)]);
                for (int n = 0; n < 256; n++) if (r.nextInt(4) != 0) blocks[row * 256 + n] = s;
            }
            if (kind == 5) for (int n = 0; n < 6; n++) blocks[r.nextInt(4096)] = REG.defaultStateId(SPARSE[r.nextInt(SPARSE.length)]);
            col.loadSectionBlocks(i, blocks);
        }
        return col;
    }

    @Test
    void randomColumnsMatchVanillaModel() {
        Random r = new Random(20261114);
        for (int i = 0; i < 400; i++) {
            ChunkColumn col = randomColumn(r);
            int[] expected = new int[256];
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) expected[x + z * 16] = vanillaLowest(col, x, z);
            int[][] got = sources(col);
            assertArrayEquals(expected, got[0], "plain " + i);
            assertArrayEquals(expected, got[1], "hinted " + i);
        }
    }
}
