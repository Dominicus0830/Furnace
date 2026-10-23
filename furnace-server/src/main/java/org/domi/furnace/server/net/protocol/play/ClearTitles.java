// S→C 제목 지우기(0x0F) — reset 이면 시간 설정도 기본값으로.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record ClearTitles(boolean reset) implements Packet {

    public static final int ID = 0x0F;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { buf.writeBoolean(reset); }
}
