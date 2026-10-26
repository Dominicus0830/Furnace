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

    /** stepOn: 웅크리지 않은 생물에게 hot_floor 피해 1(서리 보행자 인챈트는 아직 없어 늘 해당 없음). */
    @Override
    public void stepOn(BlockLevel level, Pos pos, int state, org.domi.furnace.server.entity.Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof org.domi.furnace.server.entity.LivingEntity) {
            entity.hurt(org.domi.furnace.server.entity.Entity.Damage.HOT_FLOOR, 1.0F);
        }
    }

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
