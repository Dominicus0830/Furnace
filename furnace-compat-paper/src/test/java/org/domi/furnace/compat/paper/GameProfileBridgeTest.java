package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.server.plugin.PlayerHandle;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** N-lib-2: authlib 다리 — ServerPlayer.getGameProfile()·CraftPlayer.getProfile() 이 접속 때 받은 프로필 속성으로 만든 바닐라 모양 프로필을 주는가. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameProfileBridgeTest {

    FurnaceTestServer server;
    TestPlayer skinned;
    TestPlayer plain;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        skinned = server.join("Nora");
        plain = server.join("Ivo");
        server.tick(3);
        // 온라인 모드 접속이면 세션 서버가 주는 값 — 시험 서버는 오프라인이라 직접 넣는다
        ((PlayerHandle) skinned.player()).handle().profileProperties(List.of(
                new org.domi.furnace.server.players.GameProfile.Property("textures", "dGV4dHVyZXM=", "c2ln"),
                new org.domi.furnace.server.players.GameProfile.Property("extra", "v", null)));
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void apiExposesProfileProperties() {
        assertEquals(List.of(new ProfileProperty("textures", "dGV4dHVyZXM=", "c2ln"), new ProfileProperty("extra", "v", null)),
                skinned.player().profileProperties());
        assertEquals(List.of(), plain.player().profileProperties(), "오프라인 접속은 속성 없음");
    }

    @Test
    void handleProfileCarriesIdNameAndTextures() {
        CraftPlayer cp = (CraftPlayer) Compat.player(skinned.player());
        GameProfile p = cp.getHandle().getGameProfile();
        assertEquals(skinned.player().uniqueId(), p.getId());
        assertEquals("Nora", p.getName());
        // 플러그인들이 스킨을 읽는 흔한 모양
        Property tex = p.getProperties().get("textures").iterator().next();
        assertEquals(new Property("textures", "dGV4dHVyZXM=", "c2ln"), tex);
        assertTrue(tex.hasSignature());
        Property extra = p.getProperties().get("extra").iterator().next();
        assertNull(extra.signature());
        assertFalse(extra.hasSignature());
        assertEquals(2, p.getProperties().size());
    }

    @Test
    void sameProfileObjectEveryTime() {
        CraftPlayer cp = (CraftPlayer) Compat.player(skinned.player());
        GameProfile p = cp.getProfile();
        assertSame(p, cp.getProfile(), "바닐라처럼 플레이어마다 하나");
        assertSame(p, cp.getHandle().getGameProfile());
        assertSame(p, ((CraftPlayer) Bukkit.getPlayer("Nora")).getProfile());
    }

    @Test
    void offlinePlayerHasEmptyProperties() {
        GameProfile p = ((CraftPlayer) Compat.player(plain.player())).getProfile();
        assertEquals("Ivo", p.getName());
        assertEquals(plain.player().uniqueId(), p.getId());
        assertTrue(p.getProperties().isEmpty());
    }
}
