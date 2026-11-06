// 바닐라 FollowFlockLeaderGoal: 떼 물고기가 무리 우두머리를 따라간다(우두머리가 없으면 근처 무리를 모아 스스로 우두머리가 된다).
package org.domi.furnace.server.entity.ai.goal;

import java.util.List;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.AbstractSchoolingFish;
import org.domi.furnace.server.entity.ai.Goal;

public class FollowFlockLeaderGoal extends Goal {
    private static final int INTERVAL_TICKS = 200;
    private final AbstractSchoolingFish mob;
    private int timeToRecalcPath;
    private int nextStartTick;

    public FollowFlockLeaderGoal(AbstractSchoolingFish mob) {
        this.mob = mob;
        this.nextStartTick = nextStartTick(mob);
    }

    protected int nextStartTick(AbstractSchoolingFish fish) { return reducedTickDelay(INTERVAL_TICKS + fish.random().nextInt(INTERVAL_TICKS) % 20); }

    @SuppressWarnings("unchecked")
    @Override
    public boolean canUse() {
        if (mob.hasFollowers()) return false;
        if (mob.isFollower()) return true;
        if (nextStartTick > 0) {
            nextStartTick--;
            return false;
        }
        nextStartTick = nextStartTick(mob);
        Predicate<AbstractSchoolingFish> filter = f -> f.canBeFollowed() || !f.isFollower();
        List<AbstractSchoolingFish> list = mob.level().getEntitiesOfClass((Class<AbstractSchoolingFish>) (Class<?>) mob.getClass(), mob.boundingBox().inflate(8.0, 8.0, 8.0), filter);
        AbstractSchoolingFish leader = list.stream().filter(AbstractSchoolingFish::canBeFollowed).findAny().orElse(mob);
        leader.addFollowers(list.stream().filter(f -> !f.isFollower()));
        return mob.isFollower();
    }

    @Override
    public boolean canContinueToUse() { return mob.isFollower() && mob.inRangeOfLeader(); }

    @Override
    public void start() { timeToRecalcPath = 0; }

    @Override
    public void stop() { mob.stopFollowing(); }

    @Override
    public void tick() {
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = adjustedTickDelay(10);
            mob.pathToLeader();
        }
    }
}
