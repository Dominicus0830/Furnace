// 바닐라 MessageSignatureCache(128) — 수신자별로 최근 서명을 기억해 last seen 목록을 id 로 줄여 보낸다.
package org.domi.furnace.server.chat;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MessageSignatureCache {

    private final MessageSignature[] entries;

    public MessageSignatureCache(int size) { this.entries = new MessageSignature[size]; }

    public static MessageSignatureCache createDefault() { return new MessageSignatureCache(128); }

    public int pack(MessageSignature sig) {
        for (int i = 0; i < entries.length; i++) if (sig.equals(entries[i])) return i;
        return -1;
    }

    public MessageSignature unpack(int id) { return entries[id]; }

    public void push(SignedMessageBody body, MessageSignature sig) {
        List<MessageSignature> seen = body.lastSeen().entries();
        ArrayDeque<MessageSignature> deque = new ArrayDeque<>(seen.size() + 1);
        deque.addAll(seen);
        if (sig != null) deque.add(sig);
        push(deque);
    }

    private void push(ArrayDeque<MessageSignature> deque) {
        Set<MessageSignature> set = new HashSet<>(deque);
        for (int i = 0; !deque.isEmpty() && i < entries.length; i++) {
            MessageSignature old = entries[i];
            entries[i] = deque.removeLast();
            if (old != null && !set.contains(old)) deque.addFirst(old);
        }
    }
}
