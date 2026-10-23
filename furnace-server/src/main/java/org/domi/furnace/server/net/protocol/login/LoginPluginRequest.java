// S→C 로그인 커스텀 질의(0x04) — messageId, 채널, 데이터(나머지 바이트). Velocity 포워딩 요청에 쓴다.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record LoginPluginRequest(int messageId, String channel, byte[] data) implements Packet {

    public static final int ID = 0x04;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, messageId);
        Types.writeString(buf, channel);
        buf.writeBytes(data);
    }
}
