package net.minecraft.commands.arguments;

import java.util.List;
import net.minecraft.network.chat.MessageSignature;

/**
 * 명령 인자 서명 묶음 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 서명된 인자(예: /msg 의 글)마다 인자 이름과 256바이트 서명.
 * 받은 서명 명령 패킷(ServerboundChatCommandSignedPacket)에 들어 있다.
 */
record ArgumentSignatures {

    public static final ArgumentSignatures EMPTY = new ArgumentSignatures(List.of());

    /** 바닐라: 이름이 같은 인자의 서명, 없으면 null. */
    public MessageSignature get(String name) {
        for (Entry e : entries) {
            if (e.name().equals(name)) return e.signature();
        }
        return null;
    }

    /** 바닐라: 인자 하나(이름 16자까지)와 그 서명. */
    public record Entry(String name, MessageSignature signature) {}
}
