package org.domi.furnace.compat.paper;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;

/** 서버 콘솔. 모든 권한을 가진다(Bukkit 과 같다). */
public final class CompatConsole implements ConsoleCommandSender {

    public static final CompatConsole INSTANCE = new CompatConsole();

    private CompatConsole() {}

    @Override
    public void sendMessage(String message) { sendRawMessage(message); }

    @Override
    public void sendRawMessage(String message) {
        if (message != null) Compat.furnace().console().sendMessage(Compat.text(message));
    }

    @Override
    public void sendRawMessage(java.util.UUID sender, String message) { sendRawMessage(message); }

    // ---- Conversable: 대화가 있는 동안 콘솔 입력 줄이 명령 대신 대화로 간다(CraftServer.dispatchServerCommand). 콘솔 입력은 이미 전역 스레드다.

    private volatile ConversationTracker conversations;
    /** 대화 줄을 만든 서버 — 서버를 다시 띄운 시험에서 옛 줄을 쓰지 않게. */
    private volatile org.domi.furnace.api.Furnace conversationServer;

    private ConversationTracker conversations() {
        org.domi.furnace.api.Furnace f = Compat.furnace();
        synchronized (this) {
            if (conversations == null || conversationServer != f) {
                conversations = new ConversationTracker(f::consoleInputHandler);
                conversationServer = f;
            }
            return conversations;
        }
    }

    @Override
    public boolean isConversing() {
        var t = conversations;
        return t != null && conversationServer == Compat.furnace() && t.isConversing();
    }

    @Override
    public void acceptConversationInput(String input) { conversations().acceptConversationInput(input); }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) { return conversations().beginConversation(conversation); }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        abandonConversation(conversation, new org.bukkit.conversations.ConversationAbandonedEvent(conversation,
                new org.bukkit.conversations.ManuallyAbandonedConversationCanceller()));
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation, org.bukkit.conversations.ConversationAbandonedEvent details) {
        conversations().abandonConversation(conversation, details);
    }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    @Override
    public Server getServer() { return org.bukkit.Bukkit.getServer(); }

    @Override
    public String getName() { return "CONSOLE"; }

    @Override
    public boolean isOp() { return true; }

    @Override
    public void setOp(boolean value) { throw new UnsupportedOperationException("콘솔의 OP 는 바꿀 수 없다"); }

    @Override
    public boolean isPermissionSet(String name) { return Compat.furnace().console().isPermissionSet(name); }

    @Override
    public boolean hasPermission(String name) { return Compat.furnace().console().hasPermission(name); }
}
