package furnacetools;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.SplittableRandom;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * N2b 정답기(V1): 바닐라 LevelChunkSection 의 개수 3개(nonEmptyBlockCount·tickingBlockCount·tickingFluidCount)와
 * 하이트맵(Heightmap.primeHeightmaps·update) 결과를 바닐라 클래스로 직접 계산해 시험 정답표로 쓴다.
 * 출력은 바닐라 데이터가 아니라 "이 입력에 바닐라가 낸 숫자"뿐이다(입력은 시드로 다시 만든다 — 생성기는 시험 쪽과 같아야 한다).
 * 인자: <출력>(풀어 쓴 텍스트 — 실행기가 gzip 으로 묶는다). 다시 만드는 법은 docs/feature-parity/compat-nms-n2b0-survey-2026-11-14.md 4장.
 */
public class ExportSectionOracle {
    static final int VERSION = 1;
    static Field FE, FF, FG;

    public static void main(String[] a) throws Exception {
        long t0 = System.nanoTime();
        Vanilla.boot();
        long t1 = System.nanoTime();
        FE = LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount"); FE.setAccessible(true);
        FF = LevelChunkSection.class.getDeclaredField("tickingBlockCount"); FF.setAccessible(true);
        FG = LevelChunkSection.class.getDeclaredField("tickingFluidCount"); FG.setAccessible(true);
        IdMapper<BlockState> ids = Block.BLOCK_STATE_REGISTRY;
        int n = ids.size();
        BlockState air = ids.byId(0);
        // 상태별 깃발: 1 = 하나씩 놓을 때 nonEmpty+1, 2 = tickingBlock+1, 4 = tickingFluid+1, 8 = 다시 셀 때 tickingFluid+1.
        // (다시 셀 때 nonEmpty 는 1 + 4 비트 합 — 유체 칸을 두 번 센다. 아래에서 맞는지 확인한다)
        int[] flags = new int[n];
        for (int s = 0; s < n; s++) {
            BlockState st = ids.byId(s);
            LevelChunkSection inc = new LevelChunkSection(new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES), null);
            inc.setBlockState(0, 0, 0, st);
            int[] c = counts(inc);
            PalettedContainer<BlockState> one = new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES);
            one.set(0, 0, 0, st);
            int[] r = counts(new LevelChunkSection(one, null));
            int f = (c[0] == 1 ? 1 : 0) | (c[1] == 1 ? 2 : 0) | (c[2] == 1 ? 4 : 0) | (r[2] == 1 ? 8 : 0);
            if (r[0] != (c[0] + (c[2] == 1 ? 1 : 0)) || r[1] != c[1]) throw new IllegalStateException("예상 밖 다시 세기 s=" + s);
            flags[s] = f;
        }
        long t2 = System.nanoTime();
        StringBuilder out = new StringBuilder();
        out.append("version ").append(VERSION).append('\n');
        out.append("states ").append(n).append('\n');
        StringBuilder fl = new StringBuilder(n);
        for (int f : flags) fl.append(Integer.toHexString(f));
        out.append("flags ").append(fl).append('\n');
        int[] pool = pool(flags);

        // 섹션 시나리오: 하나씩 놓기(A) → 그 블록으로 새 섹션을 만들어 다시 세기(B, 디스크에서 읽은 섹션) → B 에 하나씩 바꾸기(C).
        for (long seed = 1; seed <= 300; seed++) {
            SectionCase sc = SectionCase.make(seed, n, pool);
            LevelChunkSection inc = new LevelChunkSection(new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES), null);
            PalettedContainer<BlockState> raw = new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES);
            for (int i = 0; i < 4096; i++) {
                inc.setBlockState(i & 15, i >> 8, (i >> 4) & 15, ids.byId(sc.cells[i]));
                raw.set(i & 15, i >> 8, (i >> 4) & 15, ids.byId(sc.cells[i]));
            }
            int[] A = counts(inc);
            LevelChunkSection loaded = new LevelChunkSection(raw, null);
            int[] B = counts(loaded);
            for (int k = 0; k < sc.opIdx.length; k++) {
                int i = sc.opIdx[k];
                loaded.setBlockState(i & 15, i >> 8, (i >> 4) & 15, ids.byId(sc.opState[k]));
            }
            int[] C = counts(loaded);
            out.append("section ").append(seed).append(' ').append(sc.hash())
               .append(' ').append(A[0]).append(' ').append(A[1]).append(' ').append(A[2])
               .append(' ').append(B[0]).append(' ').append(B[1]).append(' ').append(B[2])
               .append(' ').append(C[0]).append(' ').append(C[1]).append(' ').append(C[2]).append('\n');
        }
        long t3 = System.nanoTime();

        // 하이트맵 시나리오: 24 섹션 열(minY -64)을 하나씩 놓아 채움 → ProtoChunk 로 primeHeightmaps → LevelChunk.setBlockState 처럼
        // 섹션에 쓰고(같은 상태면 건너뜀) 네 종류 Heightmap.update. 처음(prime 직후)과 끝의 raw long[] 을 적는다.
        Heightmap.Types[] types = {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}; // WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES
        for (long seed = 1; seed <= 40; seed++) {
            ColumnCase cc = ColumnCase.make(seed, n, pool);
            LevelChunkSection[] secs = new LevelChunkSection[24];
            for (int si = 0; si < 24; si++) secs[si] = new LevelChunkSection(new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES), null);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 384; y++) {
                int s = cc.cells[(y * 16 + z) * 16 + x];
                if (s != 0) secs[y >> 4].setBlockState(x, y & 15, z, ids.byId(s));
            }
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, secs, new ProtoChunkTicks<Block>(), new ProtoChunkTicks<Fluid>(), LevelHeightAccessor.create(-64, 384), null, null);
            Heightmap.primeHeightmaps(chunk, EnumSet.copyOf(Arrays.asList(types)));
            out.append("column ").append(seed).append(' ').append(cc.hash()).append(" prime");
            for (Heightmap.Types t : types) appendRaw(out, chunk.getOrCreateHeightmapUnprimed(t).getRawData());
            out.append('\n');
            for (int k = 0; k < cc.opX.length; k++) {
                int x = cc.opX[k], y = cc.opY[k], z = cc.opZ[k];
                BlockState st = ids.byId(cc.opState[k]);
                // 바닐라 LevelChunk.setBlockState 의 "빈 섹션(hasOnlyAir)에 공기면 바로 반환" 분기는 뺐다: 빈 섹션 안에는 하이트맵의
                // 맨 위 칸(h-1)이 올 수 없어(그 위 블록이 있으면 섹션이 비지 않음) update 결과가 같다. N2b-2 로 늘릴 때 주의.
                BlockState old = secs[y >> 4].setBlockState(x, y & 15, z, st);
                if (old == st) continue;
                for (Heightmap.Types t : types) chunk.getOrCreateHeightmapUnprimed(t).update(x, y - 64, z, st);
            }
            out.append("column ").append(seed).append(' ').append(cc.hash()).append(" after");
            for (Heightmap.Types t : types) appendRaw(out, chunk.getOrCreateHeightmapUnprimed(t).getRawData());
            out.append('\n');
        }
        long t4 = System.nanoTime();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8")) { w.write(out.toString()); }
        System.out.printf("부트스트랩 %d ms, 상태 깃발 %d ms(%d 상태), 섹션 300개 %d ms, 열 40개 %d ms%n",
            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, n, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000);
    }

    static int[] counts(LevelChunkSection s) throws Exception {
        return new int[]{FE.getShort(s), FF.getShort(s), FG.getShort(s)};
    }

    static void appendRaw(StringBuilder out, long[] raw) {
        out.append(' ');
        for (int i = 0; i < raw.length; i++) { if (i > 0) out.append(','); out.append(Long.toHexString(raw[i])); }
    }

    // ---- 아래 생성기는 시험(VanillaSectionOracleTest)과 글자 그대로 같아야 한다 ----

    /** 흥미로운 상태: 공기거나 개수 깃발이 nonEmpty 하나만이 아닌 것(무작위 틱·유체). */
    static int[] pool(int[] flags) {
        int c = 0;
        for (int f : flags) if (f != 1) c++;
        int[] p = new int[c];
        c = 0;
        for (int s = 0; s < flags.length; s++) if (flags[s] != 1) p[c++] = s;
        return p;
    }

    static int pick(SplittableRandom r, int n, int[] pool) {
        return r.nextInt(10) < 7 ? pool[r.nextInt(pool.length)] : r.nextInt(n);
    }

    static final class SectionCase {
        int[] cells = new int[4096];
        int[] opIdx, opState;

        static SectionCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed);
            SectionCase c = new SectionCase();
            int k = 1 + r.nextInt(24);
            int[] pal = new int[k];
            for (int i = 0; i < k; i++) pal[i] = pick(r, n, pool);
            int airPct = r.nextInt(101);
            for (int i = 0; i < 4096; i++) c.cells[i] = r.nextInt(100) < airPct ? 0 : pal[r.nextInt(k)];
            int m = r.nextInt(400);
            c.opIdx = new int[m];
            c.opState = new int[m];
            for (int i = 0; i < m; i++) {
                c.opIdx[i] = r.nextInt(4096);
                int q = r.nextInt(3);
                c.opState[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int v : opIdx) h = 31 * h + v;
            for (int v : opState) h = 31 * h + v;
            return h;
        }
    }

    static final class ColumnCase {
        int[] cells = new int[384 * 256]; // (y*16+z)*16+x, y 는 0..383 (= 블록 y + 64)
        int[] opX, opY, opZ, opState;

        static ColumnCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed);
            ColumnCase c = new ColumnCase();
            int k = 2 + r.nextInt(16);
            int[] pal = new int[k];
            for (int i = 0; i < k; i++) pal[i] = pick(r, n, pool);
            int base = r.nextInt(384);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int top = Math.max(0, Math.min(383, base + r.nextInt(33) - 16));
                for (int y = 0; y <= top; y++) {
                    int q = r.nextInt(10);
                    c.cells[(y * 16 + z) * 16 + x] = q < 2 ? 0 : pal[r.nextInt(k)];
                }
            }
            int m = r.nextInt(2000);
            c.opX = new int[m]; c.opY = new int[m]; c.opZ = new int[m]; c.opState = new int[m];
            for (int i = 0; i < m; i++) {
                c.opX[i] = r.nextInt(16);
                c.opZ[i] = r.nextInt(16);
                c.opY[i] = Math.max(0, Math.min(383, base + r.nextInt(49) - 24));
                int q = r.nextInt(3);
                c.opState[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int i = 0; i < opX.length; i++) h = 31 * (31 * (31 * (31 * h + opX[i]) + opY[i]) + opZ[i]) + opState[i];
            return h;
        }
    }
}
