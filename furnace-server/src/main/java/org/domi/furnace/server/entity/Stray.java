// 바닐라 Stray: 눈 지대 해골. 화살에 느림(600틱)을 건다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;

public class Stray extends AbstractSkeleton {

    public Stray(ChunkLevel level, int id) { super(EntityType.STRAY, level, id); }

    @Override protected void prepareArrow(Arrow arrow) { arrow.addEffect(new MobEffectInstance(MobEffect.SLOWNESS, 600)); }

    @Override public String getAmbientSound() { return "entity.stray.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.stray.hurt"; }

    @Override protected String getDeathSound() { return "entity.stray.death"; }

    @Override String getStepSound() { return "entity.stray.step"; }
}
