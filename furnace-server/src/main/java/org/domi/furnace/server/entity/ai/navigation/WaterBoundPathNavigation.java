// 바닐라 WaterBoundPathNavigation: 물속 몹의 길찾기 — SwimNodeEvaluator, 몸 한가운데 높이를 기준점으로 쓰고 물 밖에서는 길을 새로 못 만든다(돌고래 제외).
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;

public class WaterBoundPathNavigation extends PathNavigation {
    private boolean allowBreaching;

    public WaterBoundPathNavigation(Mob mob, ChunkLevel level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        allowBreaching = mob.type() == EntityType.DOLPHIN;
        nodeEvaluator = new SwimNodeEvaluator(allowBreaching);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() { return allowBreaching || mob.isInLiquid(); }

    @Override
    protected double[] getTempMobPos() { return new double[] {mob.x(), mob.y() + mob.bbHeight() * 0.5, mob.z()}; }

    @Override
    protected double getGroundY(double[] v) { return v[1]; }

    @Override
    protected boolean canMoveDirectly(double[] a, double[] b) { return isClearForMovementBetween(mob, a, b, false); }

    @Override
    public boolean isStableDestination(Pos pos) { return !B.isFullOcclusion(level.getBlockState(pos)); }

    @Override
    public void setCanFloat(boolean v) { }

    /** PathNavigation.isClearForMovementBetween: 도착 지점을 몸 반 높이만큼 올려 충돌 모양으로 시선이 막히지 않으면 참. */
    public static boolean isClearForMovementBetween(Mob mob, double[] from, double[] to, boolean allowLiquids) {
        double ty = to[1] + mob.bbHeight() * 0.5;
        var hit = Clip.clipCollider(mob.level(), org.domi.furnace.server.block.CollisionContext.of(mob), from[0], from[1], from[2], to[0], ty, to[2], allowLiquids ? Clip.FluidMode.ANY : Clip.FluidMode.NONE);
        return hit.miss();
    }
}
