// 바닐라 CandleCakeBlock: 양초 꽂은 케이크. 위쪽(양초)을 빈손으로 누르면 끄고, 그 밖에는 케이크를 한 입 먹고 양초를 떨군다.
package org.domi.furnace.server.block.types;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class CandleCakeBlock extends AbstractCandleBlock {

    private static final Map<Integer, Integer> BY_CANDLE = new ConcurrentHashMap<>();
    private static final int CAKE = B.id("cake");

    private final int candle;

    public CandleCakeBlock(int block) {
        super(block);
        this.candle = B.id(Blocks.get().params(block).get("candle").getAsString());
        BY_CANDLE.put(candle, block);
    }

    public int candle() { return candle; }

    /** byCandle(양초 블록): 그 양초 케이크의 기본 상태. */
    public static int byCandle(int candleBlock) { return B.defaultState(BY_CANDLE.get(candleBlock)); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (B.holds(stack, "flint_and_steel") || B.holds(stack, "fire_charge")) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (candleHit(hit) && stack.isEmpty() && LIT.is(state)) {
            extinguish(player, state, level, pos);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        InteractionResult r = CakeBlock.eat(level, pos, B.defaultState(CAKE), player);
        if (r.consumesAction()) {
            level.dropResources(state, pos);
        }
        return r;
    }

    private static boolean candleHit(Hit hit) { return hit.y() - hit.pos().y() > 0.5; }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.isSolid(level.getBlockState(pos.below())); }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return CakeBlock.outputSignal(0); }

    /** canLight: 꺼진 #candle_cakes. */
    public static boolean canLight(int state) { return B.isTag(state, "minecraft:candle_cakes") && LIT.has(state) && !LIT.is(state); }
}
