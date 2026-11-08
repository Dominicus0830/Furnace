package org.bukkit.craftbukkit.entity;

/** 나이가 있는 생물(CraftBukkit 계층 칸). */
public abstract class CraftAgeable extends CraftCreature implements org.bukkit.entity.Ageable {

    /** 하위 종류가 부른다. */
    protected CraftAgeable(org.domi.furnace.api.entity.Mob handle) { super(handle); }
}
