// 바닐라 RandomStrollGoal: 가끔(interval 틱에 한 번꼴) 무작위 칸으로 걷는다. 기본 위치 선택은 LandRandomPos(10, 7).
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public class RandomStrollGoal extends Goal {
    public static final int DEFAULT_INTERVAL = 120;
    protected final PathfinderMob mob;
    protected double wantedX, wantedY, wantedZ;
    protected final double speedModifier;
    protected int interval;
    protected boolean forceTrigger;
    private final boolean checkNoActionTime;

    public RandomStrollGoal(PathfinderMob mob, double speed) { this(mob, speed, DEFAULT_INTERVAL); }

    public RandomStrollGoal(PathfinderMob mob, double speed, int interval) { this(mob, speed, interval, true); }

    public RandomStrollGoal(PathfinderMob mob, double speed, int interval, boolean checkNoActionTime) {
        this.mob = mob;
        this.speedModifier = speed;
        this.interval = interval;
        this.checkNoActionTime = checkNoActionTime;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isPassenger()) return false;
        if (!forceTrigger) {
            if (checkNoActionTime && mob.getNoActionTime() >= 100) return false;
            if (mob.random().nextInt(reducedTickDelay(interval)) != 0) return false;
        }
        double[] pos = getPosition();
        if (pos == null) return false;
        wantedX = pos[0];
        wantedY = pos[1];
        wantedZ = pos[2];
        forceTrigger = false;
        return true;
    }

    protected double[] getPosition() { return LandRandomPos.getPos(mob, 10, 7); }

    @Override
    public boolean canContinueToUse() { return !mob.getNavigation().isDone() && !mob.isPassenger(); }

    @Override
    public void start() { mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier); }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        super.stop();
    }

    public void trigger() { forceTrigger = true; }

    public void setInterval(int i) { interval = i; }
}
