package net.minecraft.network.chat;

import java.util.BitSet;
import java.util.List;

/**
 * 클라이언트가 본 최근 채팅 서명 목록 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양.
 * 받은 채팅 패킷에는 {@link Update}(목록 위치 offset 과 확인한 칸 비트 acknowledged — 20칸)가 들어 있다.
 */
record LastSeenMessages {

    /** 바닐라: 목록 최대 길이. */
    public static final int LAST_SEEN_MESSAGES_MAX_LENGTH = 20;

    public static final LastSeenMessages EMPTY = new LastSeenMessages(List.of());

    /** 바닐라: 번호로 줄인 목록. */
    public record Packed(List<MessageSignature.Packed> entries) {
        public static final Packed EMPTY = new Packed(List.of());
    }

    /** 바닐라: 클라이언트가 보내는 확인(offset, 20칸 비트). */
    public record Update(int offset, BitSet acknowledged) {}
}
