// S→C play 단계 pong_response(0x36) — 클라이언트 ping_request(0x21)의 값을 그대로 되돌린다(F3 핑 그래프).
// 상태(서버 목록) 단계의 PongResponse(0x01)와는 다른 패킷이다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayPongResponse(long time) implements Packet {

    public static final int ID = 0x36;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(time);
    }
}
