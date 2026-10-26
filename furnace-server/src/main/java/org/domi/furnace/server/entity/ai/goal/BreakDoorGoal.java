// 바닐라 BreakDoorGoal: 문을 서서히 부순다(240틱 이상, 진행 0..9 를 방송). mobGriefing 과 난이도 조건을 둘 다 본다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.function.IntPredicate;
import org.domi.furnace.server.entity.Mob;

public class BreakDoorGoal extends DoorInteractGoal {
    private static final int DEFAULT_DOOR_BREAK_TIME = 240;
    private final IntPredicate validDifficulty;
    protected int breakTime;
    protected int lastBreakProgress = -1;
    protected int doorBreakTime = -1;

    public BreakDoorGoal(Mob mob, IntPredicate validDifficulty) {
        super(mob);
        this.validDifficulty = validDifficulty;
    }

    public BreakDoorGoal(Mob mob, int doorBreakTime, IntPredicate validDifficulty) {
        this(mob, validDifficulty);
        this.doorBreakTime = doorBreakTime;
    }

    protected int getDoorBreakTime() { return Math.max(DEFAULT_DOOR_BREAK_TIME, doorBreakTime); }

    @Override
    public boolean canUse() {
        if (!super.canUse()) return false;
        return mob.level().gameRule("mobGriefing") && validDifficulty.test(mob.level().difficulty()) && !isOpen();
    }

    @Override
    public void start() {
        super.start();
        breakTime = 0;
    }

    @Override
    public boolean canContinueToUse() {
        double dx = doorPos.x() + 0.5 - mob.x(), dy = doorPos.y() + 0.5 - mob.y(), dz = doorPos.z() + 0.5 - mob.z();
        return breakTime <= getDoorBreakTime() && !isOpen() && dx * dx + dy * dy + dz * dz < 4.0 && validDifficulty.test(mob.level().difficulty());
    }

    @Override
    public void stop() {
        super.stop();
        mob.level().destroyBlockProgress(mob.id(), doorPos, -1);
    }

    @Override
    public void tick() {
        super.tick();
        var level = mob.level();
        if (mob.random().nextInt(20) == 0) {
            level.levelEvent(null, 1019, doorPos, 0);
            if (!mob.isSwinging()) mob.swing(mob.getUsedItemHand());
        }
        breakTime++;
        int progress = (int) ((float) breakTime / getDoorBreakTime() * 10.0F);
        if (progress != lastBreakProgress) {
            level.destroyBlockProgress(mob.id(), doorPos, progress);
            lastBreakProgress = progress;
        }
        if (breakTime == getDoorBreakTime() && validDifficulty.test(level.difficulty())) {
            level.removeBlock(doorPos, false);
            level.levelEvent(null, 1021, doorPos, 0);
            level.levelEvent(null, 2001, doorPos, level.getBlockState(doorPos));
        }
    }
}
