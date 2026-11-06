// C-5 (F-01 후속): 엔티티 저장을 저장소 전체 잠금 안에서 할 때 IO 레인(서로 다른 region 파일) 사이에 생기는 대기를 잰다(기본 꺼짐: -Dfurnace.bench=true).
package org.domi.furnace.server.world.anvil;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLongArray;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityStoreLockBench {

    @TempDir Path tmp;

    /** 엔티티 n 개짜리 묶음(바닐라 EntityStorage 꼴 비슷하게). */
    static NbtCompound bundle(int cx, int cz, int n) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", AnvilChunkCodec.DATA_VERSION);
        root.putIntArray("Position", new int[] {cx, cz});
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (int i = 0; i < n; i++) {
            NbtCompound e = new NbtCompound();
            e.putString("id", "minecraft:zombie");
            e.putIntArray("UUID", new int[] {i, cx, cz, i * 31});
            e.putFloat("Health", 20f);
            e.putShort("Air", (short) 300);
            e.putByte("OnGround", (byte) 1);
            e.putInt("PortalCooldown", 0);
            e.putString("CustomName", "{\"text\":\"mob" + i + "\"}");
            list.add(e);
        }
        root.putList("Entities", list);
        return root;
    }

    static void write(RegionFileStore store, int cx, int cz, NbtCompound root) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        NbtIo.write(new java.io.DataOutputStream(bos), root, "");
        store.writeChunk(cx, cz, bos.toByteArray());
    }

    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        int threads = Integer.getInteger("furnace.bench.lanes", 4);   // 기본 io-threads 4
        int per = Integer.getInteger("furnace.bench.writes", 512);
        int ents = Integer.getInteger("furnace.bench.entities", 30);
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        mx.setThreadContentionMonitoringEnabled(true);
        NbtCompound[] bodies = new NbtCompound[64];
        for (int i = 0; i < bodies.length; i++) bodies[i] = bundle(i, -i, ents);
        for (int rep = 0; rep < 4; rep++) {          // rep 0 = 워밍업
            for (int g = 0; g < 2; g++) {
                boolean storeLock = g == 0;
                Path dir = tmp.resolve("r" + rep + "_" + g);
                Files.createDirectories(dir);
                RegionFileStore store = new RegionFileStore(dir, "entities", false);
                AtomicLongArray blockedN = new AtomicLongArray(threads), blockedMs = new AtomicLongArray(threads), cpu = new AtomicLongArray(threads);
                CyclicBarrier start = new CyclicBarrier(threads + 1), end = new CyclicBarrier(threads + 1);
                Thread[] ts = new Thread[threads];
                for (int t = 0; t < threads; t++) {
                    int th = t;
                    ts[t] = new Thread(() -> {
                        try {
                            start.await();
                            var i0 = mx.getThreadInfo(Thread.currentThread().threadId());
                            long c0 = mx.getCurrentThreadCpuTime();
                            for (int n = 0; n < per; n++) {
                                int cx = th * 32 + (n & 31), cz = (n >> 5) & 31;   // 스레드마다 다른 region 파일(= 다른 레인)
                                NbtCompound body = bodies[(n + th) & 63];
                                if (storeLock) {
                                    synchronized (store) { write(store, cx, cz, body); }
                                } else {
                                    write(store, cx, cz, body);
                                }
                            }
                            var i1 = mx.getThreadInfo(Thread.currentThread().threadId());
                            cpu.set(th, mx.getCurrentThreadCpuTime() - c0);
                            blockedN.set(th, i1.getBlockedCount() - i0.getBlockedCount());
                            blockedMs.set(th, i1.getBlockedTime() - i0.getBlockedTime());
                            end.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    ts[t].start();
                }
                start.await();
                end.await();
                for (Thread t : ts) t.join();
                store.closeAll();
                long bn = 0, bms = 0, c = 0;
                for (int t = 0; t < threads; t++) { bn += blockedN.get(t); bms += blockedMs.get(t); c += cpu.get(t); }
                if (rep > 0) {
                    System.out.printf("[BENCH] C5-entity rep=%d %s lanes=%d writes=%d 차단횟수=%d 차단시간=%d ms CPU합=%.1f ms%n",
                            rep, storeLock ? "저장소잠금" : "잠금없음", threads, threads * per, bn, bms, c / 1e6);
                }
            }
        }
    }
}
