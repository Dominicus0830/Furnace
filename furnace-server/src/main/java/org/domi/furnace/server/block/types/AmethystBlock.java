// 바닐라 AmethystBlock: 자수정 블록 바탕. 투사체가 맞으면 소리(서버 동작은 그것뿐).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class AmethystBlock extends BlockBehavior {

    public AmethystBlock(int block) { super(block); }

    /** onProjectileHit: amethyst_block.hit 다음 chime(높이 0.5 + 레벨 난수 nextFloat × 1.2 를 소리마다 따로). */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        Pos pos = hit.blockPos();
        level.playSound(null, pos, "minecraft:block.amethyst_block.hit", "blocks", 1.0F, 0.5F + level.random().nextFloat() * 1.2F);
        level.playSound(null, pos, "minecraft:block.amethyst_block.chime", "blocks", 1.0F, 0.5F + level.random().nextFloat() * 1.2F);
    }
}
