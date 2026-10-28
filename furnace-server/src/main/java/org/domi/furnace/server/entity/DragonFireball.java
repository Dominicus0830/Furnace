// 바닐라 DragonFireball: 엔더 드래곤의 숨결 화염구. 소유자가 아닌 것에 맞으면 드래곤의 숨결 구름(반경 3 → 7 로 커짐, 600틱, 즉시 피해 효과)을 남긴다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;

public class DragonFireball extends AbstractHurtingProjectile {
    public DragonFireball(ChunkLevel level, int id) { super(EntityType.DRAGON_FIREBALL, level, id); }

    @Override public boolean hurt(Damage source, float amount) { return false; }

    public void onHitForTest() { onHit(new Hit(null, null, 0, x(), y(), z())); }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        if (hit.isEntity() && ownedBy(hit.target())) return;
        List<Entity> near = level.getEntities(this, boundingBox().inflate(4.0, 2.0, 4.0), e -> e instanceof LivingEntity);
        AreaEffectCloud cloud = new AreaEffectCloud(level, level.nextEntityId());
        cloud.moveTo(x(), y(), z(), 0.0F, 0.0F);
        Targetable owner = getOwner();
        if (owner instanceof LivingEntity) cloud.setOwner(owner);
        cloud.setParticle(new int[] {AreaEffectCloud.DRAGON_BREATH_PARTICLE});
        cloud.setRadius(3.0F);
        cloud.setDuration(600);
        cloud.setRadiusPerTick((7.0F - cloud.getRadius()) / cloud.durationTicks());
        cloud.setEffects(List.of(new MobEffectInstance(MobEffect.INSTANT_DAMAGE, 1, 1)), 0);
        cloud.setParticle(new int[] {AreaEffectCloud.DRAGON_BREATH_PARTICLE});
        // 바닐라 getEntitiesOfClass(LivingEntity, NO_SPECTATORS)는 플레이어도 담는다. 순서는 엔티티 뒤에 플레이어(가정: 바닐라는 섹션 순 한 목록)
        java.util.List<double[]> cands = new java.util.ArrayList<>();
        for (Entity e : near) cands.add(new double[] {e.x(), e.y(), e.z()});
        level.forEachPlayerIn(boundingBox().inflate(4.0, 2.0, 4.0), p -> {
            if (p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) cands.add(new double[] {p.x(), p.y(), p.z()});
        });
        for (double[] c : cands) {
            double dx = c[0] - x(), dy = c[1] - y(), dz = c[2] - z();
            if (dx * dx + dy * dy + dz * dz < 16.0) {
                cloud.moveTo(c[0], c[1], c[2], 0.0F, 0.0F);
                break;
            }
        }
        level.levelEvent(null, 2006, blockPosition(), isSilent() ? -1 : 1);
        level.addFreshEntity(cloud);
        discard();
    }
}
