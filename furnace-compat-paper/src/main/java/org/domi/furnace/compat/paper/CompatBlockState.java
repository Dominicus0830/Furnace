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
 * 블록 엔티티가 있는 블록은 {@link CompatBlockStates} 가 Paper 와 같은 형(org.bukkit.craftbukkit.block.Craft*, Chest·Sign 등)의 하위 클래스로 만든다.
 * 블록 엔티티 내용(상자·표지판 글 등)은 아직 담지 않는다 — Furnace 는 그 스냅숏을 호환 계층에 보이지 않는다.
 * dimension 이 null 이면 놓이지 않은 상태(BlockData#createBlockState): Paper 처럼 월드·블록·청크를 물으면 IllegalStateException, update 는 아무것도 안 하고 참.
 * 형은 Paper 의 {@link org.bukkit.craftbukkit.block.CraftBlockState} 다(T-211).
 */
public class CompatBlockState extends org.bukkit.craftbukkit.block.CraftBlockState {

    private final String dimension;
    private final BlockPos pos;
    private BlockData data;

    protected CompatBlockState(String dimension, BlockPos pos, BlockData data) {
        this.dimension = dimension;
        this.pos = Objects.requireNonNull(pos, "pos");
        this.data = Objects.requireNonNull(data, "data");
    }

    /** 이 칸의 지금 블록을 스냅숏으로 — 소유 스레드에서. */
    static CompatBlockState of(String dimension, BlockPos pos) {
        return new CompatBlockState(Objects.requireNonNull(dimension, "dimension"), pos, CompatBlockData.of(Compat.furnace().world(dimension).block(pos).data()));
    }

    /** Furnace 블록 데이터로 만든 스냅숏(이벤트의 "새 상태"). */
    static CompatBlockState of(String dimension, BlockPos pos, org.domi.furnace.api.world.BlockData data) {
        return new CompatBlockState(Objects.requireNonNull(dimension, "dimension"), pos, CompatBlockData.of(data));
    }

    /**
     * 호환 모듈 전용(T-211, Paper BlockStateListPopulator): 월드에 아직 없는 블록 상태 state 를 그 칸에 놓인 상태로 — 블록 엔티티가 있는 블록은
     * Paper 와 같은 형(CraftChest 등)이고 그 내용은 새 블록 엔티티(빈 내용)다. 월드를 읽지 않는다.
     */
    public static CompatBlockState generated(String dimension, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        Objects.requireNonNull(dimension, "dimension");
        BlockData data = org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state);
        return org.bukkit.craftbukkit.block.CraftBlockEntityState.furnaceFresh(() -> CompatBlockStates.create(dimension, pos, data));
    }

    /** 놓인 상태의 차원 이름(놓이지 않았으면 null). */
    protected final String dimension() { return dimension; }

    /** 이 상태의 칸(놓이지 않았으면 0,0,0). */
    protected final BlockPos position() { return pos; }

    /** Paper CraftBlockState.requirePlaced 와 같은 문구. */
    protected final void requirePlaced() {
        if (dimension == null) throw new IllegalStateException("The blockState must be placed to call this method");
    }

    @Override
    public Block getBlock() {
        requirePlaced();
        return new CompatBlock(dimension, pos);
    }

    @Override
    public BlockData getBlockData() { return data; }

    @Override
    public Material getType() { return Compat.material(((CompatBlockData) data).furnace().type()); }

    @Override
    public World getWorld() {
        requirePlaced();
        return Compat.world(dimension);
    }

    /** 놓이지 않은 상태면 null(Paper 의 world 필드). */
    private World worldOrNull() { return dimension == null ? null : Compat.world(dimension); }

    @Override
    public int getX() { return pos.x(); }

    @Override
    public int getY() { return pos.y(); }

    @Override
    public int getZ() { return pos.z(); }

    @Override
    public Location getLocation() { return new Location(worldOrNull(), pos.x(), pos.y(), pos.z()); }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            loc.setWorld(worldOrNull());
            loc.setX(pos.x());
            loc.setY(pos.y());
            loc.setZ(pos.z());
            loc.setYaw(0);
            loc.setPitch(0);
        }
        return loc;
    }

    @Override
    public Chunk getChunk() {
        requirePlaced();
        return new org.bukkit.craftbukkit.CraftChunk(dimension, pos.chunkX(), pos.chunkZ());
    }

    @Override
    public void setBlockData(BlockData data) { this.data = Objects.requireNonNull(data, "BlockData cannot be null"); }

    @Override
    public void setType(Material type) { this.data = CompatBlockData.create(type); }

    @Override
    public boolean update() { return update(false, true); }

    @Override
    public boolean update(boolean force) { return update(force, true); }

    /**
     * Paper 와 같다: 놓이지 않은 상태면 아무것도 안 하고 참, force 가 거짓이면 그 칸의 블록 종류가 스냅숏과 같을 때만 쓴다.
     * 이웃 갱신을 끄는 것은 Furnace 에 없어 UnsupportedFeature(Block#setBlockData 와 같다).
     */
    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        if (!isPlaced()) return true;
        Block block = getBlock();
        if (!force && block.getType() != getType()) return false;
        block.setBlockData(data, applyPhysics);
        return true;
    }

    @Override
    public boolean isPlaced() { return dimension != null; }

    /** Paper 처럼 같은 클래스끼리만 같다(Chest 상태와 일반 상태는 다르다). */
    @Override
    public boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && o instanceof CompatBlockState s
                && s.pos.equals(pos) && Objects.equals(s.dimension, dimension) && s.data.equals(data);
    }

    @Override
    public int hashCode() { return Objects.hash(dimension, pos, data); }

    @Override
    public String toString() { return (getClass() == CompatBlockState.class ? "CraftBlockState" : getClass().getSimpleName()) + "{world=" + dimension + ",x=" + pos.x() + ",y=" + pos.y() + ",z=" + pos.z() + ",data=" + data.getAsString() + "}"; }
}
