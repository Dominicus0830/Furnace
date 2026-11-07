package org.bukkit.craftbukkit.entity;

import org.bukkit.entity.LivingEntity;

public class CraftLivingEntity extends CraftEntity implements LivingEntity {

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftLivingEntity(org.domi.furnace.api.entity.LivingEntity handle) { super(handle); }

    private org.domi.furnace.api.entity.LivingEntity living() { return (org.domi.furnace.api.entity.LivingEntity) furnaceHandle(); }

    @Override
    public net.minecraft.world.entity.LivingEntity getHandle() { return (net.minecraft.world.entity.LivingEntity) super.getHandle(); }

    @Override
    protected net.minecraft.world.entity.Entity createHandle() { return new net.minecraft.world.entity.LivingEntity(this); }

    @Override
    public double getHealth() { return living().health(); }

    @Override
    public double getMaxHealth() { return living().maxHealth(); }

    @Override
    public void setHealth(double health) {
        if (health < 0 || health > getMaxHealth()) throw new IllegalArgumentException("Health must be between 0 and " + getMaxHealth() + ", but was " + health);
        living().updateHealth((float) health);
    }
}
