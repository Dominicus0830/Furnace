// A03-02 회귀(2차 점검 재현 시험을 줄여 씀): 같은 스레드가 같은 청크에 보낸 작업이 region 합치기·나누기 도중 순서가 뒤바뀌지 않는가(FurnaceScheduler 문서: "그 region 의 다른 작업과
// 같은 스레드 순서로 돈다"). 생산자 스레드(넷티 스레드 대역)가 청크마다 번호를 붙여 보내고, 변경 스레드가 다리 구역을 넣었다 빼며 합치기·나누기를 되풀이한다.
// 끝에 청크마다 실행 순서가 보낸 순서와 같은지, 빠지거나 두 번 돈 작업이 없는지, 교착이 없는지 본다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionTaskOrderTest {

    static final long DURATION_MS = Long.getLong("furnace.a03.durationMs", 8_000L);
    static final int PRODUCERS = 3;
    static final int[] ANCHORS = {0, 2, 4, 6, 8};
    static final int[] BRIDGES = {1, 3, 5, 7};
    /** true(기본)면 생산자가 다리 구역 청크에도 보낸다(다리 구역이 계속 다시 생겨 거의 늘 합쳐진 채). false 면 고정 구역 청크에만 — 합치기·나누기가 번갈아 난다. */
    /** 생산자가 작업 8개마다 쉬는 시간(ns). 기본 50µs(몰아치기), 1_000_000 이면 청크마다 초당 수백 개(실제 패킷 수준). */
    static final long PARK_NANOS = Long.getLong("furnace.a03.parkNanos", 50_000L);
    static final boolean TO_BRIDGES = Boolean.parseBoolean(System.getProperty("furnace.a03.toBridges", "true"));

    @Test
    void perChunkFifoSurvivesMergeAndSplit() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, 4, 1, 2, 2,
                reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
        Regionizer rz = w.regionizer();
        try {
            for (int a : ANCHORS) rz.want(a * 16, 0, null);
            // 청크 키 → 실행된 (생산자, 번호) 순서
            ConcurrentHashMap<Long, List<long[]>> ran = new ConcurrentHashMap<>();
            AtomicLong sent = new AtomicLong(), executed = new AtomicLong();
            AtomicBoolean stop = new AtomicBoolean();
            AtomicInteger deadlocks = new AtomicInteger();
            List<Thread> threads = new ArrayList<>();
            for (int p = 0; p < PRODUCERS; p++) {
                final int pid = p;
                Thread t = new Thread(() -> {
                    long[] seq = new long[TO_BRIDGES ? ANCHORS.length * 2 : ANCHORS.length];
                    int i = 0;
                    while (!stop.get()) {
                        int slot = i++ % seq.length;
                        int sx = ANCHORS[slot % ANCHORS.length];
                        // 다리 구역(합치기·나누기로 주인이 바뀌는 곳) 청크에도 보낸다
                        int cx = slot < ANCHORS.length || !TO_BRIDGES ? sx * 16 + pid : (sx + 1) * 16 + pid;
                        long key = Regionizer.chunkKey(cx, 0);
                        long n = seq[slot]++;
                        sent.incrementAndGet();
                        w.runAtChunk(new ChunkPos(cx, 0), r -> {
                            ran.computeIfAbsent(key, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(new long[] {pid, n});
                            executed.incrementAndGet();
                        });
                        if ((i & 7) == 0) LockSupport.parkNanos(PARK_NANOS);
                    }
                }, "a03-producer-" + p);
                threads.add(t);
            }
            Thread watch = new Thread(() -> {
                var mx = ManagementFactory.getThreadMXBean();
                while (!stop.get()) {
                    long[] d = mx.findDeadlockedThreads();
                    if (d != null) deadlocks.incrementAndGet();
                    LockSupport.parkNanos(100_000_000L);
                }
            }, "a03-deadlock-watch");
            threads.add(watch);
            for (Thread t : threads) t.start();

            long end = System.currentTimeMillis() + DURATION_MS;
            int cycles = 0, maxRegions = 0, minRegions = Integer.MAX_VALUE;
            Thread.sleep(200);
            while (System.currentTimeMillis() < end) {
                for (int b : BRIDGES) rz.want(b * 16 + 8, 0, null);   // 다리 → 합치기
                Thread.sleep(300);
                int rc = w.regionCount();
                minRegions = Math.min(minRegions, rc);
                for (int b : BRIDGES) {
                    for (int tries = 0; tries < 10; tries++) {
                        Region o = rz.ownerOf(b * 16 + 8, 0);
                        if (o == null) break;
                        rz.release(o, b * 16 + 8, 0);
                        if (!o.present.contains(Regionizer.chunkKey(b * 16 + 8, 0))) break;
                    }
                }
                Thread.sleep(1_400);   // 유예 1초 + 재계산 20틱 → 나누기
                maxRegions = Math.max(maxRegions, w.regionCount());
                cycles++;
            }
            stop.set(true);
            for (Thread t : threads) t.join(5_000);
            long deadline = System.currentTimeMillis() + 15_000;
            while (executed.get() < sent.get() && System.currentTimeMillis() < deadline) Thread.sleep(20);

            long inversions = 0, dups = 0, chunksWithInversion = 0;
            StringBuilder sample = new StringBuilder();
            for (var e : ran.entrySet()) {
                long[] last = new long[PRODUCERS];
                java.util.Arrays.fill(last, -1);
                boolean bad = false;
                synchronized (e.getValue()) {
                    for (long[] rec : e.getValue()) {
                        int pid = (int) rec[0];
                        if (rec[1] == last[pid]) dups++;
                        else if (rec[1] < last[pid]) {
                            inversions++;
                            bad = true;
                            if (sample.length() < 400) sample.append(String.format(" [chunk %d p%d: %d after %d]", Regionizer.chunkX(e.getKey()), pid, rec[1], last[pid]));
                        }
                        last[pid] = Math.max(last[pid], rec[1]);
                    }
                }
                if (bad) chunksWithInversion++;
            }
            String summary = String.format("A03-ORDER toBridges=" + TO_BRIDGES + " parkNanos=" + PARK_NANOS + " cycles=%d regions[min=%d,max=%d] sent=%d executed=%d inversions=%d chunksWithInversion=%d dups=%d deadlockSamples=%d%s",
                    cycles, minRegions, maxRegions, sent.get(), executed.get(), inversions, chunksWithInversion, dups, deadlocks.get(), sample);
            System.out.println(summary);
            assertEquals(0, deadlocks.get(), summary);
            assertEquals(sent.get(), executed.get(), "작업이 빠지거나 두 번 돌았다: " + summary);
            assertEquals(0, dups, summary);
            assertEquals(0, inversions, "같은 스레드가 같은 청크에 보낸 작업의 순서가 뒤바뀌었다: " + summary);
        } finally {
            sched.close();
        }
    }
}
