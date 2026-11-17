package org.bukkit.generator;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.RegionAccessor;
import org.bukkit.block.BlockState;

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

    int getCenterChunkX();

    default int getCenterBlockX() { return getCenterChunkX() << 4; }

    int getCenterChunkZ();

    default int getCenterBlockZ() { return getCenterChunkZ() << 4; }
}
