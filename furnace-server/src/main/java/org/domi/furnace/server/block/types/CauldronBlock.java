// 바닐라 CauldronBlock: 빈 가마솥. 비(5%)·눈(10%)을 받거나 종유석에서 물·용암이 떨어지면 그 가마솥으로 바뀐다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;

public class CauldronBlock extends AbstractCauldronBlock {

    private static final int WATER_CAULDRON = B.id("water_cauldron"), LAVA_CAULDRON = B.id("lava_cauldron"),
            POWDER_SNOW_CAULDRON = B.id("powder_snow_cauldron");

    public CauldronBlock(int block) { super(block, CauldronInteraction.EMPTY); }

    @Override
    public boolean isFull(int state) { return false; }

    /** shouldHandlePrecipitation: 비 5%, 눈 10%(레벨 난수). */
    static boolean shouldHandlePrecipitation(BlockLevel level, String precipitation) {
        return switch (precipitation) {
            case "rain" -> level.random().nextFloat() < 0.05F;
            case "snow" -> level.random().nextFloat() < 0.1F;
            default -> false;
        };
    }

    @Override
    public void handlePrecipitation(int state, BlockLevel level, Pos pos, String precipitation) {
        if (!shouldHandlePrecipitation(level, precipitation)) return;
        if (precipitation.equals("rain")) {
            level.setBlockAndUpdate(pos, B.defaultState(WATER_CAULDRON));
            level.gameEvent("minecraft:block_change", pos, null, -1);
        } else if (precipitation.equals("snow")) {
            level.setBlockAndUpdate(pos, B.defaultState(POWDER_SNOW_CAULDRON));
            level.gameEvent("minecraft:block_change", pos, null, -1);
        }
    }

    @Override
    protected boolean canReceiveStalactiteDrip(int fluid) { return true; }

    @Override
    protected void receiveStalactiteDrip(int state, BlockLevel level, Pos pos, int fluid) {
        if (fluid == Fluids.T_WATER) {
            int s = B.defaultState(WATER_CAULDRON);
            level.setBlockAndUpdate(pos, s);
            level.gameEvent("minecraft:block_change", pos, null, s);
            level.levelEvent(null, 1047, pos, 0);
        } else if (fluid == Fluids.T_LAVA) {
            int s = B.defaultState(LAVA_CAULDRON);
            level.setBlockAndUpdate(pos, s);
            level.gameEvent("minecraft:block_change", pos, null, s);
            level.levelEvent(null, 1046, pos, 0);
        }
    }
}
