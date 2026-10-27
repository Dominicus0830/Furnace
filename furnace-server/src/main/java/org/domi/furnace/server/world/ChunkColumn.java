// 청크 컬럼: 24섹션(y −64..319) + MOTION_BLOCKING heightmap. minY=−64.
package org.domi.furnace.server.world;

public final class ChunkColumn {

    public static final int SECTION_COUNT = 24;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = MIN_Y + SECTION_COUNT * 16 - 1; // 319 — 블록 좌표 상한(포함)
    public static final int HEIGHTMAP_BITS = 9; // worldHeight 384 → 0..384
    public static final int DEFAULT_BIOME_DIRECT_BITS = 7;
    private static final int UNSET_HEIGHT = Integer.MIN_VALUE;
    private static final int CAVE_AIR_STATE = 12958;
    private static final int VOID_AIR_STATE = 12959;

    /** 차원의 최저 블록 y 와 섹션 수(오버월드 -64/24, 네더·엔드 0/16). */
    private final int minY;
    private final int sectionCount;
    private final int heightmapBits;
    private final ChunkSection[] sections;
    private final int[] surface = new int[256]; // heightmap 값(0 = 바닥 아래 없음) — WORLD_SURFACE(!isAir, 항상 라이브)
    private final int[] surfaceWg = new int[256]; // WORLD_SURFACE_WG 미러 — carve 완료 시 얼어붙음(wgOceanFloor와 동일 패턴)
    private final int[] oceanFloor = new int[256];
    private final int[] wgOceanFloor = new int[256];
    private final int[] motionBlocking = new int[256]; // vanilla MOTION_BLOCKING(blocksMotion || fluid) 전용, 항상 라이브
    private final java.util.Map<Long, org.domi.furnace.server.block.entity.BlockEntity> blockEntities = new java.util.HashMap<>();
    /** 월드젠(구조물 템플릿)이 만든 블록 엔티티 NBT — 키 = blockEntityKey(로컬 x,y,z). 저장/복원 연동 전 단계의 보관소. */
    private java.util.Map<Long, org.domi.furnace.server.net.io.nbt.NbtCompound> generatedBlockEntities;
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
        this(MIN_Y, SECTION_COUNT);
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = new ChunkSection();
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
    }

    public ChunkColumn(int biomeId, int directBits, int biomeDirectBits) {
        this(biomeId, directBits, biomeDirectBits, MIN_Y, SECTION_COUNT);
    }

    public ChunkColumn(int biomeId, int directBits, int biomeDirectBits, int minY, int sectionCount) {
        this(minY, sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = new ChunkSection(directBits, biomeDirectBits);
            sections[i].fillBiome(biomeId);
        }
        java.util.Arrays.fill(oceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(wgOceanFloor, UNSET_HEIGHT);
        java.util.Arrays.fill(motionBlocking, UNSET_HEIGHT);
    }

    private ChunkColumn(int minY, int sectionCount) {
        this.minY = minY;
        this.sectionCount = sectionCount;
        this.heightmapBits = 32 - Integer.numberOfLeadingZeros(sectionCount * 16);   // ceil(log2(height + 1))
        this.sections = new ChunkSection[sectionCount];
    }

    public int minY() { return minY; }

    public int sectionCount() { return sectionCount; }

    /** 블록 좌표 상한(포함). */
    public int maxY() { return minY + sectionCount * 16 - 1; }

    /**
     * 독립된 상태를 가진 깊은 복사본(ChunkPipeline pristine 캐시용 — CARVERS 상태를 캐시해두고 매 요청마다
     * 이 메서드로 복사해서 쓴다, decoration이 원본을 변형하지 않도록). 클래스 필드 전수 포함 —
     * sections(블록+바이옴), heightmap 5종, freeze 플래그, blockEntities, dirty. cached* 필드는 이 시점에
     * 항상 null이므로(decoration 전에는 조회된 적 없음) 복사하지 않고 null로 둔다(최초 조회 시 재계산, 기존과 동일 동작).
     */
    public ChunkColumn copy() {
        ChunkColumn c = new ChunkColumn(minY, sectionCount);
        for (int i = 0; i < sectionCount; i++) c.sections[i] = sections[i].copy();
        System.arraycopy(surface, 0, c.surface, 0, surface.length);
        System.arraycopy(surfaceWg, 0, c.surfaceWg, 0, surfaceWg.length);
        System.arraycopy(oceanFloor, 0, c.oceanFloor, 0, oceanFloor.length);
        System.arraycopy(wgOceanFloor, 0, c.wgOceanFloor, 0, wgOceanFloor.length);
        System.arraycopy(motionBlocking, 0, c.motionBlocking, 0, motionBlocking.length);
        c.blockEntities.putAll(blockEntities);
        c.dirty = dirty;
        c.wgHeightmapsFrozen = wgHeightmapsFrozen;
        c.structures = structures;
        return c;
    }

    public ChunkSection[] sections() {
        return sections;
    }

    public int[] sectionBlocks(int sectionIndex) {
        return sections[sectionIndex].openScratch();
    }

    // key = ((x&0x3FFFFFF)<<38) | ((z&0x3FFFFFF)<<12) | (y&0xFFF) — Types.writeBlockPos와 동형(자체 인코딩, 프로토콜과 무관)
    public void putGeneratedBlockEntity(int x, int y, int z, org.domi.furnace.server.net.io.nbt.NbtCompound nbt) {
        if (generatedBlockEntities == null) generatedBlockEntities = new java.util.HashMap<>();
        generatedBlockEntities.put(blockEntityKey(x, y, z), nbt);
    }

    // 바닐라 ChunkAccess.markPosForPostprocessing — 청크가 FULL이 될 때 neighbour shape 갱신을 받을 칸(로컬 x,z + 월드 y).
    private java.util.Set<Long> postProcessMarks;

    public void markPosForPostprocessing(int lx, int y, int lz) {
        if (postProcessMarks == null) postProcessMarks = new java.util.LinkedHashSet<>();
        postProcessMarks.add(((long) lx << 40) | ((long) lz << 32) | (y & 0xFFFFFFFFL));
    }

    /** 표시된 칸 {lx, y, lz} 목록(표시 순서). */
    public java.util.List<int[]> postProcessMarks() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (postProcessMarks != null) {
            for (long k : postProcessMarks) out.add(new int[]{(int) (k >> 40) & 0xFF, (int) k, (int) (k >> 32) & 0xFF});
        }
        return out;
    }

    public void clearPostProcessMarks() { postProcessMarks = null; }

    // 월드젠 중 구조물 등이 스폰한 엔티티(NBT: id, Pos, Rotation …) — 청크 로드/저장 쪽에서 소비한다.
    private java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> generatedEntities;

    private boolean freshlyGenerated;

    /** 방금 생성기로 만든 청크(디스크에서 읽은 것이 아님) — 올릴 때 한 번 동물을 놓는다(NaturalSpawner.spawnMobsForChunkGeneration). */
    public boolean freshlyGenerated() { return freshlyGenerated; }

    public void freshlyGenerated(boolean v) { this.freshlyGenerated = v; }

    private boolean lightPrecomputed;

    /** 빛을 생성 스레드에서 미리 계산해 뒀다(이웃과 가장자리만 맞추면 된다). */
    public boolean lightPrecomputed() { return lightPrecomputed; }

    public void lightPrecomputed(boolean v) { this.lightPrecomputed = v; }

    public void addGeneratedEntity(org.domi.furnace.server.net.io.nbt.NbtCompound nbt) {
        if (generatedEntities == null) generatedEntities = new java.util.ArrayList<>();
        generatedEntities.add(nbt);
    }

    /** 월드젠 엔티티 목록을 비운다(저장 대상으로 표시는 부르는 쪽 — addWorldGenChunkEntities 가 markDirty). */
    public void clearGeneratedEntities() { generatedEntities = null; touch(); }

    public java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> generatedEntities() {
        return generatedEntities == null ? java.util.List.of() : generatedEntities;
    }

    public org.domi.furnace.server.net.io.nbt.NbtCompound generatedBlockEntity(int x, int y, int z) {
        return generatedBlockEntities == null ? null : generatedBlockEntities.get(blockEntityKey(x, y, z));
    }

    public java.util.Map<Long, org.domi.furnace.server.net.io.nbt.NbtCompound> generatedBlockEntities() {
        return generatedBlockEntities == null ? java.util.Map.of() : generatedBlockEntities;
    }

    private static long blockEntityKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public ChestBlockEntity chestAt(int x, int y, int z) {
        var be = blockEntities.get(blockEntityKey(x, y, z));
        if (be == null && generatedBlockEntities != null) return adoptGeneratedChest(x, y, z);
        return be instanceof ChestBlockEntity c ? c : null;
    }

    /** 월드젠이 남긴 상자 NBT(LootTable·LootTableSeed, 아이템 없음)를 처음 쓸 때 상자 블록 엔티티로 바꾼다. */
    private ChestBlockEntity adoptGeneratedChest(int x, int y, int z) {
        long local = blockEntityKey(x & 15, y, z & 15);
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt = generatedBlockEntities.get(local);
        if (nbt == null) return null;
        Object id = nbt.tags().get("id");
        if (!"minecraft:chest".equals(id) && !"minecraft:trapped_chest".equals(id)) return null;
        if (nbt.contains("Items") && nbt.getList("Items").size() > 0) return null;   // 아이템이 든 생성 상자는 아직 해석하지 않는다
        ChestBlockEntity c = ChestBlockEntity.empty((String) id, x, y, z);
        if (nbt.tags().get("LootTable") instanceof String t) {
            c.lootTable = t;
            c.lootSeed = nbt.tags().get("LootTableSeed") instanceof Long l ? l : 0L;
        }
        generatedBlockEntities.remove(local);
        blockEntities.put(blockEntityKey(x, y, z), c);
        touch();
        return c;
    }

    public void putChest(ChestBlockEntity chest) { putBlockEntity(chest); }

    /** 구역 y 별 게임 이벤트 리스너(바닐라 LevelChunk.gameEventListenerRegistrySections). 저장하지 않는다. */
    private java.util.Map<Integer, org.domi.furnace.server.world.gameevent.GameEventListenerRegistry> listenerRegistries;

    /** getListenerRegistry(sectionY): create 면 없을 때 만든다(비면 스스로 빠진다). 없으면 null. */
    public org.domi.furnace.server.world.gameevent.GameEventListenerRegistry listenerRegistry(int sectionY, boolean create) {
        if (listenerRegistries == null) {
            if (!create) return null;
            listenerRegistries = new java.util.HashMap<>();
        }
        var r = listenerRegistries.get(sectionY);
        if (r == null && create) {
            r = new org.domi.furnace.server.world.gameevent.GameEventListenerRegistry(() -> listenerRegistries.remove(sectionY));
            listenerRegistries.put(sectionY, r);
        }
        return r;
    }

    /** 그 칸의 블록 엔티티(상자 포함, 없으면 null). 월드젠이 남긴 NBT 는 포함하지 않는다. */
    public org.domi.furnace.server.block.entity.BlockEntity blockEntityAt(int x, int y, int z) {
        return blockEntities.get(blockEntityKey(x, y, z));
    }

    /** 블록 엔티티를 그 칸에 둔다(같은 칸의 월드젠 NBT 는 지운다 — 바닐라 setBlockEntity 가 이전 것을 갈아 치우듯). */
    public void putBlockEntity(org.domi.furnace.server.block.entity.BlockEntity be) {
        var p = be.pos();
        var old = blockEntities.put(blockEntityKey(p.x(), p.y(), p.z()), be);
        if (old != null && old != be) old.setRemoved();
        if (generatedBlockEntities != null) generatedBlockEntities.remove(blockEntityKey(p.x() & 15, p.y(), p.z() & 15));
        touch();
    }

    public void removeBlockEntityAt(int x, int y, int z) {
        var old = blockEntities.remove(blockEntityKey(x, y, z));
        if (old != null) old.setRemoved();
        if (generatedBlockEntities != null) generatedBlockEntities.remove(blockEntityKey(x & 15, y, z & 15));
        touch();
    }

    /** 블록별: 블록 엔티티가 있어야 하는가(newBlockEntity 가 만든다, 또는 상자·덫 상자 — 상자는 ChestBlockEntity 별도 경로). */
    private static volatile boolean[] HAS_BE;

    private static boolean[] hasBlockEntityTable() {
        boolean[] t = HAS_BE;
        if (t != null) return t;
        var blocks = org.domi.furnace.server.block.Blocks.get();
        t = new boolean[blocks.blockCount()];
        var origin = new org.domi.furnace.server.block.Pos(0, 0, 0);
        for (int b = 0; b < t.length; b++) {
            int s = blocks.defaultState(b);
            t[b] = org.domi.furnace.server.block.BlockBehaviors.ofBlock(b).newBlockEntity(origin, s) != null;
        }
        t[blocks.block("minecraft:chest")] = true;
        t[blocks.block("minecraft:trapped_chest")] = true;
        return HAS_BE = t;
    }

    /**
     * LevelChunk.registerAllBlockEntitiesAfterLevelLoad 의 promotePendingBlockEntity 부분(청크를 올리기 전, 소유 스레드 밖에서 아직 공개 전인 청크에).
     * 월드젠·저장 파일이 남긴 NBT 중 이식한 종류는 블록 엔티티로 바꾼다. 상자는 adoptGeneratedChest 로(아이템이 든 생성 상자는 아직 NBT 로 남는다).
     * 상자도 청크 패킷 block_entities 에 있어야 클라이언트가 그린다.
     * dummyScan 이면 바닐라 WorldGenRegion.setBlock 의 "DUMMY" NBT 처럼, 블록 엔티티가 있어야 할 칸에 없으면 newBlockEntity 로 만든다.
     * 침대·종은 블록 엔티티가 없으면 클라이언트가 그리지 않는다(사용자 테스트 D1·D3).
     */
    public void promotePendingBlockEntities(int chunkX, int chunkZ, boolean dummyScan) {
        long before = modCount;
        promote(chunkX, chunkZ, dummyScan);
        // 바뀌었으면 저장 대상으로 — 디스크 청크도 한 번 다시 써서 승격·채운 블록 엔티티를 영속시킨다(다음 로드부터는 할 일이 없다)
        if (modCount != before) markDirty();
    }

    private void promote(int chunkX, int chunkZ, boolean dummyScan) {
        if (generatedBlockEntities != null && !generatedBlockEntities.isEmpty()) {
            java.util.List<int[]> keys = new java.util.ArrayList<>();
            forEachGeneratedBlockEntity((lx, y, lz, nbt) -> {
                Object id = nbt.tags().get("id");
                if (id instanceof String s && ("minecraft:chest".equals(s) || "minecraft:trapped_chest".equals(s)
                        || org.domi.furnace.server.block.entity.BlockEntities.known(s))) keys.add(new int[]{lx, y, lz});
            });
            for (int[] k : keys) {
                var nbt = generatedBlockEntities.get(blockEntityKey(k[0], k[1], k[2]));
                Object cid = nbt.tags().get("id");
                if ("minecraft:chest".equals(cid) || "minecraft:trapped_chest".equals(cid)) {
                    int cs = getBlock(k[0], k[1], k[2]);
                    if (chestMatches(cs, cid)) adoptGeneratedChest((chunkX << 4) + k[0], k[1], (chunkZ << 4) + k[2]);
                    continue;
                }
                int state = getBlock(k[0], k[1], k[2]);
                var pos = new org.domi.furnace.server.block.Pos((chunkX << 4) + k[0], k[1], (chunkZ << 4) + k[2]);
                var be = org.domi.furnace.server.block.entity.BlockEntities.loadStatic(pos, state, nbt);
                if (be != null && be.isValid(state)) putBlockEntity(be);
            }
        }
        if (!dummyScan) return;
        boolean[] has = hasBlockEntityTable();
        var blocks = org.domi.furnace.server.block.Blocks.get();
        int baseX = chunkX, baseZ = chunkZ;
        for (int i = 0; i < sectionCount; i++) {
            ChunkSection cs = sections[i];
            if (cs == null || cs.hasOnlyAir()) continue;
            boolean any = false;
            for (int s : cs.blocks()) if (has[blocks.blockOf(s)]) { any = true; break; }
            if (!any) continue;
            int by = minY + (i << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int s = cs.getBlock(x, y, z);
                        if (!has[blocks.blockOf(s)]) continue;
                        int wy = by + y;
                        if (blockEntities.containsKey(blockEntityKey((baseX << 4) + x, wy, (baseZ << 4) + z))) continue;
                        if (generatedBlockEntities != null && generatedBlockEntities.containsKey(blockEntityKey(x, wy, z))) continue;
                        var pos = new org.domi.furnace.server.block.Pos((baseX << 4) + x, wy, (baseZ << 4) + z);
                        String chestId = chestIdOf(s);
                        if (chestId != null) {
                            putBlockEntity(ChestBlockEntity.empty(chestId, pos.x(), pos.y(), pos.z()));
                            continue;
                        }
                        var be = org.domi.furnace.server.block.BlockBehaviors.of(s).newBlockEntity(pos, s);
                        if (be != null) putBlockEntity(be);
                    }
                }
            }
        }
    }

    private static String chestIdOf(int state) {
        String n = org.domi.furnace.server.block.Blocks.get().nameOfState(state);
        return "minecraft:chest".equals(n) || "minecraft:trapped_chest".equals(n) ? n : null;
    }

    /** 그 칸 블록이 NBT 의 상자 종류와 같은가(BlockEntityType.isValid). */
    private static boolean chestMatches(int state, Object id) { return id.equals(chestIdOf(state)); }

    /** 생성·로드된 미지원 블록 엔티티를 (로컬 x, 월드 y, 로컬 z, NBT) 로 순회. */
    public void forEachGeneratedBlockEntity(GeneratedBlockEntityVisitor v) {
        if (generatedBlockEntities == null) return;
        for (var e : generatedBlockEntities.entrySet()) {
            long k = e.getKey();
            int lx = (int) (k >>> 38) & 0x3FFFFFF;
            int lz = (int) (k >>> 12) & 0x3FFFFFF;
            int y = ((int) k << 20) >> 20;
            v.visit(lx, y, lz, e.getValue());
        }
    }

    @FunctionalInterface
    public interface GeneratedBlockEntityVisitor {
        void visit(int lx, int y, int lz, org.domi.furnace.server.net.io.nbt.NbtCompound nbt);
    }

    // 저장 파일에서 읽었지만 서버가 해석하지 않는 청크 NBT 키(구조물 시작점·틱 목록·다른 서버의 확장 키 등) — 저장 시 그대로 되돌린다.
    private org.domi.furnace.server.net.io.nbt.NbtCompound extraNbt;
    private long inhabitedTime;
    /** 빛 데이터(빛 엔진이 켠 뒤에만 있다). 복사본에는 따라가지 않는다. */
    private org.domi.furnace.server.light.ChunkLight light;

    public org.domi.furnace.server.light.ChunkLight light() { return light; }

    public void light(org.domi.furnace.server.light.ChunkLight l) { this.light = l; }

    public org.domi.furnace.server.net.io.nbt.NbtCompound extraNbt() { return extraNbt; }

    public void extraNbt(org.domi.furnace.server.net.io.nbt.NbtCompound nbt) { this.extraNbt = nbt; }

    // POI(poi/ 지역 파일). poiNbt 는 디스크에서 읽은 원본(복구 전), poi 는 블록과 맞춘 뒤의 것. 복사본에는 따라가지 않는다.
    private org.domi.furnace.server.net.io.nbt.NbtCompound poiNbt;
    private org.domi.furnace.server.world.poi.PoiChunk poi;

    public org.domi.furnace.server.net.io.nbt.NbtCompound poiNbt() { return poiNbt; }

    public void poiNbt(org.domi.furnace.server.net.io.nbt.NbtCompound nbt) { this.poiNbt = nbt; }

    public org.domi.furnace.server.world.poi.PoiChunk poi() { return poi; }

    public void poi(org.domi.furnace.server.world.poi.PoiChunk p) { this.poi = p; this.poiNbt = null; }

    // 구조물 시작점·참조(청크 NBT `structures`). 생성 스레드나 불러오기에서 한 번 정하고 바꾸지 않는다.
    private volatile org.domi.furnace.server.world.gen.structure.ChunkStructures structures =
        org.domi.furnace.server.world.gen.structure.ChunkStructures.EMPTY;

    public org.domi.furnace.server.world.gen.structure.ChunkStructures structures() { return structures; }

    public void structures(org.domi.furnace.server.world.gen.structure.ChunkStructures s) {
        this.structures = s == null ? org.domi.furnace.server.world.gen.structure.ChunkStructures.EMPTY : s;
    }

    // 엔티티 저장(entities/ 지역 파일): 아직 다루지 못하는 종류의 엔티티는 원본 NBT 그대로 들고 있다가 다시 쓴다.
    private java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> foreignEntities = new java.util.ArrayList<>();
    private boolean entitiesOnDisk;
    private boolean entitiesLoaded;

    public java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> foreignEntities() { return foreignEntities; }

    /** 디스크에 이 청크의 엔티티 묶음이 있는가(비어서 쓸 필요가 없는지 — 바닐라 EntityStorage.emptyChunks 의 반대). */
    public boolean entitiesOnDisk() { return entitiesOnDisk; }

    public void entitiesOnDisk(boolean v) { entitiesOnDisk = v; }

    /** 디스크의 엔티티를 불러왔는가(불러오기 전에는 저장하지 않는다 — 덮어쓰면 잃는다). */
    public boolean entitiesLoaded() { return entitiesLoaded; }

    public void entitiesLoaded(boolean v) { entitiesLoaded = v; }

    public long inhabitedTime() { return inhabitedTime; }

    public void inhabitedTime(long t) { this.inhabitedTime = t; }

    public java.util.Map<Long, org.domi.furnace.server.block.entity.BlockEntity> blockEntities() {
        return blockEntities;
    }

    public void compactSections() {
        for (ChunkSection section : sections) section.compact();
    }

    /** worldY 절대좌표에 블록 설정. */
    public void setBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - minY;
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
        int local = worldY - minY;
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

    /** worldY 가 든 구역이 공기뿐인가(바닐라 LevelChunkSection.hasOnlyAir). */
    public boolean sectionHasOnlyAir(int worldY) { return sections[(worldY - minY) >> 4].hasOnlyAir(); }

    /** worldY 절대좌표 블록 조회. */
    public int getBlock(int x, int worldY, int z) {
        int local = worldY - minY;
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
        for (int i = 0; i < sectionCount; i++) {
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
                for (int sec = sectionCount - 1; sec >= 0 && (surfaceValue == 0 || oceanFloorValue == 0); sec--) {
                    int[] blocks = sectionBlocks(sec);
                    for (int ly = 15; ly >= 0; ly--) {
                        int stateId = blocks[ly * 256 + lz * 16 + lx];
                        int y1 = sec * 16 + ly + 1;
                        if (surfaceValue == 0 && !ChunkSection.isAir(stateId)) surfaceValue = y1;
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
                for (int y = minY + sectionCount * 16 - 1; y >= minY; y--) {
                    int idx = (y - minY) * 256 + lz * 16 + lx;
                    int stateId = blockIds[idx];
                    if (surfaceValue == 0 && !ChunkSection.isAir(stateId)) {
                        surfaceValue = (y - minY) + 1;
                    }
                    if (oceanFloorValue == 0 && isOceanFloorBlock(stateId)) {
                        oceanFloorValue = (y - minY) + 1;
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
        return minY + surface[z * 16 + x] - 1;
    }

    /** WORLD_SURFACE_WG 전용 조회 — carve 완료 이후 값이 얼어붙는다(oceanFloorBlockY의 WG류와 대응). */
    public int worldSurfaceWgBlockY(int x, int z) {
        return minY + surfaceWg[z * 16 + x] - 1;
    }

    public int oceanFloorBlockY(int x, int z) {
        int index = z * 16 + x;
        int value = oceanFloor[index];
        if (value == UNSET_HEIGHT) {
            value = recomputeOceanFloorHeight(x, z);
            oceanFloor[index] = value;
        }
        return minY + value - 1;
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
        int first = wgOceanFloor[index]; // firstAvailable - minY
        if (first == UNSET_HEIGHT) return; // loadGenerated 이전 — 미프라임 상태는 갱신 대상 아님
        int y = worldY - minY;
        if (y <= first - 2) return;
        if (isOceanFloorBlock(stateId)) {
            if (y >= first) wgOceanFloor[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                if (isOceanFloorBlock(getBlock(x, minY + i, z))) {
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
        int y = worldY - minY;
        if (y <= first - 2) return;
        boolean isAir = stateId == ChunkSection.AIR || stateId == CAVE_AIR_STATE || stateId == VOID_AIR_STATE;
        if (!isAir) {
            if (y >= first) surface[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                int below = getBlock(x, minY + i, z);
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
        int y = worldY - minY;
        if (y <= first - 2) return;
        boolean isAir = stateId == ChunkSection.AIR || stateId == CAVE_AIR_STATE || stateId == VOID_AIR_STATE;
        if (!isAir) {
            if (y >= first) surfaceWg[index] = y + 1;
        } else if (first - 1 == y) {
            for (int i = y - 1; i >= 0; i--) {
                int below = getBlock(x, minY + i, z);
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
            return minY;
        }
        return minY + value;
    }

    /**
     * vanilla MOTION_BLOCKING(blocksMotion() || fluid) 미러 — surface[](WORLD_SURFACE, !isAir)와 달리
     * 유리·레일·토치·버튼·압력판 등 blocksMotion=false 블록은 바닥으로 세지 않는다(motionblocking-predicate task).
     * oceanFloor[]와 동일한 "invalidate to UNSET_HEIGHT + lazy 전체 재스캔" 패턴 — 증분 갱신 로직 없이도
     * setBlock/setGeneratedBlock/loadGenerated 시 무효화되면 다음 조회에서 항상 실제 블록 기준으로 재계산된다.
     */
    /** 저장용 OCEAN_FLOOR(blocksMotion) 높이맵 — 호출마다 계산. */
    public long[] oceanFloorHeightmap() {
        BitStorage bs = new BitStorage(heightmapBits, 256);
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4;
            bs.set(i, oceanFloorBlockY(x, z) - minY + 1);
        }
        return bs.data();
    }

    /** 저장용 MOTION_BLOCKING_NO_LEAVES 높이맵 — 호출마다 계산. */
    public long[] motionBlockingNoLeavesHeightmap() {
        BitStorage bs = new BitStorage(heightmapBits, 256);
        for (int i = 0; i < 256; i++) bs.set(i, motionBlockingNoLeavesBlockY(i & 15, i >> 4) - minY + 1);
        return bs.data();
    }

    public long[] motionBlockingHeightmap() {
        if (cachedMotionBlockingHeightmap != null) return cachedMotionBlockingHeightmap;
        BitStorage bs = new BitStorage(heightmapBits, 256);
        for (int i = 0; i < 256; i++) bs.set(i, motionBlockingHeightValue(i & 15, i >> 4));
        cachedMotionBlockingHeightmap = bs.data();
        return cachedMotionBlockingHeightmap;
    }

    /** vanilla MOTION_BLOCKING_NO_LEAVES 최상단 블록 y (잎 제외) — 구조물 지면 높이용, 매 호출 스캔. */
    public int motionBlockingNoLeavesBlockY(int x, int z) {
        for (int y = minY + sectionCount * 16 - 1; y >= minY; y--) {
            int st = getBlock(x, y, z);
            if (isMotionBlockingBlock(st) && !isLeaves(st)) return y;
        }
        return minY - 1;
    }

    private static volatile boolean[] leavesTable;

    private static boolean isLeaves(int stateId) {
        boolean[] t = leavesTable;
        if (t == null) {
            BlockRegistry reg = fluidRegistry();
            t = new boolean[reg.totalStateCount()];
            for (int i = 0; i < t.length; i++) {
                String key = reg.canonicalKeyOf(i);
                if (key == null) continue;
                int semi = key.indexOf(';');
                t[i] = (semi < 0 ? key : key.substring(0, semi)).endsWith("_leaves");
            }
            leavesTable = t;
        }
        return stateId >= 0 && stateId < t.length && t[stateId];
    }

    public int motionBlockingBlockY(int x, int z) {
        return minY + motionBlockingHeightValue(x, z) - 1;
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
        for (int y = minY + sectionCount * 16 - 1; y >= minY; y--) {
            if (isMotionBlockingBlock(getBlock(x, y, z))) {
                return (y - minY) + 1;
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
        BitStorage bs = new BitStorage(heightmapBits, 256);
        for (int i = 0; i < 256; i++) bs.set(i, surface[i]);
        cachedWorldSurfaceHeightmap = bs.data();
        return cachedWorldSurfaceHeightmap;
    }

    private int recomputeOceanFloorHeight(int x, int z) {
        for (int y = minY + sectionCount * 16 - 1; y >= minY; y--) {
            if (isOceanFloorBlock(getBlock(x, y, z))) {
                return (y - minY) + 1;
            }
        }
        return 0;
    }

    /**
     * vanilla MOTION_BLOCKING 술어 {@code state.blocksMotion() || !state.getFluidState().isEmpty()}.
     * blocksMotion은 바닐라에서 export한 정확한 값(BlockAttrs)이고, 유체 상태는 물/용암·버블 기둥·켈프·해초·waterlogged 블록이다.
     */
    private static boolean isMotionBlockingBlock(int stateId) {
        boolean[] t = motionBlockingTable;
        if (t == null) {
            // blocksMotion || fluid 를 상태별 한 표로 합쳐 둔다(같은 술어, 조회 1회).
            boolean[] motion = blocksMotionTable();
            boolean[] fluid = hasFluidTable();
            t = new boolean[Math.max(motion.length, fluid.length)];
            for (int i = 0; i < t.length; i++) {
                t[i] = (i < motion.length && motion[i]) || (i < fluid.length && fluid[i]);
            }
            motionBlockingTable = t;
        }
        return stateId >= 0 && stateId < t.length && t[stateId];
    }

    private static volatile boolean[] motionBlockingTable;

    /**
     * vanilla OCEAN_FLOOR/OCEAN_FLOOR_WG 술어 {@code BlockBehaviour.BlockStateBase::blocksMotion}.
     * 잎·통나무는 포함이고 덩굴·묘목·꽃·카펫류·수중 식물은 제외라서 surface_water_depth_filter(WORLD_SURFACE − OCEAN_FLOOR)와
     * 나무/식생 배치 후보 판정이 바닐라와 같아진다.
     */
    private static boolean isOceanFloorBlock(int stateId) {
        boolean[] table = blocksMotionTable();
        return stateId >= 0 && stateId < table.length && table[stateId];
    }

    private static volatile boolean[] blocksMotionTable;
    private static volatile boolean[] hasFluidTable;

    private static boolean[] blocksMotionTable() {
        boolean[] t = blocksMotionTable;
        if (t == null) {
            BlockAttrs attrs = BlockAttrs.get();
            int n = fluidRegistry().totalStateCount();
            t = new boolean[n];
            for (int i = 0; i < n; i++) t[i] = attrs.blocksMotion(i);
            blocksMotionTable = t;
        }
        return t;
    }

    private static boolean[] hasFluidTable() {
        boolean[] t = hasFluidTable;
        if (t == null) {
            BlockRegistry r = fluidRegistry();
            int n = r.totalStateCount();
            t = new boolean[n];
            for (int i = 0; i < n; i++) {
                String key = r.canonicalKeyOf(i);
                if (key == null) continue;
                int semi = key.indexOf(';');
                String name = semi < 0 ? key : key.substring(0, semi);
                t[i] = switch (name) {
                    case "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:kelp",
                         "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass" -> true;
                    default -> semi >= 0 && key.indexOf("waterlogged=true", semi) >= 0;
                };
            }
            hasFluidTable = t;
        }
        return t;
    }

    // 높이맵 술어 표를 만들 때 쓰는 지연 로드 레지스트리(ChunkColumn은 생성자에 registry를 받지 않는 기존 설계라, 다른 곳들처럼
    // 명시적 주입 대신 FurnaceMain과 동일 리소스를 1회 지연 로드해 재사용한다).
    private static volatile BlockRegistry fluidRegistry;

    private static BlockRegistry fluidRegistry() {
        BlockRegistry r = fluidRegistry;
        if (r == null) {
            r = BlockRegistry.load("blocks/1.21.1.txt");
            fluidRegistry = r;
        }
        return r;
    }

}
