// 한 상대(플레이어·콘솔)의 Bukkit 대화 줄(CraftBukkit ConversationTracker 와 같은 규칙). 맨 앞 대화만 돌고, 끝나면 다음 대화의 문구를 보여 준다.
// 대화가 하나라도 있는 동안만 Furnace 입력 가로채기(Player.chatInputHandler·Furnace.consoleInputHandler)를 걸어 둔다 — 대화가 없으면
// 채팅·콘솔 경로에 아무것도 끼우지 않는다. 대화 처리는 Bukkit 주 스레드(Furnace 전역 스레드)에서 돈다.
package org.domi.furnace.compat.paper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ManuallyAbandonedConversationCanceller;

public final class ConversationTracker {

    private Deque<Conversation> queue = new ArrayDeque<>();
    /** 입력 가로채기를 걸고(처리기)·푼다(null). */
    private final Consumer<Consumer<String>> capture;
    private final Consumer<String> handler;
    private boolean capturing;

    /**
     * @param capture   Furnace 쪽 가로채기 걸기·풀기
     * @param onGlobal  가로챈 입력이 전역 스레드가 아닌 곳(채팅 연결 스레드)에서 오면 참 — 전역 스레드로 넘겨 처리한다(CraftBukkit processQueue)
     */
    public ConversationTracker(Consumer<Consumer<String>> capture, boolean onGlobal) {
        this.capture = capture;
        this.handler = onGlobal
                ? input -> org.domi.furnace.api.Furnace.server().scheduler().global().execute(() -> acceptConversationInput(input))
                : this::acceptConversationInput;
    }

    public synchronized boolean beginConversation(Conversation conversation) {
        if (queue.contains(conversation)) return false;
        queue.addLast(conversation);
        updateCapture();
        if (queue.peekFirst() != conversation) return false;   // 앞 대화가 끝나면 시작한다
        conversation.begin();
        conversation.outputNextPrompt();
        return true;
    }

    public synchronized void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        if (queue.isEmpty()) return;
        if (queue.peekFirst() == conversation) conversation.abandon(details);
        queue.remove(conversation);
        updateCapture();
        Conversation next = queue.peekFirst();
        if (next != null) next.outputNextPrompt();
    }

    /** 퇴장 등 — 줄의 대화를 모두 손으로 끝낸 것으로 끝낸다. 하나가 던져도 나머지는 끝낸다. */
    public void abandonAllConversations() {
        List<Conversation> old;
        synchronized (this) {
            old = List.copyOf(queue);
            queue = new ArrayDeque<>();
            updateCapture();
        }
        for (Conversation c : old) {
            try {
                c.abandon(new ConversationAbandonedEvent(c, new ManuallyAbandonedConversationCanceller()));
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().log(Level.SEVERE, "Unexpected exception while abandoning a conversation", t);
            }
        }
    }

    public synchronized void acceptConversationInput(String input) {
        Conversation c = queue.peekFirst();
        if (c == null) return;
        try {
            c.acceptInput(input);
        } catch (Throwable t) {
            var p = c.getContext().getPlugin();
            (p != null ? p.getLogger() : org.bukkit.Bukkit.getLogger()).log(Level.WARNING, "Error while handling conversation input", t);
        }
    }

    public synchronized boolean isConversing() { return !queue.isEmpty(); }

    /** 맨 앞 대화가 모달인가 — 그동안 플러그인 sendMessage 는 상대에게 가지 않는다. */
    public synchronized boolean isConversingModaly() {
        Conversation c = queue.peekFirst();
        return c != null && c.isModal();
    }

    private void updateCapture() {
        boolean want = !queue.isEmpty();
        if (want == capturing) return;
        capturing = want;
        capture.accept(want ? handler : null);
    }
}
