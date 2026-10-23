// 바닐라 MagmaBlock: 마그마 블록. 위에 물이 오면 20틱 뒤 아래로 끄는 거품 기둥을 만든다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class MagmaBlock extends BlockBehavior {

    private static final int WATER = B.id("water");

    public MagmaBlock(int block) { super(block); }

    // TODO(엔티티): stepOn — 웅크리지 않은(서리 보행자 신발 없는) 생물에게 hotFloor 피해 1.

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { BubbleColumnBlock.updateColumn(level, pos.above(), state); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.UP && B.is(neighborState, WATER)) level.scheduleTick(pos, block, 20);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { level.scheduleTick(pos, block, 20); }
}
