// 생성됨: tools/compat/gen-bukkit-entity-classes.py — 손으로 고치려면 이 줄을 지운다.
package org.bukkit.craftbukkit.entity;

/** Paper 1.21.1 CraftTameableAnimal 와 같은 자리의 중간 계층(T-264). */
public class CraftTameableAnimal extends org.bukkit.craftbukkit.entity.CraftAnimals implements org.bukkit.entity.Tameable, org.bukkit.entity.Creature {

    public CraftTameableAnimal(org.domi.furnace.api.entity.Mob handle) { super(handle); }

    @Override
    public boolean isTamed() { return org.domi.furnace.compat.paper.EntityReads.tamed(furnaceHandle()); }
}
