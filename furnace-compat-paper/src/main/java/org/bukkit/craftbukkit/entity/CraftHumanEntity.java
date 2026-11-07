package org.bukkit.craftbukkit.entity;

import org.bukkit.entity.HumanEntity;

/** CraftBukkit 계층을 맞추는 칸(사람 모양 엔티티). Furnace 에서 사람 모양은 플레이어뿐이라 구현은 {@link CraftPlayer} 에 있다. */
public abstract class CraftHumanEntity extends CraftLivingEntity implements HumanEntity {

    protected CraftHumanEntity(org.domi.furnace.api.entity.LivingEntity handle) { super(handle); }

    @Override
    public net.minecraft.world.entity.player.Player getHandle() { return (net.minecraft.world.entity.player.Player) super.getHandle(); }
}
