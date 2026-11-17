package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.LevelAccessor;

/**
 * Paper CraftBlockState 의 자리(T-211) — Bukkit 블록 상태 스냅숏의 공통 상위 클래스. Furnace 의 스냅숏({@link org.domi.furnace.compat.paper.CompatBlockState})과
 * 블록 엔티티 상태({@link CraftBlockEntityState})가 모두 이 형이다(Paper 처럼 {@code (CraftBlockState) block.getState()} 가 된다).
 * 여기 있는 것은 Paper 가 더한 NMS 쪽 표면이다: 칸({@link #getPosition}), NMS 블록 상태({@link #getHandle}·{@link #setData}), 쓰기 플래그, 레벨 손잡이.
 * Paper 와 다른 점: 추상 클래스다(Bukkit 표면은 하위 클래스가 구현한다) — 플러그인이 직접 상속해 만드는 일은 없다고 보고, 그런 클래스는 링크에서 막힌다.
 */
public abstract class CraftBlockState implements org.bukkit.block.BlockState {

    /** Paper 기본 쓰기 플래그(Block.UPDATE_ALL). */
    protected int flag = 3;
    private LevelAccessor worldHandle;

    protected CraftBlockState() {}

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
