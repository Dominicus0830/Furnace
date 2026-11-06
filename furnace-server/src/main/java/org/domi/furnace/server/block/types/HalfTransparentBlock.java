// 바닐라 HalfTransparentBlock: 같은 블록끼리 맞닿은 면을 그리지 않는다(클라 skipRendering) — 서버 동작 없음.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;

public class HalfTransparentBlock extends BlockBehavior {

    public HalfTransparentBlock(int block) { super(block); }
}
