package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.MessagePrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.StartupProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * org.bukkit.conversations — ProtocolLib 의 FILTER 명령이 ConversationCanceller 가 없어 등록에 실패했다. CraftBukkit 처럼 대화 중 채팅은
 * 채팅 이벤트·방송 없이 대화로 가고(ServerGamePacketListenerImpl.broadcastChatMessage), 콘솔 줄은 명령 대신 대화로 간다(dispatchServerCommand).
 */
class ConversationTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("conv.jar"), "plugin.yml", "name: Conv\nversion: 1\nmain: " + StartupProbe.class.getName() + "\napi-version: '1.21'\n",
                StartupProbe.class);
        return FurnaceTestServer.builder().failFast(true).pluginJar(jar).start();
    }

    private static JavaPlugin plugin(FurnaceTestServer server) { return ((BukkitPluginAdapter) server.plugin("Conv")).javaPlugin(); }

    /** 이름을 묻고 인사한 뒤 끝나는 대화. */
    private static Prompt askName() {
        return new StringPrompt() {
            @Override public String getPromptText(ConversationContext c) { return "Name?"; }

            @Override public Prompt acceptInput(ConversationContext c, String input) {
                c.setSessionData("name", input);
                return new MessagePrompt() {
                    @Override public String getPromptText(ConversationContext c2) { return "Hi " + c2.getSessionData("name"); }

                    @Override protected Prompt getNextPrompt(ConversationContext c2) { return END_OF_CONVERSATION; }
                };
            }
        };
    }

    @Test
    void playerChatGoesToTheConversationNotToChat() throws Exception {
        try (FurnaceTestServer server = start()) {
            var dana = server.join("Dana");
            List<ConversationAbandonedEvent> ended = new CopyOnWriteArrayList<>();
            List<String> chats = new CopyOnWriteArrayList<>();   // 채팅 이벤트로 온 것(대화 대답은 오지 않아야 한다)
            server.onGlobal(() -> {
                Bukkit.getPluginManager().registerEvents(new ChatSpy(chats), plugin(server));
                Player p = Bukkit.getPlayer("Dana");
                Conversation c = new ConversationFactory(plugin(server)).withFirstPrompt(askName()).withLocalEcho(false)
                        .addConversationAbandonedListener(ended::add).buildConversation(p);
                c.begin();
                assertTrue(p.isConversing());
                p.sendMessage("modal-blocked");        // 모달 대화 중 플러그인 메시지는 막힌다
                p.sendRawMessage("raw-shown");         // 대화용 메시지는 간다
                return null;
            });
            dana.awaitMessage("Name?"::equals);
            dana.awaitMessage("raw-shown"::equals);
            dana.chat("Bob");
            dana.awaitMessage("Hi Bob"::equals);
            server.tick(2);
            assertEquals(1, ended.size());
            assertTrue(ended.get(0).gracefulExit());
            assertFalse(server.onGlobal(() -> Bukkit.getPlayer("Dana").isConversing()));
            assertFalse(dana.messageLines().contains("modal-blocked"));
            assertEquals(List.of(), chats, "대화 대답이 채팅 이벤트로 갔다");

            dana.chat("after");   // 대화가 끝나면 다시 채팅이다
            server.tick(2);
            assertEquals(List.of("after"), chats);
        }
    }

    public static final class ChatSpy implements org.bukkit.event.Listener {
        private final List<String> out;

        ChatSpy(List<String> out) { this.out = out; }

        @org.bukkit.event.EventHandler
        public void chat(org.bukkit.event.player.AsyncPlayerChatEvent e) { out.add(e.getMessage()); }
    }

    @Test
    void escapeSequenceAndQuitAbandonTheConversation() throws Exception {
        try (FurnaceTestServer server = start()) {
            var dana = server.join("Dana");
            List<ConversationAbandonedEvent> ended = new CopyOnWriteArrayList<>();
            server.onGlobal(() -> {
                new ConversationFactory(plugin(server)).withFirstPrompt(askName()).withEscapeSequence("quit")
                        .addConversationAbandonedListener(ended::add).buildConversation(Bukkit.getPlayer("Dana")).begin();
                return null;
            });
            dana.awaitMessage("Name?"::equals);
            dana.chat("quit");
            dana.awaitMessage("quit"::equals);   // 되울림(기본 켜짐)
            server.tick(2);
            assertEquals(1, ended.size());
            assertFalse(ended.get(0).gracefulExit());

            // 퇴장하면 남은 대화를 모두 끝낸다
            server.onGlobal(() -> {
                new ConversationFactory(plugin(server)).withFirstPrompt(askName())
                        .addConversationAbandonedListener(ended::add).buildConversation(Bukkit.getPlayer("Dana")).begin();
                return null;
            });
            dana.awaitMessage(l -> l.equals("Name?") && ended.size() == 1);
            dana.close();
            server.tick(5);
            assertEquals(2, ended.size());
        }
    }

    @Test
    void consoleLinesGoToTheConsoleConversation() throws Exception {
        try (FurnaceTestServer server = start()) {
            List<String> got = new CopyOnWriteArrayList<>();
            server.onGlobal(() -> {
                new ConversationFactory(plugin(server)).withFirstPrompt(new StringPrompt() {
                    @Override public String getPromptText(ConversationContext c) { return "Console?"; }

                    @Override public Prompt acceptInput(ConversationContext c, String input) {
                        got.add(input);
                        return END_OF_CONVERSATION;
                    }
                }).buildConversation(Bukkit.getConsoleSender()).begin();
                return null;
            });
            server.onGlobal(() -> {
                server.internals().runConsoleCommand("time set 1234");   // 명령으로 돌지 않고 대답이 된다
                return null;
            });
            assertEquals(List.of("time set 1234"), got);
            assertFalse(server.onGlobal(() -> Bukkit.getConsoleSender().isConversing()));
            server.onGlobal(() -> {
                server.internals().runConsoleCommand("say done");   // 대화가 끝나면 다시 명령
                return null;
            });
            assertTrue(server.consoleMessages().stream().anyMatch(t -> t.plainText().contains("done")));
        }
    }

    @Test
    void notPlayerMessageEndsAConsoleConversationAtOnce() throws Exception {
        try (FurnaceTestServer server = start()) {
            boolean conversing = server.onGlobal(() -> {
                new ConversationFactory(plugin(server)).withFirstPrompt(askName()).thatExcludesNonPlayersWithMessage("players only")
                        .buildConversation(Bukkit.getConsoleSender()).begin();
                return Bukkit.getConsoleSender().isConversing();
            });
            assertFalse(conversing);
            assertTrue(server.consoleMessages().stream().anyMatch(t -> t.plainText().equals("players only")));
        }
    }

    /** CraftBukkit ConversationTracker.beginConversation 은 줄 선 대화·이미 있는 대화에도 true(검토 R2). 맨 앞만 시작한다. */
    @Test
    void beginConversationReturnsTrueWhenQueued() {
        List<Boolean> captured = new CopyOnWriteArrayList<>();
        var tracker = new ConversationTracker(h -> captured.add(h != null));
        List<String> said = new CopyOnWriteArrayList<>();
        var who = new org.bukkit.conversations.Conversable() {
            public boolean isConversing() { return tracker.isConversing(); }
            public void acceptConversationInput(String input) { tracker.acceptConversationInput(input); }
            public boolean beginConversation(Conversation c) { return tracker.beginConversation(c); }
            public void abandonConversation(Conversation c) {
                tracker.abandonConversation(c, new ConversationAbandonedEvent(c, new org.bukkit.conversations.ManuallyAbandonedConversationCanceller()));
            }
            public void abandonConversation(Conversation c, ConversationAbandonedEvent d) { tracker.abandonConversation(c, d); }
            public void sendRawMessage(String m) { said.add(m); }
            public void sendRawMessage(java.util.UUID s, String m) { said.add(m); }
        };
        Prompt p = new StringPrompt() {
            public String getPromptText(ConversationContext c) { return "q"; }
            public Prompt acceptInput(ConversationContext c, String in) { return END_OF_CONVERSATION; }
        };
        Conversation a = new Conversation(null, who, p);
        Conversation b = new Conversation(null, who, p);
        assertTrue(tracker.beginConversation(a));
        assertTrue(tracker.beginConversation(b), "줄 선 대화");
        assertTrue(tracker.beginConversation(a), "이미 있는 대화");
        assertEquals(List.of("q"), said, "맨 앞 대화만 문구를 보인다");
        assertEquals(List.of(true), captured, "대화가 있는 동안 한 번만 가로채기를 건다");
    }
}
