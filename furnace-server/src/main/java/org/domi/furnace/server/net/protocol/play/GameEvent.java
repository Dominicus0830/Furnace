// S→C 게임 이벤트. event 13 = "청크 로딩 대기 시작"(join 시 클라가 로딩 화면을 정상 종료하게 함).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record GameEvent(int event, float value) implements Packet {

    public static final int ID = 0x22;

    /** event 13: 클라에 "이제부터 청크를 받을 때까지 대기하라"고 알림. */
    public static final int START_WAITING_FOR_CHUNKS = 13;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(event);
        buf.writeFloat(value);
    }
}
