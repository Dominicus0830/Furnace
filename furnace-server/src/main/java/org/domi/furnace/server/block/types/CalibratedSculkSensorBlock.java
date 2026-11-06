// 바닐라 CalibratedSculkSensorBlock: 방향이 있는 조각 감지기. 뒤쪽 신호 세기로 주파수를 고른다(블록 엔티티). 쿨다운이 10틱.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class CalibratedSculkSensorBlock extends SculkSensorBlock {

    private static final Prop FACING = B.prop("facing");

    public CalibratedSculkSensorBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(super.getStateForPlacement(ctx), Dir.NAME[ctx.horizontalDirection()]);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        return dir != Dir.byName(FACING.valueName(state)) ? super.getSignal(state, level, pos, dir) : 0;
    }

    @Override
    protected int activeTicks() { return 10; }

    /** FACING 방향 번호. */
    public static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return org.domi.furnace.server.block.entity.SculkSensorBlockEntity.calibrated(pos, state);
    }
}
