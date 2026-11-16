// 한 상대(플레이어·콘솔)의 Bukkit 대화 줄(CraftBukkit ConversationTracker 와 같은 규칙). 맨 앞 대화만 돌고, 끝나면 다음 대화의 문구를 보여 준다.
// 대화가 하나라도 있는 동안만 Furnace 입력 가로채기(Player.chatInputHandler·Furnace.consoleInputHandler)를 걸어 둔다 — 대화가 없으면
// 채팅·콘솔 경로에 아무것도 끼우지 않는다. 대화 처리는 입력이 온 Furnace 전역 스레드(Bukkit 주 스레드)에서 바로 돈다.
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
    private final Consumer<String> handler = this::acceptConversationInput;
    private boolean capturing;

    /** @param capture Furnace 쪽 가로채기 걸기·풀기. 가로챈 입력은 전역 스레드(Bukkit 주 스레드)에서 온다. */
    public ConversationTracker(Consumer<Consumer<String>> capture) {
        this.capture = capture;
    }

    /** CraftBukkit 과 같다 — 줄을 섰거나 이미 줄에 있는 대화도 true. 맨 앞이 된 대화만 바로 시작한다. */
    public synchronized boolean beginConversation(Conversation conversation) {
        if (!queue.contains(conversation)) {
            queue.addLast(conversation);
            updateCapture();
            if (queue.peekFirst() == conversation) {
                conversation.begin();
                conversation.outputNextPrompt();
            }
        }
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
            if (p != null) p.getLogger().log(Level.WARNING,
                    String.format("Plugin %s generated an exception whilst handling conversation input", p.getDescription().getFullName()), t);
            else org.bukkit.Bukkit.getLogger().log(Level.WARNING, "Exception whilst handling conversation input", t);
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
