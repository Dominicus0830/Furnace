// 엔티티 생성(add_entity). 서버에 없는 "가상 엔티티"도 만들 수 있다 — 번호는 PacketService.nextEntityId() 로 받아 서버 엔티티와 겹치지 않게 한다.
// entityType 은 "minecraft:zombie" 같은 레지스트리 이름, 각도는 도(degree), 속도는 블록/틱(축마다 ±3.9 로 잘린다).
// data 는 종류별 추가 값(낙하 블록의 상태, 투사체 주인 번호 등 — 바닐라 그대로).
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

    /** 멈춰 있는 엔티티(data 0, 속도 0, 머리는 몸과 같은 방향). */
    public static ClientboundAddEntityPacket of(int entityId, UUID uuid, String entityType, double x, double y, double z, float yaw, float pitch) {
        return new ClientboundAddEntityPacket(entityId, uuid, entityType, x, y, z, pitch, yaw, yaw, 0, 0, 0, 0);
    }

    @Override
    public PacketType<ClientboundAddEntityPacket> type() { return PacketTypes.Clientbound.ADD_ENTITY; }
}
