// 바닐라 TryLaySpawnOnWaterNearLand: 임신한 몹이 물가 땅에서 옆 물 위에 알 블록을 놓는다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class TryLaySpawnOnWaterNearLand {
    private TryLaySpawnOnWaterNearLand() {}

    public static OneShot<Mob> create(String block) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.ATTACK_TARGET) || !present(mob, MemoryModuleType.WALK_TARGET) || !present(mob, MemoryModuleType.IS_PREGNANT)) return false;
                if (!mob.isInWater() && mob.onGround()) {
                    Pos below = mob.blockPosition().below();
                    for (int d : Dir.HORIZONTAL) {
                        Pos adj = below.relative(d);
                        int state = level.getBlockState(adj);
                        if (B.behavior(state).getCollisionShape(state, level, adj, null).isFaceEmpty(Dir.UP) && level.isWaterAt(adj)) {
                            Pos above = adj.above();
                            if (B.isAir(level.getBlockState(above))) {
                                // CraftBukkit: EntityChangeBlockEvent 를 막으면 알을 놓지 않고 임신만 끝낸다
                                if (!B.entityChange(level, mob, above, Blocks.get().defaultState(block))) {
                                    mob.getBrain().eraseMemory(MemoryModuleType.IS_PREGNANT);
                                    return true;
                                }
                                level.setBlock(above, Blocks.get().defaultState(block), 3);
                                level.playSound(null, mob, "entity.frog.lay_spawn", "neutral", 1.0F, 1.0F);
                                mob.getBrain().eraseMemory(MemoryModuleType.IS_PREGNANT);
                                return true;
                            }
                        }
                    }
                    return true;
                }
                return false;
            }
        };
    }
}
