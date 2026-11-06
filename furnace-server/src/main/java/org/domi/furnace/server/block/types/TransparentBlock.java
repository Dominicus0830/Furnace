// 바닐라 TransparentBlock(유리류): 빛을 막지 않고 시야 모양이 비어 있다(모양 표에 반영됨) — 서버 동작 없음.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Shape;

import org.domi.furnace.server.block.Pos;

import org.domi.furnace.server.block.BlockLevel;

import org.domi.furnace.server.block.BlockActor;

public class TransparentBlock extends HalfTransparentBlock {

    public TransparentBlock(int block) { super(block); }

    /** getVisualShape: 빈 모양(유리류는 시선을 막지 않는다). */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return Shape.EMPTY; }
}
