// 바닐라 Husk: 햇빛에 타지 않는 좀비. 맞추면 허기 효과(140틱 × 실효 난이도), 물속 600틱이면 좀비로 변한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;

public class Husk extends Zombie {

    public Husk(ChunkLevel level, int id) { super(EntityType.HUSK, level, id); }

    @Override protected boolean isSunSensitive() { return false; }

    @Override
    public boolean doHurtTarget(Targetable t) {
        boolean hit = super.doHurtTarget(t);
        if (hit && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && t instanceof LivingEntity le) {
            float f = level.getCurrentDifficultyAt(blockPosition()).effectiveDifficulty();
            le.addEffect(new MobEffectInstance(MobEffect.HUNGER, 140 * (int) f));
        } else if (hit && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && t instanceof Player p) {
            float f = level.getCurrentDifficultyAt(blockPosition()).effectiveDifficulty();
            p.vitals().addEffect(new MobEffectInstance(MobEffect.HUNGER, 140 * (int) f));
        }
        return hit;
    }

    @Override protected boolean convertsInWater() { return true; }

    @Override
    protected Zombie createConversionTarget() { return new Zombie(level, level.nextEntityId()); }

    @Override
    protected void doUnderWaterConversion() {
        convertToZombieType(createConversionTarget());
        if (!isSilent()) level.levelEvent(null, 1041, blockPosition(), 0);
    }

    @Override public String getAmbientSound() { return "entity.husk.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.husk.hurt"; }

    @Override protected String getDeathSound() { return "entity.husk.death"; }

    @Override protected String getStepSound() { return "minecraft:entity.husk.step"; }
}
