// S→C player_chat(0x39) — 보낸이·고리 index·서명·(수신자 캐시로 압축한) 본문·서명 없는 내용·필터·채팅 타입.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.chat.ChatType;
import org.domi.furnace.server.chat.MessageSignatureCache;
import org.domi.furnace.server.chat.PlayerChatMessage;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public final class PlayerChatPacket implements Packet {

    public static final int ID = 0x39;
    private final byte[] body;

    /** 수신자의 시그니처 캐시로 본문을 압축해 바로 직렬화한다(캐시는 이후 push 로 바뀌므로). */
    public PlayerChatPacket(PlayerChatMessage m, MessageSignatureCache cache, ChatType.Bound bound) {
        ByteBuf b = io.netty.buffer.Unpooled.buffer();
        try {
            Types.writeUuid(b, m.link().sender());
            Types.writeVarInt(b, m.link().index());
            b.writeBoolean(m.signature() != null);
            if (m.signature() != null) m.signature().write(b);
            m.signedBody().writePacked(b, cache);
            b.writeBoolean(m.unsignedContent() != null);
            if (m.unsignedContent() != null) b.writeBytes(m.unsignedContent().networkNbt());
            Types.writeVarInt(b, 0);                 // FilterMask.PASS_THROUGH(텍스트 필터는 없다)
            bound.write(b);
            body = new byte[b.readableBytes()];
            b.readBytes(body);
        } finally {
            b.release();
        }
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { buf.writeBytes(body); }
}
