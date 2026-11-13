package org.domi.furnace.compat.paper;

import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.api.world.BlockPos;

/**
 * 블록 상태 스냅숏(Paper 의 CraftBlockState). 만든 순간의 블록 데이터를 들고 있고, 바꾼 값은 {@link #update()} 로만 월드에 쓴다.
 * 블록 엔티티 내용(상자·표지판 글 등)은 담지 않는다 — Furnace 는 아직 그 스냅숏을 호환 계층에 보이지 않는다.
 */
public final class CompatBlockState implements BlockState {

    private final String dimension;
    private final BlockPos pos;
    private BlockData data;

    CompatBlockState(String dimension, BlockPos pos, BlockData data) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos");
        this.data = Objects.requireNonNull(data, "data");
    }

    /** 이 칸의 지금 블록을 스냅숏으로 — 소유 스레드에서. */
    static CompatBlockState of(String dimension, BlockPos pos) {
        return new CompatBlockState(dimension, pos, CompatBlockData.of(Compat.furnace().world(dimension).block(pos).data()));
    }

    /** Furnace 블록 데이터로 만든 스냅숏(이벤트의 "새 상태"). */
    static CompatBlockState of(String dimension, BlockPos pos, org.domi.furnace.api.world.BlockData data) {
        return new CompatBlockState(dimension, pos, CompatBlockData.of(data));
    }

    @Override
    public Block getBlock() { return new CompatBlock(dimension, pos); }

    @Override
    public BlockData getBlockData() { return data; }

    @Override
    public Material getType() { return Compat.material(((CompatBlockData) data).furnace().type()); }

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
    public void setBlockData(BlockData data) { this.data = Objects.requireNonNull(data, "BlockData cannot be null"); }

    @Override
    public void setType(Material type) { this.data = CompatBlockData.create(type); }

    @Override
    public boolean update() { return update(false, true); }

    @Override
    public boolean update(boolean force) { return update(force, true); }

    /** Paper 와 같다: force 가 거짓이면 그 칸의 블록 종류가 스냅숏과 같을 때만 쓴다. 이웃 갱신을 끄는 것은 Furnace 에 없어 UnsupportedFeature(Block#setBlockData 와 같다). */
    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        Block block = getBlock();
        if (!force && block.getType() != getType()) return false;
        block.setBlockData(data, applyPhysics);
        return true;
    }

    @Override
    public boolean isPlaced() { return true; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompatBlockState s && s.pos.equals(pos) && s.dimension.equals(dimension) && s.data.equals(data);
    }

    @Override
    public int hashCode() { return Objects.hash(dimension, pos, data); }

    @Override
    public String toString() { return "CraftBlockState{world=" + dimension + ",x=" + pos.x() + ",y=" + pos.y() + ",z=" + pos.z() + ",data=" + data.getAsString() + "}"; }
}
