package org.bukkit.craftbukkit.entity;

/** 동물(CraftBukkit 계층 칸). */
public abstract class CraftAnimals extends CraftAgeable implements org.bukkit.entity.Animals {

    /** 하위 종류가 부른다. */
    protected CraftAnimals(org.domi.furnace.api.entity.Mob handle) { super(handle); }
}
