// 바닐라 PathfinderMob: 길을 찾는 몹 — 칸의 걷기 선호도(getWalkTargetValue)와 맞았을 때 반응.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public abstract class PathfinderMob extends Mob {

    /** Paper: MoveToBlockGoal 이 향하는 칸(활성 범위 면역 검사 — 벌). 결과에는 쓰지 않는다. */
    org.domi.furnace.server.block.Pos movingTarget;

    public org.domi.furnace.server.block.Pos movingTarget() { return movingTarget; }

    public void setMovingTarget(org.domi.furnace.server.block.Pos p) { movingTarget = p; }

    protected PathfinderMob(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    /** getWalkTargetValue(pos): 클수록 가고 싶은 칸. 기본 0. */
    public float getWalkTargetValue(Pos pos) { return getWalkTargetValue(pos, level); }

    public float getWalkTargetValue(Pos pos, ChunkLevel level) { return 0.0F; }

    /** PathfinderMob.checkSpawnRules: 걷기 선호도가 음수가 아닌 칸. */
    @Override
    public boolean checkSpawnRules(ChunkLevel level, org.domi.furnace.server.entity.spawn.SpawnReason reason) { return getWalkTargetValue(blockPosition(), level) >= 0.0F; }

    /** PathfinderMob.isPanicking: 달리는 PanicGoal 이 있나. */
    public boolean isPanicking() {
        if (brain != null && brain.checkMemory(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.IS_PANICKING, org.domi.furnace.server.entity.ai.brain.MemoryStatus.REGISTERED)) {
            return brain.getMemory(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.IS_PANICKING).isPresent();
        }
        for (var w : goalSelector.getAvailableGoals()) {
            if (w.isRunning() && w.getGoal() instanceof org.domi.furnace.server.entity.ai.goal.PanicGoal) return true;
        }
        return false;
    }

    /** PathfinderMob.checkLeashDistance(btw:56): 끈에 매이면 붙잡은 쪽 둘레 5칸으로 제한한다. */
    @Override
    protected boolean checkLeashDistance(Rider holder, float dist) {
        restrictTo(new Pos((int) Math.floor(holder.x()), (int) Math.floor(holder.y()), (int) Math.floor(holder.z())), 5);
        return true;
    }

    protected boolean shouldStayCloseToLeashHolder() { return true; }

    protected double followLeashSpeed() { return 1.0; }

    /** closeRangeLeashBehaviour(btw:44): 가까울 때 붙잡은 쪽 쪽으로 걷는다(패닉 중이면 제외). */
    @Override
    protected void closeRangeLeashBehaviour(Rider holder) {
        super.closeRangeLeashBehaviour(holder);
        if (shouldStayCloseToLeashHolder() && !isPanicking()) {
            goalSelector.enableControlFlag(org.domi.furnace.server.entity.ai.Goal.Flag.MOVE);
            float f = Mob.distanceTo(this, holder);
            double sc = Math.max(f - 2.0F, 0.0F);
            double dx = holder.x() - x(), dy = holder.y() - y(), dz = holder.z() - z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len >= 1.0E-5) { dx = dx / len * sc; dy = dy / len * sc; dz = dz / len * sc; } else { dx = dy = dz = 0.0; }
            getNavigation().moveTo(x() + dx, y() + dy, z() + dz, followLeashSpeed());
        }
    }
}
