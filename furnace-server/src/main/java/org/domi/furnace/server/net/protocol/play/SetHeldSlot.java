// S→C 선택된 핫바 슬롯(0~8) 설정. 접속 시 서버가 아는 선택 슬롯을 클라이언트에 맞춘다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record SetHeldSlot(int slot) implements Packet {

    public static final int ID = 0x53;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(slot);
    }
}
