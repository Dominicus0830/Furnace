// 바닐라 Portal: 엔티티를 다른 곳으로 보내는 블록(엔드 포털·지옥문).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public interface Portal {
    /** getPortalTransitionTime: 이만큼 틱 동안 포털 안에 있어야 넘어간다(기본 0 — 바로). */
    default int getPortalTransitionTime(ChunkLevel level, Entity entity) { return 0; }

    /** 플레이어의 getPortalTransitionTime(invulnerable = 능력의 무적 — 크리에이티브·관전). */
    default int getPlayerTransitionTime(ChunkLevel level, boolean invulnerable) { return 0; }

    /** 같은 레벨 안으로 보내는 포털(엔드 관문)인가 — 그러면 서버는 prepareTravel 대신 travelWithinLevel 을 쓴다. */
    default boolean withinLevel() { return false; }

    /**
     * 같은 레벨 안으로 보내는 포털의 서버판: 원천 레벨 스레드에서 부르고, 목적지를 정하면 arrive(레벨, 목적지)를 목적지 칸 region
     * 스레드에서 부른다. 목적지가 없으면 arrive(null, null)(어느 스레드든). 목적 청크를 비동기로 올리므로 바로 오지 않을 수 있다.
     */
    default void travelWithinLevel(ChunkLevel level, Pos pos, Traveler traveler,
                                   java.util.function.BiConsumer<ChunkLevel, DimensionTransition> arrive) { arrive.accept(null, null); }

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
