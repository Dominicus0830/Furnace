// 바닐라 FallingBlock: 놓이거나 이웃이 바뀌면 2틱 뒤 아래가 비었는지 보고 떨어지는 블록 엔티티가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public abstract class FallingBlock extends BlockBehavior {

    protected FallingBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { level.scheduleTick(pos, block, delayAfterPlace()); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        level.scheduleTick(pos, block, delayAfterPlace());
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (isFree(level.getBlockState(pos.below())) && pos.y() >= level.minBuildHeight()) {
            // TODO(엔티티): FallingBlockEntity.fall — 칸을 유체(없으면 공기)로 바꾸고 떨어지는 블록 엔티티를 만든다(falling(entity) 훅).
        }
    }

    protected int delayAfterPlace() { return 2; }

    /** isFree: 공기·불·액체·바뀔 수 있는 칸. */
    public static boolean isFree(int state) {
        return B.isAir(state) || B.isTag(state, "minecraft:fire") || B.isLiquid(state) || INFO.replaceable(state);
    }
}
