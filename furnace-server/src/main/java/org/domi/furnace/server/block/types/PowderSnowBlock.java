// 바닐라 PowderSnowBlock: 가루눈. 엔티티를 가두고(0.9, 1.5, 0.9) 불을 끈다 — 불붙은 엔티티가 들어오면(mobGriefing) 녹는다.
// TODO(엔티티): 충돌 모양이 엔티티마다 다르다(가죽 장화로 걷기·2.5 넘게 떨어지면 0.9 높이) — 지금 충돌 문맥에는 엔티티가 없다.
// 양동이로 뜨기는 BucketPickup. 블록 동작은 그 밖에 없다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Shape;

import org.domi.furnace.server.block.BlockActor;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

import org.domi.furnace.server.block.BlockBehavior;

public class PowderSnowBlock extends BlockBehavior {

    public PowderSnowBlock(int block) { super(block); }

    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (!entity.isLiving() || B.is(entity.inBlockState(), block)) entity.makeStuckInBlock(0.9F, 1.5, 0.9F);
        entity.setIsInPowderSnow(true);
        if (entity.isOnFire() && level.gameRule("mobGriefing") && entity.mayInteract(level, pos)) level.destroyBlock(pos, false);
        entity.setSharedFlagOnFire(false);
    }

    /** getVisualShape: 빈 모양. */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return Shape.EMPTY; }
}
