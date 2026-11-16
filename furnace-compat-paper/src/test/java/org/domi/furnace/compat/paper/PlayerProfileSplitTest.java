package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-141 E11c-2a: 엔티티 UUID 와 게임 프로필을 나눈 뒤 Bukkit 쪽 값이 Paper 1.21.1 CraftPlayer 와 같은 곳을 보는지(설계 §3 표의 호환 줄).
 * 서버 시험 훅으로 엔티티 UUID·접속 이름과 다른 프로필을 넣는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerProfileSplitTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void bukkitIdentityReadsTheRightSide() throws Exception {
        TestPlayer tp = server.join("Alex");
        try {
            var handle = ((org.domi.furnace.server.plugin.PlayerHandle) tp.player()).handle();
            UUID entity = handle.uuid();
            UUID profileId = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
            var props = List.of(new GameProfile.Property("textures", "dGV4", "c2ln"));
            handle.profileForTest(new GameProfile(profileId, "Bobby", props));
            assertNotEquals(entity, profileId);

            Player p = Compat.player(tp.player());
            assertEquals(entity, p.getUniqueId(), "getUniqueId = 엔티티 UUID");
            assertEquals("Bobby", p.getName(), "getName = gameProfile 이름");
            assertEquals("Alex", p.getDisplayName(), "getDisplayName = 접속 때 이름(adventure$displayName, R4)");
            var profile = p.getPlayerProfile();
            assertEquals(profileId, profile.getId(), "getPlayerProfile().getId() = 프로필 id(엔티티 UUID 와 다를 수 있다)");
            assertEquals("Bobby", profile.getName());
            assertEquals(1, profile.getProperties().size());
            assertEquals("dGV4", profile.getProperties().iterator().next().getValue());
            assertEquals("Bobby", ((CraftPlayer) p).getHandle().getScoreboardName(), "NMS getScoreboardName = 프로필 이름");
            assertEquals(profileId, ((CraftPlayer) p).getHandle().getGameProfile().getId(), "NMS gameProfile = 프로필");

            // 이름 조회는 새 이름(의도한 차이 §8), UUID 조회는 엔티티 UUID
            assertSame(p, Bukkit.getPlayerExact("Bobby"));
            assertNull(Bukkit.getPlayerExact("Alex"));
            assertSame(p, Bukkit.getPlayer(entity));
            assertNull(Bukkit.getPlayer(profileId));
        } finally {
            tp.close();
        }
    }
    /** T-141 E11c-2b: setPlayerProfile — 엔티티 UUID·표시 이름은 그대로, 프로필·NMS gameProfile 은 새 값(빈 id 는 NIL, Paper asAuthlibCopy). */
    @Test
    void setPlayerProfileSwapsTheProfileOnly() throws Exception {
        TestPlayer tp = server.join("Carl");
        try {
            Player p = Compat.player(tp.player());
            UUID entity = p.getUniqueId();
            var nms = ((CraftPlayer) p).getHandle();
            assertEquals("Carl", nms.getGameProfile().getName());
            UUID newId = UUID.fromString("00000000-0000-4000-8000-0000000000cc");
            var next = Bukkit.createProfile(newId, "Dora");
            next.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", "dGV4", "c2ln"));
            p.setPlayerProfile(next);
            awaitName(p, "Dora");
            next.setName("Edit");   // 넘긴 객체를 나중에 고쳐도 이어지지 않는다(복사)
            assertEquals("Dora", p.getName());
            assertEquals(entity, p.getUniqueId(), "엔티티 UUID 는 그대로");
            assertEquals("Carl", p.getDisplayName(), "표시 이름은 접속 때 이름(adventure$displayName)");
            var got = p.getPlayerProfile();
            assertEquals(newId, got.getId());
            assertEquals("dGV4", got.getProperties().iterator().next().getValue());
            assertEquals("Dora", nms.getGameProfile().getName(), "NMS gameProfile 도 새 프로필");
            assertEquals(newId, nms.getGameProfile().getId());
            assertEquals("Dora", nms.getScoreboardName());

            p.setPlayerProfile(Bukkit.createProfile("Fay"));   // id 없음 → NIL UUID
            awaitName(p, "Fay");
            assertEquals(new UUID(0L, 0L), p.getPlayerProfile().getId());
            assertEquals(entity, p.getUniqueId());
        } finally {
            tp.close();
        }
    }

    /** 시험 스레드는 소유 스레드가 아니라 다음 틱에 적용된다 — 시험 서버는 손으로 틱을 돌린다. */
    private void awaitName(Player p, String name) throws InterruptedException {
        for (int i = 0; i < 100 && !name.equals(p.getName()); i++) {
            server.tick();
            Thread.sleep(10);
        }
        assertEquals(name, p.getName());
    }
}
