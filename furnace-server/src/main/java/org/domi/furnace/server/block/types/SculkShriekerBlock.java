// 바닐라 SculkShriekerBlock: 스컬크 비명체. 비명(shrieking)이 켜지면 다음 예약 틱에 끈다. 진동 응답·경고 단계는 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SculkShriekerBlock extends BaseEntityBlock {

    static final Prop SHRIEKING = B.prop("shrieking"), CAN_SUMMON = B.prop("can_summon");

    public SculkShriekerBlock(int block) { super(block); }

    /** stepOn: 밟은 것에서 플레이어를 찾아(tryGetPlayer) 비명. */
    @Override
    public void stepOn(BlockLevel level, Pos pos, int state, org.domi.furnace.server.entity.Entity entity) {
        stepOnBy(level, pos, state, entity);
        super.stepOn(level, pos, state, entity);
    }

    public static void stepOnBy(BlockLevel level, Pos pos, int state, Object entity) {
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) {
            var p = org.domi.furnace.server.block.entity.SculkShriekerBlockEntity.tryGetPlayer(entity);
            if (p != null && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SculkShriekerBlockEntity be) be.tryShriek(cl, p);
        }
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.SculkShriekerBlockEntity(pos, state);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl && SHRIEKING.is(state) && !B.is(newState, block)
                && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SculkShriekerBlockEntity be) {
            be.tryRespond(cl);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (SHRIEKING.is(state)) {
            level.setBlock(pos, SHRIEKING.with(state, false), 3);
            if (level instanceof org.domi.furnace.server.block.ChunkLevel cl
                    && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SculkShriekerBlockEntity be) be.tryRespond(cl);
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(defaultState(), B.waterAt(ctx)); }

    @Override
    public void spawnAfterBreak(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) tryDropExperience(level, pos, tool, 5, 5);
    }
}
