package org.domi.furnace.compat.paper;

import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;

final class CompatBlock implements Block {

    private final String dimension;
    private final BlockPos pos;

    CompatBlock(String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    private org.domi.furnace.api.world.Block furnaceBlock() { return Compat.furnace().world(dimension).block(pos); }

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
    public Location getLocation(Location loc) {
        if (loc != null) {
            loc.setWorld(getWorld());
            loc.setX(pos.x());
            loc.setY(pos.y());
            loc.setZ(pos.z());
            loc.setYaw(0);
            loc.setPitch(0);
        }
        return loc;
    }

    @Override
    public Chunk getChunk() { return new CompatChunk(dimension, pos.chunkX(), pos.chunkZ()); }

    @Override
    public Material getType() { return Compat.material(furnaceBlock().type()); }

    @Override
    public BlockData getBlockData() { return CompatBlockData.of(furnaceBlock().data()); }

    @Override
    public void setType(Material type) { setBlockData(CompatBlockData.create(type), true); }

    @Override
    public void setType(Material type, boolean applyPhysics) { setBlockData(CompatBlockData.create(type), applyPhysics); }

    @Override
    public void setBlockData(BlockData data) { setBlockData(data, true); }

    @Override
    public void setBlockData(BlockData data, boolean applyPhysics) {
        Objects.requireNonNull(data, "BlockData cannot be null");
        if (!applyPhysics) throw new UnsupportedFeature("Block#setBlockData(applyPhysics=false)", "Furnace 의 setBlock 은 항상 이웃을 갱신한다");
        if (!(data instanceof CompatBlockData d)) throw new IllegalArgumentException("Furnace 가 만든 BlockData 가 아니다: " + data.getClass().getName());
        var block = furnaceBlock();
        boolean set;
        try {
            set = block.set(d.furnace());
        } catch (ThreadOwnershipException notOwner) {
            Owned.write(block.setAsync(d.furnace()), "Block#setBlockData" + pos);
            return;
        }
        if (!set) throw new IllegalStateException("블록을 바꾸지 못했다 — 청크가 로드돼 있지 않거나 높이 밖이다(Furnace 는 동기로 청크를 불러오지 않는다): " + pos);
    }

    @Override
    public boolean isEmpty() { return getType().isAir(); }

    @Override
    public boolean isLiquid() {
        Material m = getType();
        return m == Material.WATER || m == Material.LAVA;
    }

    @Override
    public Block getRelative(int modX, int modY, int modZ) { return new CompatBlock(dimension, pos.offset(modX, modY, modZ)); }

    @Override
    public Block getRelative(BlockFace face) { return getRelative(face.getModX(), face.getModY(), face.getModZ()); }

    @Override
    public Block getRelative(BlockFace face, int distance) { return getRelative(face.getModX() * distance, face.getModY() * distance, face.getModZ() * distance); }

    @Override
    public BlockFace getFace(Block block) {
        if (block == null || !block.getWorld().equals(getWorld())) return null;
        for (BlockFace f : BlockFace.values()) {
            if (block.getX() - getX() == f.getModX() && block.getY() - getY() == f.getModY() && block.getZ() - getZ() == f.getModZ()) return f;
        }
        return null;
    }

    @Override
    public boolean equals(Object o) { return o instanceof CompatBlock b && b.pos.equals(pos) && b.dimension.equals(dimension); }

    @Override
    public int hashCode() { return pos.hashCode() * 31 + dimension.hashCode(); }

    @Override
    public String toString() { return "CraftBlock{world=" + dimension + ",x=" + pos.x() + ",y=" + pos.y() + ",z=" + pos.z() + "}"; }
}
