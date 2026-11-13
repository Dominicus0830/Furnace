package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.SlotProbePlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-032: 다리가 전역 맵 대신 이벤트 붙임 칸에 Bukkit 객체를 붙인 뒤에도 계약이 같은지 —
 * 한 Furnace 이벤트의 Bukkit 처리기들(우선순위가 달라도, 사이에 Furnace 처리기가 끼어도)은 같은 Bukkit 객체를 받고,
 * 같은 Furnace 이벤트를 다시 발행해도 같은 객체, 다른 Furnace 이벤트는 다른 객체. 여러 스레드가 동시에 내도 짝이 어긋나지 않는다.
 */
class EventBridgeSlotTest {

    @TempDir Path tmp;

    @BeforeEach
    void reset() { SlotProbePlugin.reset(); }

    private FurnaceTestServer server() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("slotprobe.jar"), "plugin.yml", """
                name: SlotProbe
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.SlotProbePlugin
                api-version: '1.21'
                """, SlotProbePlugin.class);
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @Test
    void handlersShareOneBukkitObjectPerFurnaceEvent() throws Exception {
        try (FurnaceTestServer server = server()) {
            var p = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            // 사이에 낀 Furnace 처리기
            bus.subscribe(server.plugin("SlotProbe"), org.domi.furnace.api.event.player.PlayerExpChangeEvent.class, EventPriority.NORMAL, false, e -> {});
            SlotProbePlugin.reset();

            var f1 = new org.domi.furnace.api.event.player.PlayerExpChangeEvent(p, 7);
            bus.call(f1);
            assertEquals(3, SlotProbePlugin.EXP.size(), SlotProbePlugin.EXP.toString());
            Object b1 = SlotProbePlugin.EXP.get(0);
            for (Object o : SlotProbePlugin.EXP) assertSame(b1, o, "LOW·NORMAL·HIGH 가 같은 Bukkit 객체");

            bus.call(f1);   // 같은 Furnace 이벤트를 다시 — 옛 WeakHashMap 다리처럼 같은 객체
            assertEquals(6, SlotProbePlugin.EXP.size());
            for (Object o : SlotProbePlugin.EXP) assertSame(b1, o, "다시 발행해도 같은 Bukkit 객체");

            bus.call(new org.domi.furnace.api.event.player.PlayerExpChangeEvent(p, 7));
            assertEquals(9, SlotProbePlugin.EXP.size());
            assertNotSame(b1, SlotProbePlugin.EXP.get(6), "다른 Furnace 이벤트는 새 Bukkit 객체");
            assertSame(SlotProbePlugin.EXP.get(6), SlotProbePlugin.EXP.get(8));
        }
    }

    /** region 스레드 여럿이 같은 종류를 동시에 내는 장면 — 8 스레드가 물리 이벤트를 낸다. 처리기 LOW·HIGH 가 늘 같은 객체를 받는다. */
    @Test
    void concurrentEventsOfOneTypeKeepTheirOwnBukkitObjects() throws Exception {
        try (FurnaceTestServer server = server()) {
            server.tick(2);
            var bus = server.api().events();
            int threads = 8, perThread = 2_000;
            ExecutorService ex = Executors.newFixedThreadPool(threads);
            try {
                CyclicBarrier go = new CyclicBarrier(threads);
                List<Future<?>> fs = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    int tt = t;
                    fs.add(ex.submit(() -> {
                        go.await();
                        for (int i = 0; i < perThread; i++) {
                            bus.call(new org.domi.furnace.api.event.block.BlockPhysicsEvent("minecraft:overworld", new BlockPos(tt, 64, i & 15),
                                    BlockData.of("minecraft:redstone_wire"), new BlockPos(tt, 64, (i & 15) + 1), "minecraft:stone"));
                        }
                        return null;
                    }));
                }
                for (var f : fs) f.get(120, TimeUnit.SECONDS);
            } finally {
                ex.shutdownNow();
            }
            int total = threads * perThread;
            assertEquals(total, SlotProbePlugin.PHYSICS_LOW.get());
            assertEquals(total, SlotProbePlugin.PHYSICS_HIGH.get());
            assertEquals(0, SlotProbePlugin.MISMATCH.get(), "다른 이벤트의 Bukkit 객체를 받은 처리기가 있다");
            assertTrue(SlotProbePlugin.PHYSICS_OPEN.isEmpty());
        }
    }
}
