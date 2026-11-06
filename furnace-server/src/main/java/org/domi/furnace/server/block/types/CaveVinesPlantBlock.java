// 바닐라 CaveVinesPlantBlock: 동굴 덩굴 몸통. 열매 따기·뼛가루로 열매.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class CaveVinesPlantBlock extends GrowingPlantBlock.GrowingPlantBody {

    public CaveVinesPlantBlock(int block) { super(block, CAVE_VINES); }

    @Override
    protected int headFromBody(int body, int head) { return CaveVines.BERRIES.with(head, CaveVines.BERRIES.is(body)); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) { return CaveVines.use(player, state, level, pos); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return !CaveVines.BERRIES.is(state); }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlock(pos, CaveVines.BERRIES.with(state, true), 2);
    }
}
