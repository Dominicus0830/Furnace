// C-5: 팔레트 원본 사본·공유 빛 층 스냅샷이 예전 즉시 풀기 스냅샷과 같은 본문 바이트를 내는지(무작위 청크 수천 개, 편집 경쟁 포함).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.junit.jupiter.api.Test;

class ChunkSnapshotCompactCopyTest {

    private static final int DIRECT = 15, BIOME_DIRECT = 7;

    /** 수정 전 ChunkSnapshot.of 의 섹션·빛 복사 그대로(블록 엔티티는 시험 청크에 없다). */
    private static ChunkSnapshot legacyOf(int cx, int cz, ChunkColumn col) {
        ChunkSection[] src = col.sections();
        ChunkSnapshot.Section[] out = new ChunkSnapshot.Section[src.length];
        for (int i = 0; i < src.length; i++) out[i] = new ChunkSnapshot.Section(src[i].nonAirCount(), src[i].blocks(), src[i].biomes());
        ChunkSnapshot.Light light = null;
        ChunkLight l = col.light();
        if (l != null) {
            byte[][] sky = new byte[ChunkLight.SECTIONS][], block = new byte[ChunkLight.SECTIONS][];
            for (int i = 0; i < sky.length; i++) {
                sky[i] = l.sky(i).clone();
                block[i] = l.block(i).clone();
            }
            light = new ChunkSnapshot.Light(sky, block);
        }
        return new ChunkSnapshot(cx, cz, col.motionBlockingHeightmap(), col.worldSurfaceHeightmap(), out, List.of(), null, light);
    }

    private static byte[] bytes(ChunkSnapshot s, boolean sky) {
        ByteBuf b = Unpooled.buffer();
        try {
            ChunkSerializer.serialize(b, s, DIRECT, BIOME_DIRECT, ChunkColumn.MIN_Y, sky);
            byte[] out = new byte[b.readableBytes()];
            b.getBytes(0, out);
            return out;
        } finally {
            b.release();
        }
    }

    // 섹션 값 분포: 단일값 / 소수(간접 4비트) / 간접 상한 근처 / 직접(256종 초과)
    private static int[] randomValues(SplittableRandom r, int n, int maxDistinctKind, int maxId) {
        int kind = r.nextInt(maxDistinctKind);
        int distinct = switch (kind) {
            case 0 -> 1;
            case 1 -> 2 + r.nextInt(15);
            case 2 -> 17 + r.nextInt(240);
            default -> 257 + r.nextInt(400);
        };
        int[] pool = new int[distinct];
        for (int i = 0; i < distinct; i++) pool[i] = r.nextInt(maxId);
        if (r.nextInt(4) == 0) pool[0] = 0;
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = pool[r.nextInt(distinct)];
        return v;
    }

    private static void randomLayer(SplittableRandom r, byte[] layer) {
        switch (r.nextInt(5)) {
            case 0 -> java.util.Arrays.fill(layer, (byte) 0);
            case 1 -> java.util.Arrays.fill(layer, (byte) 0xFF);
            case 2 -> { java.util.Arrays.fill(layer, (byte) 0xFF); layer[r.nextInt(2048)] = (byte) 0xEF; }
            case 3 -> { java.util.Arrays.fill(layer, (byte) 0); layer[2047] = 1; }
            default -> { for (int i = 0; i < 2048; i++) layer[i] = (byte) r.nextInt(256); }
        }
    }

    private static ChunkColumn randomColumn(SplittableRandom r) {
        ChunkColumn col = new ChunkColumn(0, DIRECT, BIOME_DIRECT);
        ChunkSection[] secs = col.sections();
        for (int i = 0; i < secs.length; i++) {
            if (r.nextInt(3) != 0) secs[i].replaceBlocks(randomValues(r, 4096, 4, 27000), 0);
            if (r.nextInt(2) == 0) secs[i].replaceBiomes(randomValues(r, 64, 3, 64), 0);
            if (r.nextInt(10) == 0) col.sectionBlocks(i)[r.nextInt(4096)] = r.nextInt(27000);   // scratch 열린 섹션
        }
        if (r.nextInt(6) != 0) {
            ChunkLight l = new ChunkLight();
            for (int i = 0; i < ChunkLight.SECTIONS; i++) {
                randomLayer(r, l.writableSky(i));
                randomLayer(r, l.writableBlock(i));
            }
            col.light(l);
        }
        return col;
    }

