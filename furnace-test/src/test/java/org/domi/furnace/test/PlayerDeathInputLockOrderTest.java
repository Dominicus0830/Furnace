// PR #165 검토 회귀 시험: 살아 있는 플레이어가 맞는 중에 Q·창 THROW 를 연타해도 교착하지 않는다(inv→vitals / vitals→inv 잠금 순서 역전 방지 — isDeadOrDying·gameMode 는 잠금 없이 읽는다).
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class PlayerDeathInputLockOrderTest {

    private static void sendDropKey(TestPlayer tp, int status) {
        tp.sendPacket(0x24, b -> {
            Types.writeVarInt(b, status);
            Types.writeBlockPos(b, 0, 0, 0);
            b.writeByte(0);
            Types.writeVarInt(b, 0);
        });
    }

    private static void sendThrowClick(TestPlayer tp, int button) {
        tp.sendPacket(0x0E, b -> {
            Types.writeVarInt(b, 0);
            Types.writeVarInt(b, 0);
            b.writeShort(36);
            b.writeByte(button);
            Types.writeVarInt(b, 4);
            Types.writeVarInt(b, 0);
            Types.writeVarInt(b, 0);
        });
    }

    /** ① 살아 있는 플레이어가 맞는 중에 Q 를 연타하면 연결 스레드(inv→vitals)와 region 스레드(vitals→inv)가 서로를 기다린다. */
    @Test
    @Timeout(120)
    void dropKeyWhileTakingDamageDoesNotDeadlock() throws Exception { hurtWhileSending(tp -> sendDropKey(tp, 4)); }

    /** 같은 것을 창 THROW 클릭으로(기준 브랜치에서 이미 있던 inv→vitals 경로: applyContainerClick 의 p.gameMode()). */
    @Test
    @Timeout(120)
    void windowThrowWhileTakingDamageDoesNotDeadlock() throws Exception { hurtWhileSending(tp -> sendThrowClick(tp, 0)); }

    private void hurtWhileSending(java.util.function.Consumer<TestPlayer> send) throws Exception {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var write = tp.player().inventory().set(0, new ItemStack("minecraft:dirt", 64));
            for (int i = 0; i < 40 && !write.isDone(); i++) { server.tick(1); tp.sync(); }
            assertTrue(write.isDone());
            server.tick(100);   // 접속 직후 무적 끝
            var p = server.internals().playerByName("Dana");
            AtomicBoolean stop = new AtomicBoolean();
            AtomicInteger hits = new AtomicInteger();
            Thread hurter = new Thread(() -> {
                float a = 0.0002f;
                while (!stop.get() && a < 19.0f) {
                    if (p.vitals().hurt(DamageType.GENERIC, a)) hits.incrementAndGet();
                    a += 0.0002f;
                }
            }, "review-hurter");
            hurter.setDaemon(true);
            hurter.start();
            String failure = null;
            int sent = 0;
            try {
                for (; sent < 60; sent++) send.accept(tp);
            } catch (RuntimeException e) {
                failure = e.toString();
            }
            stop.set(true);
            var mx = ManagementFactory.getThreadMXBean();
            long[] dead = mx.findDeadlockedThreads();
            StringBuilder sb = new StringBuilder("sent=" + sent + " hits=" + hits + " failure=" + failure + "\n");
            if (dead != null) for (var ti : mx.getThreadInfo(dead, 12)) sb.append(ti);
                        assertTrue(dead == null && failure == null, sb.toString());
        }
    }
}
