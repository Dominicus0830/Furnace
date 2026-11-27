package net.minecraft.world.level;

/** 서버 레벨 접근(Mojang 이름 shim, T-211). 바닐라 엔티티 더하기(addFreshEntityWithPassengers)는 아직 없다. */
interface ServerLevelAccessor {
    net.minecraft.server.level.ServerLevel getLevel();

    /** CraftBukkit: {@link #getLevel()}. */
    @Override
    default net.minecraft.server.level.ServerLevel getMinecraftWorld() { return getLevel(); }
}
