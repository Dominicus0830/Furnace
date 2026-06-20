package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public final class FeatureWorldView {

    private final ChunkColumn col;
    private final int cx;
    private final int cz;
    private final int[] biomeIds;

    public FeatureWorldView(ChunkColumn col, int cx, int cz, int[] biomeIds) {
        this.col = col;
        this.cx = cx;
        this.cz = cz;
        this.biomeIds = biomeIds;
    }

    public int getBlock(int worldX, int worldY, int worldZ) {
        if (!inCenterChunk(worldX, worldZ) || !inWorldY(worldY)) return ChunkSection.AIR;
        return col.getBlock(worldX - cx * 16, worldY, worldZ - cz * 16);
    }

    public void setBlock(int worldX, int worldY, int worldZ, int stateId) {
        if (!inCenterChunk(worldX, worldZ) || !inWorldY(worldY)) return;
        col.setBlock(worldX - cx * 16, worldY, worldZ - cz * 16, stateId);
    }

    public int getSurfaceY(int worldX, int worldZ) {
        if (!inCenterChunk(worldX, worldZ)) return ChunkColumn.MIN_Y - 1;
        return col.surfaceBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getBiomeId(int worldX, int worldY, int worldZ) {
        int qx = (worldX - cx * 16) >> 2;
        int qz = (worldZ - cz * 16) >> 2;
        int qy = Math.floorDiv(worldY - ChunkColumn.MIN_Y, 4);
        if (qx < 0 || qx >= 4 || qz < 0 || qz >= 4 || qy < 0 || qy >= ChunkColumn.SECTION_COUNT * 4) {
            return 0;
        }
        return biomeIds[(qy * 4 + qz) * 4 + qx];
    }

    private boolean inCenterChunk(int worldX, int worldZ) {
        int minX = cx * 16;
        int minZ = cz * 16;
        return worldX >= minX && worldX < minX + 16 && worldZ >= minZ && worldZ < minZ + 16;
    }

    private static boolean inWorldY(int worldY) {
        return worldY >= ChunkColumn.MIN_Y && worldY < ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16;
    }
}
