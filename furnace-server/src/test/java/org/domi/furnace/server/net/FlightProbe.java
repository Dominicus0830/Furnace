// 실제 서버에 붙어 크리에이티브 질주 비행 속도로 날며 청크 도착을 잰다(수동 프로브: -Dprobe.port 가 있을 때만).
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FlightProbe {
    @Test
    void fly() throws Exception {
        String port = System.getProperty("furnace.probe.port");
        Assumptions.assumeTrue(port != null);
        int vd = Integer.getInteger("furnace.probe.vd", 10);
        double speed = Double.parseDouble(System.getProperty("furnace.probe.speed", "1.09")); // 블록/틱
        int seconds = Integer.getInteger("furnace.probe.seconds", 30);
        boolean diag = Boolean.getBoolean("furnace.probe.diag");
        TestClient c = TestClient.join(Integer.parseInt(port), "Flyer" + (System.nanoTime() % 1000));
        Set<Long> have = ConcurrentHashMap.newKeySet();
        Map<Long, Long> wanted = new ConcurrentHashMap<>();   // 시야 진입 시각
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        double[] pos = new double[3];
        AtomicInteger teleport = new AtomicInteger(-1);
        AtomicInteger batches = new AtomicInteger();
        AtomicInteger chunksIn = new AtomicInteger(), resyncs = new AtomicInteger();
        Object lock = new Object();
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    ByteBuf f = c.read();
                    int id = Types.readVarInt(f);
                    switch (id) {
                        case 0x27 -> {
                            int x = f.readInt(), z = f.readInt();
                            long k = key(x, z);
                            have.add(k);
                            chunksIn.incrementAndGet();
                            Long t = wanted.remove(k);
                            if (t != null) latencies.add(System.nanoTime() - t);
                        }
                        case 0x21 -> { long k = f.readLong(); have.remove(key((int) k, (int) (k >> 32))); have.remove(k); }
                        case 0x0C -> { batches.incrementAndGet(); c.write(0x08, b -> b.writeFloat(64f)); }
                        case 0x26 -> { long v = f.readLong(); c.write(0x18, b -> b.writeLong(v)); }
                        case 0x40 -> {
                            synchronized (lock) {
                                pos[0] = f.readDouble(); pos[1] = f.readDouble(); pos[2] = f.readDouble();
                            }
                            f.readFloat(); f.readFloat(); f.readByte();
                            teleport.set(Types.readVarInt(f));
                            resyncs.incrementAndGet();
                            if (resyncs.get() > 1) { int tid = teleport.get(); c.write(0x00, b -> Types.writeVarInt(b, tid)); }
                        }
                        default -> { }
                    }
                }
            } catch (Exception e) { System.out.println("PROBE reader died: " + e); }
        });
        reader.setDaemon(true);
        reader.start();
        while (teleport.get() < 0) Thread.sleep(10);
        c.write(0x00, b -> Types.writeVarInt(b, teleport.get()));
        Thread.sleep(8000); // 처음 시야를 받게 둔다
        double x, y, z;
        synchronized (lock) { x = pos[0]; y = 160; z = pos[2]; }
        long start = System.nanoTime();
        int ticks = seconds * 20;
        long missSum = 0, nearMissSum = 0, maxNear = 0, samples = 0;
        int lastCx = Integer.MIN_VALUE, lastCz = 0;
        for (int t = 0; t < ticks; t++) {
            x += diag ? speed / Math.sqrt(2) : speed;
            if (diag) z += speed / Math.sqrt(2);
            final double fx = x, fy = y, fz = z;
            c.write(0x1A, b -> { b.writeDouble(fx); b.writeDouble(fy); b.writeDouble(fz); b.writeBoolean(false); });
            int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
            if (cx != lastCx || cz != lastCz) {
                long now = System.nanoTime();
                for (int dx = -vd; dx <= vd; dx++) for (int dz = -vd; dz <= vd; dz++) {
                    long k = key(cx + dx, cz + dz);
                    if (!have.contains(k)) wanted.putIfAbsent(k, now);
                }
                wanted.keySet().removeIf(k -> Math.abs((int) (long) k - cx) > vd || Math.abs((int) (k >> 32) - cz) > vd);
                lastCx = cx; lastCz = cz;
            }
            int miss = 0, near = 0;
            for (int dx = -vd; dx <= vd; dx++) for (int dz = -vd; dz <= vd; dz++) {
                if (!have.contains(key(cx + dx, cz + dz))) {
                    miss++;
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) near++;
                }
            }
            missSum += miss; nearMissSum += near; maxNear = Math.max(maxNear, near); samples++;
            if (t % 100 == 0) System.out.printf("PROBE t=%ds missing(view)=%d missing(r3)=%d chunksIn=%d resyncs=%d%n", t / 20, miss, near, chunksIn.get(), resyncs.get());
            long due = start + (t + 1) * 50_000_000L;
            long sleep = due - System.nanoTime();
            if (sleep > 0) Thread.sleep(sleep / 1_000_000, (int) (sleep % 1_000_000));
        }
        List<Long> l;
        synchronized (latencies) { l = new ArrayList<>(latencies); }
        Collections.sort(l);
        System.out.printf("PROBE speed=%.2f b/t diag=%s vd=%d: avg missing(view)=%.1f/%d avg missing(r3)=%.1f max(r3)=%d batches=%d%n",
                speed, diag, vd, missSum / (double) samples, (2 * vd + 1) * (2 * vd + 1), nearMissSum / (double) samples, maxNear, batches.get());
        if (!l.isEmpty()) System.out.printf("PROBE latency ms: p50=%d p90=%d p99=%d max=%d n=%d, never-arrived=%d%n",
                l.get(l.size() / 2) / 1_000_000, l.get(l.size() * 9 / 10) / 1_000_000, l.get(l.size() * 99 / 100) / 1_000_000,
                l.get(l.size() - 1) / 1_000_000, l.size(), wanted.size());
        c.close();
    }

    private static long key(int x, int z) { return (x & 0xFFFFFFFFL) | ((long) z << 32); }
}
