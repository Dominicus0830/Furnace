// 청크 컬럼: 24섹션(y −64..319) + MOTION_BLOCKING heightmap. minY=−64.
package org.domi.furnace.server.world;

public final class ChunkColumn {

    public static final int SECTION_COUNT = 24;
    public static final int MIN_Y = -64;
    public static final int HEIGHTMAP_BITS = 9; // worldHeight 384 → 0..384
    public static final int DEFAULT_BIOME_DIRECT_BITS = 7;
    private static final int UNSET_HEIGHT = Integer.MIN_VALUE;
    private static final int WATER_MIN_STATE = 80;
    private static final int WATER_MAX_STATE = 95;
    private static final int LAVA_MIN_STATE = 96;
    private static final int LAVA_MAX_STATE = 111;
    private static final int CAVE_AIR_STATE = 12958;
    private static final int VOID_AIR_STATE = 12959;
    // 수중 식물: vanilla OCEAN_FLOOR heightmap에서 제외됨 (Material.PLANT = not solid)
    private static final int SEAGRASS_STATE = 2008;
    private static final int TALL_SEAGRASS_UPPER_STATE = 2009;
    private static final int TALL_SEAGRASS_LOWER_STATE = 2010;
    private static final int KELP_PLANT_STATE = 12786;
    private static final int KELP_MIN_STATE = 12760;
    private static final int KELP_MAX_STATE = 12785;
    // glow_lichen: Material.REPLACEABLE_PLANT, blocksMotion=false → OCEAN_FLOOR 제외 대상
    private static final int GLOW_LICHEN_MIN_STATE = 6869;
    private static final int GLOW_LICHEN_MAX_STATE = 6996;

    private final ChunkSection[] sections = new ChunkSection[SECTION_COUNT];
    private final int[] surface = new int[256]; // heightmap 값(0 = 바닥 아래 없음)
    private final int[] oceanFloor = new int[256];
    private final int[] wgOceanFloor = new int[256];
    private final java.util.Map<Long, ChestBlockEntity> blockEntities = new java.util.HashMap<>();
    private boolean dirty;
    private byte[] cachedSerializedBody;
    private long[] cachedMotionBlockingHeightmap;
    private ChunkSnapshot cachedSnapshot;

