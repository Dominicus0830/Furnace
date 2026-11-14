package org.bukkit.craftbukkit;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.domi.furnace.compat.paper.Compat;

/**
 * Bukkit Chunk(CraftBukkit 이름 — {@code (CraftChunk) chunk} 캐스트와 {@link #getHandle} 이 된다). 좌표만 들고, 읽을 때마다 서버를 본다.
 */
public final class CraftChunk implements Chunk {

    private final String dimension;
    private final int x, z;

    public CraftChunk(String dimension, int x, int z) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
    }

    @Override
    public int getX() { return x; }

    @Override
    public int getZ() { return z; }

    @Override
    public World getWorld() { return Compat.world(dimension); }

    @Override
    public boolean isLoaded() { return Compat.furnace().world(dimension).isChunkLoaded(x, z); }

    @Override
    public boolean load(boolean generate) { return isLoaded(); }

    @Override
    public boolean load() { return load(true); }

    @Override
    public org.bukkit.entity.Entity[] getEntities() {
        var w = Compat.furnace().world(dimension);
        if (!w.isChunkLoaded(x, z)) return new org.bukkit.entity.Entity[0];
        java.util.List<org.bukkit.entity.Entity> out = new java.util.ArrayList<>();
        for (var e : w.entitiesInChunkNow(x, z)) out.add(Compat.entity(e));
        return out.toArray(new org.bukkit.entity.Entity[0]);
    }

    public CraftWorld getCraftWorld() { return (CraftWorld) getWorld(); }

    /**
     * Paper CraftChunk.getHandle(status): 그 청크의 NMS 청크(이번 로드 동안 같은 객체). 주인 region 이 아닌 스레드에서는 공개 스냅샷을 읽는 보기이고,
     * 공개본이 아직 없으면 주인이 낼 때까지 기다린다(Paper 가 주 스레드를 기다리는 것과 같은 간선, N2b-3). 다른 region 스레드는 기다리지 않는다.
     * 로드돼 있지 않으면 IllegalStateException(Paper 는 동기로 불러온다 — Furnace 는 불러오지 않는다).
     */
    public net.minecraft.world.level.chunk.ChunkAccess getHandle(net.minecraft.world.level.chunk.status.ChunkStatus status) {
        return getCraftWorld().getHandle().getChunk(x, z, status, true);
    }

    @Override
    public boolean equals(Object o) { return o instanceof CraftChunk c && c.x == x && c.z == z && c.dimension.equals(dimension); }

    @Override
    public int hashCode() { return (x * 31 + z) * 31 + dimension.hashCode(); }

    @Override
    public String toString() { return "CraftChunk{x=" + x + ",z=" + z + "}"; }
}
