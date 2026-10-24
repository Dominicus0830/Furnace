// 바닐라 FollowOwnerGoal: 주인이 멀어지면(startDistance 밖) 따라가고, 너무 멀면 곁으로 순간이동한다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;

public class FollowOwnerGoal extends Goal {
    private final TamableAnimal tamable;
    private Player owner;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance, startDistance;
    private float oldWaterCost;

    public FollowOwnerGoal(TamableAnimal tamable, double speed, float startDistance, float stopDistance) {
        this.tamable = tamable;
        this.speedModifier = speed;
        this.navigation = tamable.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        if (!(navigation instanceof org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
        }
    }

    private double distSqr(Player p) {
        double dx = tamable.x() - p.x(), dy = tamable.y() - p.y(), dz = tamable.z() - p.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canUse() {
        Player o = tamable.getOwner();
        if (o == null) return false;
        if (tamable.unableToMove()) return false;
        if (distSqr(o) < startDistance * startDistance) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (navigation.isDone()) return false;
        return tamable.unableToMove() ? false : !(distSqr(owner) <= stopDistance * stopDistance);
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
        oldWaterCost = tamable.getPathfindingMalus(PathType.WATER);
        tamable.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        owner = null;
        navigation.stop();
        tamable.setPathfindingMalus(PathType.WATER, oldWaterCost);
    }

    @Override
    public void tick() {
        boolean far = tamable.shouldTryTeleportToOwner();
        if (!far) tamable.getLookControl().setLookAtPlayer(owner, 10.0F, tamable.getMaxHeadXRot());
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = adjustedTickDelay(10);
            if (far) tamable.tryToTeleportToOwner();
            else navigation.moveTo(owner, speedModifier);
        }
    }
}
