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
        // 호박씨 4개(면 쪽 0.65 앞, 줍기 지연 없음): 생성자가 처음 속도로 난수 2번, 다시 정하는 속도에서 2번
        double x = pos.x() + 0.5 + Dir.DX[dir] * 0.65, y = pos.y() + 0.1, z = pos.z() + 0.5 + Dir.DZ[dir] * 0.65;
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) {
            var ie = new org.domi.furnace.server.entity.ItemEntity(cl, cl.nextEntityId(), x, y, z, B.stack("minecraft:pumpkin_seeds", 4));
            ie.setDeltaMovement(0.05 * Dir.DX[dir] + level.random().nextDouble() * 0.02, 0.05, 0.05 * Dir.DZ[dir] + level.random().nextDouble() * 0.02);
            cl.addFreshEntity(ie);
        } else {
            for (int i = 0; i < 4; i++) level.random().nextDouble();
        }
        org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);   // 가위 내구도 1
        level.gameEvent("minecraft:shear", pos, player, -1);
        player.awardStat("minecraft:used:minecraft:shears");
        return ItemInteractionResult.CONSUME;
    }
}
