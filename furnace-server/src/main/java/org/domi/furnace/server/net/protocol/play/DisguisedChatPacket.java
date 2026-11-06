// S→C disguised_chat(0x1E) — 서명 없는(시스템 출처) 채팅을 채팅 타입 장식으로 보여 준다(콘솔 /say 등).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.chat.ChatType;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.api.text.Text;

public record DisguisedChatPacket(Text message, ChatType.Bound bound) implements Packet {

    public static final int ID = 0x1E;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(message.networkNbt());
        bound.write(buf);
    }
}
