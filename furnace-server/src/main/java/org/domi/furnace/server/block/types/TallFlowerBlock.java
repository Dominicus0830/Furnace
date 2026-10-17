// 바닐라 TallFlowerBlock: 두 칸 꽃. 뼛가루를 쓰면 같은 꽃 아이템 하나를 떨군다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class TallFlowerBlock extends DoublePlantBlock implements BonemealableBlock {

    public TallFlowerBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(드롭): Block.popResource(level, pos, new ItemStack(this))
    }
}
