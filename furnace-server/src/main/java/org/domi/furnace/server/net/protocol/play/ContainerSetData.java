// 컨테이너 데이터 하나 갱신(clientbound container_set_data 0x14): 창 id, 데이터 번호, 값 — 화로 진행도·석재 절단기 고른 레시피 등.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record ContainerSetData(int containerId, int id, int value) implements Packet {

    public static final int ID = 0x14;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(containerId);
        buf.writeShort(id);
        buf.writeShort(value);
    }
}
