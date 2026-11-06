// region 하나의 블록 레벨: 그 region 구역의 로드된 청크만 다룬다. 다른 region 의 칸은 로드되지 않은 것으로 본다(합쳐지기 전 1~2틱만 생길 수 있다).
// 블록 변경 방송은 청크 구독(전달 전이면 청크 뒤로 보류)을 따르고, 블록 요청 ack 는 틱 끝 방송 뒤에 보낸다(바닐라 순서).
package org.domi.furnace.server.world;

import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;

public final class RegionLevel extends ChunkLevel {

    private final Region region;
    private final World world;
    private final int chest, trappedChest;
    /** 이번 틱에 ack 할 플레이어 → 가장 큰 sequence(바닐라 ackBlockChangesUpTo). */
    private final Map<Player, Integer> pendingAcks = new LinkedHashMap<>();
    // 같은 청크 연속 조회를 싸게(퇴출·주인 변경 때 비운다)
    // 1024칸 직접 사상 캐시 — 충돌·경로·스폰이 이웃 청크를 번갈아 읽으므로 한 칸짜리로는 해시 맵(소유 확인 + 로드 맵)을 두 번씩 타게 된다.
    // 좀비의 마을 거리(sectionsToVillage)는 13×13 청크를 훑으므로 64칸·약한 해시로는 거의 다 빗나갔다(perf-entity-2026-11-06).
    private static final int CACHE = 1024;
    private final int[] cacheX = new int[CACHE], cacheZ = new int[CACHE];
    private final ChunkColumn[] cacheCol = new ChunkColumn[CACHE];

    RegionLevel(Region region, World world) {
        super(world.levelServices().maxChainedNeighborUpdates());
        this.region = region;
        this.world = world;
        this.chest = blocks.block("minecraft:chest");
        this.trappedChest = blocks.block("minecraft:trapped_chest");
    }

    @Override
    public org.domi.furnace.server.world.raid.RaidStore raidStore() { return world.raidStore(); }

    @Override
    public boolean isOwnerThread() { return region.isOwnerThread(); }

    @Override
    protected ChunkColumn chunk(int chunkX, int chunkZ) {
        int i = (chunkX * 0x9E3779B9 + chunkZ) * 0x85EBCA6B >>> 22;   // 상위 10비트(CACHE = 1024)
        ChunkColumn hit = cacheCol[i];
        if (hit != null && cacheX[i] == chunkX && cacheZ[i] == chunkZ) return hit;
        ChunkColumn c = region.ownedChunk(chunkX, chunkZ);
        if (c != null) {
            cacheX[i] = chunkX;
            cacheZ[i] = chunkZ;
            cacheCol[i] = c;
        }
        return c;
    }

    /** Region 이 쓰는 같은 조회(캐시 경유) — 이 region 소유 로드 청크, 아니면 null. */
    ChunkColumn chunkCached(int chunkX, int chunkZ) { return chunk(chunkX, chunkZ); }

    /** 청크가 내려가거나 주인이 바뀌면 조회 캐시를 비운다. */
    void chunkUnloaded() { clearChunkCache(); }

    private void clearChunkCache() { java.util.Arrays.fill(cacheCol, null); }

    @Override
    protected void markDirty(int chunkX, int chunkZ) { region.markChunkDirty(new ChunkPos(chunkX, chunkZ)); }

    @Override
    protected void broadcastToChunk(int chunkX, int chunkZ, Packet packet) { world.broadcastBlockUpdate(new ChunkPos(chunkX, chunkZ), packet); }

    @Override
    protected void broadcastLightToChunk(int chunkX, int chunkZ, Packet packet) { world.broadcastLightUpdate(new ChunkPos(chunkX, chunkZ), packet); }

    @Override
    protected LevelServices services() { return world.levelServices(); }

    @Override
    public DimensionType dimensionType() { return world.dimensionType(); }

    @Override
    protected org.domi.furnace.server.world.gen.feature.BiomeFeatureData featureData() {
        var gen = org.domi.furnace.server.world.gen.NoiseWorldSource.of(world.worldSource());
        if (gen != null && gen.featurePlacer() != null) return gen.featurePlacer().biomeData();
        return super.featureData();
    }

