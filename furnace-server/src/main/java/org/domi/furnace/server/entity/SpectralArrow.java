// 바닐라 SpectralArrow: 맞은 생물에게 발광 효과를 10초 건다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemContext;

public class SpectralArrow extends AbstractArrow {
    private int duration = 200;

    public SpectralArrow(ChunkLevel level, int id) { super(EntityType.SPECTRAL_ARROW, level, id); }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(MobEffect.GLOWING, duration, 0));
    }

    @Override
    protected void addAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Duration", duration);
    }

    @Override
    protected void readAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Duration")) duration = tag.getInt("Duration");
    }

    @Override protected ItemStack getDefaultPickupItem() { return new ItemStack(ItemContext.id("minecraft:item", "spectral_arrow"), 1); }
}
