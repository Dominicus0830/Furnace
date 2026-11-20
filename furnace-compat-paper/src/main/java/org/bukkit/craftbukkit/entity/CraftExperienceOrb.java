package org.bukkit.craftbukkit.entity;

/** EXPERIENCE_ORB 의 감싸개(Paper CraftExperienceOrb 이름). */
public class CraftExperienceOrb extends CraftEntity implements org.bukkit.entity.ExperienceOrb {

    public CraftExperienceOrb(org.domi.furnace.api.entity.Entity handle) { super(handle); }

    @Override
    protected net.minecraft.world.entity.Entity createHandle() { return new net.minecraft.world.entity.ExperienceOrb(this); }
}
