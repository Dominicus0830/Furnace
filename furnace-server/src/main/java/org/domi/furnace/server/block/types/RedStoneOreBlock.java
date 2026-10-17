// 바닐라 RedStoneOreBlock: 레드스톤 광석. 때리거나·밟거나·쓰면 빛난다(lit). 무작위 틱에 꺼진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class RedStoneOreBlock extends BlockBehavior {

    private static final Prop LIT = B.prop("lit");

    public RedStoneOreBlock(int block) { super(block); }

    @Override
    public void attack(int state, BlockLevel level, Pos pos, BlockActor player) { interact(state, level, pos); }

    // TODO(엔티티): stepOn — 웅크리지 않은 엔티티가 밟으면 interact.

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        interact(state, level, pos);
        boolean blockItem = !stack.isEmpty() && BlockItems.entry(stack.itemId()) != null;
        return blockItem && new PlaceContext(level, player, hand, stack, hit).canPlace()
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SUCCESS;
    }

    private static void interact(int state, BlockLevel level, Pos pos) {
        if (!LIT.is(state)) level.setBlock(pos, LIT.with(state, true), 3);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return LIT.is(state); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (LIT.is(state)) level.setBlock(pos, LIT.with(state, false), 3);
    }

    // TODO(드롭): spawnAfterBreak — 섬세한 손길이 없으면 경험치 1~5.
}
