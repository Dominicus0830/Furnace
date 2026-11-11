// 섹션의 비공기 칸 수(nonAirCount)가 모든 경로에서 바닐라 LevelChunkSection 과 같은 isAir 기준
// (air·cave_air·void_air 는 센다 안 함)으로 세어지는지. compact()(월드젠 scratch 되돌림)가 value != AIR 로
// 세어 동굴 공기·공허 공기를 센 틈을 막는다. 바닐라: dvj.a(IIILdtc;Z) 와 dvj$a 방문자는 둘 다 dtc.i()(isAir)로 센다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class ChunkSectionNonAirCountTest {

    private static int airState() { return Blocks.get().defaultState("minecraft:air"); }
    private static int caveAir() { return Blocks.get().defaultState("minecraft:cave_air"); }
    private static int voidAir() { return Blocks.get().defaultState("minecraft:void_air"); }
    private static int stone() { return Blocks.get().defaultState("minecraft:stone"); }

    private static int brute(ChunkSection s) {
        int n = 0;
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (!ChunkSection.isAir(s.getBlock(x, y, z))) n++;
        }
        return n;
    }

    @Test
    void airStatesAreTheThreeVanillaAirs() {
        assertTrue(ChunkSection.isAir(airState()));
        assertTrue(ChunkSection.isAir(caveAir()));
        assertTrue(ChunkSection.isAir(voidAir()));
        assertFalse(ChunkSection.isAir(stone()));
    }

    @Test
    void compactAfterScratch_caveAirOnlySectionIsEmpty() {
        ChunkSection s = new ChunkSection();
        int[] scratch = s.openScratch();
        java.util.Arrays.fill(scratch, caveAir());
        s.compact();
        assertEquals(0, s.nonAirCount());
        assertTrue(s.hasOnlyAir());
    }

    @Test
    void compactAfterScratch_voidAirOnlySectionIsEmpty() {
        ChunkSection s = new ChunkSection();
        int[] scratch = s.openScratch();
        java.util.Arrays.fill(scratch, voidAir());
        s.compact();
        assertEquals(0, s.nonAirCount());
        assertTrue(s.hasOnlyAir());
    }

    @Test
    void compactAfterScratch_mixedMatchesBruteForceAndOtherPaths() {
        int stone = stone(), cave = caveAir(), vd = voidAir();
        int[] flat = new int[4096];
        for (int i = 0; i < 4096; i++) flat[i] = switch (i % 5) { case 0 -> stone; case 1 -> cave; case 2 -> vd; default -> 0; };
        int expected = 0;
        for (int v : flat) if (v == stone) expected++;

        ChunkSection viaScratch = new ChunkSection();
        System.arraycopy(flat, 0, viaScratch.openScratch(), 0, 4096);
        viaScratch.compact();

        ChunkSection viaReplace = new ChunkSection();
        viaReplace.replaceBlocks(flat, 0);

        ChunkSection viaSet = new ChunkSection();
        for (int i = 0; i < 4096; i++) viaSet.setBlock(i & 15, i >> 8, (i >> 4) & 15, flat[i]);

        assertEquals(expected, viaScratch.nonAirCount());
        assertEquals(expected, viaReplace.nonAirCount());
        assertEquals(expected, viaSet.nonAirCount());
        assertEquals(expected, brute(viaScratch));
    }

    @Test
    void setBlockAfterCompact_keepsCountConsistent() {
        ChunkSection s = new ChunkSection();
        java.util.Arrays.fill(s.openScratch(), caveAir());
        s.compact();
        // 동굴 공기 칸을 돌로 바꾸면 +1, 다시 동굴 공기로 바꾸면 -1(음수로 새지 않는다)
        s.setBlock(3, 4, 5, stone());
        assertEquals(1, s.nonAirCount());
        s.setBlock(3, 4, 5, caveAir());
        assertEquals(0, s.nonAirCount());
        assertTrue(s.hasOnlyAir());
    }
}
