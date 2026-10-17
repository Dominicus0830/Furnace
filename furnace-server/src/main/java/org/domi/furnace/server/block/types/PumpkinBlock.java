// 바닐라 PumpkinBlock: 가위로 깎으면 깎은 호박(클릭 면 쪽, 위아래 면이면 플레이어 반대쪽)이 되고 씨 4개를 떨군다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;

public class PumpkinBlock extends BlockBehavior {

    private static final int CARVED_PUMPKIN = B.id("carved_pumpkin");
    private static final Prop FACING = B.prop("facing");

    public PumpkinBlock(int block) { super(block); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (stack.isEmpty() || stack.itemId() != ItemContext.id("minecraft:item", "minecraft:shears")) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        int face = hit.face();
        int dir = Dir.axis(face) == Dir.AXIS_Y ? Dir.opposite(player.direction()) : face;
        level.playSound(null, pos, "minecraft:block.pumpkin.carve", "blocks", 1.0F, 1.0F);
        level.setBlock(pos, FACING.with(B.defaultState(CARVED_PUMPKIN), Dir.NAME[dir]), 11);
        // TODO(엔티티): 호박씨 4개 아이템 엔티티(면 쪽 0.65 앞). 속도 난수 두 번은 지금도 뽑아 난수 순서를 맞춘다.
        level.random().nextDouble();
        level.random().nextDouble();
        // TODO(아이템): 가위 내구도 1 소모(hurtAndBreak), 통계 used:shears
        level.gameEvent("minecraft:shear", pos, player, -1);
        return ItemInteractionResult.CONSUME;
    }
}
