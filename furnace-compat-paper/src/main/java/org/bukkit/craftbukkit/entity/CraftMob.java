package org.bukkit.craftbukkit.entity;

/** AI 가 있는 몹(CraftBukkit 계층 칸). */
public abstract class CraftMob extends CraftLivingEntity implements org.bukkit.entity.Mob {

    /** 하위 종류가 부른다. */
    protected CraftMob(org.domi.furnace.api.entity.Mob handle) { super(handle); }
}
