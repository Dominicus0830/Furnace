// 바닐라 GroundPathNavigation: 걷는 몹의 길찾기 — WalkNodeEvaluator, 문 통과 허용, 목표 칸을 땅으로 보정.
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;

public class GroundPathNavigation extends PathNavigation {
    private boolean avoidSun;

    public GroundPathNavigation(Mob mob, ChunkLevel level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new WalkNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() { return mob.onGround() || mob.isInLiquid() || mob.isPassenger(); }

    @Override
    protected double[] getTempMobPos() { return new double[] {mob.x(), getSurfaceY(), mob.z()}; }

    @Override
    public Path createPath(Pos target, int accuracy) {
        int s = level.getBlockState(target);
        if (B.isAir(s)) {
            Pos below = target.below();
            while (below.y() > level.minBuildHeight() && B.isAir(level.getBlockState(below))) below = below.below();
            if (below.y() > level.minBuildHeight()) return super.createPath(below.above(), accuracy);
            while (below.y() < level.maxBuildHeight() && B.isAir(level.getBlockState(below))) below = below.above();
            target = below;
        }
        if (!B.isSolid(level.getBlockState(target))) return super.createPath(target, accuracy);
        Pos above = target.above();
        while (above.y() < level.maxBuildHeight() && B.isSolid(level.getBlockState(above))) above = above.above();
        return super.createPath(above, accuracy);
    }

    @Override
    public Path createPath(Entity target, int accuracy) { return createPath(target.blockPosition(), accuracy); }

    private int getSurfaceY() {
        if (mob.isInWater() && canFloat()) {
            int y = mob.blockY();
            int s = level.getBlockState(new Pos((int) Math.floor(mob.x()), y, (int) Math.floor(mob.z())));
            int n = 0;
            while (B.is(s, "minecraft:water")) {
                s = level.getBlockState(new Pos((int) Math.floor(mob.x()), ++y, (int) Math.floor(mob.z())));
                if (++n > 16) return mob.blockY();
            }
            return y;
        }
        return (int) Math.floor(mob.y() + 0.5);
    }

    @Override
    protected void trimPath() {
        super.trimPath();
        if (avoidSun) {
            // TODO(햇빛 피하기): 하늘이 보이는 노드에서 경로 자르기(canSeeSky)
        }
    }

    public void setAvoidSun(boolean v) { avoidSun = v; }

    public void setCanOpenDoors(boolean v) { nodeEvaluator.setCanOpenDoors(v); }

    public boolean canOpenDoors() { return nodeEvaluator.canOpenDoors(); }

    public void setCanPassDoors(boolean v) { nodeEvaluator.setCanPassDoors(v); }

    public void setCanWalkOverFences(boolean v) { nodeEvaluator.setCanWalkOverFences(v); }
}
