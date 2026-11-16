// 바닐라 MyceliumBlock: 균사체. 퍼지는 흙과 같고, 포자 파티클은 클라 쪽.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.MyceliumBlock")
public class MyceliumBlock extends SpreadingSnowyDirtBlock {

    public MyceliumBlock(int block) { super(block); }
}
