// S→C 카메라 대상(0x52): VarInt 엔티티 id. 관전자의 시점을 그 엔티티로 옮긴다(자기 id 면 원래대로).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetCamera(int entityId) implements Packet {

    public static final int ID = 0x52;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
    }
}
