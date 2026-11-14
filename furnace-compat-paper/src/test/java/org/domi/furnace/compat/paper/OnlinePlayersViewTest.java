// T-074: Bukkit.getOnlinePlayers() 보기 — Paper 접속 순, 살아 있는 보기 의미, 옛 구현(매번 다시 만들기)과 같은 답.
//
// 기대 순서는 Paper 1.21.1 CraftServer#getOnlinePlayers = PlayerList.players(접속 순 ArrayList, 리스폰 때 옛 플레이어 빼고 새 플레이어 끝에)
// 를 Lists.transform 한 보기라는 것에서 옮겼다. 오라클은 두 가지다: ① 접속·퇴장·리스폰 모델(순수 목록 조작) ② 옛 구현 =
// 호출마다 서버 접속자(ServerCore.players)를 훑어 Compat.player 로 감싸 새로 만든 목록.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;

class OnlinePlayersViewTest {

    @org.junit.jupiter.api.BeforeAll
    static void bukkit() { Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE); }   // 시험 서버는 Bukkit 싱글턴을 따로 세우지 않는다

    private static List<UUID> ids(Collection<? extends Player> c) {
        List<UUID> out = new ArrayList<>();
        for (Player p : c) out.add(p.getUniqueId());
        return out;
    }

    /** 옛 구현: 서버 접속자(끊기지 않은 것)를 접속 순으로 훑어 매번 새로 감싼다. */
    private static List<Player> legacy(FurnaceTestServer server) {
        List<Player> out = new ArrayList<>();
        for (var p : server.internals().players()) out.add(Compat.player(server.api().player(p.uuid())));
        return out;
    }

    private static void respawn(FurnaceTestServer server, UUID id) {
        server.internals().world().playerRespawned(server.internals().playerByUuid(id));
    }

    @Test
    void joinOrderRespawnMovesToEndQuitDrops() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Collection<? extends Player> held = Bukkit.getOnlinePlayers();   // 앞서 받아 둔 보기
            assertEquals(0, held.size());
            List<TestPlayer> tps = new ArrayList<>();
            for (String n : List.of("Zed", "Amy", "Bob", "Cat")) {
                tps.add(server.join(n));
                server.tick(2);
            }
            List<UUID> model = new ArrayList<>(tps.stream().map(TestPlayer::uuid).toList());
            assertEquals(model, ids(held), "접속 순(이름·eid 해시 순이 아니다)");
            assertEquals(model, ids(Bukkit.getOnlinePlayers()));
            assertEquals(4, held.size());

            // 리스폰: 옛 플레이어를 빼고 새 플레이어를 끝에
            UUID amy = tps.get(1).uuid();
            respawn(server, amy);
            model.remove(amy);
            model.add(amy);
            assertEquals(model, ids(held), "리스폰하면 끝으로");
            assertEquals(model, ids(legacy(server).stream().toList()));

            // 퇴장
            UUID zed = tps.get(0).uuid();
            tps.get(0).close();
            server.tick(3);
            model.remove(zed);
            assertEquals(model, ids(held), "끊긴 플레이어는 빠진다");
            assertEquals(3, held.size());

            // 다시 접속 = 끝
            TestPlayer back = server.join("Zed");
            server.tick(2);
            model.add(back.uuid());
            assertEquals(model, ids(held));

            // 고칠 수 없다
            assertThrows(UnsupportedOperationException.class, held::clear);
            assertThrows(UnsupportedOperationException.class, () -> ((List<Player>) held).set(0, null));
            assertThrows(IndexOutOfBoundsException.class, () -> ((List<?>) held).get(held.size()));
            assertThrows(IndexOutOfBoundsException.class, () -> ((List<?>) held).get(-1));
        }
    }

    @Test
    void unchangedRosterReusesWrapperArrayAndSameObjects() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            TestPlayer a = server.join("Ann");
            TestPlayer b = server.join("Ben");
            server.tick(2);
            // 서버 쪽 목록: 접속자 집합이 그대로이면 같은 객체
            var l1 = server.api().onlinePlayers();
            var l2 = server.api().onlinePlayers();
            assertSame(l1, l2, "안 바뀌면 같은 목록 객체");
            List<Player> v = (List<Player>) Bukkit.getOnlinePlayers();
            Player p0 = v.get(0), p1 = v.get(1);
            assertSame(p0, v.get(0));
            assertSame(Compat.player(server.api().player(a.uuid())), p0, "get 은 매번 감싸던 그 CraftPlayer");
            assertSame(Compat.player(server.api().player(b.uuid())), p1);
            // 바뀌면 새 목록 객체, 같은 사람의 CraftPlayer 는 그대로
            a.close();
            server.tick(3);
            var l3 = server.api().onlinePlayers();
            assertNotSame(l1, l3);
            assertEquals(1, l3.size());
            assertSame(p1, v.get(0));
            // 방금 끊긴 플레이어가 끊김 표시 직후에도 남지 않는다(끊김이 번호를 올려 캐시를 버린다)
            b.close();
            server.tick(3);
            assertEquals(0, v.size());
            assertTrue(server.api().onlinePlayers().isEmpty());
        }
    }

    /** 순회는 시작 때의 목록 하나를 끝까지 본다(도중 접속·퇴장이 있어도 같은 사람을 두 번 보거나 건너뛰지 않는다). */
    @Test
    void iterationUsesStartSnapshot() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            List<TestPlayer> tps = new ArrayList<>();
            for (String n : List.of("P1", "P2", "P3")) tps.add(server.join(n));
            server.tick(2);
            var it = Bukkit.getOnlinePlayers().iterator();
            UUID first = it.next().getUniqueId();
            server.join("P4");
            server.tick(2);
            List<UUID> seen = new ArrayList<>(List.of(first));
            while (it.hasNext()) seen.add(it.next().getUniqueId());
            assertEquals(tps.stream().map(TestPlayer::uuid).toList(), seen, "시작 때의 3명만, 순서대로");
            assertEquals(4, Bukkit.getOnlinePlayers().size());
            assertEquals(4, Bukkit.getOnlinePlayers().stream().count());
            assertEquals(4, Bukkit.getOnlinePlayers().toArray().length);
        }
    }

    /** 각본 차분: 무작위 접속·퇴장·리스폰 뒤 매 단계에서 보기 == 모델 == 옛 구현. */
    @Test
    void randomScriptMatchesModelAndLegacy() throws Exception {
        SplittableRandom r = new SplittableRandom(0x74B0L);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            List<TestPlayer> live = new ArrayList<>();
            List<UUID> model = new ArrayList<>();
            Collection<? extends Player> held = Bukkit.getOnlinePlayers();
            int serial = 0;
            for (int step = 0; step < 60; step++) {
                int op = r.nextInt(10);
                if (live.size() < 2 || (op < 4 && live.size() < 8)) {
                    TestPlayer t = server.join("S" + (serial++));
                    live.add(t);
                    model.add(t.uuid());
                } else if (op < 7) {
                    TestPlayer t = live.remove(r.nextInt(live.size()));
                    model.remove(t.uuid());
                    t.close();
                } else {
                    TestPlayer t = live.get(r.nextInt(live.size()));
                    respawn(server, t.uuid());
                    model.remove(t.uuid());
                    model.add(t.uuid());
                }
                server.tick(2);
                String ctx = "step " + step + " op " + op;
                assertEquals(model, ids(held), ctx);
                List<Player> old = legacy(server);
                assertEquals(old.size(), held.size(), ctx);
                List<Player> view = (List<Player>) held;
                for (int i = 0; i < old.size(); i++) assertSame(old.get(i), view.get(i), ctx + " #" + i);
            }
        }
    }
}
