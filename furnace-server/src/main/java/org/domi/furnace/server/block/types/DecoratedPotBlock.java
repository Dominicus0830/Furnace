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
import org.domi.furnace.server.block.entity.DecoratedPotBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.Stacks;

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
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new DecoratedPotBlockEntity(pos, state); }


    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity be)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        ItemStack in = be.theItem(level);
        if (!stack.isEmpty() && (in.isEmpty() || Stacks.isSameItemSameComponents(in, stack) && in.count() < Stacks.maxStack(in))) {
            be.wobble(DecoratedPotBlockEntity.WOBBLE_POSITIVE, level);
            player.awardStat("minecraft:used:" + ItemTools.itemName(stack));
            ItemStack one = stack.withCount(1);   // consumeAndReturn(1, player)
            if (!player.isCreative()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            float fill;
            if (in.isEmpty()) {
                be.setTheItem(one, level);
                fill = (float) one.count() / Stacks.maxStack(one);
            } else {
                ItemStack grown = in.withCount(in.count() + 1);
                be.setTheItem(grown, level);
                fill = (float) grown.count() / Stacks.maxStack(grown);
            }
            level.playSound(null, pos, "minecraft:block.decorated_pot.insert", "blocks", 1.0F, 0.7F + 0.5F * fill);
            level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("dust_plume"), pos.x() + 0.5, pos.y() + 1.2, pos.z() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
            be.setChanged(level);
            level.gameEvent("minecraft:block_change", pos, player, state);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity be)) return InteractionResult.PASS;
        level.playSound(null, pos, "minecraft:block.decorated_pot.insert_fail", "blocks", 1.0F, 1.0F);
        be.wobble(DecoratedPotBlockEntity.WOBBLE_NEGATIVE, level);
        level.gameEvent("minecraft:block_change", pos, player, state);
        return InteractionResult.SUCCESS;
    }

    /** Containers.dropContentsOnDestroy: 다른 블록이 되면 내용물을 쏟는다(열지 않은 전리품은 먼저 채운다). */
    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity be) {
            be.theItem(level);
            level.dropContents(pos, be.items);
            level.updateNeighbourForOutputSignal(pos, block);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // getDrops 는 바닐라처럼 전리품 표(blocks/decorated_pot — 금 간 단지는 동적 항목 minecraft:sherds, 아니면 pot_decorations 복사)에 맡긴다.
    // 동적 항목은 ServerCore.blockDrops 가 LootParams.sherds 로 이어 준다(데이터 팩이 표를 고치면 그대로 따른다).

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        ItemStack tool = player.itemInHand(BlockActor.MAIN_HAND);
        int s = state;
        // 섬세한 손길 등 #prevents_decorated_pot_shattering 인챈트가 있으면 금 가지 않고 통째로 나온다
        if (B.holdsTag(tool, "minecraft:breaks_decorated_pots") && !ItemTools.hasEnchantmentTag(tool, "minecraft:prevents_decorated_pot_shattering")) {
            s = CRACKED.with(state, true);
            level.setBlock(pos, s, 4);
        }
        return super.playerWillDestroy(level, pos, s, player);
    }

    /** onProjectileHit: 투사체가 mayInteract·mayBreak 면 금 간 상태로 바꾸고(플래그 4) 부순다 — 조각이 떨어진다. */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        Pos pos = hit.blockPos();
        if (projectile.mayInteract(level, pos) && projectile.mayBreak(level)) {
            level.setBlock(pos, CRACKED.with(state, true), BlockLevel.UPDATE_INVISIBLE);
            level.destroyBlockByEntity(pos, true, projectile);
        }
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity be ? ChestBlock.redstoneSignalFromContainer(java.util.List.<ItemStack[]>of(be.items)) : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }
}
