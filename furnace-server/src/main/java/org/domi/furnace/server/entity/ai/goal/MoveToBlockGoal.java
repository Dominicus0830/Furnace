// 바닐라 MoveToBlockGoal: 가까운 목표 칸을 찾아 걸어가 도착하면 머문다(서브클래스가 isValidTarget 정의).
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;

public abstract class MoveToBlockGoal extends Goal {
    protected final PathfinderMob mob;
    public final double speedModifier;
    protected int nextStartTick;
    protected int tryTicks;
    private int maxStayTicks;
    protected Pos blockPos = new Pos(0, 0, 0);
    private boolean reachedTarget;
    private final int searchRange;
    private final int verticalSearchRange;
    protected int verticalSearchStart;

    public MoveToBlockGoal(PathfinderMob mob, double speed, int range) { this(mob, speed, range, 1); }

    public MoveToBlockGoal(PathfinderMob mob, double speed, int range, int vRange) {
        this.mob = mob;
        this.speedModifier = speed;
        this.searchRange = range;
        this.verticalSearchStart = 0;
        this.verticalSearchRange = vRange;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (nextStartTick > 0) {
            nextStartTick--;
            return false;
        }
        nextStartTick = nextStartTick(mob);
        return findNearestBlock();
    }

    protected int nextStartTick(PathfinderMob mob) { return reducedTickDelay(200 + mob.random().nextInt(200)); }

    @Override
    public boolean canContinueToUse() { return tryTicks >= -maxStayTicks && tryTicks <= 1200 && isValidTarget(mob.level(), blockPos); }

    @Override
    public void start() {
        moveMobToBlock();
        tryTicks = 0;
        maxStayTicks = mob.random().nextInt(mob.random().nextInt(1200) + 1) + 1200;
    }

    protected void moveMobToBlock() { mob.getNavigation().moveTo(blockPos.x() + 0.5, blockPos.y() + 1, blockPos.z() + 0.5, speedModifier); }

    public double acceptedDistance() { return 1.0; }

    protected Pos getMoveToTarget() { return blockPos.above(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        Pos target = getMoveToTarget();
        double cx = target.x() + 0.5 - mob.x(), cy = target.y() + 0.5 - mob.y(), cz = target.z() + 0.5 - mob.z();
        if (!(cx * cx + cy * cy + cz * cz < acceptedDistance() * acceptedDistance())) {
            reachedTarget = false;
            tryTicks++;
            if (shouldRecalculatePath()) mob.getNavigation().moveTo(target.x() + 0.5, target.y(), target.z() + 0.5, speedModifier);
        } else {
            reachedTarget = true;
            tryTicks--;
        }
    }

    public boolean shouldRecalculatePath() { return tryTicks % 40 == 0; }

    protected boolean isReachedTarget() { return reachedTarget; }

    protected boolean findNearestBlock() {
        int r = searchRange, v = verticalSearchRange;
        Pos origin = mob.blockPosition();
        for (int k = verticalSearchStart; k <= v; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < r; l++) {
                for (int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        Pos p = new Pos(origin.x() + i1, origin.y() + k - 1, origin.z() + j1);
                        if (mob.isWithinRestriction(p) && isValidTarget(mob.level(), p)) {
                            blockPos = p;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected abstract boolean isValidTarget(BlockLevel level, Pos pos);
}
