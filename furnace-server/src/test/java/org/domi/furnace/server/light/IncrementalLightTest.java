// 블록을 바꿔 가며 증분 갱신한 빛이 처음부터 다시 계산한 빛과 칸마다 같은지 확인한다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class IncrementalLightTest {

    static final class W implements LightWorld {
        final ChunkColumn[] cols = new ChunkColumn[4];
        final ChunkLight[] lights = new ChunkLight[4];
        final int bedrock;

        W(BlockRegistry reg, int stone, int dirt) {
            bedrock = reg.defaultStateId("minecraft:bedrock");
            for (int i = 0; i < 4; i++) {
                cols[i] = new ChunkColumn(0, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, -64, 24);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 40; y++) {
                    cols[i].setBlock(x, y, z, y < 30 ? stone : dirt);
                }
                lights[i] = new ChunkLight();
            }
        }

        int idx(int cx, int cz) { return cx >= 0 && cx < 2 && cz >= 0 && cz < 2 ? cx * 2 + cz : -1; }

        public ChunkLight light(int cx, int cz) { int i = idx(cx, cz); return i < 0 ? null : lights[i]; }

        public int state(int x, int y, int z) {
            if (y < -64 || y >= 320) return 12958;
            return cols[idx(x >> 4, z >> 4)].getBlock(x & 15, y, z & 15);
        }

        public int unloadedState() { return bedrock; }

        public int minY() { return -64; }

        public int maxY() { return 320; }
    }

    @Test
    void incrementalMatchesFromScratch() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        int glow = reg.defaultStateId("minecraft:glowstone");
        W w = new W(reg, stone, air);
        LevelLight inc = new LevelLight(w, true);
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) inc.lightChunk(cx, cz);
        inc.runUpdates();
        Random r = new Random(7);
        int[] pal = {stone, air, glow};
        for (int n = 0; n < 150; n++) {
            int x = r.nextInt(32), z = r.nextInt(32), y = 20 + r.nextInt(25);
            int s = pal[r.nextInt(3)];
            int old = w.state(x, y, z);
            w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, s);
            if (LightTables.hasDifferentLightProperties(old, s)) inc.blockChanged(x, y, z);
            if (n % 10 == 9) inc.runUpdates();
        }
        inc.runUpdates();

        W fresh = new W(reg, stone, air);
        for (int i = 0; i < 4; i++) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 70; y++) {
                fresh.cols[i].setBlock(x, y, z, w.cols[i].getBlock(x, y, z));
            }
        }
        LevelLight ref = new LevelLight(fresh, true);
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) ref.lightChunk(cx, cz);
        ref.runUpdates();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -64; y < 70; y++) {
            assertEquals(ref.skyLight(x, y, z), inc.skyLight(x, y, z), "sky " + x + "," + y + "," + z);
            assertEquals(ref.blockLight(x, y, z), inc.blockLight(x, y, z), "block " + x + "," + y + "," + z);
        }
    }
}
