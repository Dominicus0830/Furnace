// 바닐라 SoulSandBlock: 영혼 모래. 놓이거나 위가 물이 되면 20틱 뒤 위로 거품 기둥을 세운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.CollisionContext;

import org.domi.furnace.server.block.Shape;


import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class SoulSandBlock extends BlockBehavior {

    private static final int WATER = B.id("water");

    public SoulSandBlock(int block) { super(block); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { BubbleColumnBlock.updateColumn(level, pos.above(), state); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.UP && B.is(neighborState, WATER)) level.scheduleTick(pos, block, 20);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { level.scheduleTick(pos, block, 20); }

    /** getVisualShape: 꽉 찬 블록. */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, CollisionContext ctx) { return Shape.BLOCK; }
}
