// T-016 검토 R5: 격자 칸 갱신의 스레드 경합(PR #308 검토 탐침을 가볍게 옮김). Level.moved 의 잠금 없는 빠른 길은
// Player.x/z·gridCell 이 volatile 인 데 기댄다 — 그것을 떼면 이 시험이 낡은 칸을 잡을 수 있다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerRosterRaceTest {

    static Player np(int i, double x, double z) {
        Player p = new Player(70000 + i, new UUID(9, i), "R" + i, new ConcurrentEmbeddedChannel());
        p.applyMove(x, 0, z, 0, 0, true);
        return p;
    }

    static boolean foundAt(PlayerRoster.Level lv, Player p) {
        boolean[] f = {false};
        double x = p.x(), z = p.z();
        lv.forEachNear(x - 0.1, z - 0.1, x + 0.1, z + 0.1, q -> { if (q == p) f[0] = true; });
        return f[0];
    }

    /** 두 스레드가 같은 플레이어 위치를 번갈아 쓴다(applyMove 대 setPosRaw). 멈춘 뒤 칸 = 위치 칸이어야 한다. */
    @Test
    void twoWritersSamePlayerConverge() throws Exception {
        int stale = 0;
        for (int round = 0; round < 80; round++) {
            PlayerRoster roster = new PlayerRoster();
            PlayerRoster.Level lv = roster.level("minecraft:overworld");
            List<Player> ps = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                Player p = np(i, i * 37.0, -i * 53.0);
                roster.put(p);
                ps.add(p);
            }
            Player t = ps.get(7);
            CountDownLatch go = new CountDownLatch(1);
            final int fr = round;
            Thread a = new Thread(() -> {
                SplittableRandom r = new SplittableRandom(fr);
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int k = 0; k < 2000; k++) t.applyMove(r.nextDouble(-600, 600), 0, r.nextDouble(-600, 600), 0, 0, true);
            });
            Thread b = new Thread(() -> {
                SplittableRandom r = new SplittableRandom(~fr);
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int k = 0; k < 2000; k++) t.setPosRaw(r.nextDouble(-600, 600), 0, r.nextDouble(-600, 600));
            });
            a.start();
            b.start();
            go.countDown();
            a.join();
            b.join();
            if (t.gridCell != PlayerRoster.Level.cellOf(t.x(), t.z()) || !foundAt(lv, t)) stale++;
        }
        assertEquals(0, stale, "멈춘 뒤 칸이 위치와 다른 회차");
    }

    /** 가만히 있는 플레이어는 다른 플레이어들이 움직이고·드나들고·리스폰·차원 이동하는 동안 언제 조회해도 찾혀야 한다. */
    @Test
    void stationaryPlayerNeverMissed() throws Exception {
        PlayerRoster roster = new PlayerRoster();
        PlayerRoster.Level lv = roster.level("minecraft:overworld");
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Player p = np(i, (i % 10) * 130.0, (i / 10) * 130.0);
            roster.put(p);
            ps.add(p);
        }
        Player still = ps.get(0);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong misses = new AtomicLong(), reads = new AtomicLong();
        List<Thread> ws = new ArrayList<>();
        for (int w = 0; w < 3; w++) {
            final int fw = w;
            ws.add(new Thread(() -> {
                SplittableRandom r = new SplittableRandom(fw);
                int extra = 1000 * (fw + 1);
                while (!stop.get()) {
                    Player p = ps.get(1 + r.nextInt(ps.size() - 1));
                    switch (r.nextInt(5)) {
                        case 0, 1 -> p.setPosRaw(r.nextDouble(-900, 900), 0, r.nextDouble(-900, 900));
                        case 2 -> roster.relist(p);
                        case 3 -> {
                            p.dimension("minecraft:the_nether");
                            p.dimension("minecraft:overworld");
                        }
                        default -> {
                            Player q = np(extra++, r.nextDouble(-900, 900), r.nextDouble(-900, 900));
                            roster.put(q);
                            roster.remove(q.entityId());
                        }
                    }
                }
            }));
        }
        ws.forEach(Thread::start);
        long end = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < end) {
            reads.incrementAndGet();
            if (!foundAt(lv, still)) misses.incrementAndGet();
        }
        stop.set(true);
        for (Thread t : ws) t.join();
        assertTrue(reads.get() > 1000, "조회 수 " + reads.get());
        assertEquals(0, misses.get(), "놓친 조회(전체 " + reads.get() + ")");
    }
}
