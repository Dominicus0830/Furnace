package org.bukkit.generator;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.RegionAccessor;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Paper 1.21.1 LimitedRegion — {@link BlockPopulator} 가 받는 생성 중 영역(중심 청크 둘레 한 청크씩, 3×3 청크). 영역 밖 칸은 IllegalArgumentException.
 * populate 가 끝나면 더 쓸 수 없다(IllegalStateException).
 */
public interface LimitedRegion extends RegionAccessor {

    /** 중심 청크 둘레 여유(블록) — 16. */
    int getBuffer();

    boolean isInRegion(Location location);

    boolean isInRegion(int x, int y, int z);

    /** 영역 안 블록 엔티티 — 아직 길이 없다(T-218). */
    List<BlockState> getTileEntities();

    default void setBlockData(Vector location, BlockData blockData) {
        setBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), blockData);
    }

    /** 블록 상태(블록 엔티티 포함) 쓰기 — 아직 길이 없다(T-218). */
    default void setBlockState(int x, int y, int z, BlockState state) {
        throw new UnsupportedFeature("LimitedRegion#setBlockState", "block states of a region being generated are not available yet (T-218)");
    }

    default void setBlockState(Vector location, BlockState state) {
        setBlockState(location.getBlockX(), location.getBlockY(), location.getBlockZ(), state);
    }

    default BlockState getBlockState(Vector location) {
        return getBlockState(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** 그 칸에 블록 틱을 건다 — 아직 길이 없다(T-218). */
    default void scheduleBlockUpdate(int x, int y, int z) {
        throw new UnsupportedFeature("LimitedRegion#scheduleBlockUpdate", "scheduled ticks in a region being generated are not available yet (T-218)");
    }

    default void scheduleBlockUpdate(Vector location) {
        scheduleBlockUpdate(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** 그 칸에 유체 틱을 건다 — 아직 길이 없다(T-218). */
    default void scheduleFluidUpdate(int x, int y, int z) {
        throw new UnsupportedFeature("LimitedRegion#scheduleFluidUpdate", "scheduled ticks in a region being generated are not available yet (T-218)");
    }

    default void scheduleFluidUpdate(Vector location) {
        scheduleFluidUpdate(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** 이 영역이 속한 월드. populate 가 끝난 뒤에는 IllegalStateException. */
    World getWorld();

    default BlockData getBlockData(Vector location) {
        return getBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    int getCenterChunkX();

    default int getCenterBlockX() { return getCenterChunkX() << 4; }

    int getCenterChunkZ();

    default int getCenterBlockZ() { return getCenterChunkZ() << 4; }
}
