// S→C 청크 캐시 반경(0x55): VarInt. 바닐라 PlayerList 가 시야 거리가 바뀔 때 보낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetChunkCacheRadius(int radius) implements Packet {

    public static final int ID = 0x55;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeVarInt(buf, radius); }
}
