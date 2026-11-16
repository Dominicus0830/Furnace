// 바닐라 ColoredFallingBlock: 모래·자갈 등 — 떨어지는 블록(먼지 입자 색만 다르다).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.ColoredFallingBlock")
public class ColoredFallingBlock extends FallingBlock {

    public ColoredFallingBlock(int block) { super(block); }
}
