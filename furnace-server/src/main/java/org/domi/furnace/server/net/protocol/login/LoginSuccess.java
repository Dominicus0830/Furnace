// S→C 로그인 성공(Game Profile). 확정된 UUID·이름·프로필 속성과 strict error handling(바닐라 true).
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.login.ClientboundGameProfilePacket")
public record LoginSuccess(UUID uuid, String name, java.util.List<org.domi.furnace.server.players.GameProfile.Property> properties,
                           boolean strictErrorHandling) implements Packet {

    public LoginSuccess(UUID uuid, String name) {
        this(uuid, name, java.util.List.of(), true);
    }

    public static final int ID = PacketIds.LOGIN_CLIENTBOUND_GAME_PROFILE;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeUuid(buf, uuid);
        Types.writeString(buf, name);
        Types.writeVarInt(buf, properties.size());
        for (var p : properties) {
            Types.writeString(buf, p.name());
            Types.writeString(buf, p.value());
            buf.writeBoolean(p.signature() != null);
            if (p.signature() != null) Types.writeString(buf, p.signature());
        }
        buf.writeBoolean(strictErrorHandling);
    }
}
