// 바닐라 HayBlock: 기둥. 떨어진 엔티티의 낙하 피해를 0.2 배로(fallOn).
package org.domi.furnace.server.block.types;

public class HayBlock extends RotatedPillarBlock {

    public HayBlock(int block) { super(block); }

    @Override
    public void fallOn(org.domi.furnace.server.block.BlockLevel level, int state, org.domi.furnace.server.block.Pos pos,
                       org.domi.furnace.server.entity.Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 0.2F, org.domi.furnace.server.entity.Entity.Damage.FALL);
    }
}
