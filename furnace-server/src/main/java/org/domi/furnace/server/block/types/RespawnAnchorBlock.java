// 바닐라 RespawnAnchorBlock: 리스폰 정박기. 발광석으로 충전(0~4). 네더에서만 리스폰 지점, 다른 차원에서 쓰면 터진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class RespawnAnchorBlock extends org.domi.furnace.server.block.BlockBehavior {

    static final Prop CHARGE = B.prop("charges");

    public RespawnAnchorBlock(int block) { super(block); }

    private static boolean isChargeFuel(ItemStack s) { return B.holds(s, "minecraft:glowstone"); }

    private static boolean canBeCharged(int state) { return CHARGE.intValue(state) < 4; }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (isChargeFuel(stack) && canBeCharged(state)) {
            charge(player, level, pos, state);
            if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            return ItemInteractionResult.CONSUME;
        }
        return hand == BlockActor.MAIN_HAND && isChargeFuel(player.itemInHand(BlockActor.OFF_HAND)) && canBeCharged(state)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (CHARGE.intValue(state) == 0) return InteractionResult.PASS;
        if (!canSetSpawn(level)) {
            level.removeBlock(pos, false);
            // TODO(폭발): 근처에 물이 있으면 불 없이·피해 계산 다르게(물 막힘) 하는 BAD_RESPAWN_POINT 폭발 5.0
            return InteractionResult.CONSUME;
        }
        // TODO(수면·리스폰): 플레이어 리스폰 지점이 이 칸이 아니면 설정(+ set_spawn 소리, SUCCESS)
        return InteractionResult.CONSUME;
    }

    /** canSetSpawn: 차원의 respawnAnchorWorks(네더만). */
    public static boolean canSetSpawn(BlockLevel level) { return "minecraft:the_nether".equals(level.dimension()); }

    /** charge: 충전 +1, BLOCK_CHANGE, 충전 소리. */
    public static void charge(BlockActor entity, BlockLevel level, Pos pos, int state) {
        int s = CHARGE.withInt(state, CHARGE.intValue(state) + 1);
        level.setBlock(pos, s, 3);
        level.gameEvent("minecraft:block_change", pos, entity, s);
        level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, "minecraft:block.respawn_anchor.charge", "blocks", 1.0F, 1.0F);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return scaledCharge(state, 15); }

    /** getScaledChargeLevel: floor(charge / 4 * max). */
    public static int scaledCharge(int state, int max) { return (int) Math.floor(CHARGE.intValue(state) / 4.0F * max); }
}
