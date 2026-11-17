// 바닐라 ClimbOnTopOfPowderSnowGoal: 가루눈을 걸을 수 있는 몹이 가루눈에 빠지면 계속 뛰어 위로 올라선다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;

public class ClimbOnTopOfPowderSnowGoal extends Goal {
    private final Mob mob;

    public ClimbOnTopOfPowderSnowGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        boolean inSnow = mob.wasInPowderSnow() || mob.isInPowderSnow();
        if (inSnow && mob.typeInTag("minecraft:powder_snow_walkable_mobs")) {
            Pos above = mob.blockPosition().above();
            int state = mob.level().getBlockState(above);
            return B.is(state, "minecraft:powder_snow") || BlockBehaviors.of(state).getCollisionShape(state, mob.level(), above, org.domi.furnace.server.block.CollisionContext.EMPTY).isEmpty();
        }
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() { mob.getJumpControl().jump(); }
}
