// 바닐라 TryFindLandNearWater: 물 밖에서, 물과 맞닿은 마른 땅(알 낳을 곳)을 찾아 걸어갈 목표로 둔다(40틱 간격).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

public final class TryFindLandNearWater {
    private TryFindLandNearWater() {}

    public static OneShot<PathfinderMob> create(int range, float speed) {
        long[] nextOk = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.ATTACK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                if (level.isWaterAt(mob.blockPosition())) return false;
                if (gameTime < nextOk[0]) {
                    nextOk[0] = gameTime + 40L;
                    return true;
                }
                Pos here = mob.blockPosition();
                BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), range, range, range, (x, y, z) -> {
                    if (x == here.x() && z == here.z()) return true;
                    Pos p = new Pos(x, y, z);
                    int state = level.getBlockState(p);
                    int belowState = level.getBlockState(p.relative(Dir.DOWN));
                    if (B.behavior(state).getCollisionShape(state, level, p, null).isEmpty()
                            && !B.behavior(belowState).getCollisionShape(belowState, level, p.relative(Dir.DOWN), null).isEmpty()) {
                        for (int d : Dir.HORIZONTAL) {
                            Pos side = p.relative(d);
                            if (B.isAir(level.getBlockState(side)) && B.is(level.getBlockState(side.relative(Dir.DOWN)), "minecraft:water")) {
                                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(p));
                                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(p), speed, 0));
                                return false;
                            }
                        }
                    }
                    return true;
                });
                nextOk[0] = gameTime + 40L;
                return true;
            }
        };
    }
}
