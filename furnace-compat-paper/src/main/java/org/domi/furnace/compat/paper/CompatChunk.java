package org.domi.furnace.compat.paper;

import org.bukkit.Chunk;
import org.bukkit.World;

public final class CompatChunk implements Chunk {

    private final String dimension;
    private final int x, z;

    public CompatChunk(String dimension, int x, int z) {
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

    @Override
    public boolean equals(Object o) { return o instanceof CompatChunk c && c.x == x && c.z == z && c.dimension.equals(dimension); }

    @Override
    public int hashCode() { return (x * 31 + z) * 31 + dimension.hashCode(); }

    @Override
    public String toString() { return "CraftChunk{x=" + x + ",z=" + z + "}"; }
}
