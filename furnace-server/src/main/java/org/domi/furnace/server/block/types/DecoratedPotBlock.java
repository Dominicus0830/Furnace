// 바닐라 DecoratedPotBlock: 장식 단지(facing·cracked·waterlogged). 아이템 한 종류를 담고(블록 엔티티), 깨는 도구로 부수면 금이 간다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class DecoratedPotBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), CRACKED = B.prop("cracked");

    public DecoratedPotBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = FACING.with(defaultState(), Dir.NAME[ctx.horizontalDirection()]);
        return CRACKED.with(WATERLOGGED.with(s, B.waterAt(ctx)), false);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // TODO(블록 엔티티): 비었거나 같은 아이템이면 하나 넣고(흔들림·소리 decorated_pot.insert, 높이 0.7 + 0.5 × 찬 비율) SUCCESS,
        //   아니면 PASS_TO_DEFAULT. 블록 엔티티가 없으면 바닐라도 SKIP_DEFAULT.
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(블록 엔티티): 흔들고 소리(decorated_pot.insert_fail)·BLOCK_CHANGE 후 SUCCESS. 블록 엔티티가 없으면 바닐라도 PASS.
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(블록 엔티티): Containers.dropContentsOnDestroy.
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        ItemStack tool = player.itemInHand(BlockActor.MAIN_HAND);
        int s = state;
        // TODO(마법 부여): 실크 터치 판정(EnchantmentHelper.hasTag(PREVENTS_DECORATED_POT_SHATTERING)) — 지금은 없다고 본다.
        if (B.holdsTag(tool, "minecraft:breaks_decorated_pots")) {
            s = CRACKED.with(state, true);
            level.setBlock(pos, s, 4);
        }
        return super.playerWillDestroy(level, pos, s, player);
    }

    // TODO(엔티티): onProjectileHit — 깰 수 있는 투사체면 금 가게 하고 부순다.

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(블록 엔티티)
}
