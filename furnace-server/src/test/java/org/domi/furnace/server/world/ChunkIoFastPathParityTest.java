// 청크 저장·로드 빠른 경로가 예전 경로와 같은 상태를 만드는지:
// PaletteStorage.loadIndexed == load(펼친 배열)(내부 상태까지), 섹션 단위 기둥 탐색 == 칸별 getBlock 탐색,
// scratch 를 열지 않는 rebuildHeightmapsAfterSurface == 예전(scratch 여는) 구현.
// IdentityKit 시범 이관(T-O15-02): 차분은 Differential, 예전 기둥 탐색은 레거시 오라클 LegacyColumnScans(since 7d55ec3a).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.SplittableRandom;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.legacy.chunkio.LegacyColumnScans;
import org.junit.jupiter.api.Test;

class ChunkIoFastPathParityTest {

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /** 상태 전부(내부 필드·펼친 값·비공기 수)를 비교할 수 있게 배열로. */
    private static Object[] snapshot(PaletteStorage a, int nonAir) throws Exception {
        BitStorage st = (BitStorage) field(a, "storage");
        return new Object[]{field(a, "bits"), field(a, "paletteSize"), field(a, "singleValue"),
                field(a, "palette"), field(a, "lookupKeys"), field(a, "lookupVals"), st == null ? null : st.data(), a.unpack(), nonAir};
    }

    private static int ceillog2(int n) { return 32 - Integer.numberOfLeadingZeros(n - 1); }

    /** NBT 팔레트 값 + 칸 인덱스 + 같은 내용을 펼친 배열. */
    private record PaletteInput(int[] vals, BitStorage idx, int[] flat) {
        String summary() { return "p=" + vals.length + " vals=" + Differential.shortString(vals); }
    }

    private static PaletteInput randomPalette(SplittableRandom r) {
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
        return new PaletteInput(vals, idx, flat);
    }

    /** 예전 경로: 펼친 배열을 load, 비공기 수는 칸마다 센다. */
    private static Object[] loadFlat(PaletteInput in) throws Exception {
        PaletteStorage a = new PaletteStorage(4096, 4, 8, 15, 0);
        a.load(in.flat, 0);
        int nonAir = 0;
        for (int v : in.flat) if (!ChunkSection.isAir(v)) nonAir++;
        return snapshot(a, nonAir);
    }

    private static Object[] loadIndexed(PaletteInput in) throws Exception {
        PaletteStorage b = new PaletteStorage(4096, 4, 8, 15, 0);
        int nonAir = b.loadIndexed(in.vals, in.idx);
        return snapshot(b, nonAir);
    }

    private static Differential<PaletteInput, Object[]> paletteDiff() {
        return Differential.<PaletteInput, Object[]>of("palette-loadIndexed", ChunkIoFastPathParityTest::randomPalette)
                .seed(0x10AD).iterations(3000).describe(PaletteInput::summary);
    }

    @Test
    void loadIndexed_sameStateAsLoad() {
        paletteDiff().check(ChunkIoFastPathParityTest::loadFlat, ChunkIoFastPathParityTest::loadIndexed);
    }

    @Test
    void loadIndexed_differentialCatchesSwappedPalette() {
        // 변이 점검: 팔레트 앞 두 항목을 바꿔 넣는 틀린 구현을 이 차분이 잡아야 한다
        paletteDiff().assertDetects(ChunkIoFastPathParityTest::loadFlat, in -> {
            int[] v = in.vals.clone();
            if (v.length > 1) { int t = v[0]; v[0] = v[1]; v[1] = t; }
            return loadIndexed(new PaletteInput(v, in.idx, in.flat));
        });
    }

    @Test
    void loadIndexed_outOfRangeIndexThrowsLikeBefore() {
        BitStorage idx = new BitStorage(4, 4096);
        idx.set(7, 5);
        PaletteStorage b = new PaletteStorage(4096, 4, 8, 15, 0);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> b.loadIndexed(new int[]{1, 2}, idx));
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

    private static java.util.function.IntPredicate predicate(String name) throws Exception {
        java.lang.reflect.Method m = ChunkColumn.class.getDeclaredMethod(name, int.class);
        m.setAccessible(true);
        return s -> call(m, s);
    }

    private static boolean call(java.lang.reflect.Method m, int s) {
        try { return (boolean) m.invoke(null, s); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    /** 섹션 상태가 섞인 기둥: 압축된 것, scratch 가 열린 섹션이 있는 것, 그대로. */
    private static ChunkColumn mixedColumn(SplittableRandom r, int[] pool) {
        ChunkColumn col = randomColumn(r, pool);
        if (r.nextBoolean()) col.compactSections();
        else if (r.nextBoolean()) col.sectionBlocks(r.nextInt(ChunkColumn.SECTION_COUNT));   // scratch 가 열린 섹션 섞기
        return col;
    }

    @Test
    void columnScans_matchPerBlockScan() throws Exception {
        int[] pool = pool(BlockRegistry.load("blocks/1.21.1.txt"));
        var isMb = predicate("isMotionBlockingBlock");
        var isOf = predicate("isOceanFloorBlock");
        var isLv = predicate("isLeaves");
        // 결과: 칸마다 (MBNL, MB, OF) — 월드 y
        Differential.<ChunkColumn, int[]>of("column-scans", r -> mixedColumn(r, pool)).seed(0x70B).iterations(200)
                .check(col -> {
                    int[] out = new int[3 * 256];
                    for (int i = 0; i < 256; i++) {
                        int x = i & 15, z = i >> 4;
                        out[3 * i] = LegacyColumnScans.top(col, x, z, s -> isMb.test(s) && !isLv.test(s), true);
                        out[3 * i + 1] = ChunkColumn.MIN_Y + LegacyColumnScans.top(col, x, z, isMb, false) - 1;
                        out[3 * i + 2] = ChunkColumn.MIN_Y + LegacyColumnScans.top(col, x, z, isOf, false) - 1;
                    }
                    return out;
                }, col -> {
                    int[] out = new int[3 * 256];
                    for (int i = 0; i < 256; i++) {
                        int x = i & 15, z = i >> 4;
                        out[3 * i] = col.motionBlockingNoLeavesBlockY(x, z);
                        out[3 * i + 1] = col.motionBlockingBlockY(x, z);
                        out[3 * i + 2] = col.oceanFloorBlockY(x, z);
                    }
                    return out;
                });
    }

    @Test
    void rebuildHeightmaps_sameValuesWithoutOpeningScratch() throws Exception {
        int[] pool = pool(BlockRegistry.load("blocks/1.21.1.txt"));
        var isOf = predicate("isOceanFloorBlock");
        Differential.<ChunkColumn, int[][]>of("rebuild-heightmaps", r -> {
            ChunkColumn col = randomColumn(r, pool);
            col.compactSections();
            return col;
        }).seed(0x5C2).iterations(200).check(col -> {
            int[][] e = LegacyColumnScans.rebuild(col, isOf);
            for (int i = 0; i < 256; i++) { e[0][i] = ChunkColumn.MIN_Y + e[0][i] - 1; e[1][i] = ChunkColumn.MIN_Y + e[1][i] - 1; }
            return e;
        }, col -> {
            col.rebuildHeightmapsAfterSurface();
            for (ChunkSection s : col.sections()) assertNull(field(s, "scratch"), "scratch 열림");
            int[][] got = new int[2][256];
            for (int i = 0; i < 256; i++) {
                got[0][i] = col.surfaceBlockY(i & 15, i >> 4);
                got[1][i] = col.oceanFloorBlockY(i & 15, i >> 4);
            }
            return got;
        });
    }
}
