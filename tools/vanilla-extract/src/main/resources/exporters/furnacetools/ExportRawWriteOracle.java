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
 * N2b-2 정답기(V1): 로드된 청크에 NMS 원시 쓰기를 섞었을 때 바닐라가 내는 하이트맵 넷과 섹션 개수 셋.
 * 읽은 청크(섹션을 생성자로 만들어 개수를 다시 셈 + primeHeightmaps)에 무작위 연산을 차례로 적용한다:
 *   0 = LevelChunk.setBlockState 의 블록·하이트맵 부분(바닐라 hasOnlyAir && 공기면 건너뜀 → 섹션 쓰기 → 같으면 건너뜀 → 네 종류 update)
 *   1 = LevelChunkSection.setBlockState(개수 ±1, 하이트맵 안 고침)   2 = 블록 컨테이너 set(개수도 하이트맵도 그대로)
 *   3 = Heightmap.update 직접(주어진 상태)   4 = Heightmap.setRawData(맞는 길이)   5 = setRawData(틀린 길이 → 경고 + 그 종류 prime)
 *   6 = 개수 필드 직접 대입(FAWE clearCounts 같은 Field.setShort)
 * 끝에 네 하이트맵 raw long[] 과 섹션 24개의 개수 셋을 적는다. 출력은 "이 입력에 바닐라가 낸 숫자"뿐이다(입력은 시드로 다시 만든다).
 * 인자: <출력>(풀어 쓴 텍스트 — 실행기가 gzip 으로 묶는다). 다시 만드는 법은 docs/feature-parity/compat-nms-n2b2-2026-11-14.md 6장.
 */
public class ExportRawWriteOracle {
    static final int VERSION = 1, CASES = 60;
    static Field FE, FF, FG;

