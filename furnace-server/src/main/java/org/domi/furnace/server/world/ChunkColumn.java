// 청크 컬럼: 24섹션(y −64..319) + MOTION_BLOCKING heightmap. minY=−64.
package org.domi.furnace.server.world;

public final class ChunkColumn {

    public static final int SECTION_COUNT = 24;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = MIN_Y + SECTION_COUNT * 16 - 1; // 319 — 블록 좌표 상한(포함)
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
    // 지상 식물(short_grass/fern/dead_bush): blocksMotion=false → vanilla OCEAN_FLOOR heightmap에서 제외됨.
    // patch_grass_forest 등 grass 계열 feature가 이 블록을 심으면(스크린 이전 지형은 여전히 dirt/grass_block),
    // OCEAN_FLOOR가 이를 바닥으로 잘못 세면 surface_water_depth_filter(WORLD_SURFACE - OCEAN_FLOOR)가
    // diff=0으로 오판해 vanilla가 스킵할 후보를 통과시킨다(tree-overlap-residual task, round2 확정).
    private static final int SHORT_GRASS_STATE = 2005;
    private static final int FERN_STATE = 2006;
    private static final int DEAD_BUSH_STATE = 2007;

    private final ChunkSection[] sections = new ChunkSection[SECTION_COUNT];
    private final int[] surface = new int[256]; // heightmap 값(0 = 바닥 아래 없음) — WORLD_SURFACE(!isAir, 항상 라이브)
    private final int[] surfaceWg = new int[256]; // WORLD_SURFACE_WG 미러 — carve 완료 시 얼어붙음(wgOceanFloor와 동일 패턴)
    private final int[] oceanFloor = new int[256];
    private final int[] wgOceanFloor = new int[256];
    private final int[] motionBlocking = new int[256]; // vanilla MOTION_BLOCKING(blocksMotion || fluid) 전용, 항상 라이브
    private final java.util.Map<Long, ChestBlockEntity> blockEntities = new java.util.HashMap<>();
    private boolean dirty;
    // 수정 카운터(단조 증가, 소유 스레드 전용). 비동기 직렬화가 "스냅샷 이후 청크가 바뀌었는가"를 dirty 플래그가 아니라
    // 이 값으로 판정한다 — dirty는 autosave가 clearDirty()로 내리므로 캐시 유효성과는 다른 의미다.
    private long modCount;
    // vanilla ChunkStatus: OCEAN_FLOOR_WG/WORLD_SURFACE_WG는 WORLDGEN_HEIGHTMAPS(EMPTY..SURFACE)에만 속하고
    // CARVERS 이후(FINAL_HEIGHTMAPS)에는 라이브 갱신 대상에서 제외된다 — carve 완료 시점의 값으로 얼어붙는다
    // (round12: OreFeature의 OCEAN_FLOOR_WG 게이트가 carve 이후 decoration 중 라이브로 계속 갱신되던 버그 수정).
    private boolean wgHeightmapsFrozen;
    private byte[] cachedSerializedBody;
    private long[] cachedMotionBlockingHeightmap;
    private long[] cachedWorldSurfaceHeightmap;
    private ChunkSnapshot cachedSnapshot;

