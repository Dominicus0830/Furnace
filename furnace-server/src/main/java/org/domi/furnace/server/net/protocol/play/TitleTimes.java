// S→C 제목 시간(0x66): 나타남·유지·사라짐 틱.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record TitleTimes(int fadeIn, int stay, int fadeOut) implements Packet {

    public static final int ID = 0x66;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(fadeIn);
        buf.writeInt(stay);
        buf.writeInt(fadeOut);
    }
}
