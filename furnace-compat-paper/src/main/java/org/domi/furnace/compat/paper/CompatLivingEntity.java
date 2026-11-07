package org.domi.furnace.compat.paper;

import org.bukkit.entity.LivingEntity;

class CompatLivingEntity extends CompatEntity implements LivingEntity {

    private final org.domi.furnace.api.entity.LivingEntity living;

    CompatLivingEntity(org.domi.furnace.api.entity.LivingEntity handle) {
        super(handle);
        this.living = handle;
    }

    @Override
    public double getHealth() { return living.health(); }

    @Override
    public double getMaxHealth() { return living.maxHealth(); }

    @Override
    public void setHealth(double health) {
        if (health < 0 || health > getMaxHealth()) throw new IllegalArgumentException("Health must be between 0 and " + getMaxHealth() + ", but was " + health);
        living.updateHealth((float) health);
    }
}
