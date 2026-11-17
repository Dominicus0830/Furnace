// S→C 구성 단계 연결 유지 핑(0x04): long. 클라는 구성 keep_alive(serverbound 0x04)로 되돌린다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.common.ClientboundKeepAlivePacket")
public record ConfigKeepAlive(long keepAliveId) implements Packet {

    public static final int ID = PacketIds.CONFIGURATION_CLIENTBOUND_KEEP_ALIVE;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { buf.writeLong(keepAliveId); }
}
