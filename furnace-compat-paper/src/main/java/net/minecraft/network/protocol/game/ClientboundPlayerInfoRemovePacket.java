package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import java.util.List;
import java.util.UUID;

/** 탭 목록에서 빼기(Mojang 이름, H7c). */
public record ClientboundPlayerInfoRemovePacket(List<UUID> profileIds) implements Packet<ClientGamePacketListener>, NmsPacket {

    public ClientboundPlayerInfoRemovePacket { profileIds = List.copyOf(profileIds); }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoRemovePacket(profileIds); }
}
