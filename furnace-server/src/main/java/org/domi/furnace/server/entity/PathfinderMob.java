// 바닐라 PathfinderMob: 길을 찾는 몹 — 칸의 걷기 선호도(getWalkTargetValue)와 맞았을 때 반응.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public abstract class PathfinderMob extends Mob {

    protected PathfinderMob(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    /** getWalkTargetValue(pos): 클수록 가고 싶은 칸. 기본 0. */
    public float getWalkTargetValue(Pos pos) { return getWalkTargetValue(pos, level); }

    public float getWalkTargetValue(Pos pos, ChunkLevel level) { return 0.0F; }

    /** PathfinderMob.isPanicking: 달리는 PanicGoal 이 있나. */
    public boolean isPanicking() {
        for (var w : goalSelector.getAvailableGoals()) {
            if (w.isRunning() && w.getGoal() instanceof org.domi.furnace.server.entity.ai.goal.PanicGoal) return true;
        }
        return false;
    }
}
