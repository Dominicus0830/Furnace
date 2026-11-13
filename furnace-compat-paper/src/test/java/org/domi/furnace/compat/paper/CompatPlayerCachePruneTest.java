package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-073 R2 — 플레이어 감싸개 표의 분할 상환 문턱: 1024 를 넘겨도 접속 중인 것만 있으면 문턱이 남은 수 × 2 로 올라 놓침마다 훑지 않고,
 * 훑을 때는 접속이 끝난 것만 걷으며, 표가 줄면 문턱이 1024 로 돌아온다. 표가 커지는 시험이라 서버를 따로 쓴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompatPlayerCachePruneTest {

    FurnaceTestServer server;
    final Set<UUID> offline = ConcurrentHashMap.newKeySet();

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    org.domi.furnace.api.entity.Player fake(UUID id, String name) {
        return (org.domi.furnace.api.entity.Player) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {org.domi.furnace.api.entity.Player.class},
                (p, m, a) -> switch (m.getName()) {
                    case "uniqueId" -> id;
                    case "name" -> name;
                    case "isOnline" -> !offline.contains(id);
                    case "type" -> "minecraft:player";
                    case "snapshotStamp" -> 0L;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> p == a[0];
                    case "toString" -> "FakePlayer(" + name + ")";
                    default -> {
                        Class<?> r = m.getReturnType();
                        yield r == boolean.class ? (Object) false : r == int.class ? (Object) 0 : r == long.class ? (Object) 0L
                                : r == double.class ? (Object) 0.0 : r == float.class ? (Object) 0f : null;
                    }
                });
    }

    /** 만들자마자 접속이 끝난 가짜 플레이어를 한 명 더 감싼다(놓침 한 번). */
    void missOffline(String name) {
        UUID id = UUID.randomUUID();
        offline.add(id);
        Compat.player(fake(id, name));
    }

    @Test
    void amortizedPruneThresholdLifecycle() {
        assertEquals(1024, Compat.playerPruneAt());

        // 1) 접속 중 1100명: 1024 를 넘은 놓침이 훑었지만 전부 접속 중이라 하나도 안 걷혔다 → 문턱이 (남은 수 × 2) 로 올라 놓침이 O(1)
        List<org.domi.furnace.api.entity.Player> ps = new ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            var p = fake(UUID.randomUUID(), "P" + i);
            ps.add(p);
            assertNotNull(Compat.player(p));
        }
        assertEquals(1100, Compat.playerCacheSize());
        int raised = Compat.playerPruneAt();
        assertTrue(raised >= 2 * 1025, "문턱이 (훑을 때 남은 수 × 2) 로 올라야 한다: " + raised);

        // 2) 600명이 접속을 끝내도 문턱 아래에서는 훑지 않는다 — 접속이 끝난 것도 표에 남고 문턱은 그대로
        for (int i = 0; i < 600; i++) offline.add(ps.get(i).uniqueId());
        for (int i = 0; i < 20; i++) Compat.player(fake(UUID.randomUUID(), "Q" + i));
        assertEquals(raised, Compat.playerPruneAt());
        assertNotNull(Compat.existingPlayer(ps.get(0)));

        // 3) 문턱을 넘기는 놓침이 훑는다 — 접속이 끝난 것만 걷히고(접속 중인 감싸개는 그대로), 문턱은 (남은 수 × 2) 로
        var keep = Compat.player(ps.get(1000));
        while (Compat.playerCacheSize() <= raised) missOffline("F" + Compat.playerCacheSize());
        missOffline("Trigger");
        assertNull(Compat.existingPlayer(ps.get(0)), "접속이 끝난 것은 걷혀야 한다");
        assertNull(Compat.existingPlayer(ps.get(599)));
        assertSame(keep, Compat.existingPlayer(ps.get(1000)), "접속 중인 것은 같은 감싸개");
        int left = 500 + 20;   // 접속 중이던 P600..P1099 와 새로 들어온 Q 20명
        assertTrue(Compat.playerCacheSize() <= left + 1, "접속 중인 것만 남아야 한다: " + Compat.playerCacheSize());
        assertEquals(Math.max(1024, 2 * (Compat.playerCacheSize() - 1)), Math.max(1024, Compat.playerPruneAt()) , "문턱은 남은 수 × 2 (최소 1024)");

        // 4) 표가 1024 이하로 줄었으면 다음 놓침이 문턱을 1024 로 되돌린다
        missOffline("AfterShrink");
        assertTrue(Compat.playerCacheSize() <= 1024);
        assertEquals(1024, Compat.playerPruneAt());
    }
}
