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
}
