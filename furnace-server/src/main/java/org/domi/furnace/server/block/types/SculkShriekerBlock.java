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

    // TODO(엔티티): stepOn — 밟은 플레이어(SculkShriekerBlockEntity.tryGetPlayer)로 tryShriek.

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(블록 엔티티): 비명 중이면 SculkShriekerBlockEntity.tryRespond.
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (SHRIEKING.is(state)) {
            level.setBlock(pos, SHRIEKING.with(state, false), 3);
            // TODO(블록 엔티티): SculkShriekerBlockEntity.tryRespond.
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
