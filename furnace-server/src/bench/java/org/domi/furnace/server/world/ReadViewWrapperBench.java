// 어댑터 분리 3b 판단용(:furnace-server:bench 에서만 돈다): 공개 스냅샷(ChunkReadView)을 API 인터페이스(ChunkView)로 직접 내줄 때와,
// 다리가 위임 감싸개로 감싸 내줄 때의 읽기 비용 차이. 호환 계층의 다른 청크 getBlockState 가 칸마다 stateId 를 부르는 길(ChunkAccess.foreignCellId)과
// 섹션 묶음을 통째로 읽는 길(sectionStates·sectionStatesPacked)을 잰다. docs/ops/adapter-boundary-step3-plan.md 의 3b.
package org.domi.furnace.server.world;

import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.server.bench.BenchReport;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class ReadViewWrapperBench {

    static final int ROUNDS = Integer.getInteger("furnace.bench.ticks", 400);

    /** 다리가 쓸 위임 감싸개(3b 를 옮긴다면 생길 모양) — 읽기에 쓰는 메서드만 넘긴다. */
    static final class Wrapped implements ChunkInternals.ChunkView {
        final ChunkReadView v;

        Wrapped(ChunkReadView v) { this.v = v; }

        @Override public long version() { return v.version(); }
        @Override public Object loadToken() { return v.loadToken(); }
        @Override public long contentStamp() { return v.contentStamp(); }
        @Override public int sectionCount() { return v.sectionCount(); }
        @Override public int stateId(int x, int y, int z) { return v.stateId(x, y, z); }
        @Override public int vanillaStateId(int x, int y, int z) { return v.vanillaStateId(x, y, z); }
        @Override public int[] sectionStates(int i) { return v.sectionStates(i); }
        @Override public int sectionNonAir(int i) { return v.sectionNonAir(i); }
        @Override public int[] sectionCounts(int i) { return v.sectionCounts(i); }
        @Override public int sectionRandomTickingParts(int i) { return v.sectionRandomTickingParts(i); }
        @Override public int[] sectionBiomes(int i) { return v.sectionBiomes(i); }
        @Override public int firstAvailable(String h, int x, int z) { return v.firstAvailable(h, x, z); }
        @Override public long[] blockEntityPositions() { return v.blockEntityPositions(); }
        @Override public boolean lightReady() { return v.lightReady(); }
        @Override public int light(boolean sky, int x, int y, int z) { return v.light(sky, x, y, z); }
        @Override public byte[] lightLayer(boolean sky, int sy) { return v.lightLayer(sky, sy); }
        @Override public ChunkInternals.PackedStates sectionStatesPacked(int i) { return new PackedWrap(v.sectionStatesPacked(i)); }
    }

    record PackedWrap(ChunkInternals.PackedStates p) implements ChunkInternals.PackedStates {
        @Override public int bits() { return p.bits(); }
        @Override public int paletteSize() { return p.paletteSize(); }
        @Override public int paletteValue(int i) { return p.paletteValue(i); }
        @Override public long[] rawCopy() { return p.rawCopy(); }
    }

    static long cells(ChunkInternals.ChunkView v, int minY, int maxY) {
        long s = 0;
        for (int y = minY; y < maxY; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) s += v.stateId(x, y, z);
        return s;
    }

    static long sections(ChunkInternals.ChunkView v) {
        long s = 0;
        for (int i = 0; i < v.sectionCount(); i++) {
            ChunkInternals.PackedStates p = v.sectionStatesPacked(i);
            s += p.bits() + p.paletteSize() + p.paletteValue(0) + p.rawCopy().length;
        }
        return s;
    }

    @Test
    void directVersusWrapped() {
        BlockBehaviors.ofBlock(0);
        int stone = Blocks.get().defaultState("minecraft:stone"), log = Blocks.get().defaultState("minecraft:oak_log");
        ChunkColumn col = new ChunkColumn(0);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 0; y++) col.setBlock(x, y, z, (x + y + z) % 5 == 0 ? log : stone);
        ChunkReadView view = col.publishReadView(0, 0);
        ChunkInternals.ChunkView direct = view, wrapped = new Wrapped(view);
        int minY = col.minY(), maxY = 0;
        long[] dCell = new long[ROUNDS], wCell = new long[ROUNDS], dSec = new long[ROUNDS], wSec = new long[ROUNDS];
        long sink = 0;
        for (int r = 0; r < ROUNDS; r++) {   // 번갈아 잰다(같은 실행 안 — 잡음이 양쪽에 같이 걸리게)
            long a = System.nanoTime(); sink += cells(direct, minY, maxY); dCell[r] = System.nanoTime() - a;
            a = System.nanoTime(); sink += cells(wrapped, minY, maxY); wCell[r] = System.nanoTime() - a;
            a = System.nanoTime(); sink += sections(direct); dSec[r] = System.nanoTime() - a;
            a = System.nanoTime(); sink += sections(wrapped); wSec[r] = System.nanoTime() - a;
        }
        int from = ROUNDS / 2, n = (maxY - minY) * 256;
        BenchReport.of("ReadViewWrapperBench").put("cells", n).put("sink", sink)
                .timesNs("direct_cell_", dCell, from, ROUNDS).timesNs("wrapped_cell_", wCell, from, ROUNDS)
                .timesNs("direct_sections_", dSec, from, ROUNDS).timesNs("wrapped_sections_", wSec, from, ROUNDS).write();
    }
}
