// 바닐라 TwistingVinesPlantBlock: 뒤틀린 덩굴 몸통.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.TwistingVinesPlantBlock")
public class TwistingVinesPlantBlock extends GrowingPlantBlock.GrowingPlantBody {

    public TwistingVinesPlantBlock(int block) { super(block, TWISTING); }
}
