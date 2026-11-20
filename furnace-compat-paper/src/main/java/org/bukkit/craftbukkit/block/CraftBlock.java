package org.bukkit.craftbukkit.block;

import org.domi.furnace.compat.paper.CompatBlock;

/**
 * Paper CraftBlock(월드의 한 칸, Bukkit {@link org.bukkit.block.Block} 구현). 동작은 {@link CompatBlock} 그대로이고, 월드가 주는 Block
 * ({@code World#getBlockAt}·이벤트의 블록)이 이 클래스다(NMS shim 5번 묶음 — VeinMiner 가 {@code (CraftBlock) block} 으로 바꾼다). 정적 {@link #at}
 * (T-198)과 {@code getCraftWorld}·{@code getPosition} 이 있다. {@code getNMS} 등 나머지 표면은 T-203.
 */
public class CraftBlock extends CompatBlock {

    protected CraftBlock(String dimension, org.domi.furnace.api.world.BlockPos pos) { super(dimension, pos); }

    /** 호환 모듈 전용 — 월드가 주는 블록(Paper 처럼 {@code getWorld().getBlockAt}·이벤트의 블록이 CraftBlock 이다). */
    public static CraftBlock of(String dimension, org.domi.furnace.api.world.BlockPos pos) { return new CraftBlock(dimension, pos); }

    /** Paper: 이 블록이 있는 월드(CraftWorld). */
    public org.bukkit.craftbukkit.CraftWorld getCraftWorld() { return (org.bukkit.craftbukkit.CraftWorld) getWorld(); }

    /** Paper: 칸 좌표(NMS BlockPos). */
    public net.minecraft.core.BlockPos getPosition() { return new net.minecraft.core.BlockPos(getX(), getY(), getZ()); }

    /** Paper CraftBlock.at(world, pos): 그 월드 그 칸의 Block. world 는 서버 월드(Level)여야 한다. */
    public static CraftBlock at(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos) {
        if (!(world instanceof net.minecraft.world.level.Level level)) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CraftBlock#at(" + (world == null ? "null" : world.getClass().getSimpleName()) + ")",
                    "only a server world (Level) is supported");
        }
        return new CraftBlock(level.getWorld().furnaceHandle().dimension(), new org.domi.furnace.api.world.BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }
}
