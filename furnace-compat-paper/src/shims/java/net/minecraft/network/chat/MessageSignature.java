package net.minecraft.network.chat;

/**
 * 채팅 서명 값 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 256바이트 서명, 그리고 서버가 기억한 서명을 번호로 가리키는 묶음(Packed).
 * 받은 채팅 패킷(ServerboundChatPacket)·명령 서명에 들어 있다. 패킷 라이브러리가 필드를 읽고 바꾼다.
 */
record MessageSignature {

    /** 바닐라: 서명 길이. */
    public static final int BYTES = 256;

    public MessageSignature {
        if (bytes.length != BYTES) throw new IllegalStateException("Invalid message signature size");   // 바닐라 Preconditions.checkState 와 같은 예외
    }

    /** 바닐라: 기억한 서명이면 번호(id), 아니면 서명 그대로(id = -1). */
    public record Packed(int id, MessageSignature fullSignature) {

        public static final int FULL_SIGNATURE = -1;

        public Packed(MessageSignature signature) { this(FULL_SIGNATURE, signature); }

        public Packed(int id) { this(id, null); }
    }
}
