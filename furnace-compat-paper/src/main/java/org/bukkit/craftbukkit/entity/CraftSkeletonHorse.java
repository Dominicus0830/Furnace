// 생성됨: tools/compat/gen-bukkit-entity-classes.py — 손으로 고치려면 이 줄을 지운다.
package org.bukkit.craftbukkit.entity;

/** SKELETON_HORSE 의 감싸개(Paper CraftSkeletonHorse 이름). */
public class CraftSkeletonHorse extends CraftAnimals implements org.bukkit.entity.SkeletonHorse {

    public CraftSkeletonHorse(org.domi.furnace.api.entity.Mob handle) { super(handle); }

    @Override
    public boolean isTamed() { return org.domi.furnace.compat.paper.EntityReads.tamed(furnaceHandle()); }
}
