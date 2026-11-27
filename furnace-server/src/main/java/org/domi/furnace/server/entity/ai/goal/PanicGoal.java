// 바닐라 PanicGoal: 최근 맞았거나 불이 붙으면 무작위 칸으로 도망친다(불이면 가까운 물로).
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.GoalUtils;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.ai.goal.PanicGoal")
public class PanicGoal extends Goal {
    protected final PathfinderMob mob;
    protected final double speedModifier;
    protected double posX, posY, posZ;
    protected boolean isRunning;
    /** 바닐라 panicCausingDamageTypes(Function — 몹마다 같은 몹을 넘기므로 Supplier). null 을 주면 어느 피해도 맞지 않는다(다른 레지스트리의 태그). */
    private final java.util.function.Supplier<String> panicTag;

    public PanicGoal(PathfinderMob mob, double speed) { this(mob, speed, "minecraft:panic_causes"); }

    public PanicGoal(PathfinderMob mob, double speed, String panicTag) { this(mob, speed, () -> panicTag); }

    public PanicGoal(PathfinderMob mob, double speed, java.util.function.Supplier<String> panicTag) {
        this.mob = mob;
        this.panicTag = panicTag;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!shouldPanic()) return false;
        if (mob.isOnFire()) {
            Pos water = lookForWater(5);
            if (water != null) {
                posX = water.x();
                posY = water.y();
                posZ = water.z();
                return true;
            }
        }
        return findRandomPosition();
    }

    protected boolean shouldPanic() {
        String t = mob.getLastDamageType();
        if (t == null) return false;
        String tag = panicTag.get();   // 바닐라처럼 마지막 피해가 있을 때만 부른다
        return tag != null && DamageType.byKey(t).isInTag(tag);
    }

    protected boolean findRandomPosition() {
        double[] p = LandRandomPos.getPos(mob, 5, 4);
        if (p == null) return false;
        posX = p[0];
        posY = p[1];
        posZ = p[2];
        return true;
    }

    public boolean isRunning() { return isRunning; }

    @Override
    public void start() {
        mob.getNavigation().moveTo(posX, posY, posZ, speedModifier);
        isRunning = true;
    }

    @Override
    public void stop() { isRunning = false; }

    @Override
    public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }

    /** 서 있는 칸에 충돌이 없을 때만, 맨해튼 순서로 가장 가까운 물 칸. */
    protected Pos lookForWater(int range) {
        Pos here = mob.blockPosition();
        var level = mob.level();
        if (!org.domi.furnace.server.block.BlockInfo.get().collisionShape(level.getBlockState(here)).isEmpty()) return null;
        Pos[] found = new Pos[1];
        BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), range, 1, range, (x, y, z) -> {
            if (GoalUtils.isWater(mob, new Pos(x, y, z))) {
                found[0] = new Pos(x, y, z);
                return false;
            }
            return true;
        });
        return found[0];
    }
}
