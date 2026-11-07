// region 파일(32x32 청크) 단위 직렬 IO 레인. 같은 .mca 의 save/load 는 제출 순서(FIFO)대로, 다른 파일끼리는 병렬로.
// 스레드를 새로 만들지 않고 바탕 풀(ioPool) 위에 레인마다 한 번에 하나의 drain 작업만 올린다 — 풀을 닫고 기다리는 종료 경로가 그대로 레인도 비운다.
package org.domi.furnace.server.world.anvil;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class IoLanes {

    private final Lane[] lanes;
    private final Executor backing;

    public IoLanes(Executor backing, int laneCount) {
        if (laneCount <= 0) throw new IllegalArgumentException("laneCount " + laneCount);
        this.backing = backing;
        this.lanes = new Lane[laneCount];
        for (int i = 0; i < laneCount; i++) lanes[i] = new Lane();
    }

    /** 그 청크가 든 region 파일의 레인. 같은 파일 → 같은 레인. */
    public Executor lane(int chunkX, int chunkZ) {
        return lanes[index(chunkX >> 5, chunkZ >> 5)];
    }

    /** 지금까지 넣은 모든 작업이 끝나면 완료(레인마다 FIFO 라 끝에 넣은 빈 작업이 돌면 앞 작업은 다 끝났다). 월드 내리기용. */
    public java.util.concurrent.CompletableFuture<Void> barrier() {
        java.util.concurrent.CompletableFuture<?>[] fs = new java.util.concurrent.CompletableFuture<?>[lanes.length];
        for (int i = 0; i < lanes.length; i++) fs[i] = java.util.concurrent.CompletableFuture.runAsync(() -> { }, lanes[i]);
        return java.util.concurrent.CompletableFuture.allOf(fs);
    }

    /** 레인 수. */
    public int laneCount() { return lanes.length; }

    /** 그 region 파일(r.rx.rz.mca)이 쓰는 레인 번호(0..laneCount-1). 같은 좌표의 region·entities·poi 파일은 같은 레인이다. */
    public int laneOfRegion(int regionX, int regionZ) { return index(regionX, regionZ); }

    /**
     * 레인마다 marker.apply(레인 번호) 를 맨 뒤에 넣는다. 레인이 FIFO 라 marker 는 지금까지 넣은 그 레인의 작업 뒤, 이후 넣는 작업 앞에 돈다
     * (백업 기준점 — 그 레인 파일들의 "이 시점" 을 정한다). 모든 marker 가 끝나면 완료.
     */
    public java.util.concurrent.CompletableFuture<Void> markers(java.util.function.IntFunction<Runnable> marker) {
        java.util.concurrent.CompletableFuture<?>[] fs = new java.util.concurrent.CompletableFuture<?>[lanes.length];
        for (int i = 0; i < lanes.length; i++) fs[i] = java.util.concurrent.CompletableFuture.runAsync(marker.apply(i), lanes[i]);
        return java.util.concurrent.CompletableFuture.allOf(fs);
    }

    /** 레인 대기열(아직 시작 안 한 작업) 길이 중 가장 긴 것 — region 쪽 저장 역압 기준. */
    public int maxBacklog() {
        int m = 0;
        for (Lane l : lanes) m = Math.max(m, l.pending);
        return m;
    }

    /** 지금까지 본 레인 대기열 최대 길이·작업 최대 대기 시간(ns). 읽으면 0 으로 되돌린다(주기 로그용). */
    public long[] drainStats() {
        long q = 0, w = 0;
        for (Lane l : lanes) {
            synchronized (l) {
                q = Math.max(q, l.peakPending);
                w = Math.max(w, l.peakWaitNanos);
                l.peakPending = 0;
                l.peakWaitNanos = 0;
            }
        }
        return new long[]{q, w};
    }

    int index(int regionX, int regionZ) {
        long k = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) Math.floorMod(k, (long) lanes.length);
    }

    private final class Lane implements Executor, Runnable {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private final ArrayDeque<Long> enqueuedAt = new ArrayDeque<>();
        private volatile int pending;          // 대기열 길이(다른 스레드가 잠금 없이 읽는다)
        private int peakPending;
        private long peakWaitNanos;
        private boolean active;   // drain 이 바탕 풀에 올라가 있거나 도는 중

        @Override
        public void execute(Runnable task) {
            synchronized (this) {
                queue.add(task);
                enqueuedAt.add(System.nanoTime());
                pending = queue.size();
                if (queue.size() > peakPending) peakPending = queue.size();
                if (active) return;
                active = true;
            }
            try {
                backing.execute(this);
            } catch (RejectedExecutionException e) {
                // 바탕 풀이 닫힌 뒤(종료 중) — 호출 스레드에서 그대로 비운다. 순서는 여전히 FIFO.
                run();
            }
        }

        @Override
        public void run() {
            while (true) {
                Runnable r;
                synchronized (this) {
                    r = queue.poll();
                    if (r == null) { active = false; return; }
                    long wait = System.nanoTime() - enqueuedAt.poll();
                    if (wait > peakWaitNanos) peakWaitNanos = wait;
                    pending = queue.size();
                }
                try {
                    r.run();
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger(IoLanes.class).error("[IO] 레인 작업 실패", t);
                }
            }
        }
    }
}
