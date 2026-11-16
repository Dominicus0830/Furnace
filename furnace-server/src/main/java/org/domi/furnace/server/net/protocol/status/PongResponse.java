// S→C 핑 응답. 클라가 보낸 페이로드(Long)를 그대로 되돌려 지연 측정에 쓰게 한다.
package org.domi.furnace.server.net.protocol.status;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.ping.ClientboundPongResponsePacket")
public record PongResponse(long payload) implements Packet {

    public static final int ID = PacketIds.STATUS_CLIENTBOUND_PONG_RESPONSE;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(payload);
    }
}
