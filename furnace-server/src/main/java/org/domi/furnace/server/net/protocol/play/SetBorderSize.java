// S→C 월드 보더 크기 즉시 설정(0x4F): double (바닐라는 getLerpTarget 을 싣는다).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record SetBorderSize(double size) implements Packet {

    public static final int ID = 0x4F;

    public static SetBorderSize of(WorldBorder border) {
        return new SetBorderSize(border.getLerpTarget());
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(size);
    }
}
