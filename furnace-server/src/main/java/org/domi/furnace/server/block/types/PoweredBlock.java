// 바닐라 PoweredBlock: 레드스톤 블록. 모든 방향으로 신호 15.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

@VanillaPort("net.minecraft.world.level.block.PoweredBlock")
public class PoweredBlock extends BlockBehavior {

    public PoweredBlock(int block) { super(block); }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return 15; }
}
