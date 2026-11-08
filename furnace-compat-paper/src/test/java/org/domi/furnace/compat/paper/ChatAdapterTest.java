package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.config.OperatorOptions;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P3 채팅·표시 이름 (docs/feature-parity/paper-vs-spigot-2026-11-08.md C2·C3·C5·D2).
 * Paper 1.21.1: Adventure 패치 ChatProcessor.viewersFromLegacy(받는 사람 집합이 곧 보는 사람, 콘솔은 늘 로그), CraftPlayer.displayName/setDisplayName,
 * Fix-kick-event-leave-message-not-being-sent, CraftChatMessage.fromString(주소 링크).
 */
class ChatAdapterTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    @AfterEach
    void restoreOptions() { OperatorOptions.install(null); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    /** 서명 있는 player_chat 패킷을 받은 사람 이름(바닐라 경로 — 내용은 불투명 패킷이라 누가 받았는지만 본다). */
    private static List<String> collectSignedChatRecipients(FurnaceTestServer server) {
        List<String> got = new CopyOnWriteArrayList<>();
        server.internals().plugins().packetsImpl().listenInternal(PacketTypes.Clientbound.PLAYER_CHAT, EventPriority.MONITOR,
                e -> got.add(e.player().name()));
        return got;
    }

    private static boolean saw(TestPlayer p, String text) { return p.messageLines().stream().anyMatch(s -> s.contains(text)); }

    @Test
    void recipientsStartAsEveryoneOnlineAndTouchingChangesNothing() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee"), kim = server.join("Kim");
            server.tick(2);
            var got = collectSignedChatRecipients(server);
            int[] size = new int[1];
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> size[0] = ((AsyncPlayerChatEvent) e).getRecipients().size());
            dana.chat("hello");
            server.tick(2);
            assertEquals(3, size[0]);
            assertTrue(got.containsAll(List.of("Dana", "Lee", "Kim")), "만지기만 하면 전원이 받는다: " + got);
        }
    }

    @Test
    void removingARecipientKeepsThemFromSeeingTheSignedChat() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee"), kim = server.join("Kim");
            server.tick(2);
            var got = collectSignedChatRecipients(server);
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> {
                var c = (AsyncPlayerChatEvent) e;
                c.getRecipients().removeIf(p -> p.getName().equals("Lee"));   // 무시 플러그인
            });
            dana.chat("hello");
            server.tick(2);
            assertTrue(got.contains("Dana") && got.contains("Kim"), got.toString());
            assertFalse(got.contains("Lee"), "뺀 사람은 받지 않는다: " + got);
        }
    }

    @Test
    void channelStyleRecipientSetWithCustomFormat() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee"), kim = server.join("Kim");
            server.tick(2);
            dana.clearMessages();
            lee.clearMessages();
            kim.clearMessages();
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> {
                var c = (AsyncPlayerChatEvent) e;
                c.setFormat("[team] %1$s: %2$s");
                c.getRecipients().clear();   // 채널: 지우고 같은 채널 사람만 더한다
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) if (!p.getName().equals("Lee")) c.getRecipients().add(p);
            });
            dana.chat("hi");
            server.tick(2);
            assertTrue(saw(dana, "[team] Dana: hi"), dana.messageLines().toString());
            assertTrue(saw(kim, "[team] Dana: hi"), kim.messageLines().toString());
            assertFalse(saw(lee, "hi"), "채널 밖 사람은 받지 않는다: " + lee.messageLines());
        }
    }

    @Test
    void emptyRecipientsMeansNobodyButConsoleStillLogs() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            var got = collectSignedChatRecipients(server);
            dana.clearMessages();
            lee.clearMessages();
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((AsyncPlayerChatEvent) e).getRecipients().clear());
            dana.chat("shh");
            server.tick(2);
            assertTrue(got.isEmpty(), "아무도 받지 않는다: " + got);
            assertFalse(saw(lee, "shh") || saw(dana, "shh"));
        }
    }

    @Test
    void recipientsAlsoFilterUnsignedSystemChat() throws Exception {
        OperatorOptions.install(new OperatorOptions(true, true, OperatorOptions.DEFAULT_BRAND, true, false, 5, "plain", true));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            dana.clearMessages();
            lee.clearMessages();
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((AsyncPlayerChatEvent) e).getRecipients().removeIf(p -> p.getName().equals("Lee")));
            dana.chat("hey");
            server.tick(2);
            assertTrue(saw(dana, "<Dana> hey"), dana.messageLines().toString());
            assertFalse(saw(lee, "hey"), lee.messageLines().toString());
        }
    }

    @Test
    void getDisplayNameIsThePlainNameEvenWithATeamPrefix() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana");
            server.tick(2);
            server.execute("team add vip");
            server.execute("team modify vip prefix \"[VIP] \"");
            server.execute("team join vip Dana");
            String[] seen = new String[1];
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> seen[0] = ((AsyncPlayerChatEvent) e).getPlayer().getDisplayName());
            dana.chat("x");
            server.tick(2);
            assertEquals("Dana", seen[0], "Paper·Spigot: 팀 접두사가 붙지 않은 이름 그대로");
        }
    }

    @Test
    void setDisplayNameShowsInTheLegacyFormatAndInVanillaChat() throws Exception {
        OperatorOptions.install(new OperatorOptions(true, true, OperatorOptions.DEFAULT_BRAND, true, false, 5, "plain", true));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            // 1) 서식을 안 바꿔도(바닐라 경로) 정한 표시 이름이 "<이름>" 으로 간다
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((AsyncPlayerChatEvent) e).getPlayer().setDisplayName("§6Boss"));
            dana.chat("one");
            server.tick(2);
            RecorderPlugin.ACTIONS.clear();
            lee.clearMessages();
            dana.chat("two");
            server.tick(2);
            assertTrue(saw(lee, "<Boss> two"), lee.messageLines().toString());
            // 2) 서식을 바꾸면 %1$s 가 표시 이름이다
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((AsyncPlayerChatEvent) e).setFormat("{%1$s} %2$s"));
            lee.clearMessages();
            dana.chat("three");
            server.tick(2);
            assertTrue(saw(lee, "{Boss} three"), lee.messageLines().toString());
            // 3) null 이면 이름 그대로
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((AsyncPlayerChatEvent) e).getPlayer().setDisplayName(null));
            dana.chat("four");
            server.tick(2);
            RecorderPlugin.ACTIONS.clear();
            lee.clearMessages();
            dana.chat("five");
            server.tick(2);
            assertTrue(saw(lee, "<Dana> five"), lee.messageLines().toString());
        }
    }

    @Test
    void kickEventLeaveMessageBecomesTheQuitMessage() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("PlayerKickEvent", e -> ((org.bukkit.event.player.PlayerKickEvent) e).setLeaveMessage("§eDana was shown the door"));
            String[] quit = new String[1];
            RecorderPlugin.ACTIONS.put("PlayerQuitEvent", e -> quit[0] = ((org.bukkit.event.player.PlayerQuitEvent) e).getQuitMessage());
            lee.clearMessages();
            server.execute("kick Dana");
            server.tick(3);
            assertEquals("§eDana was shown the door", quit[0], "PlayerQuitEvent 의 처음 메시지가 킥 이벤트가 정한 문구다");
            assertTrue(saw(lee, "Dana was shown the door"), lee.messageLines().toString());
        }
    }

    @Test
    void emptyKickLeaveMessageSuppressesTheAnnouncement() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("PlayerKickEvent", e -> ((org.bukkit.event.player.PlayerKickEvent) e).setLeaveMessage(""));
            lee.clearMessages();
            server.execute("kick Dana");
            server.tick(3);
            assertFalse(saw(lee, "Dana"), "알림이 없어야 한다: " + lee.messageLines());
        }
    }

    @Test
    void untouchedKickLeaveMessageIsTheVanillaOne() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana"), lee = server.join("Lee");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("PlayerKickEvent", e -> assertEquals("§eDana left the game", ((org.bukkit.event.player.PlayerKickEvent) e).getLeaveMessage()));
            lee.clearMessages();
            server.execute("kick Dana");
            server.tick(3);
            assertTrue(saw(lee, "Dana left the game"), lee.messageLines().toString());
        }
    }

    private static ClickEvent firstClick(Text t) {
        if (t.clickEvent() != null) return t.clickEvent();
        for (Text c : t.children()) {
            ClickEvent ce = firstClick(c);
            if (ce != null) return ce;
        }
        return null;
    }

    @Test
    void sendMessageTurnsAddressesIntoOpenUrlLinksButBroadcastDoesNot() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana");
            server.tick(2);
            Player p = org.bukkit.Bukkit.getPlayer("Dana");
            dana.clearMessages();
            p.sendMessage("§aJoin us: https://discord.gg/furnace now");
            Text t = dana.awaitMessage(m -> m.contains("Join us"));
            ClickEvent ce = firstClick(t);
            assertNotNull(ce, "주소가 링크가 돼야 한다: " + t);
            assertEquals(ClickEvent.Action.OPEN_URL, ce.action());
            assertEquals("https://discord.gg/furnace", ce.value());
            assertEquals("Join us: https://discord.gg/furnace now", t.plainText(), "글자는 그대로");
            dana.clearMessages();
            p.sendMessage("see example.com/page");
            ClickEvent bare = firstClick(dana.awaitMessage(m -> m.contains("example.com")));
            assertNotNull(bare, "스킴 없는 주소도 링크: " + dana.messages());
            assertEquals("http://example.com/page", bare.value(), "CraftChatMessage: 스킴이 없으면 http://");
            // CraftChatMessage 패턴: 끝 문장부호는 링크에서 빠지고, 포트·쿼리는 들어가고, 대소문자는 가리지 않는다
            dana.clearMessages();
            p.sendMessage("read https://furnace.dev/docs.");
            assertEquals("https://furnace.dev/docs", firstClick(dana.awaitMessage(m -> m.contains("furnace.dev"))).value());
            dana.clearMessages();
            p.sendMessage("map https://map.example.com:8123/?world=w here");
            assertEquals("https://map.example.com:8123/?world=w", firstClick(dana.awaitMessage(m -> m.contains("map.example"))).value());
            dana.clearMessages();
            p.sendMessage("go Example.COM now");
            assertEquals("http://Example.COM", firstClick(dana.awaitMessage(m -> m.contains("Example.COM"))).value());
            dana.clearMessages();
            org.bukkit.Bukkit.broadcastMessage("visit https://furnace.dev/docs");
            assertNull(firstClick(dana.awaitMessage(m -> m.contains("furnace.dev"))), "Paper 의 broadcastMessage 는 legacySection().deserialize 라 링크가 없다(Spigot 만 링크)");
            dana.clearMessages();
            p.sendMessage("no link here, just words.");
            assertTrue(firstClick(dana.awaitMessage(m -> m.contains("no link here"))) == null, "주소 아닌 글에는 링크가 없다");
        }
    }
}
