// S→C 로그인 단계 연결 종료(0x00). 사유는 JSON 텍스트 컴포넌트.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.JsonText;
import org.domi.furnace.server.net.protocol.Packet;

public record LoginDisconnect(String reason) implements Packet {

    public static final int ID = 0x00;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, JsonText.textComponent(reason));
    }
}
