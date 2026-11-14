// 바닐라 CaveVines: 동굴 덩굴 공통 — 발광 열매(berries) 따기.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

final class CaveVines {
    private CaveVines() {}

    static final Prop BERRIES = B.prop("berries");

    /** use: 열매가 있으면 발광 열매 1개를 떨구고 열매를 없앤다. */
    static InteractionResult use(BlockActor entity, int state, BlockLevel level, Pos pos) {
        if (!BERRIES.is(state)) return InteractionResult.PASS;
        // CraftBukkit: EntityChangeBlockEvent 를 막으면 따지 않고 SUCCESS
        if (!B.entityChange(level, entity, pos, BERRIES.with(state, false))) return InteractionResult.SUCCESS;
        level.popResource(pos, B.stack("minecraft:glow_berries", 1));
        float pitch = level.random().nextFloat() * (1.2F - 0.8F) + 0.8F;
        level.playSound(null, pos, "minecraft:block.cave_vines.pick_berries", "blocks", 1.0F, pitch);
        int s = BERRIES.with(state, false);
        level.setBlock(pos, s, 2);
        level.gameEvent("minecraft:block_change", pos, entity, s);
        return InteractionResult.CONSUME;
    }
}
