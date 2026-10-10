// 텍스트 하나만 담는 S→C 패킷 — 액션바(0x4C)·제목(0x65)·부제(0x63). 텍스트가 품은 NBT 인코딩을 그대로 쓴다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.Packet;

public record TextOnlyPacket(int id, Text text) implements Packet {

    public static final int ACTION_BAR = 0x4C, TITLE = 0x65, SUBTITLE = 0x63;

    @Override
    public void write(ByteBuf buf) { buf.writeBytes(text.networkNbt()); }
}
