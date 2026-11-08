package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/**
 * 엔티티 지우기(Mojang 이름, H7c). 바닐라의 {@code IntList} 생성자·{@code getEntityIds()} 는 fastutil 이 이 모듈에 없어 두지 않았다
 * (부르면 그 호출 위치에서 UnsupportedNmsCall).
 */
public class ClientboundRemoveEntitiesPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final int[] entityIds;

    public ClientboundRemoveEntitiesPacket(int... entityIds) { this.entityIds = entityIds.clone(); }

    @Override
    public ClientboundPacket toFurnace() {
        return new org.domi.furnace.api.packet.clientbound.ClientboundRemoveEntitiesPacket(java.util.Arrays.stream(entityIds).boxed().toList());
    }
}
