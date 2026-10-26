// 바닐라 Portal: 엔티티를 다른 곳으로 보내는 블록(엔드 포털 등). 지옥문은 아직 이동이 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public interface Portal {
    /** getPortalTransitionTime: 이만큼 틱 동안 포털 안에 있어야 넘어간다(기본 0 — 바로). */
    default int getPortalTransitionTime(ChunkLevel level, Entity entity) { return 0; }

    /** getPortalDestination: 목적지(갈 수 없으면 null). */
    DimensionTransition getPortalDestination(ChunkLevel level, Entity entity, Pos pos);
}
