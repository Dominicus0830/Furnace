// 바닐라 InsideBrownianWalk: 하늘이 안 보이면(실내) 둘레 3×3×3 칸을 섞어 하늘이 안 보이고 설 수 있는 첫 칸으로 조금 걷는다.
// 섞기는 바닐라처럼 Collections.shuffle(전역 난수)이라 레벨 난수를 쓰지 않는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class InsideBrownianWalk {
    private InsideBrownianWalk() {}

    public static OneShot<PathfinderMob> create(float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long time) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                if (level.canSeeSky(mob.blockPosition())) return false;
                Pos pos = mob.blockPosition();
                List<Pos> list = new ArrayList<>();
                for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) list.add(new Pos(pos.x() + x, pos.y() + y, pos.z() + z));
                Collections.shuffle(list);
                for (Pos p : list) {
                    if (!level.canSeeSky(p) && loadedAndEntityCanStandOn(level, p, mob) && level.noCollision(mob, mob.boundingBox())) {
                        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, speed, 0));
                        break;
                    }
                }
                return true;
            }
        };
    }

    /** Level.loadedAndEntityCanStandOn(p, mob): 청크가 있고 칸 충돌 모양(mob 문맥)의 윗면이 꽉 찼다. */
    static boolean loadedAndEntityCanStandOn(ChunkLevel level, Pos p, PathfinderMob mob) {
        return level.hasChunkAt(p) && B.entityCanStandOn(level.getBlockState(p), level, p, mob);
    }
}
