// S→C 리소스 팩 빼기(구성 0x08 / play 0x45): Optional<UUID>(없으면 서버가 보낸 팩 전부).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

/** @param packId 뺄 팩(null 이면 전부)  @param inPlay play 단계(0x45)면 true, 구성 단계(0x08)면 false */
@VanillaPort("net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket")
public record ResourcePackPop(UUID packId, boolean inPlay) implements Packet {

    public static final int ID_CONFIG = PacketIds.CONFIGURATION_CLIENTBOUND_RESOURCE_PACK_POP;
    public static final int ID_PLAY = PacketIds.PLAY_CLIENTBOUND_RESOURCE_PACK_POP;

    @Override
    public int id() { return inPlay ? ID_PLAY : ID_CONFIG; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBoolean(packId != null);
        if (packId != null) Types.writeUuid(buf, packId);
    }
}
