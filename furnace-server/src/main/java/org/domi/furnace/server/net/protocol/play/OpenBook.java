// S→C 책 열기(open_book 0x32, 1.21.1 GameProtocols 등록 순서로 확인): VarInt 손(0 주 손, 1 보조 손). 서명된 책(written_book)을 읽을 때만 보낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record OpenBook(int hand) implements Packet {

    public static final int ID = 0x32;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeVarInt(buf, hand); }
}