    public ChunkColumn(int biomeId) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection();
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
    }

    public ChunkColumn(int biomeId, int directBits, int biomeDirectBits) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection(directBits, biomeDirectBits);
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
    }

    public ChunkSection[] sections() {
        return sections;
    }

    public int[] sectionBlocks(int sectionIndex) {
        return sections[sectionIndex].openScratch();
    }

    // key = ((x&0x3FFFFFF)<<38) | ((z&0x3FFFFFF)<<12) | (y&0xFFF) — Types.writeBlockPos와 동형(자체 인코딩, 프로토콜과 무관)
    private static long blockEntityKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public ChestBlockEntity chestAt(int x, int y, int z) {
        return blockEntities.get(blockEntityKey(x, y, z));
    }

    public void putChest(ChestBlockEntity chest) {
        blockEntities.put(blockEntityKey(chest.x, chest.y, chest.z), chest);
        cachedSnapshot = null;
    }

    public void removeBlockEntityAt(int x, int y, int z) {
        blockEntities.remove(blockEntityKey(x, y, z));
        cachedSnapshot = null;
    }

    public java.util.Map<Long, ChestBlockEntity> blockEntities() {
        return blockEntities;
    }

    public void compactSections() {
        for (ChunkSection section : sections) section.compact();
    }

    /** worldY 절대좌표에 블록 설정. */
    public void setBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        oceanFloor[z * 16 + x] = UNSET_HEIGHT;
        updateWgOceanFloor(x, worldY, z, stateId);
        updateSurface(x, worldY, z, stateId);
        dirty = true;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    public void setGeneratedBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        oceanFloor[z * 16 + x] = UNSET_HEIGHT;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    /** 블록 자체는 안 바뀌었지만(체스트 아이템 등 블록엔티티 내용 변경) 저장이 필요할 때 호출(§3.6). */
    public void markDirty() {
        dirty = true;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }
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
        System.arraycopy(oceanFloor, 0, wgOceanFloor, 0, oceanFloor.length);
        dirty = false;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedSnapshot = null;
    }

    private void rebuildSurface(int[] blockIds) {
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int surfaceValue = 0;
                int oceanFloorValue = 0;
                for (int y = MIN_Y + SECTION_COUNT * 16 - 1; y >= MIN_Y; y--) {
                    int idx = (y - MIN_Y) * 256 + lz * 16 + lx;
                    int stateId = blockIds[idx];
                    if (surfaceValue == 0 && stateId != ChunkSection.AIR) {
                        surfaceValue = (y - MIN_Y) + 1;
                    }
                    if (oceanFloorValue == 0 && isOceanFloorBlock(stateId)) {
                        oceanFloorValue = (y - MIN_Y) + 1;
                    }
                    if (surfaceValue != 0 && oceanFloorValue != 0) {
                        break;
                    }
                }
                int index = lz * 16 + lx;
                surface[index] = surfaceValue;
                oceanFloor[index] = oceanFloorValue;
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

    public int oceanFloorBlockY(int x, int z) {
        int index = z * 16 + x;
        int value = oceanFloor[index];
        if (value == UNSET_HEIGHT) {
            value = recomputeOceanFloorHeight(x, z);
            oceanFloor[index] = value;
        }
        return MIN_Y + value - 1;
    }

    /**
     * vanilla Heightmap.update(OCEAN_FLOOR_WG) 미러 — ProtoChunk.setBlockState가 worldgen 중 매 블록 쓰기마다
     * WG 계열 heightmap을 라이브 갱신하는 것과 동일(Heightmap.java:76-103). carver·구조물·feature가 setBlock으로
     * 쓴 변경이 이후 OreFeature.place의 OCEAN_FLOOR_WG 게이트에 반영돼야 RNG 소비가 vanilla와 일치한다.
     */
    private void updateWgOceanFloor(int x, int worldY, int z, int stateId) {
        int index = z * 16 + x;
        int first = wgOceanFloor[index]; // firstAvailable - MIN_Y
        if (first == UNSET_HEIGHT) return; // loadGenerated 이전 — 미프라임 상태는 갱신 대상 아님
        int y = worldY - MIN_Y;
        if (y <= first - 2) return;
        if (isOceanFloorBlock(stateId)) {
            if (y >= first) wgOceanFloor[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                if (isOceanFloorBlock(getBlock(x, MIN_Y + i, z))) {
                    wgOceanFloor[index] = i + 1;
                    return;
                }
            }
            wgOceanFloor[index] = 0;
        }
    }

    /**
     * vanilla Heightmap.update(WORLD_SURFACE_WG/MOTION_BLOCKING 등)의 라이브 갱신 미러 — updateWgOceanFloor와
     * 동일 패턴이나 술어가 "!isAir"(surface[] 초기값은 rebuildSurface가 이미 이 술어로 계산). 이 갱신이 없으면
     * feature decoration 도중 나무가 심은 log/leaf가 같은 스텝 내 이후 candidate의 WORLD_SURFACE_WG heightmap
     * 조회(HeightmapPlacement, getSurfaceY)에 반영되지 않아 vanilla ProtoChunk.setBlockState의 라이브 heightmap과
     * 어긋난다(같은 origin 내 tree-tree spill 시 heightmap 불일치로 이어짐).
     */
    private void updateSurface(int x, int worldY, int z, int stateId) {
        int index = z * 16 + x;
        int first = surface[index];
        if (first == 0) return; // rebuildSurface 이전(미프라임) — 갱신 대상 아님
        int y = worldY - MIN_Y;
        if (y <= first - 2) return;
        boolean isAir = stateId == ChunkSection.AIR || stateId == CAVE_AIR_STATE || stateId == VOID_AIR_STATE;
        if (!isAir) {
            if (y >= first) surface[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                int below = getBlock(x, MIN_Y + i, z);
                if (below != ChunkSection.AIR && below != CAVE_AIR_STATE && below != VOID_AIR_STATE) {
                    surface[index] = i + 1;
                    return;
                }
            }
            surface[index] = 0;
        }
    }

    public int wgOceanFloorHeight(int x, int z) {
        int value = wgOceanFloor[z * 16 + x];
        if (value == UNSET_HEIGHT || value == 0) {
            return MIN_Y;
        }
        return MIN_Y + value;
    }

    public long[] motionBlockingHeightmap() {
        if (cachedMotionBlockingHeightmap != null) return cachedMotionBlockingHeightmap;
        BitStorage bs = new BitStorage(HEIGHTMAP_BITS, 256);
        for (int i = 0; i < 256; i++) bs.set(i, surface[i]);
        cachedMotionBlockingHeightmap = bs.data();
        return cachedMotionBlockingHeightmap;
    }

    private int recomputeOceanFloorHeight(int x, int z) {
        for (int y = MIN_Y + SECTION_COUNT * 16 - 1; y >= MIN_Y; y--) {
            if (isOceanFloorBlock(getBlock(x, y, z))) {
                return (y - MIN_Y) + 1;
            }
        }
        return 0;
    }

    // vanilla OCEAN_FLOOR/OCEAN_FLOOR_WG의 isOpaque 술어는 BlockBehaviour::blocksMotion —
    // 잎(leaves)은 blocksMotion=true라서 vanilla는 잎도 바닥으로 센다(MOTION_BLOCKING_NO_LEAVES와만 다름).
    private static boolean isOceanFloorBlock(int stateId) {
        return stateId != ChunkSection.AIR && stateId != CAVE_AIR_STATE && stateId != VOID_AIR_STATE
            && !isFluid(stateId)
            && !isWaterPlant(stateId);
    }

    private static boolean isWaterPlant(int stateId) {
        return stateId == SEAGRASS_STATE
            || stateId == TALL_SEAGRASS_UPPER_STATE
            || stateId == TALL_SEAGRASS_LOWER_STATE
            || stateId == KELP_PLANT_STATE
            || (stateId >= KELP_MIN_STATE && stateId <= KELP_MAX_STATE)
            || (stateId >= GLOW_LICHEN_MIN_STATE && stateId <= GLOW_LICHEN_MAX_STATE);
    }

    private static boolean isFluid(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE
            || stateId >= LAVA_MIN_STATE && stateId <= LAVA_MAX_STATE;
    }

}
