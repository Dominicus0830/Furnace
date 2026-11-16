package org.bukkit.craftbukkit.block;

import org.domi.furnace.compat.paper.CompatBlock;

/**
 * Paper CraftBlock(월드의 한 칸, Bukkit {@link org.bukkit.block.Block} 구현)의 자리 — T-198 에서 정적 {@link #at} 만 더했다(FAWE 가 지운 신호기의
 * {@code BeaconDeactivatedEvent} 를 만들 때 쓴다). 동작은 {@link CompatBlock} 그대로다. 월드가 주는 Block({@code World#getBlockAt})은 아직 이 클래스가 아니다
 * ({@code instanceof CraftBlock}·{@code getNMS} 등 나머지 표면은 T-203).
 */
public class CraftBlock extends CompatBlock {

    protected CraftBlock(String dimension, org.domi.furnace.api.world.BlockPos pos) { super(dimension, pos); }

    /** Paper CraftBlock.at(world, pos): 그 월드 그 칸의 Block. world 는 서버 월드(Level)여야 한다. */
    public static CraftBlock at(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos) {
        if (!(world instanceof net.minecraft.world.level.Level level)) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CraftBlock#at(" + (world == null ? "null" : world.getClass().getSimpleName()) + ")",
                    "only a server world (Level) is supported");
        }
        return new CraftBlock(level.getWorld().furnaceHandle().dimension(), new org.domi.furnace.api.world.BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }
}
