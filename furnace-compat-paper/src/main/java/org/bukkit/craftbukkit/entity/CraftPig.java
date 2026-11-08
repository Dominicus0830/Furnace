package org.bukkit.craftbukkit.entity;

/** 돼지. */
public class CraftPig extends CraftAnimals implements org.bukkit.entity.Pig {

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftPig(org.domi.furnace.api.entity.Mob handle) { super(handle); }
}
