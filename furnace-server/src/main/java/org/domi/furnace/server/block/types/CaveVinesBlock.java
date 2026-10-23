// 바닐라 CaveVinesBlock: 동굴 덩굴 머리(아래로 자람). 자랄 때 11% 확률로 열매. 뼛가루는 열매를 맺게 한다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class CaveVinesBlock extends GrowingPlantBlock.GrowingPlantHead {

    public CaveVinesBlock(int block) { super(block, CAVE_VINES); }

    @Override
    protected boolean canGrowInto(int state) { return B.isAir(state); }

    @Override
    protected int bodyFromHead(int head, int body) { return CaveVines.BERRIES.with(body, CaveVines.BERRIES.is(head)); }

    @Override
    protected int growIntoState(int state, RandomSource random) {
        return CaveVines.BERRIES.with(super.growIntoState(state, random), random.nextFloat() < 0.11F);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) { return CaveVines.use(player, state, level, pos); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return !CaveVines.BERRIES.is(state); }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlock(pos, CaveVines.BERRIES.with(state, true), 2);
    }
}
