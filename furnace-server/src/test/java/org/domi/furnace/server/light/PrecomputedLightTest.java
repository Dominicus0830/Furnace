// 생성 스레드에서 청크 하나씩 미리 계산한 빛 + 올릴 때 가장자리 맞추기가, 청크를 차례로 켠 빛과 칸마다 같은지 확인한다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class PrecomputedLightTest {

    private static void terrain(IncrementalLightTest.W w, BlockRegistry reg, long seed) {
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        int glow = reg.defaultStateId("minecraft:glowstone"), leaves = reg.defaultStateId("minecraft:oak_leaves");
        Random r = new Random(seed);
        int[][] h = new int[32][32];
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h[x][z] = 18 + r.nextInt(18);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            for (int y = -64; y < 70; y++) {
                int s = y < h[x][z] ? stone : air;
                w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, s);
            }
        }
        // 동굴·지붕·발광 블록을 흩뿌려 가장자리 가로지르기를 자극한다
        for (int n = 0; n < 400; n++) {
            int x = r.nextInt(32), z = r.nextInt(32), y = 10 + r.nextInt(40);
            int s = switch (r.nextInt(4)) { case 0 -> air; case 1 -> glow; case 2 -> leaves; default -> stone; };
            w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, s);
        }
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) if ((x ^ z) % 5 == 0) w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, 50, z & 15, stone);
    }

    @Test
    void precomputedThenAttachedEqualsSequential() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        for (long seed = 1; seed <= 3; seed++) {
            IncrementalLightTest.W ref = new IncrementalLightTest.W(reg, stone, air);
            terrain(ref, reg, seed);
            LevelLight seq = new LevelLight(ref, true);
            for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) seq.lightChunk(cx, cz);
            seq.runUpdates();

            IncrementalLightTest.W pre = new IncrementalLightTest.W(reg, stone, air);
            terrain(pre, reg, seed);
            int[][] order = {{1, 1}, {0, 0}, {1, 0}, {0, 1}};
            int[][] orders2 = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
            for (int[][] ord : new int[][][] {order, orders2}) {
                IncrementalLightTest.W w = ord == order ? pre : fresh(reg, stone, air, seed);
                LevelLight eng = new LevelLight(w, true);
                for (int[] c : ord) {
                    var col = w.cols[w.idx(c[0], c[1])];
                    // 이 청크 하나만 있는 세계에서 미리 계산 → 올리면서 가장자리 맞추기
                    w.lights[w.idx(c[0], c[1])] = LevelLight.computeIsolated(c[0], c[1],
                            (x, y, z) -> col.getBlock(x & 15, y, z & 15), w.unloadedState(), -64, 320, true);
                    eng.attachPrecomputed(c[0], c[1]);
                }
                eng.runUpdates();
                for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -64; y < 70; y++) {
                    assertEquals(seq.skyLight(x, y, z), eng.skyLight(x, y, z), "seed " + seed + " sky " + x + "," + y + "," + z);
                    assertEquals(seq.blockLight(x, y, z), eng.blockLight(x, y, z), "seed " + seed + " block " + x + "," + y + "," + z);
                }
            }
        }
    }

    /** 청크를 올리는 사이사이 블록을 바꿔도(runUpdates 없이 blockChanged 만) 최종 빛이 같은 블록 배치를 차례로 켠 빛과 같다. */
    @Test
    void blockChangesBetweenAttachesStillConverge() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        int glow = reg.defaultStateId("minecraft:glowstone");
        int[] pal = {stone, air, glow};
        for (long seed = 11; seed <= 13; seed++) {
            IncrementalLightTest.W w = fresh(reg, stone, air, seed);
            LevelLight eng = new LevelLight(w, true);
            Random r = new Random(seed);
            int[][] order = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            boolean[] lit = new boolean[4];
            for (int[] c : order) {
                var col = w.cols[w.idx(c[0], c[1])];
                w.lights[w.idx(c[0], c[1])] = LevelLight.computeIsolated(c[0], c[1],
                        (x, y, z) -> col.getBlock(x & 15, y, z & 15), w.unloadedState(), -64, 320, true);
                eng.attachPrecomputed(c[0], c[1]);
                lit[w.idx(c[0], c[1])] = true;
                // 올라온 청크 안과 경계 근처의 블록을 바꾼다(runUpdates 는 다음 attach 안에서 돈다)
                for (int n = 0; n < 40; n++) {
                    int x = r.nextInt(32), z = r.nextInt(32), y = 15 + r.nextInt(40);
                    if (!lit[w.idx(x >> 4, z >> 4)]) {
                        w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, pal[r.nextInt(3)]);   // 아직 안 올라온 청크: 블록만 바꾼다
                        continue;
                    }
                    int s = pal[r.nextInt(3)], old = w.state(x, y, z);
                    w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, s);
                    if (LightTables.hasDifferentLightProperties(old, s)) eng.blockChanged(x, y, z);
                }
            }
            eng.runUpdates();

            IncrementalLightTest.W fin = new IncrementalLightTest.W(reg, stone, air);
            for (int i = 0; i < 4; i++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 70; y++) {
                fin.cols[i].setBlock(x, y, z, w.cols[i].getBlock(x, y, z));
            }
            LevelLight ref = new LevelLight(fin, true);
            for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) ref.lightChunk(cx, cz);
            ref.runUpdates();
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -64; y < 70; y++) {
                assertEquals(ref.skyLight(x, y, z), eng.skyLight(x, y, z), "seed " + seed + " sky " + x + "," + y + "," + z);
                assertEquals(ref.blockLight(x, y, z), eng.blockLight(x, y, z), "seed " + seed + " block " + x + "," + y + "," + z);
            }
        }
    }

    private static IncrementalLightTest.W fresh(BlockRegistry reg, int stone, int air, long seed) {
        var w = new IncrementalLightTest.W(reg, stone, air);
        terrain(w, reg, seed);
        return w;
    }
}
