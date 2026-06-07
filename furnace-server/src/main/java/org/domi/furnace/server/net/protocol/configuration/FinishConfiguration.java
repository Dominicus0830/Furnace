// S→C configuration 종료 신호. 클라는 Ack로 응답하고 play로 넘어간다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record FinishConfiguration() implements Packet {

    public static final int ID = 0x03;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // 빈 body
    }
}
