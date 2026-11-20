package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.LevelAccessor;

/**
 * Paper CraftBlockState 의 자리(T-211) — Bukkit 블록 상태 스냅숏의 공통 상위 클래스. Furnace 의 스냅숏({@link org.domi.furnace.compat.paper.CompatBlockState})과
 * 블록 엔티티 상태({@link CraftBlockEntityState})가 모두 이 형이다(Paper 처럼 {@code (CraftBlockState) block.getState()} 가 된다).
 * 여기 있는 것은 Paper 가 더한 NMS 쪽 표면이다: 칸({@link #getPosition}), NMS 블록 상태({@link #getHandle}·{@link #setData}), 쓰기 플래그, 레벨 손잡이.
 * Furnace 의 하위 클래스는 Bukkit 표면을 스스로 구현한다. 플러그인이 Paper 생성자 {@code (World, BlockPos, BlockState)} 로 상속해 만든 것(Axiom 의 놓기 상태 —
 * NMS shim 5d)은 그 칸의 보통 스냅숏({@link org.domi.furnace.compat.paper.CompatBlockState})에 맡긴다.
 */
public class CraftBlockState implements org.bukkit.block.BlockState {

    /** Paper 기본 쓰기 플래그(Block.UPDATE_ALL). */
    protected int flag = 3;
    private LevelAccessor worldHandle;

    /** Paper 생성자로 만든 것만 — 그 밖의 하위 클래스는 Bukkit 표면을 스스로 구현한다. */
    private final org.bukkit.block.BlockState snapshot;

    protected CraftBlockState() { this.snapshot = null; }

    /** Paper: 그 월드·칸에 놓인 NMS 상태의 스냅숏(월드를 읽지 않는다). 하위 클래스로 만든다(Axiom 놓기 이벤트). */
    protected CraftBlockState(org.bukkit.World world, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState data) {
        this.snapshot = org.domi.furnace.compat.paper.CompatBlockState.furnaceOf(((org.bukkit.craftbukkit.CraftWorld) world).furnaceHandle().dimension(),
                new org.domi.furnace.api.world.BlockPos(pos.getX(), pos.getY(), pos.getZ()), data);
    }

    private org.bukkit.block.BlockState snapshot() {
        if (snapshot == null) throw new AbstractMethodError(getClass().getName() + " must implement the Bukkit block state surface");
        return snapshot;
    }

    /** Paper 처럼 CraftBlock 을 돌려준다(공변 반환형). */
    @Override public CraftBlock getBlock() { return (CraftBlock) snapshot().getBlock(); }
    @Override public org.bukkit.block.data.BlockData getBlockData() { return snapshot().getBlockData(); }
    @Override public org.bukkit.Material getType() { return snapshot().getType(); }
    @Override public org.bukkit.World getWorld() { return snapshot().getWorld(); }
    @Override public int getX() { return snapshot().getX(); }
    @Override public int getY() { return snapshot().getY(); }
    @Override public int getZ() { return snapshot().getZ(); }
    @Override public org.bukkit.Location getLocation() { return snapshot().getLocation(); }
    @Override public org.bukkit.Location getLocation(org.bukkit.Location loc) { return snapshot().getLocation(loc); }
    @Override public org.bukkit.Chunk getChunk() { return snapshot().getChunk(); }
    @Override public void setBlockData(org.bukkit.block.data.BlockData data) { snapshot().setBlockData(data); }
    @Override public void setType(org.bukkit.Material type) { snapshot().setType(type); }
    @Override public boolean update() { return snapshot().update(); }
    @Override public boolean update(boolean force) { return snapshot().update(force); }
    @Override public boolean update(boolean force, boolean applyPhysics) { return snapshot().update(force, applyPhysics); }
    @Override public boolean isPlaced() { return snapshot().isPlaced(); }

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

    public void setFlag(int flag) {
        this.flag = flag;
        if (snapshot instanceof CraftBlockState s) s.setFlag(flag);   // Paper 생성자로 만든 것: 쓰기는 스냅숏이 한다
    }

    public int getFlag() { return flag; }
}
