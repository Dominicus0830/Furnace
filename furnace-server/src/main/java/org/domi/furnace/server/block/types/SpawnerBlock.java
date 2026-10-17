// 바닐라 SpawnerBlock: 몬스터 생성기. 생성은 블록 엔티티(TODO(엔티티)). 부수면 경험치 15 + nextInt(15) + nextInt(15).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class SpawnerBlock extends BaseEntityBlock {

    public SpawnerBlock(int block) { super(block); }

    @Override
    public void spawnAfterBreak(int state, BlockLevel level, Pos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) level.popExperience(pos, 15 + level.random().nextInt(15) + level.random().nextInt(15));
    }
}
