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
    public void attack(int state, BlockLevel level, Pos pos, BlockActor player) { interact(state, level, pos, player == null ? null : player.handle()); }

    /** stepOn: 웅크리지 않은 엔티티가 밟으면 켠다(플레이어는 PlayerMovementEmission 이 {@link #stepOnBy} 로). */
    @Override
    public void stepOn(BlockLevel level, Pos pos, int state, org.domi.furnace.server.entity.Entity entity) {
        stepOnBy(level, pos, state, entity.isSteppingCarefully(), entity);
        super.stepOn(level, pos, state, entity);
    }

    /** who: 밟은 엔티티나 플레이어(EntityChangeBlockEvent 의 주체). */
    public static void stepOnBy(BlockLevel level, Pos pos, int state, boolean steppingCarefully, org.domi.furnace.server.entity.Rider who) {
        if (!steppingCarefully) interact(state, level, pos, who);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        interact(state, level, pos, player == null ? null : player.handle());
        boolean blockItem = !stack.isEmpty() && BlockItems.entry(stack.itemId()) != null;
        return blockItem && new PlaceContext(level, player, hand, stack, hit).canPlace()
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SUCCESS;
    }

    /** who: 켠 엔티티·플레이어(행위자 본체) — CraftBukkit 은 켜기 전에 EntityChangeBlockEvent 를 묻고, 막으면 켜지 않는다. */
    private static void interact(int state, BlockLevel level, Pos pos, Object who) {
        if (LIT.is(state)) return;
        if (who instanceof org.domi.furnace.server.entity.Rider r && !org.domi.furnace.server.block.B.entityChange(level, r, pos, LIT.with(state, true))) return;
        level.setBlock(pos, LIT.with(state, true), 3);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return LIT.is(state); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (LIT.is(state)) level.setBlock(pos, LIT.with(state, false), 3);
    }

    @Override
    public void spawnAfterBreak(int state, BlockLevel level, Pos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) tryDropExperience(level, pos, tool, 1, 5);
    }
}
