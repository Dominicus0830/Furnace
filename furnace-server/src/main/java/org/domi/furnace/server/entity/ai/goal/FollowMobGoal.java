// 바닐라 FollowMobGoal: 다른 종류 몹(areaSize 안)을 따라가되 stopDistance 안에서는 멈추거나 피한다. 따라가는 동안 물 비용을 0 으로 둔다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.FlyingPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;

public class FollowMobGoal extends Goal {
    private final Mob mob;
    private Mob followingMob;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance;
    private float oldWaterCost;
    private final float areaSize;

    public FollowMobGoal(Mob mob, double speed, float stopDistance, float areaSize) {
        this.mob = mob;
        this.speedModifier = speed;
        this.navigation = mob.getNavigation();
        this.stopDistance = stopDistance;
        this.areaSize = areaSize;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        if (!(mob.getNavigation() instanceof org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation) && !(mob.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowMobGoal");
        }
    }

    @Override
    public boolean canUse() {
        List<Mob> list = mob.level().getEntitiesOfClass(Mob.class, mob.boundingBox().inflate(areaSize), m -> m != null && mob.getClass() != m.getClass());
        for (Mob m : list) {
            if (!m.isInvisible()) {
                followingMob = m;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (followingMob == null || navigation.isDone()) return false;
        double dx = mob.x() - followingMob.x(), dy = mob.y() - followingMob.y(), dz = mob.z() - followingMob.z();
        return dx * dx + dy * dy + dz * dz > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
        oldWaterCost = mob.getPathfindingMalus(PathType.WATER);
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        followingMob = null;
        navigation.stop();
        mob.setPathfindingMalus(PathType.WATER, oldWaterCost);
    }

    @Override
    public void tick() {
        if (followingMob != null) {
            mob.getLookControl().setLookAt(followingMob, 10.0F, mob.getMaxHeadXRot());
            if (--timeToRecalcPath <= 0) {
                timeToRecalcPath = adjustedTickDelay(10);
                double dx = mob.x() - followingMob.x(), dy = mob.y() - followingMob.y(), dz = mob.z() - followingMob.z();
                double d = dx * dx + dy * dy + dz * dz;
                if (!(d <= stopDistance * stopDistance)) {
                    navigation.moveTo(followingMob, speedModifier);
                } else {
                    navigation.stop();
                    var look = followingMob.getLookControl();
                    if (d <= stopDistance || look.getWantedX() == mob.x() && look.getWantedY() == mob.y() && look.getWantedZ() == mob.z()) {
                        double ex = followingMob.x() - mob.x(), ez = followingMob.z() - mob.z();
                        navigation.moveTo(mob.x() - ex, mob.y(), mob.z() - ez, speedModifier);
                    }
                }
            }
        }
    }
}