    @Override
    protected long featureSeed() {
        var gen = org.domi.furnace.server.world.gen.NoiseWorldSource.of(world.worldSource());
        return gen != null ? gen.seed() : 0L;
    }

    /** 이 레벨의 월드. */
    public World world() { return world; }

    /**
     * 그 칸의 소유 region 스레드에서 실행: 지금이 이 region 스레드이고 칸의 청크를 이 region 이 가졌으면 바로, 아니면 소유 region 큐로
     * (주인이 없으면 region 을 만들지 않고 버린다). 전역 스레드의 명령 결과가 블록·엔티티 상태를 바꿀 때 쓴다.
     */
    @Override
    public void runOnOwner(Pos pos, java.util.function.Consumer<org.domi.furnace.server.block.BlockLevel> task) {
        ChunkPos cp = new ChunkPos(pos.x() >> 4, pos.z() >> 4);
        if (region.isOwnerThread() && chunk(cp.x(), cp.z()) != null) {
            task.accept(this);
            return;
        }
        world.submitAtOwnedChunk(cp, r -> {
            try {
                task.accept(r.level());
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(RegionLevel.class).error("[CMDBLOCK] 소유 스레드 작업 실패 {}", pos, e);
            }
            return null;
        });
    }

    @Override
    public void updateSleepingPlayerList() { world.updateSleepingPlayerList(); }

    @Override
    public void afterDimensionTransition(org.domi.furnace.server.entity.DimensionTransition t) {
        if (t.ticketPos() != null) world.placePortalTicket(t.ticketPos().x(), t.ticketPos().z());
    }

    @Override
    public int minBuildHeight() { return world.dimensionType().minY(); }

    @Override
    public int maxBuildHeight() { return world.dimensionType().maxY(); }

    // ------------------------------------------------------------------ 엔티티

    @Override
    public int nextEntityId() { return world.entityIds().next(); }

    @Override
    public int nextEntityIds(int count) { return world.entityIds().reserve(count); }

    @Override
    protected Iterable<Player> trackingPlayers() { return region.players().values(); }

    @Override
    protected int viewDistanceFor(Player p) { return Math.clamp(p.viewDistance(), 2, Math.max(2, world.viewDistance())); }

    @Override
    protected boolean chunkTrackedBy(Player p, int chunkX, int chunkZ) { return world.chunkReady(new ChunkPos(chunkX, chunkZ), p); }

    @Override
    protected void appendEntitiesToDisk(int cx, int cz, java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> tags) {
        world.appendEntitiesAsync(cx, cz, tags);
    }

    @Override
    protected void createBlockEntity(ChunkColumn col, Pos p, int state) {
        // 상자는 아직 자기 틀(ChestBlockEntity)로 — 나머지는 블록의 newBlockEntity
        if (blocks.blockOf(state) == chest || blocks.blockOf(state) == trappedChest) {
            if (col.chestAt(p.x(), p.y(), p.z()) == null) {
                col.putChest(ChestBlockEntity.empty(blocks.blockOf(state) == chest ? "minecraft:chest" : "minecraft:trapped_chest", p.x(), p.y(), p.z()));
            }
            return;
        }
        super.createBlockEntity(col, p, state);
    }

    @Override
    protected void removeBlockEntity(ChunkColumn col, Pos p) {
        ChestBlockEntity chest = col.chestAt(p.x(), p.y(), p.z());
        col.removeBlockEntityAt(p.x(), p.y(), p.z());
        if (chest != null) chest.setRemoved();   // 다른 region 의 플레이어가 연 메뉴도 stillValid 에서 걸러진다
        region.closeChestScreens(p.x(), p.y(), p.z());
    }

