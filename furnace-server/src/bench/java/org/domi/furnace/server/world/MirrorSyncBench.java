// NMS 청크 거울 맞추기(N2b-4) 비용 측정(:furnace-server:bench 작업에서만 돈다). 어댑터 분리 3단계(거울을 중립 훅 뒤로)의 전·후 비교용
// — docs/ops/adapter-boundary-step3-plan.md 의 P2(틱 끝 훑기·공개 알림)·P3(대기 표시 → 맞춤)·P4(내리기).
// region 이 부르는 ChunkColumn 층을 그대로 잰다(Region 은 이 메서드들을 지켜보는 청크마다 한 번씩 부를 뿐이다).
package org.domi.furnace.server.world;

import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.server.bench.BenchReport;
import org.junit.jupiter.api.Test;

class MirrorSyncBench {

    static {
        org.domi.furnace.server.plugin.InternalsImpl.installChunkMirrorOps();   // 다리를 거치지 않고 거울을 붙인다 — 훅을 먼저 꽂는다
    }

    static final int CHUNKS = Integer.getInteger("furnace.bench.count", 1000);
    static final int TICKS = Integer.getInteger("furnace.bench.ticks", 2000);
    static final int PENDING = Integer.getInteger("furnace.bench.pending", 100);

    /** 아무것도 안 하는 거울(어댑터 쪽 비용을 빼고 서버 쪽 맞춤 비용만 남긴다). */
    static final class NoopMirror implements ChunkInternals.ChunkMirror {
        long syncs, publishes;

        @Override
        public boolean sync() {
            syncs++;
            return false;
        }

        @Override
        public void published(ChunkInternals.ChunkView view) { publishes++; }

        @Override
        public void unloaded() {}
    }

    @Test
    void mirrorSyncPoints() {
        ChunkColumn[] cols = new ChunkColumn[CHUNKS];
        NoopMirror[] mirrors = new NoopMirror[CHUNKS];
        Object[] shims = new Object[CHUNKS];
        for (int i = 0; i < CHUNKS; i++) {
            cols[i] = new ChunkColumn(0);
            mirrors[i] = new NoopMirror();
            shims[i] = new Object();
            cols[i].attachViewHolder(shims[i], mirrors[i]);
        }
        AtomicInteger pend = new AtomicInteger();
        boolean[] woke = new boolean[1];
        long[] tickEnd = new long[TICKS], pendingSync = new long[TICKS];
        for (int t = 0; t < TICKS; t++) {
            // P2: 틱 끝 — 지켜보는 청크마다 훑어 맞추고(force) 공개 뒤 알린다(Region.publishReadViews 의 거울 부분)
            long a = System.nanoTime();
            for (ChunkColumn c : cols) {
                c.syncMirror(true, pend);
                c.mirrorPublished();
            }
            tickEnd[t] = System.nanoTime() - a;
            // P3: 다른 스레드가 쓴 청크 PENDING 개에 대기 표시 → 읽기 진입 맞춤(World.syncMirrorIfPending 의 컬럼 부분)
            long b = System.nanoTime();
            for (int k = 0; k < PENDING; k++) {
                int i = (t * 31 + k * 7) % CHUNKS;
                if (cols[i].markMirrorPending(mirrors[i], woke) && woke[0]) pend.incrementAndGet();
                if (pend.get() != 0 && cols[i].mirrorPending()) cols[i].syncMirror(false, pend);
            }
            pendingSync[t] = System.nanoTime() - b;
        }
        // P4: 내리기 — 맞추고 떼어 낸다(Region.unloadMirror 의 거울 부분)
        long u = System.nanoTime();
        for (ChunkColumn c : cols) {
            c.syncMirror(true, pend);
            c.mirrorUnloaded(pend);
        }
        long unloadNs = System.nanoTime() - u;
        long syncs = 0;
        for (NoopMirror m : mirrors) syncs += m.syncs;
        java.lang.ref.Reference.reachabilityFence(shims);

        int from = TICKS / 4;   // 앞 구간은 예열
        BenchReport r = BenchReport.of("MirrorSyncBench").put("chunks", CHUNKS).put("pending_per_tick", PENDING).put("syncs", syncs)
                .timesNs("tick_end_", tickEnd, from, TICKS).timesNs("pending_", pendingSync, from, TICKS).put("unload_ns", unloadNs);
        r.write();
        java.util.Arrays.sort(tickEnd, from, TICKS);
        java.util.Arrays.sort(pendingSync, from, TICKS);
        int mid = from + (TICKS - from) / 2;
        System.out.printf("MBENCH chunks=%d tickEnd_p50=%.1fns/chunk pending_p50=%.1fns/write unload=%.1fns/chunk syncs=%d%n",
                CHUNKS, tickEnd[mid] / (double) CHUNKS, pendingSync[mid] / (double) PENDING, unloadNs / (double) CHUNKS, syncs);
    }
}
