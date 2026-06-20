// 청크 컬럼: 24섹션(y −64..319) + MOTION_BLOCKING heightmap. minY=−64.
package org.domi.furnace.server.world;

public final class ChunkColumn {

    public static final int SECTION_COUNT = 24;
    public static final int MIN_Y = -64;
    public static final int HEIGHTMAP_BITS = 9; // worldHeight 384 → 0..384
    public static final int DEFAULT_BIOME_DIRECT_BITS = 7;

    private final ChunkSection[] sections = new ChunkSection[SECTION_COUNT];
    private final int[] surface = new int[256]; // heightmap 값(0 = 바닥 아래 없음)
    private boolean dirty;
    private byte[] cachedSerializedBody;
    private long[] cachedMotionBlockingHeightmap;
    private ChunkSnapshot cachedSnapshot;

    public ChunkColumn(int biomeId) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection();
            sections[i].fillBiome(biomeId);
        }
    }

    public ChunkColumn(int biomeId, int directBits, int biomeDirectBits) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection(directBits, biomeDirectBits);
            sections[i].fillBiome(biomeId);
        }
    }

    public ChunkSection[] sections() {
        return sections;
    }

    public int[] sectionBlocks(int sectionIndex) {
        return sections[sectionIndex].openScratch();
    }

    public void compactSections() {
        for (ChunkSection section : sections) section.compact();
    }

    /** worldY 절대좌표에 블록 설정. */
    public void setBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        dirty = true;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    public void setGeneratedBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public byte[] cachedSerializedBody() { return cachedSerializedBody; }
    public void cachedSerializedBody(byte[] body) { this.cachedSerializedBody = body; }
    public ChunkSnapshot cachedSnapshot() { return cachedSnapshot; }
    public void cachedSnapshot(ChunkSnapshot snapshot) { this.cachedSnapshot = snapshot; }

    /** worldY 절대좌표 블록 조회. */
    public int getBlock(int x, int worldY, int z) {
        int local = worldY - MIN_Y;
        return sections[local >> 4].getBlock(x, local & 15, z);
    }

    /**
     * 청크 내 quart 좌표(quartX,quartY,quartZ)에 바이옴 id 설정.
     * quartX, quartZ 0..3, quartY 0..95 (블록 y = -64 + quartY*4).
     */
    public void setBiome(int quartX, int quartY, int quartZ, int biomeId) {
        sections[quartY / 4].setBiome(quartX, quartY % 4, quartZ, biomeId);
        cachedSerializedBody = null;
    }

    public void loadGenerated(int[] blockIds, int[] biomeIds) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i].replaceBlocks(blockIds, i * 4096);
            sections[i].replaceBiomes(biomeIds, i * 64);
        }
        rebuildSurface(blockIds);
        dirty = false;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    private void rebuildSurface(int[] blockIds) {
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int value = 0;
                for (int y = MIN_Y + SECTION_COUNT * 16 - 1; y >= MIN_Y; y--) {
                    int idx = (y - MIN_Y) * 256 + lz * 16 + lx;
                    if (blockIds[idx] != ChunkSection.AIR) {
                        value = (y - MIN_Y) + 1;
                        break;
                    }
                }
                surface[lz * 16 + lx] = value;
            }
        }
    }

    /** (x,z) heightmap 값(= surfaceWorldY − minY + 1). */
    public void setSurface(int x, int z, int value) {
        surface[z * 16 + x] = value;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    public int surfaceBlockY(int x, int z) {
        return MIN_Y + surface[z * 16 + x] - 1;
    }

    public long[] motionBlockingHeightmap() {
        if (cachedMotionBlockingHeightmap != null) return cachedMotionBlockingHeightmap;
        BitStorage bs = new BitStorage(HEIGHTMAP_BITS, 256);
        for (int i = 0; i < 256; i++) bs.set(i, surface[i]);
        cachedMotionBlockingHeightmap = bs.data();
        return cachedMotionBlockingHeightmap;
    }
}
