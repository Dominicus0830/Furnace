// 바닐라 AmethystBlock: 자수정 블록 바탕. 투사체가 맞으면 소리(서버 동작은 그것뿐).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;

public class AmethystBlock extends BlockBehavior {

    public AmethystBlock(int block) { super(block); }

    // TODO(엔티티): onProjectileHit — amethyst_block.hit·chime 소리(높이 0.5 + 레벨 난수 nextFloat × 1.2 씩 두 번).
}
