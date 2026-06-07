// 청크 좌표(블록>>4). diff·로드셋 키로 쓰이는 value type.
package org.domi.furnace.server.world;

public record ChunkPos(int x, int z) {

    /** 블록 절대좌표 → 청크 좌표. */
    public static ChunkPos ofBlock(double blockX, double blockZ) {
        return new ChunkPos(Math.floorDiv((int) Math.floor(blockX), 16),
                Math.floorDiv((int) Math.floor(blockZ), 16));
    }
}
