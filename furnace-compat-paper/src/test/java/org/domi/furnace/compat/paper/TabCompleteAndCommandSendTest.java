package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FAWE 가 쓰는 Paper AsyncTabCompleteEvent 와 PlayerCommandSendEvent 가 Paper 처럼 불리는지(서버 → Furnace 이벤트 → Bukkit 처리기).
 * 기준: Paper 1.21.1 ServerGamePacketListenerImpl.handleCustomCommandSuggestions0 / Commands.sendCommands.
 */
class TabCompleteAndCommandSendTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private static void suggest(TestPlayer tp, int id, String text) {
        tp.sendPacket(0x0B, b -> {
            Types.writeVarInt(b, id);
            Types.writeString(b, text);
        });
    }

    private static boolean idIs(GenericPacket p, int id) { return ((Integer) p.get("id")) == id; }

    @SuppressWarnings("unchecked")
    private static List<String> texts(GenericPacket p) {
        List<String> out = new ArrayList<>();
        for (Object o : (List<Object>) p.get("suggestions")) out.add((String) ((PacketStruct) o).get("text"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rootNames(GenericPacket tree) {
        List<PacketStruct> entries = (List<PacketStruct>) (List<?>) tree.get("entries");
        int root = (Integer) tree.get("root_index");
        List<String> out = new ArrayList<>();
        for (int c : (int[]) entries.get(root).get("children")) out.add(((java.util.Optional<String>) entries.get(c).get("name")).orElse("?"));
        return out;
    }

    @Test
    void handledEventSuppressesTheServerAndSendsOnlyThePluginsCompletions() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("AsyncTabCompleteEvent", e -> {
                var t = (com.destroystokyo.paper.event.server.AsyncTabCompleteEvent) e;
                assertTrue(t.isAsynchronous());
                assertTrue(t.isCommand());
                assertEquals("/foo ", t.getBuffer());
                assertEquals("Dana", t.getSender().getName());
                assertEquals(null, t.getLocation());
                t.setCompletions(List.of("Beta", "alpha", "12", "-3", "+4", "007"));
            });
            suggest(tp, 7, "/foo ");
            GenericPacket r = tp.awaitPacket(PacketTypes.Clientbound.COMMAND_SUGGESTIONS, p -> idIs(p, 7));
            // 바꿀 자리는 마지막 공백 뒤, 정렬은 대소문자 무시(Brigadier Suggestions.create), 정수 꼴은 정수 후보(앞의 '+' 는 문자열)
            assertEquals(5, r.get("start"));
            assertEquals(6, texts(r).size());
            assertTrue(texts(r).containsAll(List.of("alpha", "Beta", "+4", "12", "-3", "7")), texts(r).toString());
            assertFalse(texts(r).contains("007"), "정수 꼴 후보는 정수로 낸다(Guava Ints.tryParse)");
            assertFalse(texts(r).contains("list"), "명령 트리 후보는 섞이지 않는다");
        }
    }

    @Test
    void unhandledEventFallsBackToTheServerAndCancelledSendsNothing() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            // 처리기가 아무것도 안 하면 서버가 평소처럼 후보를 만든다
            suggest(tp, 1, "/lis");
            GenericPacket r = tp.awaitPacket(PacketTypes.Clientbound.COMMAND_SUGGESTIONS, p -> idIs(p, 1));
            assertTrue(texts(r).contains("list"), texts(r).toString());
            // 취소 — 응답이 없다(뒤의 요청이 답을 받은 뒤에도)
            RecorderPlugin.ACTIONS.put("AsyncTabCompleteEvent", e -> ((com.destroystokyo.paper.event.server.AsyncTabCompleteEvent) e).setCancelled(true));
            tp.clearReceived();
            suggest(tp, 2, "/lis");
            // 후보를 채우고 취소하면 취소가 이긴다(Paper: completions 는 빈 목록으로 본다)
            RecorderPlugin.ACTIONS.put("AsyncTabCompleteEvent", e -> {
                var t = (com.destroystokyo.paper.event.server.AsyncTabCompleteEvent) e;
                t.setCompletions(List.of("x"));
                t.setCancelled(true);
            });
            suggest(tp, 3, "/lis");
            // handled 표시만 하고 후보가 없으면 빈 응답도 없다
            RecorderPlugin.ACTIONS.put("AsyncTabCompleteEvent", e -> ((com.destroystokyo.paper.event.server.AsyncTabCompleteEvent) e).setHandled(true));
            suggest(tp, 4, "/lis");
            // 확인용: 마지막으로 후보를 채우는 요청
            RecorderPlugin.ACTIONS.put("AsyncTabCompleteEvent", e -> ((com.destroystokyo.paper.event.server.AsyncTabCompleteEvent) e).getCompletions().add("done"));
            suggest(tp, 5, "/lis");
            tp.awaitPacket(PacketTypes.Clientbound.COMMAND_SUGGESTIONS, p -> idIs(p, 5));
            Thread.sleep(300);
            var got = tp.received(PacketTypes.Clientbound.COMMAND_SUGGESTIONS);
            assertEquals(1, got.size(), "취소·빈 handled 요청에는 응답이 없다: " + got);
        }
    }

    @Test
    void commandSendEventTrimsTheTreeAtJoinAndOnUpdate() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            RecorderPlugin.ACTIONS.put("PlayerCommandSendEvent", e -> {
                var c = (org.bukkit.event.player.PlayerCommandSendEvent) e;
                assertTrue(c.getCommands().contains("list"), c.getCommands().toString());
                c.getCommands().remove("list");
                c.getCommands().add("not-a-command");   // 더한 이름은 무시된다
            });
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            var trees = tp.received(PacketTypes.Clientbound.COMMANDS);
            assertFalse(trees.isEmpty());
            var last = trees.get(trees.size() - 1);
            List<String> names = rootNames(last);
            assertFalse(names.contains("list"), "처리기가 뺀 명령은 트리에서 빠진다");
            assertFalse(names.contains("not-a-command"));
            assertTrue(names.contains("say") || names.contains("tell") || names.contains("give"), names.toString());
            // 쳐서 실행하는 것은 그대로다
            RecorderPlugin.SEEN.clear();
            tp.clearReceived();
            server.api().commands().updateCommands(tp.player());
            server.tick(2);
            var later = tp.received(PacketTypes.Clientbound.COMMANDS);
            assertEquals(1, later.size());
            assertFalse(rootNames(later.get(0)).contains("list"));
            assertEquals(1, RecorderPlugin.SEEN.stream().filter(x -> x instanceof org.bukkit.event.player.PlayerCommandSendEvent).count());
        }
    }

    /** Paper: 접속 때 PlayerCommandSendEvent 는 PlayerJoinEvent 뒤에 불린다(트리 빌드 뒤 메인에서) — 검토 R1. 트리 패킷도 이벤트가 거른 한 번만 간다. */
    @Test
    void joinTimeCommandSendEventComesAfterPlayerJoinEvent() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            RecorderPlugin.ACTIONS.put("PlayerCommandSendEvent", e -> ((org.bukkit.event.player.PlayerCommandSendEvent) e).getCommands().remove("list"));
            TestPlayer tp = server.join("Dana");
            server.tick(4);
            int join = -1, send = -1;
            for (int i = 0; i < RecorderPlugin.SEEN.size(); i++) {
                var e = RecorderPlugin.SEEN.get(i);
                if (e instanceof org.bukkit.event.player.PlayerJoinEvent && join < 0) join = i;
                if (e instanceof org.bukkit.event.player.PlayerCommandSendEvent && send < 0) send = i;
            }
            assertTrue(join >= 0 && send >= 0, "join=" + join + " send=" + send);
            assertTrue(send > join, "PlayerJoinEvent 뒤여야 한다: join=" + join + " send=" + send);
            var trees = tp.received(PacketTypes.Clientbound.COMMANDS);
            assertEquals(1, trees.size(), "접속 트리는 한 번만(걸러서) 간다");
            assertFalse(rootNames(trees.get(0)).contains("list"));
        }
    }
}
