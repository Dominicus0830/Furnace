// S→C 로그인 성공. 확정된 UUID와 이름, 빈 속성 목록을 보낸다.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record LoginSuccess(UUID uuid, String name) implements Packet {

    public static final int ID = 0x02;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeUuid(buf, uuid);
        Types.writeString(buf, name);
        Types.writeVarInt(buf, 0);  // property count
        buf.writeBoolean(false);    // strict error handling
    }
}
