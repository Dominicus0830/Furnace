// 교차region 가시성용 불변 엔티티 스냅샷. 다른 region 스레드가 락프리로 읽는다.
package org.domi.furnace.server.entity;

import java.util.UUID;

public record EntityView(int entityId, UUID uuid, String name,
                         double x, double y, double z, float yaw, float pitch, boolean onGround) {

    public static EntityView of(Player p) {
        return new EntityView(p.entityId(), p.uuid(), p.name(),
                p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.onGround());
    }
}
