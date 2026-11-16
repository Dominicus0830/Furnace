// 컨테이너 강제 닫기 알림(clientbound). 다른 플레이어가 열어둔 체스트를 파괴했을 때만 전송(§3.5).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundContainerClosePacket")
public record ContainerClose(int containerId) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_CONTAINER_CLOSE;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(containerId);   // 바닐라 1.21.1: unsigned byte (바이트코드 확인)
    }
}
