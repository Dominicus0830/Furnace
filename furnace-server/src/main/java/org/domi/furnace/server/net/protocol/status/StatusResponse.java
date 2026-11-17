// S→C 서버 상태 JSON(버전/플레이어수/MOTD) 응답 패킷
package org.domi.furnace.server.net.protocol.status;

import org.domi.furnace.api.GameVersion;
import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.status.ClientboundStatusResponsePacket")
public record StatusResponse(String json) implements Packet {

    public static final int ID = PacketIds.STATUS_CLIENTBOUND_STATUS_RESPONSE;

    /** 1.21.1(767) 상태 JSON 빌드. */
    public static StatusResponse of(String motd) {
        String json = "{\"version\":{\"name\":\"" + GameVersion.MINECRAFT + "\",\"protocol\":" + GameVersion.PROTOCOL + "},"
                + "\"players\":{\"max\":20,\"online\":0,\"sample\":[]},"
                + "\"description\":" + org.domi.furnace.server.net.protocol.JsonText.textComponent(motd) + "}";
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
