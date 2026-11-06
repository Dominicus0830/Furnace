// 청크 저장·로드 빠른 경로가 예전 경로와 같은 상태를 만드는지:
// PaletteStorage.loadIndexed == load(펼친 배열)(내부 상태까지), 섹션 단위 기둥 탐색 == 칸별 getBlock 탐색,
// scratch 를 열지 않는 rebuildHeightmapsAfterSurface == 예전(scratch 여는) 구현.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ChunkIoFastPathParityTest {

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void assertSameStorage(PaletteStorage a, PaletteStorage b, String where) throws Exception {
        for (String f : new String[]{"bits", "paletteSize", "singleValue"}) assertEquals(field(a, f), field(b, f), where + " " + f);
        for (String f : new String[]{"palette", "lookupKeys", "lookupVals"}) {
            assertArrayEquals((int[]) field(a, f), (int[]) field(b, f), where + " " + f);
        }
        BitStorage sa = (BitStorage) field(a, "storage"), sb = (BitStorage) field(b, "storage");
        assertEquals(sa == null, sb == null, where + " storage");
        if (sa != null) assertArrayEquals(sa.data(), sb.data(), where + " storage data");
        assertArrayEquals(a.unpack(), b.unpack(), where + " unpack");
    }

    private static int ceillog2(int n) { return 32 - Integer.numberOfLeadingZeros(n - 1); }

    @Test
    void loadIndexed_sameStateAsLoad() throws Exception {
        SplittableRandom r = new SplittableRandom(0x10AD);
        for (int iter = 0; iter < 3000; iter++) {
            // NBT 팔레트: 겹치는 값(모르는 이름 → 공기 둘), 안 쓰는 항목, 간접 경계(16/17, 256/257)와 직접 크기
            int[] sizes = {1, 2, 15, 16, 17, 100, 255, 256, 257, 300, 1000};
            int p = sizes[r.nextInt(sizes.length)];
            int[] vals = new int[p];
            for (int i = 0; i < p; i++) vals[i] = r.nextInt(4) == 0 ? r.nextInt(3) : r.nextInt(27000);
            if (r.nextInt(3) == 0 && p > 1) vals[p - 1] = vals[0];
            int used = 1 + r.nextInt(p);   // 앞 used 개만 칸에 쓴다(뒤는 안 쓰는 항목)
            int bits = Math.max(4, ceillog2(Math.max(2, p)));
            BitStorage idx = new BitStorage(bits, 4096);
            int[] flat = new int[4096];
            boolean runs = r.nextBoolean();
            int k = r.nextInt(used);
            for (int i = 0; i < 4096; i++) {
                if (!runs || r.nextInt(64) == 0) k = r.nextInt(used);
                idx.set(i, k);
                flat[i] = vals[k];
            }
            PaletteStorage a = new PaletteStorage(4096, 4, 8, 15, 0);
            a.load(flat, 0);
            PaletteStorage b = new PaletteStorage(4096, 4, 8, 15, 0);
            int nonAir = b.loadIndexed(vals, idx);
            assertSameStorage(a, b, "iter=" + iter + " p=" + p);
            int expect = 0;
            for (int v : flat) if (!ChunkSection.isAir(v)) expect++;
            assertEquals(expect, nonAir, "iter=" + iter + " nonAir");
        }
    }

    @Test
    void loadIndexed_outOfRangeIndexThrowsLikeBefore() {
        BitStorage idx = new BitStorage(4, 4096);
        idx.set(7, 5);
        PaletteStorage b = new PaletteStorage(4096, 4, 8, 15, 0);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> b.loadIndexed(new int[]{1, 2}, idx));
    }

    /** 예전 칸별 탐색 그대로. kind 0 MB_NO_LEAVES(월드 y, 없으면 minY-1) / 1 MB / 2 OCEAN_FLOOR(높이값, 없으면 0). */
    private static int legacyTop(ChunkColumn col, int x, int z, java.util.function.IntPredicate p, boolean worldY) {
        int minY = ChunkColumn.MIN_Y;
        for (int y = minY + ChunkColumn.SECTION_COUNT * 16 - 1; y >= minY; y--) {
            if (p.test(col.getBlock(x, y, z))) return worldY ? y : (y - minY) + 1;
        }
        return worldY ? minY - 1 : 0;
    }

    private static ChunkColumn randomColumn(SplittableRandom r, int[] pool) {
        ChunkColumn col = new ChunkColumn(0, 15, 7, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        for (int sec = 0; sec < ChunkColumn.SECTION_COUNT; sec++) {
            int kind = r.nextInt(6);
            int baseY = ChunkColumn.MIN_Y + sec * 16;
            if (kind == 0) continue;   // 공기
            if (kind == 1) {   // 한 값(물·돌·잎·유리 등)
                int v = pool[r.nextInt(pool.length)];
                for (int i = 0; i < 4096; i++) col.setBlock(i & 15, baseY + (i >> 8), (i >> 4) & 15, v);
                continue;
            }
            int density = kind == 2 ? 300 : kind == 3 ? 20 : 2;   // 드문드문 ~ 빽빽
            for (int i = 0; i < 4096; i++) {
                if (r.nextInt(density) == 0) col.setBlock(i & 15, baseY + (i >> 8), (i >> 4) & 15, pool[r.nextInt(pool.length)]);
            }
        }
        return col;
    }

    private static int[] pool(BlockRegistry reg) {
        String[] names = {"minecraft:stone", "minecraft:water", "minecraft:oak_leaves", "minecraft:glass", "minecraft:torch",
                "minecraft:rail", "minecraft:cave_air", "minecraft:void_air", "minecraft:lava", "minecraft:grass_block",
                "minecraft:short_grass", "minecraft:seagrass", "minecraft:kelp", "minecraft:birch_leaves", "minecraft:snow"};
        int[] p = new int[names.length];
        for (int i = 0; i < names.length; i++) p[i] = reg.defaultStateId(names[i]);
        return p;
    }

    @Test
    void columnScans_matchPerBlockScan() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int[] pool = pool(reg);
        SplittableRandom r = new SplittableRandom(0x70B);
        java.lang.reflect.Method mb = ChunkColumn.class.getDeclaredMethod("isMotionBlockingBlock", int.class);
        java.lang.reflect.Method of = ChunkColumn.class.getDeclaredMethod("isOceanFloorBlock", int.class);
        java.lang.reflect.Method lv = ChunkColumn.class.getDeclaredMethod("isLeaves", int.class);
        mb.setAccessible(true); of.setAccessible(true); lv.setAccessible(true);
        java.util.function.IntPredicate isMb = s -> call(mb, s), isOf = s -> call(of, s), isLv = s -> call(lv, s);
        for (int iter = 0; iter < 200; iter++) {
            ChunkColumn col = randomColumn(r, pool);
            if (r.nextBoolean()) col.compactSections();
            else if (r.nextBoolean()) col.sectionBlocks(r.nextInt(ChunkColumn.SECTION_COUNT));   // scratch 가 열린 섹션 섞기
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                String w = "iter=" + iter + " " + x + "," + z;
                assertEquals(legacyTop(col, x, z, s -> isMb.test(s) && !isLv.test(s), true), col.motionBlockingNoLeavesBlockY(x, z), w + " MBNL");
                assertEquals(ChunkColumn.MIN_Y + legacyTop(col, x, z, isMb, false) - 1, col.motionBlockingBlockY(x, z), w + " MB");
                assertEquals(ChunkColumn.MIN_Y + legacyTop(col, x, z, isOf, false) - 1, col.oceanFloorBlockY(x, z), w + " OF");
            }
        }
    }

    private static boolean call(java.lang.reflect.Method m, int s) {
        try { return (boolean) m.invoke(null, s); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    /** 수정 전 rebuildHeightmapsAfterSurface 의 기둥 계산(섹션마다 scratch 를 열어 칸 배열로 읽음). */
    private static int[][] legacyRebuild(ChunkColumn col, java.util.function.IntPredicate isOf) {
        int[] surface = new int[256], ocean = new int[256];
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int sv = 0, ov = 0;
            for (int sec = ChunkColumn.SECTION_COUNT - 1; sec >= 0 && (sv == 0 || ov == 0); sec--) {
                for (int ly = 15; ly >= 0; ly--) {
                    int st = col.getBlock(lx, ChunkColumn.MIN_Y + sec * 16 + ly, lz);
                    int y1 = sec * 16 + ly + 1;
                    if (sv == 0 && !ChunkSection.isAir(st)) sv = y1;
                    if (ov == 0 && isOf.test(st)) ov = y1;
                    if (sv != 0 && ov != 0) break;
                }
            }
            surface[lz * 16 + lx] = sv;
            ocean[lz * 16 + lx] = ov;
        }
        return new int[][]{surface, ocean};
    }

    @Test
    void rebuildHeightmaps_sameValuesWithoutOpeningScratch() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int[] pool = pool(reg);
        java.lang.reflect.Method of = ChunkColumn.class.getDeclaredMethod("isOceanFloorBlock", int.class);
        of.setAccessible(true);
        SplittableRandom r = new SplittableRandom(0x5C2);
        for (int iter = 0; iter < 200; iter++) {
            ChunkColumn col = randomColumn(r, pool);
            col.compactSections();
            int[][] expect = legacyRebuild(col, s -> call(of, s));
            col.rebuildHeightmapsAfterSurface();
            for (ChunkSection s : col.sections()) assertNull(field(s, "scratch"), "iter=" + iter + " scratch 열림");
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                assertEquals(ChunkColumn.MIN_Y + expect[0][i] - 1, col.surfaceBlockY(x, z), "iter=" + iter + " surface");
                assertEquals(ChunkColumn.MIN_Y + expect[1][i] - 1, col.oceanFloorBlockY(x, z), "iter=" + iter + " ocean");
            }
        }
    }
}
