// T-074: markDisconnected 가 epoch 를 올리는 것에 캐시가 기댄다 — 끊김 표시만 한 직후와 읽기 스레드 경합에서도 낡은 목록이 남지 않는다(변이: bumpRoster 줄 삭제 시 실패).
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;

class OnlinePlayersDisconnectTest {
    @org.junit.jupiter.api.BeforeAll
    static void bukkit() { Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE); }

    /** 끊김 표시만 하고(롤러에서 아직 안 뺌) 곧바로 보기를 본다 — epoch 상승이 없으면 낡은 캐시가 남는다. */
    @Test
    void markDisconnectedAloneDropsPlayerFromView() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            TestPlayer a = server.join("Ann");
            server.join("Ben");
            server.tick(2);
            var view = Bukkit.getOnlinePlayers();
            assertEquals(2, view.size());
            assertEquals(2, server.api().onlinePlayers().size());
            server.internals().playerByUuid(a.uuid()).markDisconnected();
            assertEquals(1, server.api().onlinePlayers().size(), "서버 목록");
            assertEquals(1, view.size(), "Bukkit 보기");
            for (Player p : view) assertNotEquals(a.uuid(), p.getUniqueId());
        }
    }

    /** 읽기 스레드가 계속 목록을 만들어 캐시에 쓰는 동안, 끊김 표시가 끝난 뒤 부르면 반드시 빠져 있어야 한다. */
    @Test
    void staleSnapshotNeverSurvivesMarkDisconnected() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            List<TestPlayer> ps = new ArrayList<>();
            for (int i = 0; i < 12; i++) ps.add(server.join("R" + i));
            server.tick(2);
            var view = Bukkit.getOnlinePlayers();
            AtomicBoolean stop = new AtomicBoolean();
            AtomicReference<Throwable> err = new AtomicReference<>();
            Thread[] readers = new Thread[3];
            for (int t = 0; t < readers.length; t++) {
                readers[t] = new Thread(() -> {
                    try {
                        while (!stop.get()) {
                            int n = 0;
                            for (Player p : view) n++;
                            server.api().onlinePlayers().size();
                            view.size();
                        }
                    } catch (Throwable e) { err.set(e); }
                });
                readers[t].start();
            }
            try {
                for (TestPlayer p : ps) {
                    UUID id = p.uuid();
                    server.internals().playerByUuid(id).markDisconnected();
                    for (int k = 0; k < 2000; k++) {
                        for (var h : server.api().onlinePlayers()) assertNotEquals(id, h.uniqueId(), "낡은 서버 목록 k=" + k);
                        for (Player q : view) assertNotEquals(id, q.getUniqueId(), "낡은 Bukkit 보기 k=" + k);
                    }
                }
            } finally {
                stop.set(true);
                for (Thread t : readers) t.join();
            }
            assertNull(err.get());
            assertEquals(0, view.size());
        }
    }
}
