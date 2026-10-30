// 바닐라 StrollThroughVillageGoal: 밤에 마을 근처(6섹션)면 마을 쪽 섹션 거리가 줄어드는 곳(15×7)으로 걷고, 닿으면 다시 그 방향으로 이어 걷는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public class StrollThroughVillageGoal extends Goal {
    private static final int DISTANCE_THRESHOLD = 10;
    protected final PathfinderMob mob;
    private final int interval;
    private Pos target;

    public StrollThroughVillageGoal(PathfinderMob mob, int interval) {
        this.mob = mob;
        this.interval = reducedTickDelay(interval);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isVehicle()) return false;
        ChunkLevel level = mob.level();
        if (level.isDay()) return false;
        if (mob.random().nextInt(interval) != 0) return false;
        Pos pos = mob.blockPosition();
        if (!level.isCloseToVillage(pos, 6)) return false;
        var poi = level.poiManager();
        double[] vec = LandRandomPos.getPos(mob, 15, 7, p -> -poi.sectionsToVillage(p.x() >> 4, p.y() >> 4, p.z() >> 4));
        target = vec == null ? null : new Pos((int) Math.floor(vec[0]), (int) Math.floor(vec[1]), (int) Math.floor(vec[2]));
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !mob.getNavigation().isDone() && target.equals(mob.getNavigation().getTargetPos());
    }

    @Override
    public void tick() {
        if (target == null) return;
        var nav = mob.getNavigation();
        double cx = target.x() + 0.5, cy = target.y() + 0.5, cz = target.z() + 0.5;
        double dx = cx - mob.x(), dy = cy - mob.y(), dz = cz - mob.z();   // 거리는 칸 중심 기준
        if (nav.isDone() && !(dx * dx + dy * dy + dz * dz < DISTANCE_THRESHOLD * DISTANCE_THRESHOLD)) {
            double mx = mob.x(), my = mob.y(), mz = mob.z();
            // 바닐라: a = 목표(칸 아래 가운데) + (몹 - 목표) * 0.4, (a - 몹) 단위벡터 * 10 + 몹 의 칸으로
            double bx = cx, by = target.y(), bz = cz;
            double ax = bx + (mx - bx) * 0.4, ay = by + (my - by) * 0.4, az = bz + (mz - bz) * 0.4;
            double vx = ax - mx, vy = ay - my, vz = az - mz;
            double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > 1.0E-4) { vx /= len; vy /= len; vz /= len; }
            else { vx = vy = vz = 0.0; }
            int px = (int) Math.floor(vx * 10.0 + mx), pz = (int) Math.floor(vz * 10.0 + mz);
            int py = mob.level().getMotionBlockingNoLeavesHeight(px, pz);
            if (!nav.moveTo(px, py, pz, 1.0)) moveRandomly();
        }
    }

    private void moveRandomly() {
        var random = mob.random();
        Pos base = mob.blockPosition().offset(-8 + random.nextInt(16), 0, -8 + random.nextInt(16));
        int h = mob.level().getMotionBlockingNoLeavesHeight(base.x(), base.z());
        mob.getNavigation().moveTo(base.x(), h, base.z(), 1.0);
    }
}
