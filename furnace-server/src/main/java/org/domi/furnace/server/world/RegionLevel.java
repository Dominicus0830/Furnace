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
    // 64칸 직접 사상 캐시 — 충돌·경로·스폰이 이웃 청크를 번갈아 읽으므로 한 칸짜리로는 해시 맵(소유 확인 + 로드 맵)을 두 번씩 타게 된다.
    private static final int CACHE = 64;
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
    protected ChunkColumn chunk(int chunkX, int chunkZ) {
        int i = (chunkX * 31 ^ chunkZ) & (CACHE - 1);
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

    /** 청크가 내려가거나 주인이 바뀌면 조회 캐시를 비운다. */
    void chunkUnloaded() { clearChunkCache(); }

    private void clearChunkCache() { java.util.Arrays.fill(cacheCol, null); }

    @Override
    protected void markDirty(int chunkX, int chunkZ) { region.markChunkDirty(new ChunkPos(chunkX, chunkZ)); }

    @Override
    protected void broadcastToChunk(int chunkX, int chunkZ, Packet packet) { world.broadcastBlockUpdate(new ChunkPos(chunkX, chunkZ), packet); }

    @Override
    protected LevelServices services() { return world.levelServices(); }

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
    private final org.domi.furnace.server.entity.spawn.WanderingTraderSpawner traderSpawner = new org.domi.furnace.server.entity.spawn.WanderingTraderSpawner();

    void tickBlocks(long[] tickingChunks, int count, java.util.function.LongPredicate blockEntityTicking,
                    java.util.function.Predicate<Player> inRegion) {
        clearChunkCache();   // 틱 사이에 합치기·나누기가 있었을 수 있다
        runScheduledTicks();
        raids().tick();   // 바닐라 ServerLevel.tick: 예약 틱 → 습격 → 청크(스폰·무작위 틱)
        if (count > 0) {
            shuffle(tickingChunks, count);
            int speed = gameRuleInt("randomTickSpeed");
            // ServerChunkCache.tickChunks: 몹 수를 세고, 청크마다 자연 스폰(doMobSpawning)한 뒤 무작위 틱
            boolean spawnMonsters = spawnMonstersAllowed() && difficulty() != 0, spawnAnimals = spawnAnimalsAllowed();
            org.domi.furnace.server.entity.spawn.NaturalSpawner.SpawnState spawnState = null;
            if (gameRule("doMobSpawning") && (spawnMonsters || spawnAnimals)) {
                spawnState = org.domi.furnace.server.entity.spawn.NaturalSpawner.createState(count, entities().tickSnapshot(), this);
            }
            boolean spawnPassives = gameTime() % 400L == 0L;
            for (int i = 0; i < count; i++) {
                long k = tickingChunks[i];
                int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                if (spawnState != null && withinBorder(new org.domi.furnace.server.block.Pos(cx * 16 + 8, 64, cz * 16 + 8))) {
                    org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnForChunk(this, cx, cz, spawnState, spawnAnimals, spawnMonsters, spawnPassives);
                }
                tickChunk(cx, cz, speed);
            }
            // ServerLevel.tickCustomSpawners: 청크 스폰·틱 뒤 팬텀 등 특수 스포너(doMobSpawning 일 때)
            if (gameRule("doMobSpawning")) {
                java.util.List<Player> here = new java.util.ArrayList<>();
                services().forEachPlayer(pl -> { if (inRegion.test(pl)) here.add(pl); });
                phantomSpawner.tick(this, here, spawnMonsters);
                patrolSpawner.tick(this, here, spawnMonsters);   // 바닐라 customSpawners 순서: 팬텀 → 순찰 → (고양이·포위) → 상인
                traderSpawner.tick(this, here);
            }
        }
        broadcastChanges();
        // 엔티티 틱 청크: 이 region 이 가진 로드된 청크 중 블록 엔티티 틱을 받는 것(바닐라 inEntityTickingRange)
        java.util.function.LongPredicate entityTicking = k -> chunk((int) (k >> 32), (int) k) != null && blockEntityTicking.test(k);
        trackEntities(entityTicking, inRegion);   // 바닐라 ChunkMap.tick — 청크 틱 끝 방송 뒤
        runBlockEvents();
        tickEntities(entityTicking);
        tickBlockEntities(blockEntityTicking);
    }
}
