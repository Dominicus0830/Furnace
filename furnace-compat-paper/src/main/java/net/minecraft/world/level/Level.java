package net.minecraft.world.level;

import org.bukkit.craftbukkit.CraftWorld;

/** 월드 shim(Mojang 이름, H6 최소 표면). 실제 객체는 늘 {@link net.minecraft.server.level.ServerLevel} 이다. */
public abstract class Level {

    private final CraftWorld world;

    protected Level(CraftWorld world) { this.world = java.util.Objects.requireNonNull(world, "world"); }

    /** CraftBukkit 이 더한 메서드: 이 월드의 Bukkit 객체. */
    public CraftWorld getWorld() { return world; }

    /** 서버 레지스트리 묶음(H7b, {@link net.minecraft.server.MinecraftServer#registryAccess()} 와 같다). */
    public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.SERVER; }

    public boolean isClientSide() { return false; }

    /**
     * 바닐라 Level#getEntity(int): 그 네트워크 id 의 이 월드 엔티티(플레이어 포함), 없으면 null — ProtocolLib 의 getEntityFromID 가 쓴다.
     * Paper·Folia 는 어느 스레드에서나 기다림 없이 돌려준다. Furnace 도 그 엔티티의 Bukkit 객체를 이미 알면(같은 UUID 캐시) 어느 스레드에서나
     * 곧바로 마지막 스냅샷의 shim 을 준다. 처음 보는 엔티티는 스냅샷을 그 엔티티를 가진 region 이 떠야 해서: 그 region 스레드(빌림 구간 포함)면 곧바로,
     * 틱 스레드가 아닌 스레드(네트워크·비동기)는 최대 1초 기다리고, 틱 스레드가 남의 region 의 처음 보는 엔티티를 찾으면 교착을 피하려고
     * ThreadOwnershipException 이다(Paper·Folia 와 다른 점).
     */
    public net.minecraft.world.entity.Entity getEntity(int id) { return null; }   // 서버 월드(ServerLevel)가 재정의한다

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + world.getName() + "]"; }
}
