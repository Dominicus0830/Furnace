// 엔티티 생성(add_entity). 서버에 없는 "가상 엔티티"도 만들 수 있다.
// 엔티티 번호는 PacketService.nextEntityId() 로 받는다. 서버 엔티티와 겹치지 않는다.
//
// 필드는 다음과 같다.
//   - entityType 은 "minecraft:zombie" 같은 레지스트리 이름이다.
//   - 각도는 도(degree)다.
//   - 속도는 블록/틱이다. 축마다 ±3.9 로 잘린다.
//   - data 는 종류별 추가 값이다(낙하 블록의 상태, 투사체 주인 번호 등). 바닐라와 같다.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import java.util.UUID;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundAddEntityPacket(int entityId, UUID uuid, String entityType, double x, double y, double z,
                                         float pitch, float yaw, float headYaw, int data,
                                         double velocityX, double velocityY, double velocityZ) implements ClientboundPacket {

    public ClientboundAddEntityPacket {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(entityType, "entityType");
        if (entityType.indexOf(':') < 0) entityType = "minecraft:" + entityType;
    }

    /** 멈춰 있는 엔티티를 만든다. data 와 속도는 0이고, 머리는 몸과 같은 방향을 본다. */
    public static ClientboundAddEntityPacket of(int entityId, UUID uuid, String entityType, double x, double y, double z, float yaw, float pitch) {
        return new ClientboundAddEntityPacket(entityId, uuid, entityType, x, y, z, pitch, yaw, yaw, 0, 0, 0, 0);
    }

    @Override
    public PacketType<ClientboundAddEntityPacket> type() { return PacketTypes.Clientbound.ADD_ENTITY; }
}
