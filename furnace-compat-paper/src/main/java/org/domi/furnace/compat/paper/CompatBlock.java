package org.domi.furnace.compat.paper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.domi.furnace.api.world.BlockPos;

final class CompatBlock implements Block {

    private final String dimension;
    private final BlockPos pos;

    CompatBlock(String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    @Override
    public World getWorld() { return Compat.world(dimension); }

    @Override
    public int getX() { return pos.x(); }

    @Override
    public int getY() { return pos.y(); }

    @Override
    public int getZ() { return pos.z(); }

    @Override
    public Location getLocation() { return new Location(getWorld(), pos.x(), pos.y(), pos.z()); }

    @Override
    public Material getType() {
        org.domi.furnace.api.world.World w = Compat.furnace().world(dimension);
        return Compat.material(w.block(pos).type());
    }

    @Override
    public Block getRelative(int modX, int modY, int modZ) { return new CompatBlock(dimension, pos.offset(modX, modY, modZ)); }

    @Override
    public Block getRelative(BlockFace face) { return getRelative(face.getModX(), face.getModY(), face.getModZ()); }

    @Override
    public boolean equals(Object o) { return o instanceof CompatBlock b && b.pos.equals(pos) && b.dimension.equals(dimension); }

    @Override
    public int hashCode() { return pos.hashCode() * 31 + dimension.hashCode(); }

    @Override
    public String toString() { return "CraftBlock{world=" + dimension + ",x=" + pos.x() + ",y=" + pos.y() + ",z=" + pos.z() + "}"; }
}
