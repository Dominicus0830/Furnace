// 바닐라 Portal: 엔티티를 다른 곳으로 보내는 블록(엔드 포털·지옥문).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public interface Portal {
    /** getPortalTransitionTime: 이만큼 틱 동안 포털 안에 있어야 넘어간다(기본 0 — 바로). */
    default int getPortalTransitionTime(ChunkLevel level, Entity entity) { return 0; }

    /** 플레이어의 getPortalTransitionTime(invulnerable = 능력의 무적 — 크리에이티브·관전). */
    default int getPlayerTransitionTime(ChunkLevel level, boolean invulnerable) { return 0; }

    /** 원천 쪽 계획(목적 차원이 없으면 null). 원천 레벨 스레드에서 부른다. */
    PortalTravel prepareTravel(ChunkLevel level, Traveler traveler, Pos pos);

    /** getPortalDestination: 목적 레벨을 같은 스레드에서 바로 쓸 수 있을 때(시험 레벨)의 목적지. 목적 차원 레벨이 없으면 null. */
    default DimensionTransition getPortalDestination(ChunkLevel level, Entity entity, Pos pos) {
        ChunkLevel target = null;
        PortalTravel plan = prepareTravel(level, Traveler.of(entity), pos);
        if (plan != null) target = level.levelFor(plan.targetDimension());
        return target == null ? null : plan.complete().apply(target);
    }
}