    public static void main(String[] a) throws Exception {
        long t0 = System.nanoTime();
        Vanilla.boot();
        FE = LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount"); FE.setAccessible(true);
        FF = LevelChunkSection.class.getDeclaredField("tickingBlockCount"); FF.setAccessible(true);
        FG = LevelChunkSection.class.getDeclaredField("tickingFluidCount"); FG.setAccessible(true);
        IdMapper<BlockState> ids = Block.BLOCK_STATE_REGISTRY;
        int n = ids.size();
        BlockState air = ids.byId(0);
        // 공기 판정·흥미로운 상태(ExportSectionOracle 의 깃발과 같은 규칙: 1 = 놓으면 nonEmpty+1(공기 아님))
        int[] flags = new int[n];
        for (int s = 0; s < n; s++) {
            LevelChunkSection inc = new LevelChunkSection(new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES), null);
            inc.setBlockState(0, 0, 0, ids.byId(s));
            int[] c = counts(inc);
            flags[s] = (c[0] == 1 ? 1 : 0) | (c[1] == 1 ? 2 : 0) | (c[2] == 1 ? 4 : 0);
        }
        int[] pool = pool(flags);
        long t1 = System.nanoTime();
        StringBuilder out = new StringBuilder();
        out.append("version ").append(VERSION).append('\n');
        Heightmap.Types[] types = {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}; // WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES
        int[] kinds = new int[7];
        for (long seed = 1; seed <= CASES; seed++) {
            RawCase rc = RawCase.make(seed, n, pool);
            LevelChunkSection[] secs = new LevelChunkSection[24];
            for (int si = 0; si < 24; si++) {
                PalettedContainer<BlockState> raw = new PalettedContainer<BlockState>(ids, air, PalettedContainer.Strategy.SECTION_STATES);
                for (int i = 0; i < 4096; i++) {
                    int s = rc.cells[(si * 16 + (i >> 8)) * 256 + (i & 255)];
                    if (s != 0) raw.set(i & 15, i >> 8, (i >> 4) & 15, ids.byId(s));
                }
                secs[si] = new LevelChunkSection(raw, null);   // 디스크에서 읽은 섹션처럼 개수를 다시 센다
            }
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, secs, new ProtoChunkTicks<Block>(), new ProtoChunkTicks<Fluid>(), LevelHeightAccessor.create(-64, 384), null, null);
            Heightmap.primeHeightmaps(chunk, EnumSet.copyOf(Arrays.asList(types)));
            for (int k = 0; k < rc.kind.length; k++) {
                int x = rc.x[k], y = rc.y[k], z = rc.z[k], kind = rc.kind[k];
                kinds[kind]++;
                LevelChunkSection sec = secs[y >> 4];
                BlockState st = ids.byId(rc.state[k]);
                switch (kind) {
                    case 0 -> {
                        if (FE.getShort(sec) == 0 && (flags[rc.state[k]] & 1) == 0) break;   // hasOnlyAir && state.isAir()
                        BlockState old = sec.setBlockState(x, y & 15, z, st);
                        if (old == st) break;
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).update(x, y - 64, z, st);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(x, y - 64, z, st);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR).update(x, y - 64, z, st);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).update(x, y - 64, z, st);
                    }
                    case 1 -> sec.setBlockState(x, y & 15, z, st);
                    case 2 -> sec.getStates().set(x, y & 15, z, st);
                    case 3 -> chunk.getOrCreateHeightmapUnprimed(types[rc.type[k]]).update(x, y - 64, z, st);
                    case 4, 5 -> chunk.getOrCreateHeightmapUnprimed(types[rc.type[k]]).setRawData(chunk, types[rc.type[k]], rc.raw[k]);
                    case 6 -> {
                        FE.setShort(sec, (short) rc.counts[k][0]);
                        FF.setShort(sec, (short) rc.counts[k][1]);
                        FG.setShort(sec, (short) rc.counts[k][2]);
                    }
                    default -> throw new IllegalStateException();
                }
            }
            out.append("raw ").append(seed).append(' ').append(rc.hash());
            for (Heightmap.Types t : types) appendRaw(out, chunk.getOrCreateHeightmapUnprimed(t).getRawData());
            out.append(' ');
            for (int si = 0; si < 24; si++) {
                int[] c = counts(secs[si]);
                if (si > 0) out.append(',');
                out.append(c[0]).append('/').append(c[1]).append('/').append(c[2]);
            }
            out.append('\n');
        }
        long t2 = System.nanoTime();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8")) { w.write(out.toString()); }
        System.out.printf("부트스트랩+깃발 %d ms, 청크 %d개 %d ms, 연산 종류별 %s%n", (t1 - t0) / 1_000_000, CASES, (t2 - t1) / 1_000_000,
            Arrays.toString(kinds));
    }

    static int[] counts(LevelChunkSection s) throws Exception {
        return new int[]{FE.getShort(s), FF.getShort(s), FG.getShort(s)};
    }

    static void appendRaw(StringBuilder out, long[] raw) {
        out.append(' ');
        for (int i = 0; i < raw.length; i++) { if (i > 0) out.append(','); out.append(Long.toHexString(raw[i])); }
    }

    // ---- 아래 생성기는 시험(VanillaRawWriteOracleTest)과 글자 그대로 같아야 한다 ----

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

    /** 하이트맵 raw(값 256개, 9비트, long 당 7개 — 384 높이 바닐라 배치). 값은 0..511(높이 밖 값도 바닐라는 그대로 받는다). */
    static long[] packHeights(SplittableRandom r, int base) {
        long[] raw = new long[37];
        for (int i = 0; i < 256; i++) {
            int q = r.nextInt(4);
            long v = q == 0 ? r.nextInt(512) : q == 1 ? 0 : Math.max(0, Math.min(384, base + 1 + r.nextInt(17) - 8));
            raw[i / 7] |= v << ((i % 7) * 9);
        }
        return raw;
    }

    static final class RawCase {
        int[] cells = new int[384 * 256]; // (y*16+z)*16+x, y 는 0..383 (= 블록 y + 64)
        int[] kind, x, y, z, state, type;
        long[][] raw;
        int[][] counts;

        static RawCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed * 7919 + 17);
            RawCase c = new RawCase();
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
            int m = r.nextInt(1500);
            c.kind = new int[m]; c.x = new int[m]; c.y = new int[m]; c.z = new int[m]; c.state = new int[m]; c.type = new int[m];
            c.raw = new long[m][]; c.counts = new int[m][];
            for (int i = 0; i < m; i++) {
                int p = r.nextInt(100);
                c.kind[i] = p < 40 ? 0 : p < 62 ? 1 : p < 82 ? 2 : p < 91 ? 3 : p < 95 ? 4 : p < 97 ? 5 : 6;
                c.x[i] = r.nextInt(16);
                c.z[i] = r.nextInt(16);
                c.y[i] = Math.max(0, Math.min(383, base + r.nextInt(49) - 24));
                int q = r.nextInt(3);
                c.state[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
                c.type[i] = r.nextInt(4);
                if (c.kind[i] == 4) c.raw[i] = packHeights(r, base);
                if (c.kind[i] == 5) {
                    long[] bad = new long[r.nextBoolean() ? 36 : 38];
                    for (int j = 0; j < bad.length; j++) bad[j] = r.nextLong();
                    c.raw[i] = bad;
                }
                if (c.kind[i] == 6) {
                    c.counts[i] = new int[3];
                    for (int j = 0; j < 3; j++) c.counts[i][j] = r.nextInt(3) == 0 ? 0 : r.nextInt(41) - 20;
                }
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int i = 0; i < kind.length; i++) {
                h = 31 * (31 * (31 * (31 * (31 * (31 * h + kind[i]) + x[i]) + y[i]) + z[i]) + state[i]) + type[i];
                if (raw[i] != null) for (long v : raw[i]) h = 31 * h + v;
                if (counts[i] != null) for (int v : counts[i]) h = 31 * h + v;
            }
            return h;
        }
    }
}
