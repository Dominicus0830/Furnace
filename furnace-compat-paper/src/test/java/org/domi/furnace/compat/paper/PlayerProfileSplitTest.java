package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

            // 이름 조회(D2): getPlayerExact 는 접속 때 이름(Paper playersByName), getPlayer(이름)의 앞부분 일치는 현재 이름, UUID 조회는 엔티티 UUID
            assertSame(p, Bukkit.getPlayerExact("Alex"));
            assertNull(Bukkit.getPlayerExact("Bobby"));
            assertSame(p, Bukkit.getPlayer("Alex"), "정확히 일치는 접속 이름");
            assertSame(p, Bukkit.getPlayer("Bob"), "앞부분 일치는 현재 이름(Paper getPlayer(String) 의 훑기)");
            assertNull(Bukkit.getPlayer("Ale"), "접속 이름의 앞부분은 훑기 대상이 아니다");
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

    /**
     * D1: 프로필 id 가 엔티티 UUID 와 달라질 때만, 서버 전체에서 한 번, 콘솔에 경고한다. 패킷(서버 시험 ProfileChangeTest)은 경고와 무관하다.
     * 같은 UUID(스킨·이름만 바꿈)는 경고하지 않는다.
     */
    @Test
    void changingTheUuidWarnsOnceServerWide() throws Exception {
        CraftPlayer.resetUuidChangeWarning();
        var furnace = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("Furnace");
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        furnace.addAppender(logs);
        TestPlayer tp = server.join("Gus");
        TestPlayer tp2 = null;
        try {
            Player p = Compat.player(tp.player());
            p.setPlayerProfile(Bukkit.createProfileExact(p.getUniqueId(), "Gus2"));   // 같은 UUID(createProfile 은 접속 중이면 그 플레이어 값을 비춘다)
            awaitName(p, "Gus2");
            assertEquals(0, uuidWarnings(logs), "UUID 가 같으면 경고 없음");
            UUID other = UUID.fromString("00000000-0000-4000-8000-0000000000dd");
            p.setPlayerProfile(Bukkit.createProfile(other, "Gus3"));
            awaitName(p, "Gus3");
            assertEquals(1, uuidWarnings(logs), "UUID 가 바뀌면 경고 한 번");
            String msg = logs.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).filter(m -> m.contains("setPlayerProfile")).findFirst().orElseThrow();
            assertTrue(msg.contains(other.toString()) && msg.contains(p.getUniqueId().toString()) && msg.contains("Paper"), msg);
            p.setPlayerProfile(Bukkit.createProfile(UUID.fromString("00000000-0000-4000-8000-0000000000ee"), "Gus4"));   // 두 번째 호출
            awaitName(p, "Gus4");
            tp2 = server.join("Hal");
            Player p2 = Compat.player(tp2.player());
            p2.setPlayerProfile(Bukkit.createProfile(UUID.fromString("00000000-0000-4000-8000-0000000000ff"), "Hal2"));   // 다른 플레이어
            awaitName(p2, "Hal2");
            assertEquals(1, uuidWarnings(logs), "서버 전체에서 한 번만");
        } finally {
            furnace.detachAppender(logs);
            tp.close();
            if (tp2 != null) tp2.close();
        }
    }

    private static long uuidWarnings(ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs) {
        return logs.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("setPlayerProfile")).count();
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
