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
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.entity.ItemStack;

public class MovingPistonBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), TYPE = B.prop("type");
    private static final Prop EXTENDED = B.prop("extended");

    public MovingPistonBlock(int block) { super(block); }

    /** onRemove: 다른 블록이 되면 움직이던 블록 엔티티를 바로 끝낸다(super 를 부르지 않는다 — 바닐라 그대로). */
    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m) m.finalTick(level);
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

    /** getDrops: 옮기던 블록의 드롭(블록 엔티티가 없으면 없음). */
    @Override
    public java.util.List<ItemStack> getDrops(int state, BlockLevel level, Pos pos, Object blockEntity, BlockActor entity, ItemStack tool,
                                              Float explosionRadius) {
        if (!(level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m)) return java.util.List.of();
        int moved = m.getMovedState();
        return B.behavior(moved).getDrops(moved, level, pos, null, entity, tool, explosionRadius);
    }

    /** getShape 는 빈 모양, 충돌 모양은 블록 엔티티의 이동 중 모양. */
    @Override
    public Shape getShape(int state, BlockLevel level, Pos pos) { return Shape.EMPTY; }

    @Override
    public Shape getCollisionShape(int state, BlockLevel level, Pos pos, BlockActor entity) {
        return level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m ? m.getCollisionShape(level, pos) : Shape.EMPTY;
    }
}
