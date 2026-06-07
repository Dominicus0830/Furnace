// S→C play 단계 연결 종료. 사유 텍스트를 NBT 텍스트 컴포넌트로 보낸다(1.20.3+).
// M1은 NBT 미구현이라 이 패킷을 보내지 않고 ctx.close()로 끊는다. 클래스는 ID/자리만 잡는다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record Disconnect() implements Packet {

    public static final int ID = 0x1D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // 1.20.3+ play Disconnect는 NBT 텍스트 컴포넌트를 요구한다.
        // M1은 NBT 미구현이라 이 패킷을 보내지 않고 ctx.close()로 끊는다.
        // (Task 11 이후 NBT 도입 시 사유 텍스트를 채운다.)
        throw new UnsupportedOperationException("M1: NBT 미구현 — ctx.close() 사용");
    }
}
