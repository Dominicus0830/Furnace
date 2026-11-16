// S→C 센터 청크 설정. 이후 청크 패킷들의 기준 좌표.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket")
public record SetCenterChunk(int chunkX, int chunkZ) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SET_CHUNK_CACHE_CENTER;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, chunkX);
        Types.writeVarInt(buf, chunkZ);
    }
}
