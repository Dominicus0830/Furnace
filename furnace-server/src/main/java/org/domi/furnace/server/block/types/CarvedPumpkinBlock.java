// 바닐라 CarvedPumpkinBlock(깎은 호박·잭오랜턴): 바라보는 방향의 반대로 놓인다. 눈·철 골렘 모양 위에 놓이면 골렘이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {

    public CarvedPumpkinBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) trySpawnGolem(level, pos);
    }

    private static void trySpawnGolem(BlockLevel level, Pos pos) {
        // TODO(엔티티): 눈 골렘(호박 + 눈 블록 2) / 철 골렘(T자 철 블록) 모양이면 블록을 지우고(2001) 골렘 생성, 발전 과제 summoned_entity.
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]); }
}
