// 바닐라 Swim(행동): 눈높이까지 물에 잠기거나 용암에 있으면 chance 확률로 틱마다 뛰어 떠오른다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;

public class Swim extends Behavior<Mob> {
    private final float chance;

    public Swim(float chance) {
        super(Map.of());
        this.chance = chance;
    }

    public static boolean shouldSwim(Mob mob) { return mob.isInWater() && mob.fluidHeight(true) > mob.getFluidJumpThreshold() || mob.isInLava(); }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, Mob mob) { return shouldSwim(mob); }

    @Override protected boolean canStillUse(ChunkLevel level, Mob mob, long gameTime) { return shouldSwim(mob); }

    @Override
    protected void tick(ChunkLevel level, Mob mob, long gameTime) {
        if (mob.random().nextFloat() < chance) mob.getJumpControl().jump();
    }
}
