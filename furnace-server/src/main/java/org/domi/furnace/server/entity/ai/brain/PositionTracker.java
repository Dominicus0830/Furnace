// 바닐라 PositionTracker: 바라보거나 걸어갈 대상의 위치 제공자(엔티티 따라가기 / 고정 블록 위치).
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public interface PositionTracker {
    /** 정확한 위치 {x, y, z}. */
    double[] currentPosition();

    Pos currentBlockPosition();

    /** looker 가 그 대상을 볼 수 있는가. */
    boolean isVisibleBy(Mob looker);
}