    /** 이 칸 용기를 열어 둔 플레이어 수 — 이 region 소속 플레이어 중 그 용기의 창을 가졌고 몸이 칸 상자 ± radius 안에 걸치는 사람. */
    @Override
    public int countContainerOpeners(Pos pos, double radius, Object container) {
        int n = 0;
        double x0 = pos.x() - radius, x1 = pos.x() + 1 + radius, y0 = pos.y() - radius, y1 = pos.y() + 1 + radius,
                z0 = pos.z() - radius, z1 = pos.z() + 1 + radius;
        for (var pl : region.playersView()) {
            if (!(pl.containerMenu() instanceof org.domi.furnace.server.menu.ContainerBackedMenu m) || !owns(m.container(), container)) continue;
            if (pl.x() + 0.3 > x0 && pl.x() - 0.3 < x1 && pl.y() + 1.8 > y0 && pl.y() < y1 && pl.z() + 0.3 > z0 && pl.z() - 0.3 < z1) n++;
        }
        return n;
    }

    private static boolean owns(org.domi.furnace.server.menu.Container c, Object be) {
        if (c instanceof OpenableContainer b) return b.opener() == be;
        return c instanceof org.domi.furnace.server.menu.CompoundContainer cc && (owns(cc.first(), be) || owns(cc.second(), be));
    }

    // ------------------------------------------------------------------ 합치기·나누기

    void absorbFrom(RegionLevel o) {
        absorb(o);
        for (var e : o.pendingAcks.entrySet()) pendingAcks.merge(e.getKey(), e.getValue(), Math::max);
        o.pendingAcks.clear();
        clearChunkCache();
    }

    void splitInto(RegionLevel target, ChunkFilter inTarget) {
        splitTo(target, inTarget);
        for (var it = pendingAcks.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ChunkPos c = e.getKey().chunkPos();
            if (inTarget.test(c.x(), c.z())) {
                target.pendingAcks.merge(e.getKey(), e.getValue(), Math::max);
                it.remove();
            }
        }
        clearChunkCache();
    }

    // ------------------------------------------------------------------ ack

    /** 블록 요청의 sequence 를 받아 둔다 — 이번 틱 방송 뒤에 한 번(가장 큰 값). 음수는 호출 쪽에서 거른다. */
    public void ackBlockChangesUpTo(Player p, int sequence) { pendingAcks.merge(p, sequence, Math::max); }

    boolean hasPendingAcks() { return !pendingAcks.isEmpty(); }

    void flushAcks() {
        if (pendingAcks.isEmpty()) return;
        for (var e : pendingAcks.entrySet()) e.getKey().send(new BlockChangedAck(e.getValue()));
        pendingAcks.clear();
    }

    /**
     * 레벨 틱(바닐라 ServerLevel.tick 의 블록 부분): 예약 틱 → 청크 틱(무작위 틱·날씨 — 섞은 순서로) → 변경 방송 → 블록 이벤트.
     * tickingChunks 는 무작위 틱을 받을 청크 키(Regionizer.chunkKey).
     */
    private final org.domi.furnace.server.entity.spawn.PhantomSpawner phantomSpawner = new org.domi.furnace.server.entity.spawn.PhantomSpawner();
    private final org.domi.furnace.server.entity.spawn.PatrolSpawner patrolSpawner = new org.domi.furnace.server.entity.spawn.PatrolSpawner();
    private final org.domi.furnace.server.entity.spawn.CatSpawner catSpawner = new org.domi.furnace.server.entity.spawn.CatSpawner();
    private final org.domi.furnace.server.entity.spawn.VillageSiege villageSiege = new org.domi.furnace.server.entity.spawn.VillageSiege();
    private final org.domi.furnace.server.entity.spawn.WanderingTraderSpawner traderSpawner = new org.domi.furnace.server.entity.spawn.WanderingTraderSpawner();

    /**
     * ServerLevel.tickCustomSpawners. 바닐라 MinecraftServer.createLevels 는 특수 스포너 다섯(팬텀 → 순찰 → 고양이 → 포위 → 상인,
     * 이 순서로 돈다)을 오버월드에만 주고 다른 차원에는 빈 목록을 준다 — 네더·엔드에서는 아무것도 돌지 않고 레벨 난수도 쓰지 않는다.
     */
    void tickCustomSpawners(java.util.List<Player> here, boolean spawnMonsters, boolean spawnAnimals) {
        if (!"minecraft:overworld".equals(dimension())) return;
        phantomSpawner.tick(this, here, spawnMonsters);
        patrolSpawner.tick(this, here, spawnMonsters);
        catSpawner.tick(this, here, spawnAnimals);
        villageSiege.tick(this, here, spawnMonsters);
        var traderState = world.wanderingTraderState();
        if (traderState != null) traderSpawner.attach(traderState);
        traderSpawner.tick(this, here);
    }

