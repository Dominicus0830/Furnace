// A08 조사: 여러 모양·불투명도의 블록(반 블록·계단·물·잎·유리·색유리·얼음 …)과 발광원을 청크 경계 둘레에 무작위로 놓고 지우며
// (1) 증분 갱신한 빛이 처음부터 다시 계산한 빛과 같은지, (2) 블록빛이 독립 구현(고정점 반복)과 같은지 칸마다 본다.
// 기존 IncrementalLightTest 는 돌·공기·발광석만 쓴다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A08LightRandomizedTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static int[] palette() {
        Blocks b = Blocks.get();
        Prop type = B.prop("type");
        int slab = b.defaultState("minecraft:oak_slab");
        return new int[] {
            b.defaultState("minecraft:stone"), b.defaultState("minecraft:air"), b.defaultState("minecraft:air"), b.defaultState("minecraft:air"),
            b.defaultState("minecraft:glowstone"), b.defaultState("minecraft:sea_lantern"), b.defaultState("minecraft:lava"),
            b.defaultState("minecraft:torch"), b.defaultState("minecraft:light"),
            type.with(slab, "bottom"), type.with(slab, "top"), b.defaultState("minecraft:oak_stairs"),
            b.defaultState("minecraft:water"), b.defaultState("minecraft:oak_leaves"), b.defaultState("minecraft:glass"),
            b.defaultState("minecraft:tinted_glass"), b.defaultState("minecraft:ice"), b.defaultState("minecraft:snow"),
            b.defaultState("minecraft:farmland"), b.defaultState("minecraft:honey_block"), b.defaultState("minecraft:cobweb"),
            b.defaultState("minecraft:composter"), b.defaultState("minecraft:white_carpet"), b.defaultState("minecraft:oak_trapdoor"),
        };
    }

    @Test
    void incrementalMatchesFromScratchWithMixedShapes() {
        for (long seed = 1; seed <= 6; seed++) runOnce(seed, false);
    }

    @Test
    void blockLightMatchesBruteForceReference() {
        for (long seed = 11; seed <= 16; seed++) runOnce(seed, true);
    }

    private void runOnce(long seed, boolean reference) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        IncrementalLightTest.W w = new IncrementalLightTest.W(reg, stone, air);
        LevelLight inc = new LevelLight(w, true);
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) inc.lightChunk(cx, cz);
        inc.runUpdates();
        Random r = new Random(seed);
        int[] pal = palette();
        for (int n = 0; n < 500; n++) {
            // 청크 경계(x=15/16, z=15/16) 둘레를 자주 건드린다
            int x = r.nextInt(3) == 0 ? 14 + r.nextInt(4) : r.nextInt(32), z = r.nextInt(3) == 0 ? 14 + r.nextInt(4) : r.nextInt(32);
            int y = 22 + r.nextInt(14);
            int s = pal[r.nextInt(pal.length)];
            int old = w.state(x, y, z);
            w.cols[w.idx(x >> 4, z >> 4)].setBlock(x & 15, y, z & 15, s);
            if (LightTables.hasDifferentLightProperties(old, s)) inc.blockChanged(x, y, z);
            if (r.nextInt(7) == 0) inc.runUpdates();
        }
        inc.runUpdates();

        IncrementalLightTest.W fresh = new IncrementalLightTest.W(reg, stone, air);
        for (int i = 0; i < 4; i++) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 70; y++) {
                fresh.cols[i].setBlock(x, y, z, w.cols[i].getBlock(x, y, z));
            }
        }
        LevelLight ref = new LevelLight(fresh, true);
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) ref.lightChunk(cx, cz);
        ref.runUpdates();
        int badSky = 0, badBlock = 0;
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -64; y < 70; y++) {
            if (ref.skyLight(x, y, z) != inc.skyLight(x, y, z)) { badSky++; if (sb.length() < 400) sb.append("sky ").append(x).append(',').append(y).append(',').append(z).append(" scratch=").append(ref.skyLight(x, y, z)).append(" inc=").append(inc.skyLight(x, y, z)).append('\n'); }
            if (ref.blockLight(x, y, z) != inc.blockLight(x, y, z)) { badBlock++; if (sb.length() < 400) sb.append("block ").append(x).append(',').append(y).append(',').append(z).append(" scratch=").append(ref.blockLight(x, y, z)).append(" inc=").append(inc.blockLight(x, y, z)).append('\n'); }
        }
        assertEquals(0, badSky + badBlock, "seed " + seed + " 증분≠처음부터: sky " + badSky + " block " + badBlock + "\n" + sb);
        if (reference) bruteForceBlockLight(fresh, ref, seed);
    }

    /** 독립 구현: 발광 칸에서 시작해 고정점까지 반복(이웃 값 − max(1, 빛 막는 정도), 면이 가리면 못 지남). */
    private void bruteForceBlockLight(IncrementalLightTest.W w, LevelLight engine, long seed) {
        int X = 32, Z = 32, Y0 = -64, Y1 = 70, H = Y1 - Y0;
        int[][][] L = new int[X][H][Z];
        int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
        for (int x = 0; x < X; x++) for (int y = 0; y < H; y++) for (int z = 0; z < Z; z++) L[x][y][z] = LightTables.lightEmission(w.state(x, y + Y0, z));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int x = 0; x < X; x++) for (int y = 0; y < H; y++) for (int z = 0; z < Z; z++) {
                int to = w.state(x, y + Y0, z);
                int op = Math.max(1, LightTables.lightBlock(to));
                int best = L[x][y][z];
                for (int d = 0; d < 6; d++) {
                    int nx = x - DX[d], ny = y - DY[d], nz = z - DZ[d];   // 이웃 → 이 칸 방향 d
                    if (nx < 0 || nx >= X || ny < 0 || ny >= H || nz < 0 || nz >= Z) continue;
                    int nl = L[nx][ny][nz];
                    if (nl - op <= best) continue;
                    int from = w.state(nx, ny + Y0, nz);
                    if (LightTables.shapeOccludes(from, to, d)) continue;
                    best = nl - op;
                }
                if (best != L[x][y][z]) { L[x][y][z] = best; changed = true; }
            }
        }
        int bad = 0, lit = 0, partial = 0;
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < X; x++) for (int y = 0; y < H; y++) for (int z = 0; z < Z; z++) {
            if (L[x][y][z] > 0) lit++;
            if (L[x][y][z] > 0 && L[x][y][z] < 14) partial++;
            // 바깥(월드 밖 청크) 둘레는 엔진이 안 채운다 — 가장자리 한 칸은 비교에서 뺀다
            if (x == 0 || z == 0 || x == X - 1 || z == Z - 1) continue;
            if (engine.blockLight(x, y + Y0, z) != L[x][y][z]) { bad++; if (sb.length() < 300) sb.append(x).append(',').append(y + Y0).append(',').append(z).append(" engine=").append(engine.blockLight(x, y + Y0, z)).append(" ref=").append(L[x][y][z]).append('\n'); }
        }
        org.junit.jupiter.api.Assertions.assertTrue(lit > 1000 && partial > 500, "시험이 헛돈다: lit=" + lit + " partial=" + partial);
        assertEquals(0, bad, "seed " + seed + " 블록빛이 독립 구현과 다름: " + bad + "\n" + sb);
    }
}
