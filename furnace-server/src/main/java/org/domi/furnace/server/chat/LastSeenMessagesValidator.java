// 바닐라 LastSeenMessagesValidator(20) — 서버가 보낸 서명 메시지와 클라이언트의 확인(창 이동·비트셋)을 맞춰 본다.
package org.domi.furnace.server.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LastSeenMessagesValidator {

    private record Entry(MessageSignature signature, boolean pending) {
        Entry acknowledge() { return pending ? new Entry(signature, false) : this; }
    }

    private final int lastSeenCount;
    private final List<Entry> trackedMessages = new ArrayList<>();
    private MessageSignature lastPendingMessage;

    public LastSeenMessagesValidator(int lastSeenCount) {
        this.lastSeenCount = lastSeenCount;
        for (int i = 0; i < lastSeenCount; i++) trackedMessages.add(null);
    }

    public synchronized void addPending(MessageSignature sig) {
        if (!sig.equals(lastPendingMessage)) {
            trackedMessages.add(new Entry(sig, true));
            lastPendingMessage = sig;
        }
    }

    public synchronized int trackedMessagesCount() { return trackedMessages.size(); }

    public synchronized boolean applyOffset(int offset) {
        int max = trackedMessages.size() - lastSeenCount;
        if (offset < 0 || offset > max) return false;
        trackedMessages.subList(0, offset).clear();
        return true;
    }

    public synchronized Optional<LastSeenMessages> applyUpdate(LastSeenMessages.Update update) {
        if (!applyOffset(update.offset())) return Optional.empty();
        if (update.acknowledged().length() > lastSeenCount) return Optional.empty();
        List<MessageSignature> seen = new ArrayList<>(update.acknowledged().cardinality());
        for (int i = 0; i < lastSeenCount; i++) {
            boolean ack = update.acknowledged().get(i);
            Entry e = trackedMessages.get(i);
            if (ack) {
                if (e == null) return Optional.empty();
                trackedMessages.set(i, e.acknowledge());
                seen.add(e.signature());
            } else {
                if (e != null && !e.pending()) return Optional.empty();
                trackedMessages.set(i, null);
            }
        }
        return Optional.of(new LastSeenMessages(List.copyOf(seen)));
    }
}
