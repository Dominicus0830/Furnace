// 바닐라 WitherSkullBlock: 위더 해골. 영혼 모래·흙 T자 위에 세 개가 모이면 위더(엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class WitherSkullBlock extends SkullBlock {

    public WitherSkullBlock(int block) { super(block); }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) { checkSpawn(level, pos); }

    /** checkSpawn: TODO(엔티티) — 평화로움이 아니고 #wither_summon_base_blocks T자 위에 위더 해골 셋이면 블록을 지우고 위더 생성. */
    static void checkSpawn(BlockLevel level, Pos pos) {}
}
