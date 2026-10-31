// S→C 끈 연결(set_entity_link 0x59): int 매인 몹 번호, int 붙잡은 쪽 번호(풀리면 0). 바닐라 ClientboundSetEntityLinkPacket(writeInt 두 개, VarInt 아님).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record SetEntityLink(int sourceId, int destId) implements Packet {

    public static final int ID = 0x59;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(sourceId);
        buf.writeInt(destId);
    }
}
