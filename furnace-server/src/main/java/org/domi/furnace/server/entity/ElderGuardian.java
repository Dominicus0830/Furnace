// 바닐라 ElderGuardian: 큰 가디언(체력 80, 공격력 8, 느림). 1200틱마다 50칸 안 생존 모드 플레이어에게 채굴 피로 III(5분)를 건다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.world.GameMode;

public class ElderGuardian extends Guardian {
    public ElderGuardian(ChunkLevel level, int id) {
        super(EntityType.ELDER_GUARDIAN, level, id);
        persistenceRequired = true;
        if (randomStrollGoal != null) randomStrollGoal.setInterval(400);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 8.0).add(Attribute.MAX_HEALTH, 80.0);
    }

    @Override
    public int getAttackDuration() { return 60; }

    @Override public String getAmbientSound() { return isInWaterOrBubble() ? "entity.elder_guardian.ambient" : "entity.elder_guardian.ambient_land"; }

    @Override protected String getHurtSound(Damage source) { return isInWaterOrBubble() ? "entity.elder_guardian.hurt" : "entity.elder_guardian.hurt_land"; }

    @Override protected String getDeathSound() { return isInWaterOrBubble() ? "entity.elder_guardian.death" : "entity.elder_guardian.death_land"; }

    @Override protected String getFlopSound() { return "entity.elder_guardian.flop"; }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if ((tickCount + id()) % 1200 == 0) {
            MobEffectInstance effect = new MobEffectInstance(MobEffect.MINING_FATIGUE, 6000, 2);
            double r2 = 50.0 * 50.0;
            level.forEachPlayer(p -> {
                if (p.gameMode() != GameMode.SURVIVAL && p.gameMode() != GameMode.ADVENTURE) return;
                if (isAlliedTo(p)) return;   // addEffectToPlayersAround: 출처의 아군은 제외
                double dx = p.x() - x(), dy = p.y() - y(), dz = p.z() - z();
                if (dx * dx + dy * dy + dz * dz >= r2) return;
                var cur = p.vitals().getEffect(MobEffect.MINING_FATIGUE);
                if (cur == null || cur.amplifier() < effect.amplifier() || cur.endsWithin(1200 - 1)) {
                    p.vitals().addEffect(new MobEffectInstance(effect));
                    p.send(new GameEvent(GameEvent.ELDER_GUARDIAN_EFFECT, isSilent() ? 0.0F : 1.0F));
                }
            });
        }
        if (!hasRestriction()) restrictTo(blockPosition(), 16);
    }
}
