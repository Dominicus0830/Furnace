// 바닐라 MudBlock: 진흙. 충돌 모양이 조금 낮다(모양 표에 반영) — 서버 블록 동작 없음.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Shape;

import org.domi.furnace.server.block.Pos;

import org.domi.furnace.server.block.BlockLevel;

import org.domi.furnace.server.block.BlockActor;

import org.domi.furnace.server.block.BlockBehavior;

public class MudBlock extends BlockBehavior {

    public MudBlock(int block) { super(block); }

    /** getVisualShape: 꽉 찬 블록. */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return Shape.BLOCK; }
}
