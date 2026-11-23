package org.bukkit.craftbukkit.block;

import java.util.Objects;
import net.minecraft.world.level.LevelAccessor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;

/**
 * Paper CraftBlockState — Bukkit 블록 상태 스냅숏(T-211, T-264 P2c 에서 옛 CompatBlockState 를 합쳤다). 만든 순간의 블록 데이터를 들고 있고, 바꾼 값은
 * {@link #update()} 로만 월드에 쓴다. 블록 엔티티가 없는 블록의 상태는 Paper 처럼 이 클래스 그대로이고, 블록 엔티티가 있으면
 * {@link org.domi.furnace.compat.paper.CompatBlockStates} 가 Paper 와 같은 하위 형(CraftChest·CraftSign 등)으로 만든다.
 * dimension 이 null 이면 놓이지 않은 상태(BlockData#createBlockState): Paper 처럼 월드·블록·청크를 물으면 IllegalStateException, update 는 아무것도 안 하고 참.
 * Paper 생성자 {@code (World, BlockPos, BlockState)} 로 상속한 플러그인 클래스(Axiom 의 놓기 상태)도 같은 스냅숏이다.
 */
public class CraftBlockState implements BlockState {

    /** Paper 기본 쓰기 플래그(Block.UPDATE_ALL). */
    protected int flag = 3;
    private LevelAccessor worldHandle;
    private final String dimension;
    private final BlockPos pos;
    private BlockData data;

    /** 호환 모듈 전용 — Furnace 스냅숏(dimension null = 놓이지 않은 상태). Furnace 하위 클래스(CraftChest 등)와 같은 모양(P3 에서 함께 정리). */
    public CraftBlockState(String dimension, BlockPos pos, BlockData data) {
        this.dimension = dimension;
        this.pos = Objects.requireNonNull(pos, "pos");
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Paper: 그 월드·칸에 놓인 NMS 상태의 스냅숏(월드를 읽지 않는다). 하위 클래스로 만든다(Axiom 놓기 이벤트 — NMS shim 5d). world null = 놓이지 않은 상태. */
    protected CraftBlockState(org.bukkit.World world, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState data) {
        this(world == null ? null : ((org.bukkit.craftbukkit.CraftWorld) world).furnaceHandle().dimension(),
                new BlockPos(pos.getX(), pos.getY(), pos.getZ()), org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(data));
    }

    /** 놓인 상태의 차원 이름(놓이지 않았으면 null). */
    final String dimension() { return dimension; }

    /** 이 상태의 칸(놓이지 않았으면 0,0,0). */
    final BlockPos position() { return pos; }

    /** Paper CraftBlockState.requirePlaced 와 같은 문구. */
    protected void requirePlaced() {
        if (dimension == null) throw new IllegalStateException("The blockState must be placed to call this method");
    }

    @Override
    public org.bukkit.craftbukkit.block.CraftBlock getBlock() {
        requirePlaced();
        return org.bukkit.craftbukkit.block.CraftBlock.of(dimension, pos);
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
        return o != null && o.getClass() == getClass() && o instanceof CraftBlockState s
                && s.pos.equals(pos) && Objects.equals(s.dimension, dimension) && s.data.equals(data);
    }

    @Override
    public int hashCode() { return Objects.hash(dimension, pos, data); }

    @Override
    public String toString() { return getClass().getSimpleName() + "{world=" + dimension + ",x=" + pos.x() + ",y=" + pos.y() + ",z=" + pos.z() + ",data=" + data.getAsString() + "}"; }

    // ---- Paper 가 더한 NMS 쪽 표면

    /** Paper: 이 상태가 기록된 레벨(populator 등). 정하지 않았으면 놓인 상태의 서버 레벨, 놓이지 않았으면 null. */
    public void setWorldHandle(LevelAccessor generatorAccess) { this.worldHandle = generatorAccess; }

    public LevelAccessor getWorldHandle() {
        if (worldHandle != null) return worldHandle;
        return isPlaced() ? ((org.bukkit.craftbukkit.CraftWorld) getWorld()).getHandle() : null;
    }

    /** NMS 칸. */
    public net.minecraft.core.BlockPos getPosition() { return new net.minecraft.core.BlockPos(getX(), getY(), getZ()); }

    /** NMS 블록 상태(지금 스냅숏 데이터). */
    public net.minecraft.world.level.block.state.BlockState getHandle() {
        return ((org.bukkit.craftbukkit.block.data.CraftBlockData) getBlockData()).getState();
    }

    /** NMS 블록 상태로 스냅숏 데이터를 바꾼다. */
    public void setData(net.minecraft.world.level.block.state.BlockState data) {
        setBlockData(org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(data));
    }

    public void setFlag(int flag) { this.flag = flag; }

    public int getFlag() { return flag; }
}
