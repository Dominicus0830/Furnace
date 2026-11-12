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
     * 엔티티를 가진 region 스레드(또는 옛 동기 작업의 빌림 구간)면 곧바로, 그 밖의 스레드(네트워크·비동기)는 그 region 이 읽어 줄 때까지 잠깐 기다린다.
     * region 스레드가 남의 region 엔티티를 찾으면 ThreadOwnershipException(Paper 가 Folia 에서 던지는 것과 같은 규칙).
     */
    public abstract net.minecraft.world.entity.Entity getEntity(int id);

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + world.getName() + "]"; }
}
