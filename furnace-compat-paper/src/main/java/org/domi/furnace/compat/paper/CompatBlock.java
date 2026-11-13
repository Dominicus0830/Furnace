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

public final class CompatBlock implements Block {

    private final String dimension;
    private final BlockPos pos;
    /** 이벤트가 "놓일 블록"처럼 아직 월드에 없는 상태를 보일 때만 값이 있다(없으면 월드의 지금 블록을 읽는다). */
    private final org.domi.furnace.api.world.BlockData shown;

    public CompatBlock(String dimension, BlockPos pos) { this(dimension, pos, null); }

    private CompatBlock(String dimension, BlockPos pos, org.domi.furnace.api.world.BlockData shown) {
        this.dimension = dimension;
        this.pos = pos;
        this.shown = shown;
    }

    /**
     * 설치 직전 이벤트용 — CraftBukkit 은 블록을 놓은 뒤에 BlockPlaceEvent 를 부르므로 getBlock().getType() 이 놓인 블록이다. Furnace 는 놓기 전에
     * 부르니 이 칸이 놓일 상태를 보이는 블록을 준다(이벤트가 끝나도 그 상태를 계속 보인다 — 취소돼도 되돌아가지 않는다).
     */
    static CompatBlock showing(String dimension, BlockPos pos, org.domi.furnace.api.world.BlockData shown) { return new CompatBlock(dimension, pos, shown); }

    private org.domi.furnace.api.world.BlockData currentData() { return shown != null ? shown : furnaceBlock().data(); }

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
    public Material getType() { return Compat.material(shown != null ? shown.type() : furnaceBlock().type()); }

    @Override
    public BlockData getBlockData() { return CompatBlockData.of(currentData()); }

    @Override
    public org.bukkit.block.BlockState getState() { return CompatBlockState.of(dimension, pos, currentData()); }

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

    // ---- 메타데이터(월드 UID 와 칸으로 — 블록이 바뀌어도 칸에 남는다)

    @Override
    public void setMetadata(String metadataKey, org.bukkit.metadata.MetadataValue newMetadataValue) { Compat.blockMetadata().setMetadata(this, metadataKey, newMetadataValue); }

    @Override
    public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(String metadataKey) { return Compat.blockMetadata().getMetadata(this, metadataKey); }

    @Override
    public boolean hasMetadata(String metadataKey) { return Compat.blockMetadata().hasMetadata(this, metadataKey); }

    @Override
    public void removeMetadata(String metadataKey, org.bukkit.plugin.Plugin owningPlugin) { Compat.blockMetadata().removeMetadata(this, metadataKey, owningPlugin); }
}
