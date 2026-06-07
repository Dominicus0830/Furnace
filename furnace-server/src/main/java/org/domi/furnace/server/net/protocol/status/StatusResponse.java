// S→C 서버 상태 JSON(버전/플레이어수/MOTD) 응답 패킷
package org.domi.furnace.server.net.protocol.status;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record StatusResponse(String json) implements Packet {

    public static final int ID = 0x00;

    /** 1.21.1(767) 상태 JSON 빌드. */
    public static StatusResponse of(String motd) {
        String safe = motd.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"version\":{\"name\":\"1.21.1\",\"protocol\":767},"
                + "\"players\":{\"max\":20,\"online\":0,\"sample\":[]},"
                + "\"description\":{\"text\":\"" + safe + "\"}}";
        return new StatusResponse(json);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, json);
    }
}
