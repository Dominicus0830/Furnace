// 컨테이너 강제 닫기 알림(clientbound). 다른 플레이어가 열어둔 체스트를 파괴했을 때만 전송(§3.5).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ContainerClose(int containerId) implements Packet {

    public static final int ID = 0x12;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, containerId);
    }
}
