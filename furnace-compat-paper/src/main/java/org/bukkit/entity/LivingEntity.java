package org.bukkit.entity;

public interface LivingEntity extends Entity {

    double getHealth();

    double getMaxHealth();

    void setHealth(double health);
}
