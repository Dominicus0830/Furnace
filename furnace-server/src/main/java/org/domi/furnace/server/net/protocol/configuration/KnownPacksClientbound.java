// S→C 서버가 아는 데이터팩 목록. minecraft:core/1.21.1 하나를 선언해 무데이터 레지스트리 전송을 가능케 함.
package org.domi.furnace.server.net.protocol.configuration;

import org.domi.furnace.api.GameVersion;
import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks")
public record KnownPacksClientbound() implements Packet {

    public static final int ID = PacketIds.CONFIGURATION_CLIENTBOUND_SELECT_KNOWN_PACKS;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, 1);                 // pack count
        Types.writeString(buf, "minecraft");       // namespace
        Types.writeString(buf, "core");            // id
        Types.writeString(buf, GameVersion.MINECRAFT);          // version
    }
}
