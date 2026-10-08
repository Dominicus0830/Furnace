// S→C 월드 시간(0x64): 게임 시간과 하루 시간. 바이트코드 확인: long 2개(하루 시간이 음수면 daylight cycle 정지 표시).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record SetTime(long gameTime, long dayTime) implements Packet {

    public static final int ID = 0x64;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(gameTime);
        buf.writeLong(dayTime);
    }
}
