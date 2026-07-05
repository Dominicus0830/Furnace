package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public final class FeatureWorldView {
    private final ChunkColumn col;
    private final ChunkColumn[] gridCols;
    private final int cx;
    private final int cz;
    private final int[] biomeIds;
    private final int[][] biomeIdsGrid;
    private final boolean writeCenterOnly;
    private final long worldSeed;
    // 조회(read) 범위 — oracle의 OracleWorldGenLevel.getChunk와 동일하게, 쓰기 범위(origin ±1)보다 넓게
    // 파이프라인 grid center 기준 ±gridReadRadius까지 볼 수 있어야 한다(fancy oak makeLimb의 isFree 판정 등).
    private final int gridCenterX;
    private final int gridCenterZ;
    private final int gridReadRadius;
    private int debugSetBlockCalls;

    public FeatureWorldView(ChunkColumn col, int cx, int cz, int[] biomeIds) {
        this(col, cx, cz, biomeIds, 0L);
    }

    public FeatureWorldView(ChunkColumn col, int cx, int cz, int[] biomeIds, long worldSeed) {
        this.col = col;
        this.gridCols = null;
        this.cx = cx;
        this.cz = cz;
        this.biomeIds = biomeIds;
        this.biomeIdsGrid = null;
        this.writeCenterOnly = false;
        this.worldSeed = worldSeed;
        this.gridCenterX = cx;
        this.gridCenterZ = cz;
        this.gridReadRadius = 1;
    }

    public FeatureWorldView(ChunkColumn[] grid, int[][] biomeIdsGrid, int cx, int cz) {
        this(grid, biomeIdsGrid, cx, cz, false, 0L);
    }

    public FeatureWorldView(ChunkColumn[] grid, int[][] biomeIdsGrid, int cx, int cz, boolean writeCenterOnly) {
        this(grid, biomeIdsGrid, cx, cz, writeCenterOnly, 0L);
    }

    public int cx() { return cx; }
    public int cz() { return cz; }

    public FeatureWorldView(ChunkColumn[] grid, int[][] biomeIdsGrid, int cx, int cz, boolean writeCenterOnly, long worldSeed) {
        this(grid, biomeIdsGrid, cx, cz, cx, cz, 1, writeCenterOnly, worldSeed);
    }

    /**
     * ChunkPipeline 전용 — grid는 gridCenter 기준 ±gridReadRadius 크기(예: 5x5, radius=2)이고, 이 view가
     * 나타내는 origin(cx,cz)은 grid 내부의 한 청크다. 읽기는 gridCenter±gridReadRadius 전체를 허용하되,
     * 쓰기는 oracle(OracleWorldGenLevel writeRadius=1 고정)과 동일하게 origin(cx,cz) ±1로 제한한다.
     */
    public FeatureWorldView(
            ChunkColumn[] grid, int[][] biomeIdsGrid, int cx, int cz,
            int gridCenterX, int gridCenterZ, int gridReadRadius,
            boolean writeCenterOnly, long worldSeed) {
        this.col = null;
        this.gridCols = grid;
        this.cx = cx;
        this.cz = cz;
        this.biomeIds = null;
        this.biomeIdsGrid = biomeIdsGrid;
        this.writeCenterOnly = writeCenterOnly;
        this.worldSeed = worldSeed;
        this.gridCenterX = gridCenterX;
        this.gridCenterZ = gridCenterZ;
        this.gridReadRadius = gridReadRadius;
    }

    public int getBlock(int worldX, int worldY, int worldZ) {
        if (gridCols != null) return getGridBlock(worldX, worldY, worldZ);
        if (!inCenterChunk(worldX, worldZ) || !inWorldY(worldY)) return ChunkSection.AIR;
        return col.getBlock(worldX - cx * 16, worldY, worldZ - cz * 16);
    }

    public void setBlock(int worldX, int worldY, int worldZ, int stateId) {
        if (gridCols != null) {
            setGridBlock(worldX, worldY, worldZ, stateId);
            return;
        }
        if (!inCenterChunk(worldX, worldZ) || !inWorldY(worldY)) return;
        debugSetBlockCalls++;
        col.setBlock(worldX - cx * 16, worldY, worldZ - cz * 16, stateId);
    }

    public int getSurfaceY(int worldX, int worldZ) {
        if (gridCols != null) return getGridSurfaceY(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return ChunkColumn.MIN_Y - 1;
        return col.surfaceBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    /** WORLD_SURFACE_WG 전용 — carve 완료 이후 값이 얼어붙는다(getSurfaceY의 MOTION_BLOCKING류와 대응). */
    public int getWorldSurfaceWgY(int worldX, int worldZ) {
        if (gridCols != null) return getGridWorldSurfaceWgY(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return ChunkColumn.MIN_Y - 1;
        return col.worldSurfaceWgBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getOceanFloorY(int worldX, int worldZ) {
        if (gridCols != null) return getGridOceanFloorY(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return ChunkColumn.MIN_Y - 1;
        return col.oceanFloorBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getWgOceanFloorHeight(int worldX, int worldZ) {
        if (gridCols != null) return getGridWgOceanFloorHeight(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return ChunkColumn.MIN_Y;
        return col.wgOceanFloorHeight(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getBiomeId(int worldX, int worldY, int worldZ) {
        if (gridCols != null) return getGridBiomeId(worldX, worldY, worldZ);
        int qx = (worldX - cx * 16) >> 2;
        int qz = (worldZ - cz * 16) >> 2;
        int qy = Math.floorDiv(worldY - ChunkColumn.MIN_Y, 4);
        if (qx < 0 || qx >= 4 || qz < 0 || qz >= 4 || qy < 0 || qy >= ChunkColumn.SECTION_COUNT * 4) {
            return 0;
        }
        return biomeIds[(qy * 4 + qz) * 4 + qx];
    }

    public long worldSeed() {
        return worldSeed;
    }

    public void resetDebugSetBlockCalls() {
        debugSetBlockCalls = 0;
    }

    public int debugSetBlockCalls() {
        return debugSetBlockCalls;
    }

    private boolean inCenterChunk(int worldX, int worldZ) {
        int minX = cx * 16;
        int minZ = cz * 16;
        return worldX >= minX && worldX < minX + 16 && worldZ >= minZ && worldZ < minZ + 16;
    }

    private int getGridBlock(int worldX, int worldY, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz) || !inWorldY(worldY)) return ChunkSection.AIR;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return ChunkSection.AIR;
        return neighbor.getBlock(worldX - nx * 16, worldY, worldZ - nz * 16);
    }

    private void setGridBlock(int worldX, int worldY, int worldZ, int stateId) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (writeCenterOnly && (nx != cx || nz != cz)) return;
        if (!inWriteRange(nx, nz) || !inWorldY(worldY)) return;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return;
        debugSetBlockCalls++;
        neighbor.setBlock(worldX - nx * 16, worldY, worldZ - nz * 16, stateId);
    }

    private int getGridSurfaceY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return ChunkColumn.MIN_Y - 1;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return ChunkColumn.MIN_Y - 1;
        return neighbor.surfaceBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridWorldSurfaceWgY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return ChunkColumn.MIN_Y - 1;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return ChunkColumn.MIN_Y - 1;
        return neighbor.worldSurfaceWgBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridOceanFloorY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return ChunkColumn.MIN_Y - 1;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return ChunkColumn.MIN_Y - 1;
        return neighbor.oceanFloorBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridWgOceanFloorHeight(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return ChunkColumn.MIN_Y;
        ChunkColumn neighbor = gridCols[gridIndex(nx, nz)];
        if (neighbor == null) return ChunkColumn.MIN_Y;
        return neighbor.wgOceanFloorHeight(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridBiomeId(int worldX, int worldY, int worldZ) {
        int qy = Math.floorDiv(worldY - ChunkColumn.MIN_Y, 4);
        if (qy < 0 || qy >= ChunkColumn.SECTION_COUNT * 4) return 0;
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return 0;
        int[] neighborBiomeIds = biomeIdsGrid[gridIndex(nx, nz)];
        if (neighborBiomeIds == null) return 0;
        int qx = (worldX - nx * 16) >> 2;
        int qz = (worldZ - nz * 16) >> 2;
        return neighborBiomeIds[(qy * 4 + qz) * 4 + qx];
    }

    // 읽기(makeLimb의 isFree 등) — 파이프라인 grid center 기준 ±gridReadRadius. oracle의
    // OracleWorldGenLevel.getChunk(radius=grid 전체)와 동일한 범위여야 fancy oak 분기(dry-run) 판정이 일치한다.
    private boolean inReadRange(int chunkX, int chunkZ) {
        return Math.abs(chunkX - gridCenterX) <= gridReadRadius && Math.abs(chunkZ - gridCenterZ) <= gridReadRadius;
    }

    // 쓰기 — oracle의 OracleWorldGenLevel.setBlock(writeRadius=1 고정)과 동일하게 origin(cx,cz) ±1로 제한.
    private boolean inWriteRange(int chunkX, int chunkZ) {
        return Math.abs(chunkX - cx) <= 1 && Math.abs(chunkZ - cz) <= 1;
    }

    private int gridIndex(int chunkX, int chunkZ) {
        int size = 2 * gridReadRadius + 1;
        return (chunkZ - gridCenterZ + gridReadRadius) * size + (chunkX - gridCenterX + gridReadRadius);
    }

    private static boolean inWorldY(int worldY) {
        return worldY >= ChunkColumn.MIN_Y && worldY < ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16;
    }
}
