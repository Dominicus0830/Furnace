package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public final class FeatureWorldView implements org.domi.furnace.server.world.block.ShapeLevel {
    private final ChunkColumn col;
    private final ChunkColumn[] gridCols;
    private final int cx;
    private final int cz;
    private final int[] biomeIds;
    private final int[][] biomeIdsGrid;
    private final boolean writeCenterOnly;
    private final long worldSeed;
    /** obfuscateSeed(worldSeed) — SHA-256 이라 view 마다 한 번만 계산한다. */
    private final long biomeZoomSeed;
    /** 차원 높이(중심 청크 열 기준) — 오버월드 -64..319, 네더·엔드 0..255. */
    private final int minY, maxY;
    // 조회(read) 범위 — oracle의 OracleWorldGenLevel.getChunk와 동일하게, 쓰기 범위(origin ±1)보다 넓게
    // 파이프라인 grid center 기준 ±gridReadRadius까지 볼 수 있어야 한다(fancy oak makeLimb의 isFree 판정 등).
    private final int gridCenterX;
    private final int gridCenterZ;
    private final int gridReadRadius;
    /** 쓰기 범위(origin ±writeRadius 청크). 월드젠은 oracle 과 같은 1 고정, /place 의 라이브 격자만 넓힌다. */
    private int writeRadius = 1;
    private boolean live;
    private int missingAccess;
    private int debugSetBlockCalls;
    /** LevelHeightAccessor.getMinBuildHeight(). */
    public int minBuildHeight() { return minY; }
    /** LevelHeightAccessor.getMaxBuildHeight() (배타적 상한). */
    public int maxBuildHeight() { return maxY + 1; }

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
        this.biomeZoomSeed = obfuscateSeed(worldSeed);
        this.minY = col != null ? col.minY() : ChunkColumn.MIN_Y;
        this.maxY = col != null ? col.maxY() : ChunkColumn.MAX_Y;
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
        this.biomeZoomSeed = obfuscateSeed(worldSeed);
        ChunkColumn ref = null;
        for (ChunkColumn c : grid) if (c != null) { ref = c; break; }
        this.minY = ref != null ? ref.minY() : ChunkColumn.MIN_Y;
        this.maxY = ref != null ? ref.maxY() : ChunkColumn.MAX_Y;
        this.gridCenterX = gridCenterX;
        this.gridCenterZ = gridCenterZ;
        this.gridReadRadius = gridReadRadius;
    }

    /**
     * 명령(/place)용: 실제 월드 청크 복사본 격자(center ±radius) 전체를 읽고 쓰는 view. 없는 칸(null)은 읽으면 공기, 쓰면 무시된다.
     * 월드젠 경로는 쓰지 않는다(쓰기 범위가 oracle 과 달라진다).
     */
    public static FeatureWorldView liveGrid(ChunkColumn[] grid, int[][] biomeIdsGrid, int centerX, int centerZ, int radius, long worldSeed) {
        FeatureWorldView v = new FeatureWorldView(grid, biomeIdsGrid, centerX, centerZ, centerX, centerZ, radius, false, worldSeed);
        v.writeRadius = radius;
        v.live = true;
        return v;
    }

    public int getBlock(int worldX, int worldY, int worldZ) {
        if (gridCols != null) return getGridBlock(worldX, worldY, worldZ);
        if (!inCenterChunk(worldX, worldZ) || !inWorldY(worldY)) return ChunkSection.AIR;
        return col.getBlock(worldX - cx * 16, worldY, worldZ - cz * 16);
    }

    // 디버그: -Dfurnace.watch=x,y,z 를 주면 해당 칸에 쓰는 setBlock을 RandomTrace로 남긴다(호출 스택 1줄 포함).
    /** 디버그: 마지막으로 배치를 시작한 placed feature의 origin(watch 출력용). */
    static volatile String debugOrigin = "";
    private static final int[] WATCH = parseWatch(System.getProperty("furnace.watch"));
    static final boolean WATCHING = WATCH != null;

    private static int[] parseWatch(String v) {
        if (v == null) return null;
        String[] p = v.split(",");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
    }

    /** vanilla WorldGenLevel.setBlock의 성공 여부 — 쓰기 범위·월드 높이 안이면 true. */
    public boolean trySetBlock(int worldX, int worldY, int worldZ, int stateId) {
        if (!inWorldY(worldY)) return false;
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (writeCenterOnly && (nx != cx || nz != cz)) return false;
            if (!inWriteRange(nx, nz) || cell(nx, nz) == null) return false;
        } else if (!inCenterChunk(worldX, worldZ)) {
            return false;
        }
        setBlock(worldX, worldY, worldZ, stateId);
        return true;
    }

    /** 생성 중 블록 엔티티 NBT(상자 loot table 등)를 해당 칸의 청크에 남긴다. */
    public void putGeneratedBlockEntity(int worldX, int worldY, int worldZ, org.domi.furnace.server.net.io.nbt.NbtCompound nbt) {
        ChunkColumn target;
        int lx, lz;
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (!inWriteRange(nx, nz) || cell(nx, nz) == null) return;
            target = cell(nx, nz);
            lx = worldX - nx * 16;
            lz = worldZ - nz * 16;
        } else {
            if (!inCenterChunk(worldX, worldZ)) return;
            target = col;
            lx = worldX - cx * 16;
            lz = worldZ - cz * 16;
        }
        target.putGeneratedBlockEntity(lx, worldY, lz, nbt);
    }

    /** 엔티티 스폰 요청(구조물 마커 등) — 위치가 속한 청크에 NBT 로 남긴다. 월드 좌표 정확 위치. */
    public org.domi.furnace.server.net.io.nbt.NbtCompound addEntity(String id, double x, double y, double z, float yaw, float pitch) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        ChunkColumn target;
        if (gridCols != null) {
            int nx = Math.floorDiv(bx, 16);
            int nz = Math.floorDiv(bz, 16);
            if (!inWriteRange(nx, nz) || cell(nx, nz) == null) return new org.domi.furnace.server.net.io.nbt.NbtCompound();
            target = cell(nx, nz);
        } else {
            if (!inCenterChunk(bx, bz)) return new org.domi.furnace.server.net.io.nbt.NbtCompound();
            target = col;
        }
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        nbt.putString("id", id);
        org.domi.furnace.server.net.io.nbt.NbtList pos = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 6);
        pos.add(x); pos.add(y); pos.add(z);
        nbt.put("Pos", pos);
        org.domi.furnace.server.net.io.nbt.NbtList rot = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 5);
        rot.add(yaw); rot.add(pitch);
        nbt.put("Rotation", rot);
        target.addGeneratedEntity(nbt);
        return nbt;
    }

    /** 완성된 엔티티 NBT(Pos 포함)를 그 위치의 청크에 남긴다(구조물 템플릿 엔티티). */
    public void addEntityNbt(org.domi.furnace.server.net.io.nbt.NbtCompound nbt, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int nx = Math.floorDiv(bx, 16), nz = Math.floorDiv(bz, 16);
        if (gridCols != null) {
            if (!inWriteRange(nx, nz) || cell(nx, nz) == null) return;
            cell(nx, nz).addGeneratedEntity(nbt);
        } else if (inCenterChunk(bx, bz)) {
            col.addGeneratedEntity(nbt);
        }
    }

    /** 바닐라 level.getChunk(pos).markPosForPostprocessing(pos). */
    public void markPosForPostprocessing(int worldX, int worldY, int worldZ) {
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (!inWriteRange(nx, nz) || cell(nx, nz) == null) return;
            cell(nx, nz).markPosForPostprocessing(worldX - nx * 16, worldY, worldZ - nz * 16);
            return;
        }
        if (!inCenterChunk(worldX, worldZ)) return;
        col.markPosForPostprocessing(worldX - cx * 16, worldY, worldZ - cz * 16);
    }

    /** putGeneratedBlockEntity로 남겨 둔 NBT 조회(없으면 null). */
    public org.domi.furnace.server.net.io.nbt.NbtCompound generatedBlockEntity(int worldX, int worldY, int worldZ) {
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (!inReadRange(nx, nz) || cell(nx, nz) == null) return null;
            return cell(nx, nz).generatedBlockEntity(worldX - nx * 16, worldY, worldZ - nz * 16);
        }
        if (!inCenterChunk(worldX, worldZ)) return null;
        return col.generatedBlockEntity(worldX - cx * 16, worldY, worldZ - cz * 16);
    }

    String debugCenter() { return cx + "," + cz; }

    public void setBlock(int worldX, int worldY, int worldZ, int stateId) {
        if (WATCH != null && WATCH[0] == worldX && WATCH[1] == worldY && WATCH[2] == worldZ) {
            StackTraceElement[] st = new Throwable().getStackTrace();
            System.out.println("FURNACE_WATCH set " + worldX + " " + worldY + " " + worldZ + " state=" + stateId + " viewCenter=" + cx + "," + cz
                + " origin=" + debugOrigin + " by " + (st.length > 1 ? st[1] : "?") + " <- " + (st.length > 2 ? st[2] : "?") + " <- " + (st.length > 3 ? st[3] : "?"));
        }
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
        if (!inCenterChunk(worldX, worldZ)) return minY - 1;
        return col.surfaceBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    /**
     * vanilla LevelReader.getRawBrightness(pos,0) &lt; 13 근사 — 정밀 광량 계산 대신, carve 완료 시점에
     * 얼어붙는 WORLD_SURFACE_WG(getWorldSurfaceWgY)보다 위면 완전히 열린 하늘로 보고 skylight=15로 취급한다.
     * (라이브 MOTION_BLOCKING 대신 얼어붙은 WG를 쓰는 이유: 같은 decoration 패스에서 먼저 놓인 mushroom
     * 자신이 "지면"으로 잡혀 다음 시도의 판정을 오염시키는 자기간섭을 피하기 위함 — mushroom은 vanilla에서
     * 빛을 막지 않는 블록이라 원래 광량 계산에 영향을 주면 안 된다.)
     * MushroomBlock.canSurvive(하늘이 열린 곳은 mushroom_grow_block 지면이 아니면 생존 불가) 판정에만 쓰인다.
     */
    public boolean isOpenToSky(int worldX, int worldY, int worldZ) {
        return getWorldSurfaceWgY(worldX, worldZ) < worldY;
    }

    /** WORLD_SURFACE_WG 전용 — carve 완료 이후 값이 얼어붙는다(getSurfaceY의 MOTION_BLOCKING류와 대응). */
    public int getWorldSurfaceWgY(int worldX, int worldZ) {
        if (gridCols != null) return getGridWorldSurfaceWgY(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return minY - 1;
        return col.worldSurfaceWgBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    /** MOTION_BLOCKING(blocksMotion || 유체) 라이브 최상단 블록 y. 없으면 MIN_Y-1. */
    public int getMotionBlockingY(int worldX, int worldZ) {
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (!inReadRange(nx, nz)) return minY - 1;
            ChunkColumn neighbor = cell(nx, nz);
            if (neighbor == null) return minY - 1;
            return neighbor.motionBlockingBlockY(worldX - nx * 16, worldZ - nz * 16);
        }
        if (!inCenterChunk(worldX, worldZ)) return minY - 1;
        return col.motionBlockingBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    /** MOTION_BLOCKING_NO_LEAVES 라이브 최상단 블록 y. 없으면 MIN_Y-1. */
    public int getMotionBlockingNoLeavesY(int worldX, int worldZ) {
        if (gridCols != null) {
            int nx = Math.floorDiv(worldX, 16);
            int nz = Math.floorDiv(worldZ, 16);
            if (!inReadRange(nx, nz) || cell(nx, nz) == null) return minY - 1;
            return cell(nx, nz).motionBlockingNoLeavesBlockY(worldX - nx * 16, worldZ - nz * 16);
        }
        if (!inCenterChunk(worldX, worldZ)) return minY - 1;
        return col.motionBlockingNoLeavesBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getOceanFloorY(int worldX, int worldZ) {
        if (gridCols != null) return getGridOceanFloorY(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return minY - 1;
        return col.oceanFloorBlockY(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getWgOceanFloorHeight(int worldX, int worldZ) {
        if (gridCols != null) return getGridWgOceanFloorHeight(worldX, worldZ);
        if (!inCenterChunk(worldX, worldZ)) return minY;
        return col.wgOceanFloorHeight(worldX - cx * 16, worldZ - cz * 16);
    }

    public int getBiomeId(int worldX, int worldY, int worldZ) {
        if (gridCols != null) return getGridBiomeId(worldX, worldY, worldZ);
        int qx = (worldX - cx * 16) >> 2;
        int qz = (worldZ - cz * 16) >> 2;
        // vanilla ChunkAccess.getNoiseBiome — 월드 높이 밖 quart Y는 [min,max]로 clamp해 최상/최하단
        // quart의 biome을 돌려준다(0 반환이 아님). ore_emerald(트라페조이드 상한 y=480)처럼 월드 밖 후보의
        // biome 게이트 판정이 vanilla와 일치해야 한다(mega-tree-placers task에서 수정).
        int qy = clampQuartY(Math.floorDiv(worldY - minY, 4));
        if (qx < 0 || qx >= 4 || qz < 0 || qz >= 4) {
            return 0;
        }
        return biomeIds[(qy * 4 + qz) * 4 + qx];
    }

    private int clampQuartY(int qy) {
        if (qy < 0) return 0;
        return Math.min(qy, ((maxY - minY + 1) / 4 - 1));
    }

    /**
     * vanilla BiomeManager.getBiome(pos) 미러 — 단순 quart 조회(getBiomeId)가 아니라, 8개 인접 quart를
     * 결정론적 "biome zoom" 지터(fiddledDistance, SurfaceSystem.biomeManagerQuart와 동일 알고리즘)로 블렌딩한
     * 결과다. 실제 vanilla의 BiomeFilter(placement modifier "biome")·FreezeTopLayer 등 게임플레이 biome
     * 질의는 전부 이 블렌딩된 값을 쓴다 — 청크에 저장되는 raw quart biome과는 다른 값일 수 있다(biome 경계
     * 근처에서 특히 그렇다). tree-overlap-residual task round4에서 확정: origin(4,4)의 birch_tall이
     * old_growth_birch_forest/forest 경계 근처 후보에서 raw quart(old_growth_birch_forest, birch_tall
     * 서식지)와 블렌딩 결과(forest, 서식지 아님)가 달라 vanilla가 스킵하는데 Furnace(raw 조회)는 통과시켜
     * RNG 소비 순서가 어긋났었다.
     */
    public int getBlendedBiomeId(int worldX, int worldY, int worldZ) {
        int x = worldX - 2;
        int y = worldY - 2;
        int z = worldZ - 2;
        int qx = x >> 2;
        int qy = y >> 2;
        int qz = z >> 2;
        double fx = (x & 3) / 4.0;
        double fy = (y & 3) / 4.0;
        double fz = (z & 3) / 4.0;
        int bestQx = qx;
        int bestQy = qy;
        int bestQz = qz;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean useX0 = (i & 4) == 0;
            boolean useY0 = (i & 2) == 0;
            boolean useZ0 = (i & 1) == 0;
            int cqx = useX0 ? qx : qx + 1;
            int cqy = useY0 ? qy : qy + 1;
            int cqz = useZ0 ? qz : qz + 1;
            double dx = useX0 ? fx : fx - 1.0;
            double dy = useY0 ? fy : fy - 1.0;
            double dz = useZ0 ? fz : fz - 1.0;
            double distance = fiddledDistance(biomeZoomSeed, cqx, cqy, cqz, dx, dy, dz);
            if (bestDistance > distance) {
                bestDistance = distance;
                bestQx = cqx;
                bestQy = cqy;
                bestQz = cqz;
            }
        }
        // bestQy는 절대 quart 좌표(world y >> 2)이므로 world block y = bestQy*4. 이전의 "+ MIN_Y"는
        // 64블록 아래의 quart를 읽는 오프셋 버그 — 지표 위 조회는 대개 같은 biome 기둥이라 티가 안 났지만,
        // 아래에 동굴 biome(deep_dark·dripstone_caves 등)이 있는 지역에서는 biome 게이트가 어긋나
        // ore_infested/ore_emerald 등의 후보가 잘못 기각됐다(mega-tree-placers task에서 수정).
        int rx = bestQx * 4;
        int rz = bestQz * 4;
        int ry = bestQy * 4;
        return getBiomeId(rx, ry, rz);
    }

    private static double fiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        long mixed = lcgNext(seed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        mixed = lcgNext(mixed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        double xFiddle = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double yFiddle = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double zFiddle = fiddle(mixed);
        return (dz + zFiddle) * (dz + zFiddle) + (dy + yFiddle) * (dy + yFiddle) + (dx + xFiddle) * (dx + xFiddle);
    }

    private static long lcgNext(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
    }

    private static double fiddle(long seed) {
        double d = Math.floorMod(seed >> 24, 1024) / 1024.0;
        return (d - 0.5) * 0.9;
    }

    // SurfaceSystem.obfuscateSeed와 동일 알고리즘(리틀엔디안 바이트 순서) — vanilla
    // Hashing.sha256().hashLong(seed).asLong()의 실제 바이트 순서와 일치시킨 것을 그대로 재사용한다.
    private static long obfuscateSeed(long seed) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] input = new byte[Long.BYTES];
            for (int i = 0; i < Long.BYTES; i++) {
                input[i] = (byte) (seed >>> (i * 8));
            }
            byte[] hash = digest.digest(input);
            long result = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                result |= (hash[i] & 0xFFL) << (i * 8);
            }
            return result;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 차원 최저 블록 y(WorldGenLevel.getMinBuildHeight). */
    public int minY() { return minY; }

    /** 차원 최고 블록 y(포함, getMaxBuildHeight-1). */
    public int maxY() { return maxY; }

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
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return ChunkSection.AIR;
        return neighbor.getBlock(worldX - nx * 16, worldY, worldZ - nz * 16);
    }

    private void setGridBlock(int worldX, int worldY, int worldZ, int stateId) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (writeCenterOnly && (nx != cx || nz != cz)) return;
        if (!inWriteRange(nx, nz) || !inWorldY(worldY)) return;
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return;
        debugSetBlockCalls++;
        neighbor.setBlock(worldX - nx * 16, worldY, worldZ - nz * 16, stateId);
        if (writeLog != null) writeLog.add(new long[]{worldX, worldY, worldZ});
    }

    private int getGridSurfaceY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return minY - 1;
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return minY - 1;
        return neighbor.surfaceBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridWorldSurfaceWgY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return minY - 1;
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return minY - 1;
        return neighbor.worldSurfaceWgBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridOceanFloorY(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return minY - 1;
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return minY - 1;
        return neighbor.oceanFloorBlockY(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridWgOceanFloorHeight(int worldX, int worldZ) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return minY;
        ChunkColumn neighbor = cell(nx, nz);
        if (neighbor == null) return minY;
        return neighbor.wgOceanFloorHeight(worldX - nx * 16, worldZ - nz * 16);
    }

    private int getGridBiomeId(int worldX, int worldY, int worldZ) {
        int qy = clampQuartY(Math.floorDiv(worldY - minY, 4));
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (!inReadRange(nx, nz)) return 0;
        if (lazyFill != null && gridCols[gridIndex(nx, nz)] == null) lazyFill.accept(gridIndex(nx, nz));
        int[] neighborBiomeIds = biomeIdsGrid[gridIndex(nx, nz)];
        if (neighborBiomeIds == null) return 0;
        int qx = (worldX - nx * 16) >> 2;
        int qz = (worldZ - nz * 16) >> 2;
        return neighborBiomeIds[(qy * 4 + qz) * 4 + qx];
    }

    // 읽기(makeLimb의 isFree 등) — 파이프라인 grid center 기준 ±gridReadRadius. oracle의
    // OracleWorldGenLevel.getChunk(radius=grid 전체)와 동일한 범위여야 fancy oak 분기(dry-run) 판정이 일치한다.
    private boolean inReadRange(int chunkX, int chunkZ) {
        boolean in = Math.abs(chunkX - gridCenterX) <= gridReadRadius && Math.abs(chunkZ - gridCenterZ) <= gridReadRadius;
        if (!in && live) missingAccess++;
        return in;
    }

    // 쓰기 — oracle의 OracleWorldGenLevel.setBlock(writeRadius=1 고정)과 동일하게 origin(cx,cz) ±1로 제한.
    private boolean inWriteRange(int chunkX, int chunkZ) {
        boolean in = Math.abs(chunkX - cx) <= writeRadius && Math.abs(chunkZ - cz) <= writeRadius;
        if (!in && live) missingAccess++;
        return in;
    }

    /** 격자 칸의 청크(없으면 null). /place 라이브 격자에서는 없는 칸에 닿은 횟수를 센다. */
    private ChunkColumn cell(int chunkX, int chunkZ) {
        int idx = gridIndex(chunkX, chunkZ);
        if (lazyFill != null && gridCols[idx] == null) lazyFill.accept(idx);
        ChunkColumn c = gridCols[idx];
        if (c == null && live) missingAccess++;
        return c;
    }

    // 블록 쪽(묘목·뼛가루) 라이브 격자: 칸 청크를 처음 닿을 때 복사하고(idx → gridCols/biomeIdsGrid 채움), 쓴 칸을 순서대로 남긴다.
    private java.util.function.IntConsumer lazyFill;
    private java.util.List<long[]> writeLog;

    /** 블록 레벨용 라이브 격자: 중심 칸만 미리 채우고 나머지는 lazyFill 이 처음 닿을 때 채운다. 쓴 칸은 writeLog 에 쌓인다. */
    public static FeatureWorldView lazyLiveGrid(ChunkColumn[] grid, int[][] biomeIdsGrid, int centerX, int centerZ, int radius, long worldSeed,
            java.util.function.IntConsumer lazyFill, java.util.List<long[]> writeLog) {
        FeatureWorldView v = liveGrid(grid, biomeIdsGrid, centerX, centerZ, radius, worldSeed);
        v.lazyFill = lazyFill;
        v.writeLog = writeLog;
        return v;
    }

    /** /place 라이브 격자: 범위 밖·로드 안 된 청크를 읽거나 쓴 횟수(0 이 아니면 결과를 믿을 수 없다). */
    public int missingAccess() { return missingAccess; }

    private int gridIndex(int chunkX, int chunkZ) {
        int size = 2 * gridReadRadius + 1;
        return (chunkZ - gridCenterZ + gridReadRadius) * size + (chunkX - gridCenterX + gridReadRadius);
    }

    private boolean inWorldY(int worldY) {
        return worldY >= minY && worldY < (maxY + 1);
    }
}
