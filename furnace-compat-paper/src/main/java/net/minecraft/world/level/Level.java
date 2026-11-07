package net.minecraft.world.level;

import org.bukkit.craftbukkit.CraftWorld;

/** 월드 shim(Mojang 이름, H6 최소 표면). 실제 객체는 늘 {@link net.minecraft.server.level.ServerLevel} 이다. */
public abstract class Level {

    private final CraftWorld world;

    protected Level(CraftWorld world) { this.world = java.util.Objects.requireNonNull(world, "world"); }

    /** CraftBukkit 이 더한 메서드: 이 월드의 Bukkit 객체. */
    public CraftWorld getWorld() { return world; }

    public boolean isClientSide() { return false; }

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + world.getName() + "]"; }
}
