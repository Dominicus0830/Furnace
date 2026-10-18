// 바닐라 WebBlock: 거미줄. 안에 든 엔티티를 느리게 한다(엔티티 동작뿐).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

import org.domi.furnace.server.block.BlockBehavior;

public class WebBlock extends BlockBehavior {

    public WebBlock(int block) { super(block); }

    /** entityInside: makeStuckInBlock(0.25, 0.05, 0.25) — 거미줄 짜기(WEAVING) 효과 생물이면 (0.5, 0.25, 0.5)(효과는 아직 없다). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        entity.makeStuckInBlock(0.25, 0.05F, 0.25);
    }
}
