// 바닐라 TryFindWater: 물 밖에서, 가까운(range) 수면 물 블록을 찾아 걸어갈 목표로 둔다(40틱 간격).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

public final class TryFindWater {
    private TryFindWater() {}

    public static OneShot<PathfinderMob> create(int range, float speed) {
        long[] nextOk = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.ATTACK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET)) return false;
                if (Fluids.isWater(level.getFluidState(mob.blockPosition()))) return false;
                if (gameTime < nextOk[0]) {
                    nextOk[0] = gameTime + 20L + 2L;
                    return true;
                }
                Pos here = mob.blockPosition();
                Pos[] found = new Pos[2];   // [0] 수면(위가 공기), [1] 대신 쓸 물 블록
                BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), range, range, range, (x, y, z) -> {
                    if (x != here.x() || z != here.z()) {
                        Pos p = new Pos(x, y, z);
                        if (B.is(level.getBlockState(p), "minecraft:water")) {
                            if (B.isAir(level.getBlockState(p.above()))) {
                                found[0] = p;
                                return false;
                            }
                            double dx = x + 0.5 - mob.x(), dy = y + 0.5 - mob.y(), dz = z + 0.5 - mob.z();
                            if (found[1] == null && !(dx * dx + dy * dy + dz * dz < 1.5 * 1.5)) found[1] = p;
                        }
                    }
                    return true;
                });
                Pos target = found[0] != null ? found[0] : found[1];
                if (target != null) {
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
                    mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(target), speed, 0));
                }
                nextOk[0] = gameTime + 40L;
                return true;
            }
        };
    }
}
