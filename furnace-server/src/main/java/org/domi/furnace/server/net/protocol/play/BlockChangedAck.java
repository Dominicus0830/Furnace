// 블록 변경 예측 확인(clientbound). 클라의 예측 sequence를 회신해 예측을 reconcile.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockChangedAck(int sequence) implements Packet {

    public static final int ID = 0x05; // datagen: play.clientbound.block_changed_ack

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, sequence);
    }
}