    /** @param spawnCount 앞쪽 몇 청크가 자연 스폰 대상(플레이어 근처)인가. 나머지(count 까지)는 /forceload 청크라 무작위 틱만 받는다. */
    void tickBlocks(long[] tickingChunks, int count, int spawnCount, java.util.function.LongPredicate blockEntityTicking,
                    java.util.function.Predicate<Player> inRegion, boolean frozen) {
        clearChunkCache();   // 틱 사이에 합치기·나누기가 있었을 수 있다
        if (!frozen) {   // /tick freeze: 예약 틱·습격·청크 틱·스폰은 멈춘다(count 도 0 으로 온다)
            runScheduledTicks();
            raids().tick();   // 바닐라 ServerLevel.tick: 예약 틱 → 습격 → 청크(스폰·무작위 틱)
        }
        if (count > 0) {
            shuffle(tickingChunks, spawnCount);
            int speed = gameRuleInt("randomTickSpeed");
            // ServerChunkCache.tickChunks: 몹 수를 세고, 청크마다 자연 스폰(doMobSpawning)한 뒤 무작위 틱
            boolean spawnMonsters = spawnMonstersAllowed() && difficulty() != 0, spawnAnimals = spawnAnimalsAllowed();
            org.domi.furnace.server.entity.spawn.NaturalSpawner.SpawnState spawnState = null;
            if (spawnCount > 0 && gameRule("doMobSpawning") && (spawnMonsters || spawnAnimals)) {
                spawnState = org.domi.furnace.server.entity.spawn.NaturalSpawner.createState(spawnCount, entities().tickSnapshot(), this);
            }
            boolean spawnPassives = gameTime() % 400L == 0L;
            for (int i = 0; i < count; i++) {
                long k = tickingChunks[i];
                int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                if (spawnState != null && i < spawnCount && withinBorder(new org.domi.furnace.server.block.Pos(cx * 16 + 8, 64, cz * 16 + 8))) {
                    org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnForChunk(this, cx, cz, spawnState, spawnAnimals, spawnMonsters, spawnPassives);
                }
                tickChunk(cx, cz, speed);
            }
            // ServerLevel.tickCustomSpawners: 청크 스폰·틱 뒤 팬텀 등 특수 스포너(doMobSpawning 일 때)
            if (spawnCount > 0 && gameRule("doMobSpawning")) {
                java.util.List<Player> here = new java.util.ArrayList<>();
                services().forEachPlayer(pl -> { if (inRegion.test(pl)) here.add(pl); });
                tickCustomSpawners(here, spawnMonsters, spawnAnimals);
            }
        }
        broadcastChanges();
        // 엔티티 틱 청크: 이 region 이 가진 로드된 청크 중 블록 엔티티 틱을 받는 것(바닐라 inEntityTickingRange)
        java.util.function.LongPredicate entityTicking = k -> chunk((int) (k >> 32), (int) k) != null && blockEntityTicking.test(k);
        trackEntities(entityTicking, inRegion);   // 바닐라 ChunkMap.tick — 청크 틱 끝 방송 뒤
        runBlockEvents();
        runEntityTasks();   // 플러그인 EntityScheduler — /tick freeze 중에도 돈다(플러그인 논리는 게임 틱 정지와 무관)
        if (frozen) return;   // 정지 중에는 엔티티·블록 엔티티·드래곤 전투를 틱하지 않는다
        world.tickDragonFight(region, this);   // 바닐라 ServerLevel.tick: 엔티티 틱 바로 앞
        tickEntities(entityTicking);
        tickBlockEntities(blockEntityTicking);
    }
}
