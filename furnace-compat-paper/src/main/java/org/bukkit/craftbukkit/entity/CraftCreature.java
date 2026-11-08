package org.bukkit.craftbukkit.entity;

/** 생물(CraftBukkit 계층 칸). */
public abstract class CraftCreature extends CraftMob implements org.bukkit.entity.Creature {

    /** 하위 종류가 부른다. */
    protected CraftCreature(org.domi.furnace.api.entity.Mob handle) { super(handle); }
}
