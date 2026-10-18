// 바닐라 SlimeBlock: 떨어진 엔티티를 튕기고(생물 1, 그 밖 0.8) 낙하 피해가 없다. 천천히 걸으면 미끄러지지 않게 붙잡는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;

public class SlimeBlock extends HalfTransparentBlock {

    public SlimeBlock(int block) { super(block); }

    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, Entity entity, float fallDistance) {
        if (entity.isSuppressingBounce()) super.fallOn(level, state, pos, entity, fallDistance);
        else entity.causeFallDamage(fallDistance, 0.0F);
    }

    @Override
    public void updateEntityAfterFallOn(BlockLevel level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else if (entity.vy() < 0.0) {
            double f = entity.isLiving() ? 1.0 : 0.8;
            entity.setDeltaMovement(entity.vx(), -entity.vy() * f, entity.vz());
        }
    }

    @Override
    public void stepOn(BlockLevel level, Pos pos, int state, Entity entity) {
        double d = Math.abs(entity.vy());
        if (d < 0.1 && !entity.isSteppingCarefully()) {
            double e = 0.4 + d * 0.2;
            entity.setDeltaMovement(entity.vx() * e, entity.vy() * 1.0, entity.vz() * e);
        }
        super.stepOn(level, pos, state, entity);
    }
}
