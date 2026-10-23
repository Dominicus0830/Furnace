// 바닐라 TntBlock: 신호를 받거나 불·부싯돌로 점화되면 점화된 TNT 엔티티가 된다. unstable 이면 부수기만 해도 점화.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class TntBlock extends BlockBehavior {

    private static final Prop UNSTABLE = B.prop("unstable");

    public TntBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state)) && level.hasNeighborSignal(pos)) {
            prime(level, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (level.hasNeighborSignal(pos)) {
            prime(level, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (!player.isCreative() && UNSTABLE.is(state)) prime(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // TODO(폭발·엔티티): wasExploded — 폭발에 휘말리면 짧은 도화선의 점화된 TNT.

    public static void prime(BlockLevel level, Pos pos) { explode(level, pos, null); }

    /** explode: 점화된 TNT 엔티티(생성 때 레벨 난수 1회 — 처음 튀는 방향) + 소리 + PRIME_FUSE. TODO(엔티티): 엔티티 생성. */
    private static void explode(BlockLevel level, Pos pos, BlockActor igniter) {
        level.random().nextDouble();
        level.playSound(null, pos.x() + 0.5, pos.y(), pos.z() + 0.5, "minecraft:entity.tnt.primed", "blocks", 1.0F, 1.0F);
        level.gameEvent("minecraft:prime_fuse", pos, igniter, -1);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (!B.holds(stack, "minecraft:flint_and_steel") && !B.holds(stack, "minecraft:fire_charge")) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        explode(level, pos, player);
        level.setBlock(pos, B.defaultState("air"), 11);
        // TODO(아이템): 부싯돌 내구도 1(hurtAndBreak) / 화염구 1개 소모(consume), 통계 used
        return ItemInteractionResult.CONSUME;
    }

    // TODO(엔티티): onProjectileHit — 불붙은 투사체에 맞으면 점화.
}
