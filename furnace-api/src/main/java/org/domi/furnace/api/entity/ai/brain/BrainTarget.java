// 바라보거나 걸어갈 대상: 엔티티(그 엔티티를 따라간다) 또는 고정 블록 한 칸.
package org.domi.furnace.api.entity.ai.brain;

import java.util.UUID;
import org.domi.furnace.api.world.BlockPos;

public sealed interface BrainTarget permits BrainTarget.OfEntity, BrainTarget.OfBlock {

    /** 같은 region 에 있는 엔티티(플레이어 포함). 쓸 때 못 찾으면 기억 쓰기가 false. */
    record OfEntity(UUID entity) implements BrainTarget {
        public OfEntity {
            if (entity == null) throw new NullPointerException("entity");
        }
    }

    record OfBlock(BlockPos pos) implements BrainTarget {
        public OfBlock {
            if (pos == null) throw new NullPointerException("pos");
        }
    }

    static BrainTarget entity(UUID entity) { return new OfEntity(entity); }

    static BrainTarget block(int x, int y, int z) { return new OfBlock(new BlockPos(x, y, z)); }
}
