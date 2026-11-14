// 바닐라 HoneyBlock: 옆면에 붙은 엔티티를 천천히 미끄러져 내리게 하고(-0.05), 낙하 피해를 0.2 배로 줄인다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;

public class HoneyBlock extends HalfTransparentBlock {

    public HoneyBlock(int block) { super(block); }

    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, Entity entity, float fallDistance) {
        entity.playSound("minecraft:block.honey_block.slide", 1.0F, 1.0F);
        entity.broadcastEntityEvent((byte) 54);
        if (entity.causeFallDamage(fallDistance, 0.2F, org.domi.furnace.server.entity.Entity.Damage.FALL)) {
            BlockInfo info = BlockInfo.get();
            entity.playSound(info.fallSound(block), info.soundVolume(block) * 0.5F, info.soundPitch(block) * 0.75F);
        }
    }

    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, Entity entity) {
        if (isSlidingDown(pos, entity)) {
            // maybeDoSlideAchievement 는 플레이어만(Region 의 플레이어 쪽) — 여기는 doSlideMovement → maybeDoSlideEffects
            if (entity.vy() < -0.13) {
                double f = -0.05 / entity.vy();
                entity.setDeltaMovement(entity.vx() * f, -0.05, entity.vz() * f);
            } else {
                entity.setDeltaMovement(entity.vx(), -0.05, entity.vz());
            }
            entity.resetFallDistance();
            if (doesEntityDoHoneyBlockSlideEffects(entity)) {
                if (level.random().nextInt(5) == 0) entity.playSound("minecraft:block.honey_block.slide", 1.0F, 1.0F);
                if (level.random().nextInt(5) == 0) entity.broadcastEntityEvent((byte) 53);   // 꿀 미끄럼 입자
            }
        }
        super.entityInside(state, level, pos, entity);
    }

    /** doesEntityDoHoneyBlockSlideEffects: 생물·광산 수레·점화된 TNT·보트(플레이어는 따로 — 생물이라 늘 참). */
    public static boolean doesEntityDoHoneyBlockSlideEffects(Entity entity) {
        return entity.isLiving() || entity instanceof org.domi.furnace.server.entity.AbstractMinecart
                || entity instanceof org.domi.furnace.server.entity.PrimedTnt || entity instanceof org.domi.furnace.server.entity.Boat;
    }

    /**
     * maybeDoSlideEffects 의 플레이어 쪽: 레벨 난수 nextInt(5) 두 번 — 미끄럼 소리(자기 말고 주변에), 엔티티 이벤트 53(자기 포함).
     * 미끄러지는지는 {@link #isSlidingDown(Pos, boolean, double, double, double, double, double)} 로 부르는 쪽이 판정한다.
     */
    public static void playerSlideEffects(BlockLevel level, Runnable sound, Runnable particles) {
        if (level.random().nextInt(5) == 0) sound.run();
        if (level.random().nextInt(5) == 0) particles.run();
    }

    private static boolean isSlidingDown(Pos pos, Entity entity) {
        return isSlidingDown(pos, entity.onGround(), entity.x(), entity.y(), entity.z(), entity.vy(), entity.bbWidth());
    }

    /** HoneyBlock.isSlidingDown 의 값 꼴(플레이어는 Entity 가 아니라 값으로 받는다). */
    public static boolean isSlidingDown(Pos pos, boolean onGround, double x, double y, double z, double vy, double bbWidth) {
        if (onGround) return false;
        if (y > pos.y() + 0.9375 - 1.0E-7) return false;
        if (vy >= -0.08) return false;
        double dx = Math.abs(pos.x() + 0.5 - x), dz = Math.abs(pos.z() + 0.5 - z);
        double f = 0.4375 + (float) bbWidth / 2.0F;
        return dx + 1.0E-7 > f || dz + 1.0E-7 > f;
    }
}
