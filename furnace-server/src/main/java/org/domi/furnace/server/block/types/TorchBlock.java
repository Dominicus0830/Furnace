// 바닐라 TorchBlock: 서 있는 횃불(서버 동작은 BaseTorchBlock 그대로, 연기 입자는 클라).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.TorchBlock")
public class TorchBlock extends BaseTorchBlock {

    public TorchBlock(int block) { super(block); }
}
