// 바닐라 LayeredCauldronBlock: 물·가루눈 가마솥(level 1..3). 같은 날씨(비·눈)를 받으면 차오르고, 물은 종유석에서도 받는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class LayeredCauldronBlock extends AbstractCauldronBlock {

    static final Prop LEVEL = B.prop("level");
    private static final int CAULDRON = B.id("cauldron"), WATER_CAULDRON = B.id("water_cauldron");

    private final String precipitation;

    public LayeredCauldronBlock(int block) {
        super(block, CauldronInteraction.byName(Blocks.get().params(block).get("interactions").getAsString()));
        this.precipitation = Blocks.get().params(block).get("precipitation").getAsString();
    }

    @Override
    public boolean isFull(int state) { return LEVEL.intValue(state) == 3; }

    @Override
    protected boolean canReceiveStalactiteDrip(int fluid) { return fluid == Fluids.T_WATER && precipitation.equals("rain"); }

    @Override
    protected double getContentHeight(int state) { return (6.0 + LEVEL.intValue(state) * 3.0) / 16.0; }

    // TODO(엔티티): entityInside — 불붙은 엔티티가 들어오면 불을 끄고(가루눈이면 물 가마솥으로 바꿔) 한 단계 줄인다(handleEntityOnFireInside).

    /** lowerFillLevel: 한 단계 줄이고 0 이면 빈 가마솥. */
    public static void lowerFillLevel(int state, BlockLevel level, Pos pos) {
        int n = LEVEL.intValue(state) - 1;
        int s = n == 0 ? B.defaultState(CAULDRON) : LEVEL.withInt(state, n);
        level.setBlockAndUpdate(pos, s);
        level.gameEvent("minecraft:block_change", pos, null, s);
    }

    @Override
    public void handlePrecipitation(int state, BlockLevel level, Pos pos, String precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && LEVEL.intValue(state) != 3 && precipitation.equals(this.precipitation)) {
            int s = LEVEL.cycle(state);
            level.setBlockAndUpdate(pos, s);
            level.gameEvent("minecraft:block_change", pos, null, s);
        }
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return LEVEL.intValue(state); }

    @Override
    protected void receiveStalactiteDrip(int state, BlockLevel level, Pos pos, int fluid) {
        if (!isFull(state)) {
            int s = LEVEL.withInt(state, LEVEL.intValue(state) + 1);
            level.setBlockAndUpdate(pos, s);
            level.gameEvent("minecraft:block_change", pos, null, s);
            level.levelEvent(null, 1047, pos, 0);
        }
    }

    static int waterCauldronFull() { return LEVEL.withInt(B.defaultState(WATER_CAULDRON), 3); }
}
