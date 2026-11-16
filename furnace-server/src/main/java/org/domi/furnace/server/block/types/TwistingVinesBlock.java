// 바닐라 TwistingVinesBlock: 뒤틀린 덩굴 머리(위로 자람).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.block.RandomSource;

@VanillaPort("net.minecraft.world.level.block.TwistingVinesBlock")
public class TwistingVinesBlock extends GrowingPlantBlock.GrowingPlantHead {

    public TwistingVinesBlock(int block) { super(block, TWISTING); }

    @Override
    protected boolean canGrowInto(int state) { return NetherVines.isValidGrowthState(state); }

    @Override
    protected int blocksToGrowWhenBonemealed(RandomSource random) { return NetherVines.blocksToGrowWhenBonemealed(random); }
}
