// 블록 좌표. 불변.
package org.domi.furnace.api.world;

public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(x + dx, y + dy, z + dz); }

    /** 청크 좌표. */
    public int chunkX() { return x >> 4; }

    public int chunkZ() { return z >> 4; }
}