    @Test
    void randomChunks_sameBodyBytesAsLegacySnapshot() {
        SplittableRandom r = new SplittableRandom(0xC5L);
        for (int n = 0; n < 2500; n++) {
            ChunkColumn col = randomColumn(r);
            int cx = r.nextInt(-100, 100), cz = r.nextInt(-100, 100);
            ChunkSnapshot legacy = legacyOf(cx, cz, col);
            ChunkSnapshot now = ChunkSnapshot.of(cx, cz, col);
            boolean sky = r.nextBoolean();
            assertArrayEquals(bytes(legacy, sky), bytes(now, sky), "청크 " + n);
            for (int s = 0; s < now.sections().length; s++) {
                assertArrayEquals(legacy.sections()[s].blocks(), now.sections()[s].blocks(), "청크 " + n + " 섹션 " + s);
                assertArrayEquals(legacy.sections()[s].biomes(), now.sections()[s].biomes(), "청크 " + n + " 바이옴 " + s);
                assertEquals(legacy.sections()[s].nonAirCount(), now.sections()[s].nonAirCount());
            }
        }
    }

    /**
     * -Dfurnace.bench=true 일 때만: 스냅샷 1개당 뜨는 스레드(region 쪽) 할당·CPU 와 직렬화 스레드(풀 쪽) CPU 를 예전/지금으로 잰다.
     * 청크: 평지(빛 층 대부분 균일), 무작위(시험 분포). 3회 반복, 회마다 각 2000개.
     */
    @Test
    void bench() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn flat = new org.domi.furnace.server.world.gen.SuperflatWorldSource(reg, 0).generate(0, 0);
        ChunkLight fl = new ChunkLight();
        for (int i = 0; i < ChunkLight.SECTIONS; i++) if (i >= 6) fl.fillSkyFull(i);
        flat.light(fl);
        SplittableRandom r = new SplittableRandom(7);
        ChunkColumn[] rnd = new ChunkColumn[64];
        for (int i = 0; i < rnd.length; i++) rnd[i] = randomColumn(r);
        for (String kind : new String[] {"평지", "무작위"}) {
            for (int rep = -1; rep < 3; rep++) {   // -1 = 워밍업
                for (int g = 0; g < 2; g++) {
                    boolean legacy = g == 0;
                    long snapAlloc = 0, snapCpu = 0, serCpu = 0, serAlloc = 0;
                    int n = 2000;
                    for (int k = 0; k < n; k++) {
                        ChunkColumn col = kind.equals("평지") ? flat : rnd[k & 63];
                        long a0 = mx.getThreadAllocatedBytes(tid), c0 = mx.getCurrentThreadCpuTime();
                        ChunkSnapshot s = legacy ? legacyOf(0, 0, col) : ChunkSnapshot.of(0, 0, col);
                        long a1 = mx.getThreadAllocatedBytes(tid), c1 = mx.getCurrentThreadCpuTime();
                        bytes(s, true);
                        long a2 = mx.getThreadAllocatedBytes(tid), c2 = mx.getCurrentThreadCpuTime();
                        snapAlloc += a1 - a0; snapCpu += c1 - c0; serAlloc += a2 - a1; serCpu += c2 - c1;
                    }
                    if (rep >= 0) {
                        System.out.printf("[BENCH] C5-snapshot %s rep=%d %s 스냅샷 할당 %.1f KB/개, 스냅샷 CPU %.1f us/개, 직렬화 할당 %.1f KB/개, 직렬화 CPU %.1f us/개%n",
                                kind, rep, legacy ? "전" : "후", snapAlloc / 1024.0 / n, snapCpu / 1e3 / n, serAlloc / 1024.0 / n, serCpu / 1e3 / n);
                    }
                }
            }
        }
    }

    /** N-05 의미: 스냅샷 뒤(풀기 전) 원본 편집·빛 변경이 끼어도 스냅샷은 요청 시점 상태와 같은 바이트. */
    @Test
    void editsAfterSnapshot_doNotLeakIntoLazyUnpack() {
        SplittableRandom r = new SplittableRandom(0x5EEDL);
        for (int n = 0; n < 600; n++) {
            ChunkColumn col = randomColumn(r);
            ChunkSnapshot legacy = legacyOf(1, 2, col);
            ChunkSnapshot now = ChunkSnapshot.of(1, 2, col);
            byte[] expected = bytes(legacy, true);
            // 편집: 블록(팔레트 성장 포함)·바이옴·scratch·빛 층(공유 사본을 가리키는 원본 층 포함)
            for (int k = 0; k < 200; k++) {
                col.setBlock(r.nextInt(16), ChunkColumn.MIN_Y + r.nextInt(384), r.nextInt(16), r.nextInt(27000));
            }
            col.sections()[r.nextInt(col.sections().length)].setBiome(1, 1, 1, r.nextInt(64));
            col.sectionBlocks(r.nextInt(col.sections().length))[0] = 5;
            if (col.light() != null) {
                for (int i = 0; i < ChunkLight.SECTIONS; i++) {
                    col.light().writableSky(i)[r.nextInt(2048)] ^= 0x11;
                    col.light().writableBlock(i)[r.nextInt(2048)] ^= 0x22;
                }
            }
            assertArrayEquals(expected, bytes(now, true), "청크 " + n);
        }
    }
}
