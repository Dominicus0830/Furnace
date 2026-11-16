// 바닐라 WeepingVinesPlantBlock: 눈물 덩굴 몸통.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.WeepingVinesPlantBlock")
public class WeepingVinesPlantBlock extends GrowingPlantBlock.GrowingPlantBody {

    public WeepingVinesPlantBlock(int block) { super(block, WEEPING); }
}
