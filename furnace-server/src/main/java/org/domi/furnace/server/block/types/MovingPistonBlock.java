// 바닐라 MovingPistonBlock: 움직이는 피스톤(밀리는 중인 블록 자리). 이동은 블록 엔티티(PistonMovingBlockEntity)가 2틱 동안 처리한다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class MovingPistonBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), TYPE = B.prop("type");
    private static final Prop EXTENDED = B.prop("extended");

    public MovingPistonBlock(int block) { super(block); }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(3-4e 피스톤): 다른 블록이 되면 PistonMovingBlockEntity.finalTick — 옮기던 블록을 그 자리에 놓는다.
    }

    /** destroy: 이 자리가 부서지면 뒤의 늘어난 피스톤 몸통을 없앤다. */
    @Override
    public void destroy(BlockLevel level, Pos pos, int state) {
        Pos behind = pos.relative(Dir.opposite(Dir.byName(FACING.valueName(state))));
        int s = level.getBlockState(behind);
        if (B.behavior(s) instanceof PistonBaseBlock && EXTENDED.is(s)) level.removeBlock(behind, false);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (level.getBlockEntity(pos) == null) {
            level.removeBlock(pos, false);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    // TODO(3-4e 피스톤): newMovingBlockEntity(pos, state, movedState, direction, extending, isSourcePiston)·getDrops(옮기던 블록의 드롭)·
    //   getCollisionShape(블록 엔티티의 이동 중 모양).
}