    public ChunkColumn(int biomeId) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection();
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
    }

    public ChunkColumn(int biomeId, int directBits, int biomeDirectBits) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection(directBits, biomeDirectBits);
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
    }

    private ChunkColumn() {}

    /**
     * 독립된 상태를 가진 깊은 복사본(ChunkPipeline pristine 캐시용 — CARVERS 상태를 캐시해두고 매 요청마다
     * 이 메서드로 복사해서 쓴다, decoration이 원본을 변형하지 않도록). 클래스 필드 전수 포함 —
     * sections(블록+바이옴), heightmap 5종, freeze 플래그, blockEntities, dirty. cached* 필드는 이 시점에
     * 항상 null이므로(decoration 전에는 조회된 적 없음) 복사하지 않고 null로 둔다(최초 조회 시 재계산, 기존과 동일 동작).
     */
    public ChunkColumn copy() {
        ChunkColumn c = new ChunkColumn();
        for (int i = 0; i < SECTION_COUNT; i++) c.sections[i] = sections[i].copy();
        System.arraycopy(surface, 0, c.surface, 0, surface.length);
        System.arraycopy(surfaceWg, 0, c.surfaceWg, 0, surfaceWg.length);
        System.arraycopy(oceanFloor, 0, c.oceanFloor, 0, oceanFloor.length);
        System.arraycopy(wgOceanFloor, 0, c.wgOceanFloor, 0, wgOceanFloor.length);
        System.arraycopy(motionBlocking, 0, c.motionBlocking, 0, motionBlocking.length);
        c.blockEntities.putAll(blockEntities);
        c.dirty = dirty;
        c.wgHeightmapsFrozen = wgHeightmapsFrozen;
        return c;
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
        touch();
    }

    public void removeBlockEntityAt(int x, int y, int z) {
        blockEntities.remove(blockEntityKey(x, y, z));
        touch();
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
        motionBlocking[z * 16 + x] = UNSET_HEIGHT;
        updateWgOceanFloor(x, worldY, z, stateId);
        updateSurface(x, worldY, z, stateId);
        updateSurfaceWg(x, worldY, z, stateId);
        dirty = true;
        touch();
    }

    public void setGeneratedBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        oceanFloor[z * 16 + x] = UNSET_HEIGHT;
        motionBlocking[z * 16 + x] = UNSET_HEIGHT;
        touch();
    }

    /** 청크 내용이 바뀌었음을 기록하고 파생 캐시를 전부 버린다. 모든 변경자는 이 한 곳을 거친다. */
    private void touch() {
        modCount++;
        cachedSerializedBody = null;
        cachedMotionBlockingHeightmap = null;
        cachedWorldSurfaceHeightmap = null;
        cachedSnapshot = null;
    }

    public long modCount() { return modCount; }

    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    /** 블록 자체는 안 바뀌었지만(체스트 아이템 등 블록엔티티 내용 변경) 저장이 필요할 때 호출(§3.6). */
    public void markDirty() {
        dirty = true;
        touch();
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
        touch();
    }

    public void loadGenerated(int[] blockIds, int[] biomeIds) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i].replaceBlocks(blockIds, i * 4096);
            sections[i].replaceBiomes(biomeIds, i * 64);
        }
        rebuildSurface(blockIds);
        System.arraycopy(oceanFloor, 0, wgOceanFloor, 0, oceanFloor.length);
        System.arraycopy(surface, 0, surfaceWg, 0, surface.length);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
        dirty = false;
        touch();
    }

    /**
     * 표면 단계(SurfaceSystem)가 섹션 블록 배열을 직접 고쳐 쓴 뒤 높이맵을 다시 만든다 — 침식 배드랜드 기둥·얼어붙은 바다 빙산처럼
     * 높이를 바꾸는 확장이 있기 때문. 바닐라는 이 단계에서 WG 높이맵(OCEAN_FLOOR_WG, WORLD_SURFACE_WG)을 setBlockState마다 라이브 갱신한다.
     */
    public void rebuildHeightmapsAfterSurface() {
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int surfaceValue = 0;
                int oceanFloorValue = 0;
                for (int sec = SECTION_COUNT - 1; sec >= 0 && (surfaceValue == 0 || oceanFloorValue == 0); sec--) {
                    int[] blocks = sectionBlocks(sec);
                    for (int ly = 15; ly >= 0; ly--) {
                        int stateId = blocks[ly * 256 + lz * 16 + lx];
                        int y1 = sec * 16 + ly + 1;
                        if (surfaceValue == 0 && stateId != ChunkSection.AIR) surfaceValue = y1;
                        if (oceanFloorValue == 0 && isOceanFloorBlock(stateId)) oceanFloorValue = y1;
                        if (surfaceValue != 0 && oceanFloorValue != 0) break;
                    }
                }
                int index = lz * 16 + lx;
                surface[index] = surfaceValue;
                oceanFloor[index] = oceanFloorValue;
            }
        }
        System.arraycopy(oceanFloor, 0, wgOceanFloor, 0, oceanFloor.length);
        System.arraycopy(surface, 0, surfaceWg, 0, surface.length);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
        touch();
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
        touch();
    }

    public int surfaceBlockY(int x, int z) {
        return MIN_Y + surface[z * 16 + x] - 1;
    }

    /** WORLD_SURFACE_WG 전용 조회 — carve 완료 이후 값이 얼어붙는다(oceanFloorBlockY의 WG류와 대응). */
    public int worldSurfaceWgBlockY(int x, int z) {
        return MIN_Y + surfaceWg[z * 16 + x] - 1;
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
    /** carve 완료 시점 이후로는 OCEAN_FLOOR_WG를 더 이상 갱신하지 않는다(vanilla CARVERS 상태부터 FINAL_HEIGHTMAPS만 라이브). */
    public void freezeWgHeightmaps() {
        wgHeightmapsFrozen = true;
    }

    private void updateWgOceanFloor(int x, int worldY, int z, int stateId) {
        if (wgHeightmapsFrozen) return;
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

    /** WORLD_SURFACE_WG 미러 — updateWgOceanFloor와 동일 패턴(freeze 이후 no-op)으로 surfaceWg[]를 라이브 갱신. */
    private void updateSurfaceWg(int x, int worldY, int z, int stateId) {
        if (wgHeightmapsFrozen) return;
        int index = z * 16 + x;
        int first = surfaceWg[index];
        if (first == 0) return; // rebuildSurface 이전(미프라임) — 갱신 대상 아님
        int y = worldY - MIN_Y;
        if (y <= first - 2) return;
        boolean isAir = stateId == ChunkSection.AIR || stateId == CAVE_AIR_STATE || stateId == VOID_AIR_STATE;
        if (!isAir) {
            if (y >= first) surfaceWg[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                int below = getBlock(x, MIN_Y + i, z);
                if (below != ChunkSection.AIR && below != CAVE_AIR_STATE && below != VOID_AIR_STATE) {
                    surfaceWg[index] = i + 1;
                    return;
                }
            }
            surfaceWg[index] = 0;
        }
    }

    public int wgOceanFloorHeight(int x, int z) {
        int value = wgOceanFloor[z * 16 + x];
        if (value == UNSET_HEIGHT || value == 0) {
            return MIN_Y;
        }
        return MIN_Y + value;
    }

    /**
     * vanilla MOTION_BLOCKING(blocksMotion() || fluid) 미러 — surface[](WORLD_SURFACE, !isAir)와 달리
     * 유리·레일·토치·버튼·압력판 등 blocksMotion=false 블록은 바닥으로 세지 않는다(motionblocking-predicate task).
     * oceanFloor[]와 동일한 "invalidate to UNSET_HEIGHT + lazy 전체 재스캔" 패턴 — 증분 갱신 로직 없이도
     * setBlock/setGeneratedBlock/loadGenerated 시 무효화되면 다음 조회에서 항상 실제 블록 기준으로 재계산된다.
     */
    public long[] motionBlockingHeightmap() {
        if (cachedMotionBlockingHeightmap != null) return cachedMotionBlockingHeightmap;
        BitStorage bs = new BitStorage(HEIGHTMAP_BITS, 256);
        for (int i = 0; i < 256; i++) bs.set(i, motionBlockingHeightValue(i & 15, i >> 4));
        cachedMotionBlockingHeightmap = bs.data();
        return cachedMotionBlockingHeightmap;
    }

    public int motionBlockingBlockY(int x, int z) {
        return MIN_Y + motionBlockingHeightValue(x, z) - 1;
    }

    private int motionBlockingHeightValue(int x, int z) {
        int index = z * 16 + x;
        int value = motionBlocking[index];
        if (value == UNSET_HEIGHT) {
            value = recomputeMotionBlockingHeight(x, z);
            motionBlocking[index] = value;
        }
        return value;
    }

    private int recomputeMotionBlockingHeight(int x, int z) {
        for (int y = MIN_Y + SECTION_COUNT * 16 - 1; y >= MIN_Y; y--) {
            if (isMotionBlockingBlock(getBlock(x, y, z))) {
                return (y - MIN_Y) + 1;
            }
        }
        return 0;
    }

    /**
     * vanilla WORLD_SURFACE(isOpaque = not air) 미러 — surface[]는 updateSurface의 !isAir 술어로
     * 라이브 갱신되므로(§updateSurface 문서 참조) WORLD_SURFACE semantics와 동일한 소스다.
     */
    public long[] worldSurfaceHeightmap() {
        if (cachedWorldSurfaceHeightmap != null) return cachedWorldSurfaceHeightmap;
        BitStorage bs = new BitStorage(HEIGHTMAP_BITS, 256);
        for (int i = 0; i < 256; i++) bs.set(i, surface[i]);
        cachedWorldSurfaceHeightmap = bs.data();
        return cachedWorldSurfaceHeightmap;
    }

    private int recomputeOceanFloorHeight(int x, int z) {
        for (int y = MIN_Y + SECTION_COUNT * 16 - 1; y >= MIN_Y; y--) {
            if (isOceanFloorBlock(getBlock(x, y, z))) {
                return (y - MIN_Y) + 1;
            }
        }
        return 0;
    }

    // MOTION_BLOCKING 전용 이름 분류에 쓰는 지연 로드 레지스트리(ChunkColumn은 생성자에 registry를 받지 않는
    // 기존 설계라, 다른 곳들처럼 명시적 주입 대신 FurnaceMain과 동일 리소스를 1회 지연 로드해 재사용한다).
    private static volatile BlockRegistry motionBlockingNameRegistry;

    private static BlockRegistry motionBlockingNameRegistry() {
        BlockRegistry r = motionBlockingNameRegistry;
        if (r == null) {
            r = BlockRegistry.load("blocks/1.21.1.txt");
            motionBlockingNameRegistry = r;
        }
        return r;
    }

    /**
     * vanilla MOTION_BLOCKING(blocksMotion() || fluid) 술어 — isOceanFloorBlock(blocksMotion 근사: 공기/유체/
     * 식물 제외)에서 fluid를 다시 포함시키고, blocksMotion=false인 무충돌 장식 블록(레일·레드스톤류·버튼·
     * 압력판·토치·표지판·현수막·사다리·덩굴 등, isNonMotionBlockingByName 참조)을 추가로 제외한다.
     */
    private static boolean isMotionBlockingBlock(int stateId) {
        if (isFluid(stateId)) return true;
        if (!isOceanFloorBlock(stateId)) return false;
        return !isNonMotionBlockingByName(motionBlockingNameRegistry().nameOf(stateId));
    }

    /**
     * 근사: vanilla BlockBehaviour::blocksMotion은 실제로는 콜리전 shape 부피(≥0.7291666)로 계산되는데,
     * 이 저장소엔 블록별 콜리전 shape 데이터가 없다(motionblocking-predicate task 조사 결과) — 이름 기반으로
     * "무충돌/얇은 장식" 블록을 화이트리스트해 blocksMotion=false로 근사한다. 범위: 레일류, 레드스톤 부품
     * (와이어·리피터·컴퍼레이터·레버·트립와이어·버튼·압력판), 토치·랜턴·양초, 표지판·현수막, 사다리, 덩굴류
     * (vine/lily_pad/sea_pickle/cobweb/end_rod), 새싹, 짧은/키큰 식물·꽃·버섯·작물류(짧은잔디/양치/죽은덤불은
     * isLandPlant가 이미 처리). **커버 안 된 항목**(포인티드 드립스톤, 이끼 카펫 예외 케이스, 눈더미 높이별
     * 콜리전, 스캐폴딩, 조각술 감지기 등)은 기존 동작대로 blocksMotion=true로 남는다 — 이번 조사에서 실측
     * 확증하지 못해 회귀 위험 최소화 차원에서 손대지 않았다(surgical 원칙, 아래 result.md Issues 참조).
     */
    private static boolean isNonMotionBlockingByName(String name) {
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        if (n.endsWith("_rail") || n.equals("rail")) return true;
        if (n.endsWith("_button")) return true;
        if (n.endsWith("_pressure_plate")) return true;
        if (n.endsWith("torch")) return true; // torch/wall_torch/redstone_torch/redstone_wall_torch/soul_torch/soul_wall_torch
        if (n.endsWith("_sign")) return true; // 서있는/벽/현수 표지판 전부 "_sign"으로 끝남
        if (n.endsWith("_banner")) return true;
        if (n.endsWith("candle")) return true; // *_candle_cake는 "cake"로 끝나 매치 안 됨(별개 취급, 미확인)
        if (n.endsWith("lantern")) return true;
        if (n.endsWith("_sapling") || n.equals("sapling")) return true;
        switch (n) {
            case "redstone_wire", "repeater", "comparator", "lever", "tripwire", "tripwire_hook",
                 "ladder", "vine", "lily_pad", "sea_pickle", "end_rod", "cobweb", "bamboo_sapling",
                 "sugar_cane", "bamboo", "sweet_berry_bush",
                 "wheat", "carrots", "potatoes", "beetroots", "nether_wart",
                 "tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony",
                 "brown_mushroom", "red_mushroom",
                 "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
                 "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                 "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // vanilla OCEAN_FLOOR/OCEAN_FLOOR_WG의 isOpaque 술어는 BlockBehaviour::blocksMotion —
    // 잎(leaves)은 blocksMotion=true라서 vanilla는 잎도 바닥으로 센다(MOTION_BLOCKING_NO_LEAVES와만 다름).
    private static boolean isOceanFloorBlock(int stateId) {
        return stateId != ChunkSection.AIR && stateId != CAVE_AIR_STATE && stateId != VOID_AIR_STATE
            && !isFluid(stateId)
            && !isWaterPlant(stateId)
            && !isLandPlant(stateId);
    }

    private static boolean isLandPlant(int stateId) {
        return stateId == SHORT_GRASS_STATE || stateId == FERN_STATE || stateId == DEAD_BUSH_STATE;
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
