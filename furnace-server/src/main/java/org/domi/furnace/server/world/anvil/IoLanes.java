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

    int index(int regionX, int regionZ) {
        long k = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) Math.floorMod(k, (long) lanes.length);
    }

    private final class Lane implements Executor, Runnable {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean active;   // drain 이 바탕 풀에 올라가 있거나 도는 중

        @Override
        public void execute(Runnable task) {
            synchronized (this) {
                queue.add(task);
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
