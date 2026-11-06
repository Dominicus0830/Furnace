// 바닐라 FlyingPathNavigation: 나는 몹의 길찾기 — FlyNodeEvaluator, 몸 중심을 기준점으로 쓰고 경로 노드를 바로 따라간다(지면 보정 없음).
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.world.BlockAttrs;

public class FlyingPathNavigation extends PathNavigation {

    public FlyingPathNavigation(Mob mob, ChunkLevel level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new FlyNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canMoveDirectly(double[] a, double[] b) { return WaterBoundPathNavigation.isClearForMovementBetween(mob, a, b, true); }

    @Override
    protected boolean canUpdatePath() { return canFloat() && mob.isInLiquid() || !mob.isPassenger(); }

    @Override
    protected double[] getTempMobPos() { return new double[] {mob.x(), mob.y(), mob.z()}; }

    @Override
    public Path createPath(Targetable target, int accuracy) { return createPath(target.blockPosition(), accuracy); }

    @Override
    public void tick() {
        tick++;
        if (hasDelayedRecomputation) recomputePath();
        if (!isDone()) {
            if (canUpdatePath()) {
                followThePath();
            } else if (path != null && !path.isDone()) {
                double[] v = path.getNextEntityPos(mob);
                if (mob.blockX() == (int) Math.floor(v[0]) && mob.blockY() == (int) Math.floor(v[1]) && mob.blockZ() == (int) Math.floor(v[2])) path.advance();
            }
            if (!isDone()) {
                double[] v = path.getNextEntityPos(mob);
                mob.getMoveControl().setWantedPosition(v[0], v[1], v[2], speedModifier);
            }
        }
    }

    public void setCanOpenDoors(boolean v) { nodeEvaluator.setCanOpenDoors(v); }

    public void setCanPassDoors(boolean v) { nodeEvaluator.setCanPassDoors(v); }

    public boolean canPassDoors() { return nodeEvaluator.canPassDoors(); }

    public boolean canOpenDoors() { return nodeEvaluator.canOpenDoors(); }

    @Override
    public boolean isStableDestination(Pos pos) { return BlockAttrs.get().isCollisionFaceFull(level.getBlockState(pos), Dir.UP); }
}
