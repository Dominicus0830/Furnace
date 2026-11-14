// 바닐라 EatBlockGoal: 가끔 발밑의 짧은 풀이나 풀 블록을 먹고(4틱 전에 실행) 양털이 다시 자란다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;

public class EatBlockGoal extends Goal {
    private static final int EAT_ANIMATION_TICKS = 40;
    private final Mob mob;
    private int eatAnimationTick;
    private final Runnable ate;

    /** @param ate 먹은 뒤 부르는 쪽(Sheep.ate). */
    public EatBlockGoal(Mob mob, Runnable ate) {
        this.mob = mob;
        this.ate = ate;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (mob.random().nextInt(mob.isBaby() ? 50 : 1000) != 0) return false;
        Pos pos = mob.blockPosition();
        return B.is(mob.level().getBlockState(pos), "minecraft:short_grass") || B.is(mob.level().getBlockState(pos.below()), "minecraft:grass_block");
    }

    @Override
    public void start() {
        eatAnimationTick = adjustedTickDelay(EAT_ANIMATION_TICKS);
        mob.broadcastEntityEvent((byte) 10);
        mob.getNavigation().stop();
    }

    @Override
    public void stop() { eatAnimationTick = 0; }

    @Override
    public boolean canContinueToUse() { return eatAnimationTick > 0; }

    public int getEatAnimationTick() { return eatAnimationTick; }

    @Override
    public void tick() {
        eatAnimationTick = Math.max(0, eatAnimationTick - 1);
        if (eatAnimationTick != adjustedTickDelay(4)) return;
        var level = mob.level();
        Pos pos = mob.blockPosition();
        int here = level.getBlockState(pos);
        // CraftBukkit(+Paper 새 상태): mobGriefing 거짓이어도 EntityChangeBlockEvent 를 미리 취소한 채로 내고, 처리기가 풀면 먹는다
        if (B.is(here, "minecraft:short_grass")) {
            if (B.entityChange(level, mob, pos, B.fluidLeftover(here), !level.gameRule("mobGriefing"))) level.destroyBlock(pos, false, null, 512);
            ate.run();
        } else {
            Pos below = pos.below();
            if (B.is(level.getBlockState(below), "minecraft:grass_block")) {
                int dirt = org.domi.furnace.server.block.Blocks.get().defaultState("minecraft:dirt");
                if (B.entityChange(level, mob, below, dirt, !level.gameRule("mobGriefing"))) {
                    // 바닐라: Block.getId(GRASS_BLOCK.defaultBlockState()) — 눈 덮인 풀이어도 기본 상태로 알린다
                    level.levelEvent(null, 2001, below, B.defaultState("minecraft:grass_block"));
                    level.setBlock(below, dirt, 2, 512);
                }
                ate.run();
            }
        }
    }
}
