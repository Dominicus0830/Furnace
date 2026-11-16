// 바닐라 TintedGlassBlock: 빛을 모두 막는 유리(빛 계산 쪽 속성) — 서버 블록 동작 없음.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.TintedGlassBlock")
public class TintedGlassBlock extends TransparentBlock {

    public TintedGlassBlock(int block) { super(block); }
}
