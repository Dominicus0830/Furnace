// 바닐라 AmphibiousPathNavigation: 물과 땅을 모두 다니는 몹의 길찾기 — 어디서든 길을 만들고, 몸 한가운데 높이를 기준점으로 쓴다.
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public class AmphibiousPathNavigation extends PathNavigation {

    public AmphibiousPathNavigation(Mob mob, ChunkLevel level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new AmphibiousNodeEvaluator(false);
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() { return true; }

    @Override
    protected double[] getTempMobPos() { return new double[] {mob.x(), mob.y() + mob.bbHeight() * 0.5, mob.z()}; }

    @Override
    protected double getGroundY(double[] v) { return v[1]; }

    @Override
    protected boolean canMoveDirectly(double[] a, double[] b) { return mob.isInLiquid() && WaterBoundPathNavigation.isClearForMovementBetween(mob, a, b, false); }

    @Override
    public boolean isStableDestination(Pos pos) { return !B.isAir(level.getBlockState(pos.below())); }

    @Override
    public void setCanFloat(boolean v) { }
}
