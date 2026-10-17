// 바닐라 BeehiveBlock: 벌집·벌통(honey_level 0..5). 가득 차면 가위로 벌집 조각 3개, 유리병으로 꿀병을 얻고 비운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class BeehiveBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), HONEY_LEVEL = B.prop("honey_level");
    public static final int MAX_HONEY_LEVELS = 5;

    public BeehiveBlock(int block) { super(block); }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return HONEY_LEVEL.intValue(state); }

    // TODO(블록 엔티티·엔티티): playerDestroy — 실크 터치가 아니면 안의 벌을 화나게 내보내고 벌집 근처 벌들이 그 플레이어를 노린다.

    /** dropHoneycomb: 벌집 조각 3개. */
    public static void dropHoneycomb(BlockLevel level, Pos pos) { level.popResource(pos, B.stack("honeycomb", 3)); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        boolean used = false;
        if (HONEY_LEVEL.intValue(state) >= MAX_HONEY_LEVELS) {
            if (B.holds(stack, "shears")) {
                level.playSound(player, player.x(), player.y(), player.z(), "minecraft:block.beehive.shear", "blocks", 1.0F, 1.0F);
                dropHoneycomb(level, pos);
                // TODO(아이템): 가위 내구도 1(hurtAndBreak).
                used = true;
                level.gameEvent("minecraft:shear", pos, player, -1);
            } else if (B.holds(stack, "glass_bottle")) {
                ItemStack rest = stack.withCount(stack.count() - 1);
                level.playSound(player, player.x(), player.y(), player.z(), "minecraft:item.bottle.fill", "blocks", 1.0F, 1.0F);
                ItemStack honey = B.stack("honey_bottle", 1);
                if (rest.isEmpty()) {
                    player.setItemInHand(hand, honey);
                } else {
                    player.setItemInHand(hand, rest);
                    if (!player.addToInventory(honey)) player.drop(honey);
                }
                used = true;
                level.gameEvent("minecraft:fluid_pickup", pos, player, -1);
            }
            if (used) player.awardStat("minecraft:used");
        }
        if (!used) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!CampfireBlock.isSmokeyPos(level, pos)) {
            // TODO(블록 엔티티·엔티티): 안에 벌이 있으면 근처 벌을 화나게 하고 EMERGENCY 로 모두 내보낸다.
            resetHoneyLevel(level, state, pos);
        } else {
            resetHoneyLevel(level, state, pos);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    public void resetHoneyLevel(BlockLevel level, int state, Pos pos) { level.setBlock(pos, HONEY_LEVEL.withInt(state, 0), 3); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    // TODO(블록 엔티티): playerWillDestroy(크리에이티브에서 벌이 있으면 벌 포함 아이템 드롭)·getDrops(폭발 등으로 부서지면 벌 내보내기)·
    //   updateShape(이웃에 불이 붙으면 벌을 모두 내보낸다).
}
