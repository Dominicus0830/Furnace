// 플레이어 연결별 채팅 상태(바닐라 ServerGamePacketListenerImpl 의 채팅 필드) — 채팅 세션·디코더·last seen 검증기·
// 시그니처 캐시·채팅 표시 설정·스팸 카운터.
package org.domi.furnace.server.chat;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PlayerChatState {

    public static final int VISIBILITY_FULL = 0, VISIBILITY_SYSTEM = 1, VISIBILITY_HIDDEN = 2;
    private static final int MAX_PENDING = 4096;

    private final UUID profileId;
    private final BooleanSupplier enforcesSecureChat;
    public final LastSeenMessagesValidator lastSeen = new LastSeenMessagesValidator(LastSeenMessages.LAST_SEEN_MESSAGES_MAX_LENGTH);
    private final MessageSignatureCache signatureCache = MessageSignatureCache.createDefault();
    private volatile RemoteChatSession session;
    private volatile SignedMessageChain.Decoder decoder;
    private volatile int visibility = VISIBILITY_FULL;
    // 바닐라 chatSpamThrottler: 메시지마다 +20, 틱마다 -1, 200 초과면(OP 제외) 킥.
    private int spamTicks;
    private long spamUpdatedNanos = System.nanoTime();

    public PlayerChatState(UUID profileId, BooleanSupplier enforcesSecureChat) {
        this.profileId = profileId;
        this.enforcesSecureChat = enforcesSecureChat;
        this.decoder = SignedMessageChain.Decoder.unsigned(profileId, enforcesSecureChat);
    }

    public RemoteChatSession session() { return session; }

    public SignedMessageChain.Decoder decoder() { return decoder; }

    /** 바닐라 resetPlayerChatState. */
    public void resetSession(RemoteChatSession s) {
        this.session = s;
        this.decoder = s.createMessageDecoder(profileId);
    }

    public int visibility() { return visibility; }

    public void visibility(int v) { this.visibility = v; }

    public boolean acceptsChatMessages() { return visibility == VISIBILITY_FULL; }

    public boolean acceptsSystemMessages(boolean overlay) { return visibility != VISIBILITY_HIDDEN || overlay; }

    /** 수신자 캐시로 압축한 뒤 서명을 기억한다(바닐라 sendPlayerChatMessage + addPendingMessage). 대기 서명이 너무 많으면 false. */
    public synchronized boolean packAndTrack(PlayerChatMessage m, java.util.function.Function<MessageSignatureCache, Object> pack) {
        pack.apply(signatureCache);
        MessageSignature sig = m.signature();
        if (sig == null) return true;
        signatureCache.push(m.signedBody(), sig);
        lastSeen.addPending(sig);
        return lastSeen.trackedMessagesCount() <= MAX_PENDING;
    }

    /** 스팸 카운터를 올리고, 기준(200)을 넘었는지 돌려준다. */
    public synchronized boolean incrementSpamAndCheck() {
        long now = System.nanoTime();
        long ticks = (now - spamUpdatedNanos) / 50_000_000L;
        if (ticks > 0) {
            spamTicks = (int) Math.max(0, spamTicks - ticks);
            spamUpdatedNanos += ticks * 50_000_000L;
        }
        spamTicks += 20;
        return spamTicks > 200;
    }
}
