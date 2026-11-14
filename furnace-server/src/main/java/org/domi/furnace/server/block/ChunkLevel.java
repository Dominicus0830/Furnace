// BlockLevel 구현의 뼈대 — 바닐라 Level.setBlock / LevelChunk.setBlockState / 이웃 갱신 / 신호 / 소리·이벤트 방송을 청크 열 위에서.
// 청크를 어디서 얻는지(소유 region 의 로드된 청크)와 변경 방송·저장 표시는 하위 클래스가 정한다.
package org.domi.furnace.server.block;

import java.util.TreeMap;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityStore;
import org.domi.furnace.server.entity.EntityTracker;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.BlockEventPacket;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.LevelEventPacket;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public abstract class ChunkLevel implements BlockLevel {

    protected final Blocks blocks = Blocks.get();
    protected final BlockInfo info = BlockInfo.get();
    private final NeighborUpdater neighborUpdater;
    private final RandomSource random;
    private final RandomSource soundRandom;
    private final BlockTicks blockTicks = new BlockTicks();
    private final BlockTicks fluidTicks = new BlockTicks();
    private long subTickCount;
    /** Level.randValue — 무작위 칸 고르기 LCG(getBlockRandomPos). 바닐라처럼 처음 값은 임의. */
    private int randValue;
    /** 지역 틱 시계(예약 틱 기준 — Folia 의 redstoneTime 처럼 region 마다 따로 간다. 월드 시간은 gameTime()). */
    private long levelTime;
    /** 레드스톤 횃불 과열 기록(바닐라 정적 RECENT_TOGGLES 대신 레벨마다 — region 스레드끼리 겹치지 않고 합치기·나누기 때 옮긴다). */
    private final RedstoneTorchToggles torchToggles = new RedstoneTorchToggles();
    /** ServerLevel.handlingTick — 예약 틱을 돌리는 동안 참. */
    private boolean handlingTick;
    /** 방송할 변경: 구역 키(청크 x,z,구역 y) → 구역 안 칸 번호 집합(바닐라 ChunkHolder.changedBlocksPerSection). */
    private final TreeMap<Long, SectionChanges> changed = new TreeMap<>();

    /**
     * 한 구역의 바뀐 칸 번호 집합. 번호 오름차순으로 돈다(예전 TreeSet<Short> 과 같은 순서, 칸마다 상자를 만들지 않는다).
     * 한 칸뿐인 흔한 경우(무작위 틱·레드스톤)는 필드 하나, 둘째 칸부터 4096비트 배열.
     */
    static final class SectionChanges {
        private long[] bits;
        private int single = -1;
        int count;

        void add(int local) {
            if (bits == null) {
                if (count == 0) {
                    single = local;
                    count = 1;
                    return;
                }
                if (single == local) return;
                bits = new long[64];
                bits[single >>> 6] |= 1L << single;
            }
            long m = 1L << local;
            int w = local >>> 6;
            if ((bits[w] & m) == 0) {
                bits[w] |= m;
                count++;
            }
        }

        void addAll(SectionChanges o) {
            if (o.bits == null) {
                if (o.count > 0) add(o.single);
                return;
            }
            for (short l : o.toArray()) add(l);
        }

        /** 오름차순 칸 번호. */
        short[] toArray() {
            if (bits == null) return count == 0 ? new short[0] : new short[] {(short) single};
            short[] out = new short[count];
            int k = 0;
            for (int w = 0; w < 64; w++) {
                long b = bits[w];
                while (b != 0) {
                    out[k++] = (short) (w << 6 | Long.numberOfTrailingZeros(b));
                    b &= b - 1;
                }
            }
            return out;
        }
    }
    /** 바닐라 ObjectLinkedOpenHashSet<BlockEventData>: 넣은 순서대로, 같은 이벤트가 큐에 있으면 다시 넣지 않는다. */
    private final java.util.LinkedHashSet<BlockEventData> blockEvents = new java.util.LinkedHashSet<>();

    private record BlockEventData(int x, int y, int z, int block, int type, int data) {}

    protected ChunkLevel(int maxChainedNeighborUpdates) { this(maxChainedNeighborUpdates, 0L); }

    /** ownerKey: 이 레벨의 난수 주인 열쇠(RandomSeeds.world). 시험 JVM 에서 같은 열쇠면 같은 난수열을 얻는다. */
    protected ChunkLevel(int maxChainedNeighborUpdates, long ownerKey) {
        this.random = RandomSource.createFor(ownerKey, RandomSeeds.Purpose.LEVEL);
        this.soundRandom = RandomSource.createFor(ownerKey, RandomSeeds.Purpose.LEVEL_SOUND);
        this.randValue = RandomSource.createFor(ownerKey, RandomSeeds.Purpose.LEVEL_RAND_VALUE).nextInt();
        this.neighborUpdater = new NeighborUpdater(this, maxChainedNeighborUpdates);
    }

    // ------------------------------------------------------------------ POI(바닐라 ServerLevel.getPoiManager)

    /** POI 는 청크(ChunkColumn.poi)에 붙어 다닌다. 아직 맞추지 않은 청크(동기 로드 경로)는 처음 볼 때 블록에서 복구한다. */
    private final org.domi.furnace.server.world.poi.PoiManager poiManager = new org.domi.furnace.server.world.poi.PoiManager((cx, cz) -> {
        ChunkColumn col = chunk(cx, cz);
        if (col == null) return null;
        var p = col.poi();
        if (p == null) col.poi(p = org.domi.furnace.server.world.poi.PoiManager.restore(col, cx, cz, col.poiNbt()));
        return p;
    });

    public org.domi.furnace.server.world.poi.PoiManager poiManager() { return poiManager; }

    private final org.domi.furnace.server.world.raid.Raids raids = new org.domi.furnace.server.world.raid.Raids(this);

    private org.domi.furnace.server.world.raid.RaidStore detachedRaidStore;

    /** 습격 번호·저장소(region 레벨은 월드 것). 단독 레벨은 파일 없는 저장소. */
    public org.domi.furnace.server.world.raid.RaidStore raidStore() {
        if (detachedRaidStore == null) detachedRaidStore = org.domi.furnace.server.world.raid.RaidStore.detached();
        return detachedRaidStore;
    }

    /** ServerLevel.getRaids. */
    public org.domi.furnace.server.world.raid.Raids raids() { return raids; }

    /** ServerLevel.isCloseToVillage(pos, i). */
    public boolean isCloseToVillage(Pos pos, int i) { return poiManager.isCloseToVillage(pos, i); }

    /** ServerLevel.isVillage(pos). */
    public boolean isVillage(Pos pos) { return poiManager.isVillage(pos); }

    // ------------------------------------------------------------------ 하위 클래스가 정할 것

    /** 이 레벨이 다룰 수 있는(로드돼 있고 소유한) 청크. 아니면 null. */
    protected abstract ChunkColumn chunk(int chunkX, int chunkZ);

    /** 청크가 바뀌었음(저장 대상). */
    protected abstract void markDirty(int chunkX, int chunkZ);

    /** 방송: 그 청크를 받은 플레이어들에게. */
    protected abstract void broadcastToChunk(int chunkX, int chunkZ, Packet packet);

    protected abstract LevelServices services();

    /** 플러그인 이벤트 입구(엔티티·Region 코드용). */
    public org.domi.furnace.server.plugin.GameEvents gameEvents() { return services().gameEvents(); }

    /**
     * LevelChunk.setBlockState 의 블록 엔티티 부분: 있던 것이 이 상태에 맞지 않으면 지우고, 없으면 블록이 새로 만들고(newBlockEntity),
     * 있으면 상태만 바꾼다.
     */
    protected void createBlockEntity(ChunkColumn col, Pos pos, int state) {
        BlockEntity be = col.blockEntityAt(pos.x(), pos.y(), pos.z());
        if (be != null && !be.isValid(state)) {
            removeBlockEntity(col, pos);
            be = null;
        }
        if (be == null) {
            be = BlockBehaviors.of(state).newBlockEntity(pos, state);
            if (be != null) addBlockEntity(col, be);
        } else {
            be.setBlockState(state);
        }
    }

    /** addAndRegisterBlockEntity: 청크에 두고 틱을 받는 종류면 틱 목록에. */
    protected void addBlockEntity(ChunkColumn col, BlockEntity be) {
        BlockEntity old = col.blockEntityAt(be.pos().x(), be.pos().y(), be.pos().z());
        if (old != null && old != be) {
            removeGameEventListener(col, old);
            old.setRemoved();   // 바닐라 LevelChunk.setBlockEntity: 바뀐 옛 것은 지운다(틱 목록에서 빠진다)
        }
        be.clearRemoved();
        col.putBlockEntity(be);
        if (old != be) addGameEventListener(col, be);
        if (be.ticking()) addTicker(be);
    }

    protected void removeBlockEntity(ChunkColumn col, Pos pos) {
        BlockEntity old = col.blockEntityAt(pos.x(), pos.y(), pos.z());
        if (old != null) removeGameEventListener(col, old);
        col.removeBlockEntityAt(pos.x(), pos.y(), pos.z());
    }

    /** LevelChunk.addGameEventListener: 리스너를 가진 블록 엔티티는 그 칸 구역 리스너 목록에. */
    private static void addGameEventListener(ChunkColumn col, BlockEntity be) {
        if (be instanceof org.domi.furnace.server.world.gameevent.GameEventListener.Provider p && p.getListener() != null) {
            org.domi.furnace.server.world.gameevent.GameEventDispatcher.registerBlockListener(col, be.pos().y(), p.getListener());
        }
    }

    private static void removeGameEventListener(ChunkColumn col, BlockEntity be) {
        if (be instanceof org.domi.furnace.server.world.gameevent.GameEventListener.Provider p && p.getListener() != null) {
            org.domi.furnace.server.world.gameevent.GameEventDispatcher.unregisterBlockListener(col, be.pos().y(), p.getListener());
        }
    }

    /** 게임 이벤트가 볼 청크(getChunkNow) — 이 레벨이 가진 로드된 청크만. */
    public ChunkColumn listenerChunk(int chunkX, int chunkZ) { return chunk(chunkX, chunkZ); }

    protected Object blockEntity(ChunkColumn col, Pos pos) {
        BlockEntity be = col.blockEntityAt(pos.x(), pos.y(), pos.z());
        return be != null ? be : col.chestAt(pos.x(), pos.y(), pos.z());
    }

    // ------------------------------------------------------------------ 블록 엔티티 틱(Level.tickBlockEntities)

    private final java.util.ArrayList<BlockEntity> blockEntityTickers = new java.util.ArrayList<>();
    private final java.util.ArrayList<BlockEntity> pendingTickers = new java.util.ArrayList<>();
    private boolean tickingBlockEntities;
    private final long[] tickMemoKey = new long[64];
    private final boolean[] tickMemoVal = new boolean[64], tickMemoUsed = new boolean[64];

    /** addBlockEntityTicker: 틱 도중에 생긴 것은 다음 틱부터. */
    protected void addTicker(BlockEntity be) {
        (tickingBlockEntities ? pendingTickers : blockEntityTickers).add(be);
    }

    /** 불러온 청크의 블록 엔티티를 틱 목록에(registerAllBlockEntitiesAfterLevelLoad). */
    public void registerBlockEntities(ChunkColumn col) {
        for (BlockEntity be : col.blockEntities().values()) {
            if (be.isRemoved()) continue;
            addGameEventListener(col, be);
            if (be.ticking()) addTicker(be);
        }
    }

    /**
     * tickBlockEntities: 지워진 것은 빼고, 틱 받는 청크에 있고 보더 안이며 칸의 상태가 그 종류에 맞는 것만 tick.
     * ticking 은 그 칸의 청크가 이번 틱에 블록 틱을 받는가(바닐라 shouldTickBlocksAt + LevelChunk.isTicking 의 청크 부분).
     */
    public void tickBlockEntities(java.util.function.LongPredicate ticking) {
        tickingBlockEntities = true;
        if (!pendingTickers.isEmpty()) {
            blockEntityTickers.addAll(pendingTickers);
            pendingTickers.clear();
        }
        int w = 0;
        // 청크별 "블록 엔티티 틱을 받는가" 판정을 이번 호출 안에서 기억한다(X1): 깔때기 수천 개가 같은 청크에 있어도 판정(강제 로드·티켓·플레이어 거리)은 청크당 한 번.
        // 바닐라의 청크 틱 단계도 청크 맵 틱에서만 바뀌어, 블록 엔티티를 도는 동안에는 고정이다.
        java.util.Arrays.fill(tickMemoUsed, false);
        double[] border = null;
        for (int i = 0; i < blockEntityTickers.size(); i++) {
            BlockEntity be = blockEntityTickers.get(i);
            if (be.isRemoved()) continue;
            blockEntityTickers.set(w++, be);
            Pos p = be.pos();
            long ckey = (long) (p.x() >> 4) << 32 | (p.z() >> 4) & 0xFFFFFFFFL;
            int slot = (int) ((ckey * 0x9E3779B97F4A7C15L) >>> 58);
            boolean chunkTicks;
            if (tickMemoUsed[slot] && tickMemoKey[slot] == ckey) chunkTicks = tickMemoVal[slot];
            else {
                chunkTicks = ticking.test(ckey);
                tickMemoUsed[slot] = true;
                tickMemoKey[slot] = ckey;
                tickMemoVal[slot] = chunkTicks;
            }
            if (!chunkTicks) continue;
            // LevelChunk.isTicking 의 보더 판정(WorldBorder.isWithinBounds(BlockPos)). 보더는 틱 사이에만 움직이므로 이 호출 처음 값으로 본다
            // (보더 객체 잠금을 블록 엔티티마다 잡지 않게).
            if (border == null) border = borderBounds();
            if (p.x() < border[0] || p.x() >= border[2] || p.z() < border[1] || p.z() >= border[3]) continue;
            int s = getBlockState(p);
            if (be.isValid(s)) be.tick(this);
        }
        while (blockEntityTickers.size() > w) blockEntityTickers.remove(blockEntityTickers.size() - 1);
        tickingBlockEntities = false;
    }

    // ------------------------------------------------------------------ 읽기

    private ChunkColumn chunkAt(Pos pos) { return chunk(pos.x() >> 4, pos.z() >> 4); }

    // ------------------------------------------------------------------ 구조물 질의(StructureManager)

    private static final org.domi.furnace.server.world.gen.structure.ChunkStructures.Ref[] NO_REFS = new org.domi.furnace.server.world.gen.structure.ChunkStructures.Ref[0];

    /** pos 의 청크가 참조하는 시작점들(참조 순서, 그 청크에 걸치는 조각만). 시작 청크가 로드 안 됐으면 빠진다. */
    public org.domi.furnace.server.world.gen.structure.ChunkStructures.Ref[] structuresAt(Pos pos) {
        int cx = pos.x() >> 4, cz = pos.z() >> 4;
        ChunkColumn c = chunk(cx, cz);
        if (c == null) return NO_REFS;
        return c.structures().referenced(key -> {
            ChunkColumn s = chunk((int) key, (int) (key >>> 32));
            return s == null ? null : s.structures();
        }, cx, cz);
    }

    /** StructureManager.getStructureAt(pos, structure): 전체 상자에 pos 가 드는 첫 시작점(없으면 null). */
    public org.domi.furnace.server.world.gen.structure.ChunkStructures.Ref structureAt(Pos pos, String structureId) {
        for (var r : structuresAt(pos)) if (r.id().equals(structureId) && r.isInside(pos.x(), pos.y(), pos.z())) return r;
        return null;
    }

    /** StructureManager.getStructureWithPieceAt(pos, structure): 조각 하나에 pos 가 드는 첫 시작점(없으면 null). */
    public org.domi.furnace.server.world.gen.structure.ChunkStructures.Ref structureWithPieceAt(Pos pos, String structureId) {
        for (var r : structuresAt(pos)) if (r.id().equals(structureId) && r.hasPieceAt(pos.x(), pos.y(), pos.z())) return r;
        return null;
    }

    @Override
    public int getBlockState(Pos pos) {
        if (isOutsideBuildHeight(pos.y())) return blocks.defaultState("minecraft:void_air");
        ChunkColumn c = chunkAt(pos);
        return c == null ? blocks.defaultState("minecraft:void_air") : c.getBlock(pos.x() & 15, pos.y(), pos.z() & 15);
    }

    @Override
    public int getFluidState(Pos pos) {
        if (isOutsideBuildHeight(pos.y())) return Fluids.EMPTY;
        int s = getBlockState(pos);
        return BlockBehaviors.of(s).getFluidState(s);
    }

    @Override
    public int minBuildHeight() { return dimensionType().minY(); }

    @Override
    public int maxBuildHeight() { return dimensionType().maxY(); }

    /** 차원 이동 도착 뒤 처리(포털 티켓). 서버 레벨이 티켓을 건다 — 시험 레벨은 아무것도. */
    public void afterDimensionTransition(org.domi.furnace.server.entity.DimensionTransition t) {}

    /** 바닐라 ServerLevel.resetEmptyTime: 엔티티가 차원을 넘으면(changeDimension) 떠난·닿은 차원이 다시 300 틱 엔티티를 돌린다. 시험 레벨은 아무것도. */
    public void resetEmptyTime() {}

    /** 다른 스레드 소유 차원으로 옮기는 경로(시험 레벨이면 null). */
    public LevelServices.DimensionTravel dimensionTravel() { return services().dimensionTravel(); }

    /** 이 레벨의 차원 타입. */
    public org.domi.furnace.server.world.DimensionType dimensionType() { return services().dimensionType(); }

    @Override
    public boolean hasChunkAt(Pos pos) { return chunkAt(pos) != null; }

    /** Level.getSeaLevel: 오버월드 63. */
    public int seaLevel() { return 63; }

    // ------------------------------------------------------------------ setBlock(바닐라 Level.setBlock + LevelChunk.setBlockState)

    @Override
    public boolean setBlock(Pos pos, int state, int flags, int recursionLeft) {
        if (isOutsideBuildHeight(pos.y())) return false;
        ChunkColumn col = chunkAt(pos);
        if (col == null) return false;
        int old = setBlockStateInChunk(col, pos, state, (flags & UPDATE_MOVE_BY_PISTON) != 0);
        if (old < 0) return false;
        int now = getBlockState(pos);
        if (now == state) {
            if ((flags & UPDATE_CLIENTS) != 0) {
                markChanged(pos);
                updateNavigations(pos, old, state);   // ServerLevel.sendBlockUpdated 의 길찾기 부분
            }
            if ((flags & UPDATE_NEIGHBORS) != 0) {
                blockUpdated(pos, blocks.blockOf(old));
                if (BlockBehaviors.of(state).hasAnalogOutputSignal(state)) updateNeighbourForOutputSignal(pos, blocks.blockOf(state));
            }
            if ((flags & UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
                int f = flags & ~(UPDATE_NEIGHBORS | UPDATE_SUPPRESS_DROPS);
                BlockBehaviors.of(old).updateIndirectNeighbourShapes(old, this, pos, f, recursionLeft - 1);
                B.updateNeighbourShapes(state, this, pos, f, recursionLeft - 1);
                BlockBehaviors.of(state).updateIndirectNeighbourShapes(state, this, pos, f, recursionLeft - 1);
            }
            poiManager.onBlockStateChange(pos, old, now);   // 바닐라 onBlockStateChange(server.execute 로 미루지 않고 바로)
        }
        return true;
    }

    /** ServerLevel.isUpdatingNavigations: 길 다시 찾기 도중 다시 들어오면 바닐라는 오류만 남기고 계속한다. */
    private boolean updatingNavigations;

    /**
     * ServerLevel.sendBlockUpdated(pos, old, new, flags) 의 길찾기 부분: 충돌 모양(문맥 없음)이 바뀌었으면 navigatingMobs 마다
     * shouldRecomputePath(pos) 인 길찾기를 모두 모은 뒤 차례로 recomputePath(). 같은 길찾기가 두 번 모이면(탈것을 조종하는 몹과 그 탈것 몹 —
     * getNavigation 이 탈것 것을 준다) 두 번 부른다: 두 번째는 20틱 안이라 미룬 다시 찾기 표시만 남는다(바닐라 그대로).
     * 바닐라처럼 모양을 먼저 비교한다 — 충돌 모양은 표의 공유 객체라 같은 모양이면 바로 끝나고(레드스톤 선 세기 변경 등), 몹 훑기는 모양이 바뀐 때만.
     * 가정: 바닐라는 청크 단계가 BLOCK_TICKING 이상일 때만 sendBlockUpdated 를 부른다(Level.setBlock). Furnace 는 변경 방송(markChanged)과 같이
     * 이 조건을 보지 않는다 — 보더 청크에서 블록이 바뀌는 드문 경우만 다르다.
     */
    private void updateNavigations(Pos pos, int old, int now) {
        var mobs = entities.mobs();
        if (mobs.isEmpty()) return;
        Shape before = BlockBehaviors.of(old).getCollisionShape(old, this, pos, CollisionContext.EMPTY);
        Shape after = BlockBehaviors.of(now).getCollisionShape(now, this, pos, CollisionContext.EMPTY);
        if (!Shape.differs(before, after)) return;
        java.util.ArrayList<org.domi.furnace.server.entity.ai.navigation.PathNavigation> list = null;
        for (var mob : mobs) {
            var nav = mob.getNavigation();
            if (nav != null && nav.shouldRecomputePath(pos)) {
                if (list == null) list = new java.util.ArrayList<>(4);
                list.add(nav);
            }
        }
        if (list == null) return;
        if (updatingNavigations) {
            org.slf4j.LoggerFactory.getLogger(ChunkLevel.class).error("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }
        updatingNavigations = true;
        try {
            for (var nav : list) nav.recomputePath();
        } finally {
            updatingNavigations = false;
        }
    }

    /**
     * 바닐라 LevelChunk.setBlockState 만(이웃·모양 갱신, 클라이언트 알림, POI 없음) — 호환 계층 NMS shim 용. 높이 밖·로드돼 있지 않음·
     * 바뀌지 않음·onRemove 가 갈아 치움이면 -1, 아니면 이전 상태.
     */
    public int setBlockStateInChunkOnly(Pos pos, int state, boolean moved) {
        if (isOutsideBuildHeight(pos.y())) return -1;
        ChunkColumn col = chunkAt(pos);
        return col == null ? -1 : setBlockStateInChunk(col, pos, state, moved);
    }

    /** LevelChunk.setBlockState — 바뀌지 않았거나 onRemove 가 블록을 갈아 치웠으면 -1, 아니면 이전 상태. */
    private int setBlockStateInChunk(ChunkColumn col, Pos pos, int state, boolean moved) {
        int lx = pos.x() & 15, lz = pos.z() & 15;
        int old = col.getBlock(lx, pos.y(), lz);
        if (old == state) return -1;
        if (info.isAir(state) && col.sectionHasOnlyAir(pos.y())) return -1;
        col.setBlock(lx, pos.y(), lz, state);
        markDirty(pos.x() >> 4, pos.z() >> 4);
        if (col.light() != null && org.domi.furnace.server.light.LightTables.hasDifferentLightProperties(old, state)) {
            lightEngine().blockChanged(pos.x(), pos.y(), pos.z());   // 하늘빛 원천을 고치고 검사를 건다(계산은 틱 끝 runLightUpdates)
        }
        BlockBehaviors.of(old).onRemove(old, this, pos, state, moved);
        if (blocks.blockOf(col.getBlock(lx, pos.y(), lz)) != blocks.blockOf(state)) return -1;
        BlockBehaviors.of(state).onPlace(state, this, pos, old, moved);
        if (info.hasBlockEntity(state)) createBlockEntity(col, pos, state);
        return old;
    }

    @Override
    public boolean destroyBlock(Pos pos, boolean dropBlock, BlockActor breaker, int recursionLeft) {
        return destroyBlock(pos, dropBlock, breaker, breaker, recursionLeft);
    }

    /** 투사체처럼 BlockActor 가 아닌 엔티티가 부술 때: 전리품의 this 엔티티는 비우고(TODO(전리품): 엔티티를 싣기) 게임 이벤트 원인은 그 엔티티. */
    @Override
    public boolean destroyBlockByEntity(Pos pos, boolean dropBlock, Object entity) { return destroyBlock(pos, dropBlock, null, entity, UPDATE_LIMIT); }

    private boolean destroyBlock(Pos pos, boolean dropBlock, BlockActor breaker, Object eventSource, int recursionLeft) {
        int s = getBlockState(pos);
        if (info.isAir(s)) return false;
        int f = getFluidState(pos);
        if (!B.isTag(s, "minecraft:fire")) levelEvent(null, 2001, pos, s);
        if (dropBlock) dropResources(s, pos, info.hasBlockEntity(s) ? getBlockEntity(pos) : null, breaker, ItemStack.EMPTY);
        boolean ok = setBlock(pos, Fluids.createLegacyBlock(f), UPDATE_ALL, recursionLeft);
        if (ok) gameEventFrom("minecraft:block_destroy", pos, eventSource, s);
        return ok;
    }

    @Override
    public java.util.List<ItemStack> lootDrops(String lootTable, int state, Pos pos, BlockActor entity, ItemStack tool, Float explosionRadius,
                                            java.util.Map<String, Object> blockEntity) {
        return services().blockDrops(lootTable, state, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, tool,
                entity == null ? null : entity.handle(), explosionRadius, blockEntity);
    }

    @Override
    public void popExperience(Pos pos, int amount) {
        if (!gameRule("doTileDrops")) return;
        awardExperience(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, amount);
    }

    @Override
    public void destroyBlockProgress(int breakerId, Pos pos, int progress) {
        var pkt = new org.domi.furnace.server.net.protocol.play.BlockDestruction(breakerId, pos.x(), pos.y(), pos.z(), progress);
        services().forEachPlayer(p -> {
            if (p.entityId() == breakerId) return;
            double dx = pos.x() - p.x(), dy = pos.y() - p.y(), dz = pos.z() - p.z();
            if (dx * dx + dy * dy + dz * dz < 1024.0) p.send(pkt);
        });
    }

    // ------------------------------------------------------------------ 이웃 갱신

    @Override
    public void updateNeighborsAt(Pos pos, int sourceBlock) { neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, -1); }

    @Override
    public void updateNeighborsAtExceptFromFacing(Pos pos, int sourceBlock, int skipDir) {
        neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, skipDir);
    }

    @Override
    public void neighborChanged(Pos pos, int sourceBlock, Pos fromPos) { neighborUpdater.neighborChanged(pos, sourceBlock, fromPos); }

    @Override
    public void neighborChanged(int state, Pos pos, int sourceBlock, Pos fromPos, boolean moved) {
        neighborUpdater.neighborChanged(state, pos, sourceBlock, fromPos, moved);
    }

    @Override
    public void neighborShapeChanged(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft) {
        neighborUpdater.shapeUpdate(dir, neighborState, pos, neighborPos, flags, recursionLeft);
    }

    @Override
    public void updateNeighbourForOutputSignal(Pos pos, int block) {
        int comparator = blocks.block("minecraft:comparator");
        for (int d : Dir.HORIZONTAL) {
            Pos p = pos.relative(d);
            if (!hasChunkAt(p)) continue;
            int s = getBlockState(p);
            if (blocks.blockOf(s) == comparator) {
                neighborChanged(s, p, block, pos, false);
            } else if (BlockBehaviors.of(s).isRedstoneConductor(s, this, p)) {
                p = p.relative(d);
                s = getBlockState(p);
                if (blocks.blockOf(s) == comparator) neighborChanged(s, p, block, pos, false);
            }
        }
    }

    // ------------------------------------------------------------------ 예약 틱

    /**
     * 바닐라 LevelAccessor.scheduleTick → createTick(nextSubTickCount) → LevelTicks.schedule: 순번은 먼저 하나 쓰고, 그 칸의 청크(컨테이너)가
     * 로드돼 있지 않으면 예약을 버린다(바닐라 Util.pauseInIde 뒤 return). 그래서 버린 예약도 뒤 예약들의 순번을 하나 민다.
     */
    @Override
    public void scheduleTick(Pos pos, int block, int delay, int priority) {
        long order = subTickCount++;
        int cx = pos.x() >> 4, cz = pos.z() >> 4;
        if (chunk(cx, cz) == null) return;
        blockTicks.schedule(pos, block, levelTime + delay, priority, order);
        markDirty(cx, cz);   // 예약도 청크와 함께 저장한다
    }

    @Override
    public void scheduleFluidTick(Pos pos, int fluidType, int delay) {
        long order = subTickCount++;
        int cx = pos.x() >> 4, cz = pos.z() >> 4;
        if (chunk(cx, cz) == null) return;
        fluidTicks.schedule(pos, fluidType, levelTime + delay, 0, order);
        markDirty(cx, cz);
    }

    // ------------------------------------------------------------------ 예약 틱 저장(청크 NBT block_ticks·fluid_ticks — SavedTick)

    private static final String[] FLUID_NAMES = {"minecraft:empty", "minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"};

    /** 그 청크에 예약이 있는가. */
    public boolean hasChunkTicks(int chunkX, int chunkZ) {
        return blockTicks.hasChunkTicks(chunkX, chunkZ) || fluidTicks.hasChunkTicks(chunkX, chunkZ);
    }

    /**
     * 그 청크의 예약을 NBT 목록 둘(block_ticks, fluid_ticks)로 쓴다 — 칸마다 {i: 종류 id, x, y, z, t: 남은 틱, p: 우선순위}.
     * remove 면 예약을 꺼낸다(청크를 내릴 때).
     */
    public void saveChunkTicks(int chunkX, int chunkZ, org.domi.furnace.server.net.io.nbt.NbtCompound root, boolean remove) {
        var bt = remove ? blockTicks.removeChunk(chunkX, chunkZ) : blockTicks.chunkTicks(chunkX, chunkZ);
        var ft = remove ? fluidTicks.removeChunk(chunkX, chunkZ) : fluidTicks.chunkTicks(chunkX, chunkZ);
        root.putList("block_ticks", tickList(bt, false));
        root.putList("fluid_ticks", tickList(ft, true));
    }

    private org.domi.furnace.server.net.io.nbt.NbtList tickList(java.util.List<BlockTicks.Scheduled> ticks, boolean fluid) {
        var list = new org.domi.furnace.server.net.io.nbt.NbtList(ticks.isEmpty() ? org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END
                : org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (BlockTicks.Scheduled s : ticks) {
            Pos p = Pos.of(s.pos());
            var e = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            e.putString("i", fluid ? FLUID_NAMES[s.type()] : blocks.name(s.type()));
            e.putInt("x", p.x());
            e.putInt("y", p.y());
            e.putInt("z", p.z());
            e.putInt("t", (int) (s.triggerTick() - levelTime));
            e.putInt("p", s.priority());
            list.add(e);
        }
        return list;
    }

    /**
     * 불러온 청크 NBT 의 예약을 다시 건다 — 모르는 종류는 건너뛴다. 읽은 목록은 지운다.
     * 적재 중인 청크의 것이라 로드 여부를 보지 않고 바로 건다(scheduleTick 의 "청크가 없으면 버림"을 타지 않는다).
     * 순번은 바닐라 LevelChunkTicks.unpack 그대로 목록마다 -n, -n+1, …, -1(n = 읽은 예약 수)이고 레벨 순번 카운터는 쓰지 않는다 —
     * 같은 (시각, 우선순위)이면 불러온 예약이 이미 있던 예약보다 먼저 돈다. 바닐라 SavedTick.loadTickList·loadTick 처럼 그 청크 밖 칸의 예약은 버리고,
     * 우선순위는 TickPriority.byValue 로 -3..3 에 가둔다.
     */
    public void loadChunkTicks(org.domi.furnace.server.net.io.nbt.NbtCompound root, int chunkX, int chunkZ) {
        if (root == null) return;
        for (String key : new String[] {"block_ticks", "fluid_ticks"}) {
            if (!(root.tags().get(key) instanceof org.domi.furnace.server.net.io.nbt.NbtList list)) continue;
            boolean fluid = key.equals("fluid_ticks");
            java.util.List<Object[]> read = new java.util.ArrayList<>();   // SavedTick.loadTickList: 종류를 아는 것만
            for (Object o : list.items()) {
                if (!(o instanceof org.domi.furnace.server.net.io.nbt.NbtCompound e)) continue;
                String id = e.contains("i") ? e.getString("i") : null;
                int type = -1;
                if (fluid) {
                    for (int i = 1; i < FLUID_NAMES.length; i++) if (FLUID_NAMES[i].equals(id)) type = i;
                } else if (id != null) {
                    type = blocks.block(id);
                }
                if (type < 0) continue;
                Pos p = new Pos(e.getIntOr0("x"), e.getIntOr0("y"), e.getIntOr0("z"));
                if (p.x() >> 4 != chunkX || p.z() >> 4 != chunkZ) continue;
                read.add(new Object[] {p, type, e.getIntOr0("t"), Math.max(-3, Math.min(3, e.getIntOr0("p")))});
            }
            long order = -read.size();
            for (Object[] r : read) {
                (fluid ? fluidTicks : blockTicks).schedule((Pos) r[0], (int) r[1], levelTime + (int) r[2], (int) r[3], order++);
            }
            root.tags().remove(key);
        }
    }

    @Override
    public boolean hasScheduledTick(Pos pos, int block) { return blockTicks.hasScheduledTick(pos, block); }

    @Override
    public boolean willTickThisTick(Pos pos, int block) { return blockTicks.willTickThisTick(pos, block); }

    /** 레벨 틱의 블록 틱 → 유체 틱(바닐라 ServerLevel.tick 순서). 지역 시계를 한 틱 진행한다. 모든 청크가 틱 범위다. */
    public void runScheduledTicks() { runScheduledTicks(null); }

    /**
     * chunkTicking(청크 키 (x << 32 | z), null 이면 모두)이 참인 청크의 예약만 돌린다 — 바닐라 LevelTicks 의 술어
     * ServerLevel.isPositionTickingWithEntitiesLoaded. 범위 밖 청크의 예약은 남아 있다가 범위에 들면 돈다.
     */
    public void runScheduledTicks(java.util.function.LongPredicate chunkTicking) {
        long now = ++levelTime;
        handlingTick = true;
        blockTicks.tick(now, 65536, chunkTicking, (pos, type) -> {
            int s = getBlockState(pos);
            if (blocks.blockOf(s) == type) BlockBehaviors.of(s).tick(s, this, pos, random);
        });
        fluidTicks.tick(now, 65536, chunkTicking, (pos, type) -> {
            int f = getFluidState(pos);
            if (Fluids.type(f) == type) FluidTicker.tick(this, pos, f);
        });
        handlingTick = false;
    }

    public BlockTicks blockTicks() { return blockTicks; }

    // ------------------------------------------------------------------ 무작위 틱·날씨(바닐라 ServerLevel.tickChunk)

    /** Util.shuffle(list, level.random): 뒤에서부터 nextInt(i) 자리와 바꾼다(ServerChunkCache.tickChunks 의 청크 순서). */
    protected void shuffle(long[] a, int n) {
        for (int i = n; i > 1; i--) {
            int j = random.nextInt(i);
            long t = a[i - 1];
            a[i - 1] = a[j];
            a[j] = t;
        }
    }

    /**
     * ServerLevel.findLightningTargetAround: 피뢰침 → 하늘이 보이는 생물 하나 → 지표.
     * 피뢰침: findLightningRod — 반경 128 의 LIGHTNING_ROD POI 중 WORLD_SURFACE 바로 아래 칸인 가장 가까운 것의 위 칸.
     */
    /** 피뢰침 POI 찾기 반경(바닐라 128). region 한정 읽기라 기본 region 띠(129블록) 안이어야 한다 — RegionBandRadiusTest. */
    public static final int LIGHTNING_ROD_SEARCH_RADIUS = 128;

    private Pos findLightningTargetAround(Pos pos) {
        Pos top = new Pos(pos.x(), getMotionBlockingHeight(pos.x(), pos.z()), pos.z());
        Pos rod = poiManager.findClosest(t -> t == org.domi.furnace.server.world.poi.PoiTypes.LIGHTNING_ROD,
                p -> p.y() == getWorldSurfaceHeight(p.x(), p.z()) - 1, top, LIGHTNING_ROD_SEARCH_RADIUS, org.domi.furnace.server.world.poi.PoiManager.Occupancy.ANY);
        if (rod != null) return rod.above(1);
        org.domi.furnace.server.entity.AABB box = new org.domi.furnace.server.entity.AABB(top.x(), top.y(), top.z(), top.x() + 1, maxBuildHeight() + 1, top.z() + 1).inflate(3.0);
        var list = new java.util.ArrayList<Pos>();
        for (var e : getEntitiesOfClass(org.domi.furnace.server.entity.LivingEntity.class, box, t -> t.isAlive() && canSeeSky(t.blockPosition()))) list.add(e.blockPosition());
        forEachPlayerIn(box, p -> { if (p.isAlive() && canSeeSky(p.blockPosition())) list.add(p.blockPosition()); });
        if (!list.isEmpty()) return list.get(random.nextInt(list.size()));
        if (top.y() == minBuildHeight() - 1) top = top.above(2);
        return top;
    }

    /** Level.getBlockRandomPos(x, y, z, 15): randValue = randValue·3 + 1013904223, 그 >> 2 의 비트로 칸 안 위치. */
    Pos blockRandomPos(int x, int y, int z) {
        randValue = randValue * 3 + 1013904223;
        int i = randValue >> 2;
        return new Pos(x + (i & 15), y + (i >> 16 & 15), z + (i >> 8 & 15));
    }

    /**
     * ServerLevel.tickChunk(chunk, randomTickSpeed): 천둥(번개) → 얼음·눈(randomTickSpeed 번, 1/48 로 강수 칸 하나) →
     * 무작위 틱(무작위 틱 받는 섹션마다 randomTickSpeed 칸: 블록 randomTick, 그 유체 randomTick).
     */
    public void tickChunk(int chunkX, int chunkZ, int randomTickSpeed) {
        ChunkColumn col = chunk(chunkX, chunkZ);
        if (col == null) return;
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        boolean raining = isRaining();
        if (raining && isThundering() && random.nextInt(100000) == 0) {
            Pos strike = findLightningTargetAround(blockRandomPos(x0, 0, z0));
            if (isRainingAt(strike)) {
                var diff = getCurrentDifficultyAt(strike);
                boolean trap = gameRule("doMobSpawning") && random.nextDouble() < diff.effectiveDifficulty() * 0.01
                        && !B.is(getBlockState(strike.below()), "lightning_rod");
                if (trap) {
                    var horse = new org.domi.furnace.server.entity.SkeletonHorse(this, nextEntityId());
                    horse.setTrap(true);
                    horse.setAge(0);
                    horse.setPos(strike.x(), strike.y(), strike.z());
                    addFreshEntity(horse);
                }
                var bolt = new org.domi.furnace.server.entity.LightningBolt(this, nextEntityId());
                bolt.moveTo(strike.x() + 0.5, strike.y(), strike.z() + 0.5, 0.0F, 0.0F);
                bolt.setVisualOnly(trap);
                addFreshEntity(bolt);
            }
        }
        for (int l = 0; l < randomTickSpeed; l++) {
            if (random.nextInt(48) == 0) tickPrecipitation(col, blockRandomPos(x0, 0, z0));
        }
        if (randomTickSpeed <= 0) return;
        randomTickSections(col, x0, z0, randomTickSpeed);
    }

    /**
     * tickChunk 의 구역 무작위 틱: 무작위 틱 받는 구역마다 randomTickSpeed 칸. 컬럼 마스크(T-053)로 꺼진 구역(센 값 0 — 원래도
     * isRandomlyTicking 이 거짓)을 건너뛴다. 마스크는 구역마다 새로 읽어 앞 구역의 틱이 켠 뒤 구역도 같은 틱에 돈다(옛 루프와 같음).
     */
    void randomTickSections(ChunkColumn col, int x0, int z0, int randomTickSpeed) {
        ChunkSection[] sections = col.sections();
        assert col.randomTickMaskCovers() : "무작위 틱 구역 마스크가 무작위 틱 받는 구역을 놓침";
        for (int si = col.nextRandomTickingSection(0); si < sections.length; si = col.nextRandomTickingSection(si + 1)) {
            ChunkSection sec = sections[si];
            if (sec == null || !sec.isRandomlyTicking()) continue;
            int y0 = col.minY() + (si << 4);
            for (int n = 0; n < randomTickSpeed; n++) {
                Pos p = blockRandomPos(x0, y0, z0);
                // 칸마다 다시 읽는다: 빛 작업 모드(T-064)에서 앞 칸의 randomTick 이 얼린 구역에 쓰면 컬럼은 복사본으로 갈아 끼운다 — 옛 객체는 낡았다
                // (바닐라는 같은 객체가 그 자리에서 바뀐다)
                int st = sections[si].getBlock(p.x() - x0, p.y() - y0, p.z() - z0);
                BlockBehavior b = BlockBehaviors.of(st);
                if (b.isRandomlyTicking(st)) b.randomTick(st, this, p, random);
                int f = b.getFluidState(st);
                if (Fluids.isLava(f)) FluidTicker.randomTick(this, p, f, random);
            }
        }
    }

    /**
     * ServerLevel.tickPrecipitation(pos): MOTION_BLOCKING 높이 칸(위)·그 아래 칸 — 아래가 얼 수 있으면 얼음, 비가 오면 위에 눈을 쌓고
     * (게임룰 snowAccumulationHeight 까지), 아래 칸 블록에 그 강수를 알린다(가마솥).
     */
    void tickPrecipitation(ChunkColumn col, Pos pos) {
        Pos top = new Pos(pos.x(), col.motionBlockingBlockY(pos.x() & 15, pos.z() & 15) + 1, pos.z());
        Pos below = top.below();
        var climate = org.domi.furnace.server.world.BiomeWeather.of(services().biomeAt(top.x(), top.y(), top.z()));
        if (shouldFreeze(climate, below)) B.form(this, below, blocks.defaultState("minecraft:ice"), UPDATE_ALL);   // 플러그인 BlockFormEvent
        if (isRaining()) {
            int max = gameRuleInt("snowAccumulationHeight");
            if (max > 0 && shouldSnow(climate, top)) {
                int s = getBlockState(top);
                if (blocks.blockOf(s) == SNOW) {
                    Prop layers = blocks.prop("layers");
                    int j = layers.intValue(s);
                    if (j < Math.min(max, 8)) {
                        int more = layers.withInt(s, j + 1);
                        pushEntitiesUp(s, more, top);
                        B.form(this, top, more, UPDATE_ALL);   // 플러그인 BlockFormEvent
                    }
                } else {
                    B.form(this, top, blocks.defaultState(SNOW), UPDATE_ALL);   // 플러그인 BlockFormEvent
                }
            }
            String precip = climate.precipitationAt(below.x(), below.y(), below.z());
            if (!precip.equals(org.domi.furnace.server.world.BiomeWeather.NONE)) {
                int s = getBlockState(below);
                BlockBehaviors.of(s).handlePrecipitation(s, this, below, precip);
            }
        }
    }

    private final int SNOW = blocks.block("minecraft:snow"), WATER_BLOCK = blocks.block("minecraft:water");

    /** Biome.shouldFreeze(level, pos, true): 따뜻하지 않고 블록빛 < 10, 물 원천 블록이며 네 옆이 모두 물은 아님(가장자리). */
    private boolean shouldFreeze(org.domi.furnace.server.world.BiomeWeather.Climate c, Pos pos) {
        if (c.warmEnoughToRain(pos.x(), pos.y(), pos.z())) return false;
        if (pos.y() < minBuildHeight() || pos.y() >= maxBuildHeight() || getBlockLight(pos) >= 10) return false;
        int s = getBlockState(pos);
        if (blocks.blockOf(s) != WATER_BLOCK || !Fluids.isWaterSource(getFluidState(pos))) return false;
        boolean surrounded = isWaterAt(pos.west()) && isWaterAt(pos.east()) && isWaterAt(pos.north()) && isWaterAt(pos.south());
        return !surrounded;
    }

    /** Biome.shouldSnow(level, pos): 따뜻하지 않고 블록빛 < 10, 공기나 눈이며 눈이 살 수 있는 칸. */
    private boolean shouldSnow(org.domi.furnace.server.world.BiomeWeather.Climate c, Pos pos) {
        if (c.warmEnoughToRain(pos.x(), pos.y(), pos.z())) return false;
        if (pos.y() < minBuildHeight() || pos.y() >= maxBuildHeight() || getBlockLight(pos) >= 10) return false;
        int s = getBlockState(pos);
        if (!info.isAir(s) && blocks.blockOf(s) != SNOW) return false;
        int snow = blocks.defaultState(SNOW);
        return BlockBehaviors.of(snow).canSurvive(snow, this, pos);
    }

    public long levelTime() { return levelTime; }

    @Override
    public RedstoneTorchToggles redstoneTorchToggles() { return torchToggles; }

    // ------------------------------------------------------------------ region 합치기·나누기

    /** 다른 레벨의 자료를 모두 흡수한다(그 레벨은 버려진다). 예약 틱은 두 지역 시계 차이만큼 보정한다. */
    public void absorb(ChunkLevel other) {
        long offset = levelTime - other.levelTime;
        other.blockTicks.moveTo(blockTicks, k -> true, offset);
        other.fluidTicks.moveTo(fluidTicks, k -> true, offset);
        subTickCount = Math.max(subTickCount, other.subTickCount);
        for (var e : other.changed.entrySet()) changed.computeIfAbsent(e.getKey(), k -> new SectionChanges()).addAll(e.getValue());
        other.changed.clear();
        blockEvents.addAll(other.blockEvents);
        other.blockEvents.clear();
        blockEntityTickers.addAll(other.blockEntityTickers);
        blockEntityTickers.addAll(other.pendingTickers);
        other.blockEntityTickers.clear();
        other.pendingTickers.clear();
        for (Entity e : other.entities.all()) e.setLevel(this);
        entities.absorb(other.entities);
        absorbSeenIndex(other);
        raids.absorb(other.raids, this);
        torchToggles.absorb(other.torchToggles);
        // 빛: 틱 사이에 놓인 블록의 미처리 검사와 방송 전 빛 변경은 합친 쪽이 이어받는다(안 옮기면 빛이 계산·방송되지 않는다).
        // 빛 작업 모드면 둘 다 먼저 끝까지 계산한다(도는 묶음은 옮기지 않는다 — 아직 각자 청크를 가진 지금).
        if (asyncLight()) {
            drainLight();
            other.drainLight();
        }
        if (other.lightEngine != null && other.lightEngine.hasWork()) other.lightEngine.moveChecksTo(lightEngine(), p -> true);
        for (var e : other.lightChanges.entrySet()) {
            long[] m = lightChanges.computeIfAbsent(e.getKey(), k -> new long[2]);
            m[0] |= e.getValue()[0];
            m[1] |= e.getValue()[1];
        }
        other.lightChanges.clear();
        lightWorld.resetLast();
    }

    /** 청크 조건(청크 x, z)에 맞는 자료를 target 으로 옮긴다. target 은 이 레벨의 시계를 이어받는다. */
    public void splitTo(ChunkLevel target, ChunkFilter inTarget) {
        target.levelTime = levelTime;
        target.subTickCount = subTickCount;
        java.util.function.LongPredicate byPos = packed -> {
            Pos p = Pos.of(packed);
            return inTarget.test(p.x() >> 4, p.z() >> 4);
        };
        java.util.function.LongPredicate byChunk = key -> inTarget.test((int) (key >> 32), (int) key);   // ChunkPredicateMemo.chunkKey
        blockTicks.moveTo(target.blockTicks, byChunk, 0);
        fluidTicks.moveTo(target.fluidTicks, byChunk, 0);
        var it = changed.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            long key = e.getKey();
            int cx = (int) (key >> 42), cz = (int) (key << 22 >> 42);
            if (inTarget.test(cx, cz)) {
                target.changed.put(key, e.getValue());
                it.remove();
            }
        }
        for (var list : java.util.List.of(blockEntityTickers, pendingTickers)) {
            var tit = list.iterator();
            while (tit.hasNext()) {
                BlockEntity be = tit.next();
                if (inTarget.test(be.pos().x() >> 4, be.pos().z() >> 4)) {
                    target.blockEntityTickers.add(be);
                    tit.remove();
                }
            }
        }
        for (Entity e : entities.splitTo(target.entities, inTarget::test)) {
            e.setLevel(target);
            if (e.tracker != null) target.adoptTracker(e);
        }
        raids.splitTo(target.raids, target, inTarget);
        torchToggles.splitTo(target.torchToggles, byPos);
        var bit = blockEvents.iterator();
        while (bit.hasNext()) {
            BlockEventData ev = bit.next();
            if (inTarget.test(ev.x() >> 4, ev.z() >> 4)) {
                target.blockEvents.add(ev);
                bit.remove();
            }
        }
        // 빛: 청크가 target 쪽인 미처리 검사와 방송 전 빛 변경(키 = sx<<32 | sz). 빛 작업 모드면 나누기 전에(주인을 바꾸기 전에)
        // settleLightBeforeSplit 으로 끝까지 계산해 두었다 — 도는 묶음·새 청크 빛 켜기는 남아 있지 않다.
        assert lightBatch == null : "빛 작업이 남은 채 나눔";
        if (lightEngine != null && lightEngine.hasWork()) {
            lightEngine.moveChecksTo(target.lightEngine(),
                    p -> inTarget.test(org.domi.furnace.server.light.LightEngine.px(p) >> 4, org.domi.furnace.server.light.LightEngine.pz(p) >> 4));
        }
        var lit = lightChanges.entrySet().iterator();
        while (lit.hasNext()) {
            var e = lit.next();
            if (inTarget.test((int) (e.getKey() >> 32), (int) (long) e.getKey())) {
                long[] m = target.lightChanges.computeIfAbsent(e.getKey(), k -> new long[2]);
                m[0] |= e.getValue()[0];
                m[1] |= e.getValue()[1];
                lit.remove();
            }
        }
        lightWorld.resetLast();   // 옮겨 비운 lightChanges 때문에 방송이 안 돌면 섹션 기억이 낡는다
    }

    /** 청크 좌표 조건. */
    @FunctionalInterface
    public interface ChunkFilter {
        boolean test(int chunkX, int chunkZ);
    }

    // ------------------------------------------------------------------ 레드스톤(바닐라 SignalGetter)

    @Override
    public int getDirectSignal(Pos pos, int dir) {
        int s = getBlockState(pos);
        return BlockBehaviors.of(s).getDirectSignal(s, this, pos, dir);
    }

    @Override
    public int getDirectSignalTo(Pos pos) {
        int best = 0;
        for (int d : Dir.ALL) {
            best = Math.max(best, getDirectSignal(pos.relative(d), d));
            if (best >= 15) return best;
        }
        return best;
    }

    @Override
    public int getSignal(Pos pos, int dir) {
        int s = getBlockState(pos);
        BlockBehavior b = BlockBehaviors.of(s);
        int i = b.getSignal(s, this, pos, dir);
        return b.isRedstoneConductor(s, this, pos) ? Math.max(i, getDirectSignalTo(pos)) : i;
    }

    @Override
    public boolean hasSignal(Pos pos, int dir) { return getSignal(pos, dir) > 0; }

    @Override
    public boolean hasNeighborSignal(Pos pos) {
        for (int d : Dir.ALL) if (getSignal(pos.relative(d), d) > 0) return true;
        return false;
    }

    @Override
    public int getBestNeighborSignal(Pos pos) {
        int best = 0;
        for (int d : Dir.ALL) {
            int v = getSignal(pos.relative(d), d);
            if (v >= 15) return 15;
            best = Math.max(best, v);
        }
        return best;
    }

    // ------------------------------------------------------------------ 변경 방송(바닐라 ChunkHolder.blockChanged / broadcastChanges)

    private void markChanged(Pos pos) {
        int cx = pos.x() >> 4, cz = pos.z() >> 4, sy = pos.y() >> 4;
        long key = ((long) cx & 0x3FFFFF) << 42 | ((long) cz & 0x3FFFFF) << 20 | (long) sy & 0xFFFFF;
        short local = (short) ((pos.x() & 15) << 8 | (pos.z() & 15) << 4 | pos.y() & 15);
        changed.computeIfAbsent(key, k -> new SectionChanges()).add(local);
    }

    /** 청크 키 → {하늘빛, 블록빛} 바뀐 빛 섹션 마스크(ChunkHolder.sectionLightChanged — 비트 = 섹션 y − 맨 아래 빛 섹션). */
    private final java.util.HashMap<Long, long[]> lightChanges = new java.util.HashMap<>();

    /** 처음 빛을 만드는 청크(lightNewChunk 동안): 아직 안 보낸 청크라 변경을 표시하지 않는다(바닐라 sectionLightChanged 는 보낸 청크만). */
    private long initialLightChunk = Long.MIN_VALUE;

    /** lightNewChunk 동안 그 청크 자신의 빛이 바뀌었다(저장 빛이 이웃과 어긋나 가장자리 맞추기가 고쳤다). */
    private boolean initialLightChanged;

    private void markLightChanged(int sx, int sy, int sz, boolean sky) {
        if (initialLightChunk != Long.MIN_VALUE && initialLightChunk == ((long) sx << 32 | (sz & 0xFFFFFFFFL))) {
            initialLightChanged = true;
            return;
        }
        int idx = sy - ((minBuildHeight() >> 4) - 1);
        int count = ((maxBuildHeight() - minBuildHeight()) >> 4) + 2;
        if (idx < 0 || idx >= count) return;
        long key = (long) sx << 32 | (sz & 0xFFFFFFFFL);
        lightChanges.computeIfAbsent(key, k -> new long[2])[sky ? 0 : 1] |= 1L << idx;
        // 나중에 이 청크를 처음 받는 플레이어가 캐시된 옛 빛을 받지 않게(바닐라는 청크 패킷을 보낼 때의 빛을 담는다)
        ChunkColumn col = chunk(sx, sz);
        if (col != null) {
            col.lightTouched();
            // 바닐라 ChunkHolder.sectionLightChanged: 빛이 바뀐 청크는 저장 대상(setUnsaved). 디스크 빛을 읽으므로(T-012) 바뀐 빛도 써야 한다
            if (col.markLightDirty()) markDirty(sx, sz);
        }
    }

    /** 빛 갱신 패킷을 그 청크를 보는 플레이어 중 시야 가장자리인 사람에게(바닐라 getPlayers(pos, true)). 기본은 버린다. */
    protected void broadcastLightToChunk(int chunkX, int chunkZ, Packet packet) {}

    /** ChunkHolder.broadcastChanges 앞부분: 바뀐 빛 섹션이 있으면 그 섹션만 담은 빛 갱신(블록 변경보다 먼저). */
    private void broadcastLightChanges() {
        int lightBase = (minBuildHeight() >> 4) - 1 - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
        int count = ((maxBuildHeight() - minBuildHeight()) >> 4) + 2;
        for (var e : lightChanges.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            ChunkColumn col = chunk(cx, cz);
            if (col == null || col.light() == null) continue;
            long[] m = e.getValue();
            broadcastLightToChunk(cx, cz, org.domi.furnace.server.net.protocol.play.LightUpdate.of(cx, cz, col.light(), m[0], m[1], lightBase, count));
        }
        lightChanges.clear();
        lightWorld.resetLast();
    }

    /** 그 청크의 아직 안 보낸 칸 변경을 버린다 — 청크를 통째로 다시 보낼 때(빠른 블록 쓰기 CHUNKS). 빛 변경은 남긴다. */
    public void discardChanges(int chunkX, int chunkZ) {
        if (changed.isEmpty()) return;
        int lo = (minBuildHeight() >> 4), hi = (maxBuildHeight() - 1) >> 4;
        for (int sy = lo; sy <= hi; sy++) {
            changed.remove(((long) chunkX & 0x3FFFFF) << 42 | ((long) chunkZ & 0x3FFFFF) << 20 | (long) sy & 0xFFFFF);
        }
    }

    /** 틱 끝에 모아 둔 변경을 보낸다: 칸 하나면 블록 갱신, 여럿이면 구역 갱신(그때의 상태로). */
    public void broadcastChanges() {
        if (!lightChanges.isEmpty()) broadcastLightChanges();
        if (changed.isEmpty()) return;
        for (var e : changed.entrySet()) {
            long key = e.getKey();
            int cx = (int) (key >> 42), cz = (int) (key << 22 >> 42), sy = (int) (key << 44 >> 44);
            ChunkColumn col = chunk(cx, cz);
            if (col == null) continue;
            short[] pos = e.getValue().toArray();
            if (pos.length == 1) {
                short l = pos[0];
                int x = cx << 4 | l >> 8 & 15, z = cz << 4 | l >> 4 & 15, y = sy << 4 | l & 15;
                broadcastToChunk(cx, cz, new BlockUpdate(x, y, z, getBlockState(new Pos(x, y, z))));
                broadcastBlockEntityIfNeeded(cx, cz, new Pos(x, y, z));
            } else {
                int[] states = new int[pos.length];
                // 블록 엔티티가 하나도 없는 청크(생성 상자 NBT 도 없음)는 칸마다 찾지 않는다 — 있으면 예전처럼 칸마다(생성 상자 입양 포함)
                boolean anyBlockEntity = !col.blockEntities().isEmpty() || !col.generatedBlockEntities().isEmpty();
                for (int i = 0; i < pos.length; i++) {
                    short l = pos[i];
                    states[i] = col.getBlock(l >> 8 & 15, sy << 4 | l & 15, l >> 4 & 15);   // 구역 안 칸 — 높이 안, 그 청크
                }
                broadcastToChunk(cx, cz, new SectionBlocksUpdate(cx, sy, cz, pos, states));
                if (anyBlockEntity) {
                    for (short l : pos) broadcastBlockEntityIfNeeded(cx, cz, new Pos(cx << 4 | l >> 8 & 15, sy << 4 | l & 15, cz << 4 | l >> 4 & 15));
                }
            }
        }
        changed.clear();
    }

    /** ChunkHolder.broadcastBlockEntityIfNeeded: 그 칸의 블록 엔티티가 갱신 패킷(getUpdatePacket)을 내면 보낸다. */
    private void broadcastBlockEntityIfNeeded(int cx, int cz, Pos p) {
        if (!(getBlockEntity(p) instanceof BlockEntity be) || !be.hasUpdatePacket()) return;
        broadcastToChunk(cx, cz, new org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket(p.x(), p.y(), p.z(), be.typeId(), be.updateTag()));
    }

    // ------------------------------------------------------------------ 소리·이벤트

    @Override
    public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) {
        playSound(except, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, sound, source, volume, pitch);
    }

    @Override
    public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) {
        playSeededSound(except, x, y, z, sound, source, volume, pitch, soundRandom.nextLong());
    }

    /** Level.playSeededSound: 씨앗을 부르는 쪽이 정한다(엔티티 난수 등). */
    @Override
    public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:sound_event", sound) : null;
        double range = volume > 1.0F ? 16.0 * volume : 16.0;
        SoundPacket pkt = new SoundPacket(id == null ? -1 : id, sound, SoundPacket.source(source), x, y, z, volume, pitch, seed);
        Object skip = except == null ? null : except.handle();
        services().forEachPlayer(p -> {
            if (p == skip) return;
            double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
            if (dx * dx + dy * dy + dz * dz < range * range) p.send(pkt);
        });
    }

    /**
     * Level.playSound(except, entity, 소리, 분류, 크기, 높이) → ServerLevel.playSeededSound 엔티티 변형: sound_entity 를 보낸다.
     * 클라이언트가 소리를 그 엔티티에 붙여 따라 움직인다. 받는 범위·씨앗은 위치 변형과 같다(엔티티의 지금 위치 기준).
     */
    public void playSound(BlockActor except, Entity entity, String sound, String source, float volume, float pitch) {
        playSeededSound(except, entity.id(), entity.x(), entity.y(), entity.z(), sound, source, volume, pitch, soundRandom.nextLong());
    }

    /** 플레이어(엔티티 계층 밖)에 붙는 소리 — 위와 같다. */
    public void playSound(BlockActor except, Player player, String sound, String source, float volume, float pitch) {
        playSeededSound(except, player.entityId(), player.x(), player.y(), player.z(), sound, source, volume, pitch, soundRandom.nextLong());
    }

    /** BlockActor 의 손잡이가 엔티티나 플레이어면 거기에 붙이고, 아니면 그 위치에서 낸다. */
    public void playSoundOn(BlockActor except, BlockActor on, String sound, String source, float volume, float pitch) {
        switch (on.handle()) {
            case Entity e -> playSound(except, e, sound, source, volume, pitch);
            case Player p -> playSound(except, p, sound, source, volume, pitch);
            case null, default -> playSound(except, on.x(), on.y(), on.z(), sound, source, volume, pitch);
        }
    }

    public void playSeededSound(BlockActor except, int entityId, double x, double y, double z, String sound, String source,
                                float volume, float pitch, long seed) {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:sound_event", sound) : null;
        double range = volume > 1.0F ? 16.0 * volume : 16.0;
        org.domi.furnace.server.net.protocol.play.SoundEntity pkt = new org.domi.furnace.server.net.protocol.play.SoundEntity(
                id == null ? -1 : id, sound, SoundPacket.source(source), entityId, volume, pitch, seed);
        Object skip = except == null ? null : except.handle();
        services().forEachPlayer(p -> {
            if (p == skip) return;
            double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
            if (dx * dx + dy * dy + dz * dz < range * range) p.send(pkt);
        });
    }

    @Override
    public void levelEvent(BlockActor except, int type, Pos pos, int data) {
        LevelEventPacket pkt = new LevelEventPacket(type, pos.x(), pos.y(), pos.z(), data, false);
        Object skip = except == null ? null : except.handle();
        services().forEachPlayer(p -> {
            if (p == skip) return;
            double dx = pos.x() - p.x(), dy = pos.y() - p.y(), dz = pos.z() - p.z();
            if (dx * dx + dy * dy + dz * dz < 64.0 * 64.0) p.send(pkt);
        });
    }

    @Override
    public void globalLevelEvent(int type, Pos pos, int data) {
        if (!gameRule("globalSoundEvents")) {
            levelEvent(null, type, pos, data);
            return;
        }
        LevelEventPacket pkt = new LevelEventPacket(type, pos.x(), pos.y(), pos.z(), data, true);
        services().forEachPlayer(p -> p.send(pkt));
    }

    @Override
    public void blockEvent(Pos pos, int block, int type, int data) {
        blockEvents.add(new BlockEventData(pos.x(), pos.y(), pos.z(), block, type, data));
    }

    /** 틱 끝 블록 이벤트 처리(바닐라 runBlockEvents): 블록이 그대로면 triggerEvent, 참이면 64칸 안에 방송. 모든 청크가 틱 범위다. */
    public void runBlockEvents() { runBlockEvents(null); }

    private final java.util.ArrayList<BlockEventData> blockEventsToReschedule = new java.util.ArrayList<>();
    private final ChunkPredicateMemo blockEventMemo = new ChunkPredicateMemo();

    /**
     * 바닐라 ServerLevel.runBlockEvents: shouldTickBlocksAt 이 거짓인 칸의 이벤트는 처리하지 않고 모아 두었다가 끝에 큐로 되돌린다(다음 틱에 다시 본다).
     * chunkTicking 은 청크 키 (x << 32 | z), null 이면 모든 청크.
     */
    public void runBlockEvents(java.util.function.LongPredicate chunkTicking) {
        java.util.function.LongPredicate ticking = chunkTicking == null ? null : blockEventMemo.reset(chunkTicking);
        blockEventsToReschedule.clear();
        while (!blockEvents.isEmpty()) {
            var first = blockEvents.iterator();
            BlockEventData e = first.next();
            first.remove();
            if (ticking != null && !ticking.test(ChunkPredicateMemo.chunkKey(e.x() >> 4, e.z() >> 4))) {
                blockEventsToReschedule.add(e);
                continue;
            }
            Pos pos = new Pos(e.x(), e.y(), e.z());
            int s = getBlockState(pos);
            if (blocks.blockOf(s) != e.block()) continue;
            if (BlockBehaviors.of(s).triggerEvent(s, this, pos, e.type(), e.data())) {
                BlockEventPacket pkt = new BlockEventPacket(e.x(), e.y(), e.z(), e.type(), e.data(), e.block());
                services().forEachPlayer(p -> {
                    double dx = e.x() - p.x(), dy = e.y() - p.y(), dz = e.z() - p.z();
                    if (dx * dx + dy * dy + dz * dz < 64.0 * 64.0) p.send(pkt);
                });
            }
        }
        if (!blockEventsToReschedule.isEmpty()) {
            blockEvents.addAll(blockEventsToReschedule);
            blockEventsToReschedule.clear();
        }
    }

    @Override
    public void gameEvent(String event, Pos pos, BlockActor source, int state) {
        gameEventFrom(event, pos, source, state);
    }

    /** Level.gameEvent(holder, BlockPos, Context.of(entity, state)) — 칸 가운데에서. entity 는 Entity·Player·BlockActor(또는 null). */
    @Override
    public void gameEventFrom(String event, Pos pos, Object entity, int state) {
        org.domi.furnace.server.world.gameevent.GameEvent e = org.domi.furnace.server.world.gameevent.GameEvent.byName(event);
        if (e == null) return;
        gameEvent(e, new org.domi.furnace.server.entity.Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                org.domi.furnace.server.world.gameevent.GameEventContext.of(entity, state));
    }

    /** ServerLevel.gameEvent(holder, Vec3, Context) → GameEventDispatcher.post. */
    public void gameEvent(org.domi.furnace.server.world.gameevent.GameEvent event, org.domi.furnace.server.entity.Vec3 pos,
            org.domi.furnace.server.world.gameevent.GameEventContext ctx) {
        org.domi.furnace.server.world.gameevent.GameEventDispatcher.post(this, event, pos, ctx);
    }

    /**
     * ServerLevel.sendParticles 의 반환값: 이 레벨 플레이어 중 칸 가운데가 (x,y,z)에서 32 미만인 수.
     * 패킷 없이 세기만 한다(진동 입자의 재전송 표시 상태).
     */
    public int countParticleViewers(double x, double y, double z) {
        return sendParticlePacket(null, false, x, y, z);
    }

    @Override
    public int sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions p, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        return sendParticles(p, false, x, y, z, count, dx, dy, dz, speed);
    }

    /** force(overrideLimiter)면 512 칸까지. 패킷은 (float) 로 줄인 퍼짐·속도를 싣는다. */
    public int sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions p, boolean force, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        var pkt = new org.domi.furnace.server.net.protocol.play.LevelParticles(force, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count, p);
        return sendParticlePacket(pkt, force, x, y, z);
    }

    /** closerToCenterThan: 플레이어 칸 가운데가 (x,y,z)에서 (force ? 512 : 32) 미만. */
    public static boolean particleInRange(org.domi.furnace.server.entity.Player p, boolean force, double x, double y, double z) {
        double range = force ? 512.0 : 32.0;
        double dx = Math.floor(p.x()) + 0.5 - x, dy = Math.floor(p.y()) + 0.5 - y, dz = Math.floor(p.z()) + 0.5 - z;
        return dx * dx + dy * dy + dz * dz < range * range;
    }

    /** 칸 가운데가 (x,y,z)에서 (force ? 512 : 32) 미만인 플레이어에게 보낸다(pkt 가 null 이면 세기만). */
    private int sendParticlePacket(org.domi.furnace.server.net.protocol.play.LevelParticles pkt, boolean force, double x, double y, double z) {
        int[] n = {0};
        services().forEachPlayer(p -> {
            if (p.isDisconnected()) return;
            if (particleInRange(p, force, x, y, z)) {
                n[0]++;
                if (pkt != null) p.send(pkt);
            }
        });
        return n[0];
    }

    // ------------------------------------------------------------------ 기타

    @Override
    public boolean isUnobstructed(Shape worldShape) {
        if (worldShape.isEmpty()) return true;
        double[] b = worldShape.bounds();
        boolean[] blocked = {false};
        services().forEachPlayer(p -> {
            if (blocked[0] || p.isDisconnected() || p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            double h = p.bbHeight();
            double x0 = p.x() - 0.3, y0 = p.y(), z0 = p.z() - 0.3, x1 = p.x() + 0.3, y1 = p.y() + h, z1 = p.z() + 0.3;
            // getEntities(bounds) 는 상자끼리 겹침(경계 제외)으로 먼저 거른 뒤 모양과 맞대 본다
            if (x1 <= b[0] || x0 >= b[3] || y1 <= b[1] || y0 >= b[4] || z1 <= b[2] || z0 >= b[5]) return;
            if (worldShape.intersects(x0, y0, z0, x1, y1, z1)) blocked[0] = true;
        });
        return !blocked[0];
    }

    @Override
    public RandomSource random() { return random; }

    @Override
    public long gameTime() { return services().gameTime(); }

    @Override
    public long dayTime() { return services().dayTime(); }

    @Override
    public boolean isRaining() { return services().isRaining(); }

    @Override
    public boolean isThundering() { return services().isThundering(); }

    @Override
    public float rainLevel() { return services().rainLevel(); }

    @Override
    public float thunderLevel() { return services().thunderLevel(); }

    @Override
    public boolean gameRule(String name) { return services().gameRule(name); }

    /** MinecraftServer.isFlightAllowed(allow-flight). */
    public boolean flightAllowed() { return services().flightAllowed(); }

    /** ServerTickRateManager.runsNormally. */
    public boolean tickRunsNormally() { return services().tickRunsNormally(); }

    /** 팀 조회용 점수판(없으면 null). */
    public org.domi.furnace.server.scores.Scoreboard scoreboard() { return services().scoreboard(); }

    /** 엔티티 밀기가 쓰는 재사용 작업 공간(레벨은 한 번에 한 region 스레드만 돌린다). */
    public final org.domi.furnace.server.entity.PushScratch pushScratch = new org.domi.furnace.server.entity.PushScratch();

    @Override
    public int gameRuleInt(String name) { return services().gameRuleInt(name); }

    /** ServerLevel.canSleepThroughNights: playersSleepingPercentage ≤ 100. */
    public boolean canSleepThroughNights() { return gameRuleInt("playersSleepingPercentage") <= 100; }

    /** ServerLevel.updateSleepingPlayerList(차원 전체 — 월드가 없는 레벨은 아무것도 안 한다). */
    public void updateSleepingPlayerList() {}

    @Override
    public int difficulty() { return services().difficulty(); }

    /** 서버 설정 pvp(바닐라 MinecraftServer.isPvpAllowed). */
    public boolean pvp() { return services().pvp(); }

    @Override
    public String dimension() { return services().dimension(); }

    /** DimensionType.piglinSafe(바닐라 타입 중 네더만 참). */
    public boolean piglinSafe() { return dimensionType().piglinSafe(); }

    @Override
    public boolean biomeIs(Pos pos, String tag) { return services().biomeIs(pos.x(), pos.y(), pos.z(), tag); }

    @Override
    public boolean ultraWarm() { return services().ultraWarm(); }

    @Override
    public boolean hasSkyLight() { return services().hasSkyLight(); }

    @Override
    public boolean withinBorder(Pos pos) { return services().withinBorder(pos.x(), pos.z()); }

    public boolean withinBorder(int x, int z) { return services().withinBorder(x, z); }

    /** 보더 {minX, minZ, maxX, maxZ}. */
    public double[] borderBounds() { return services().borderBounds(); }

    /** WorldBorder.clampToBounds(x, y, z): BlockPos.containing(clamp(x, minX, maxX − 1), y, clamp(z, minZ, maxZ − 1)). */
    public Pos clampToBorder(double x, double y, double z) {
        double[] b = borderBounds();
        return new Pos(Entity.floor(Math.clamp(x, b[0], b[2] - 1.0)), Entity.floor(y), Entity.floor(Math.clamp(z, b[1], b[3] - 1.0)));
    }

    public Pos clampToBorder(Pos p) { return clampToBorder(p.x(), p.y(), p.z()); }

    /** 다른 차원 레벨 보더의 clampToBounds(지옥문: 목적 레벨 보더로 출구를 자른다). 그 차원이 이 레벨이면 clampToBorder 와 같다. */
    public Pos clampToBorderOf(String dimension, double x, double y, double z) {
        double[] b = dimension.equals(dimension()) ? borderBounds() : services().borderBoundsOf(dimension);
        return new Pos(Entity.floor(Math.clamp(x, b[0], b[2] - 1.0)), Entity.floor(y), Entity.floor(Math.clamp(z, b[1], b[3] - 1.0)));
    }

    @Override
    public void setBlockEntity(BlockEntity be) {
        Pos pos = be.pos();
        if (isOutsideBuildHeight(pos.y())) return;
        ChunkColumn col = chunkAt(pos);
        if (col == null || !info.hasBlockEntity(getBlockState(pos))) return;   // 바닐라는 경고 후 무시
        addBlockEntity(col, be);
    }

    @Override
    public boolean isHandlingTick() { return handlingTick; }

    /**
     * EntityGetter.hasNearbyAlivePlayer: 관전자 아니고 살아 있는 플레이어가 (x,y,z) 에서 range 안(< range²). 바닐라처럼 range 가 음수면 거리와
     * 무관하게 그런 플레이어가 하나라도 있으면 참이다(스포너 RequiredPlayerRange 음수 = 늘 켜짐).
     */
    public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
        boolean[] found = {false};
        boolean anyDistance = range < 0.0;
        java.util.function.Consumer<Player> scan = p -> {
            if (found[0] || p.isDisconnected() || p.isSpectator() || !p.isAlive()) return;
            if (anyDistance) {
                found[0] = true;
                return;
            }
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            if (dx * dx + dy * dy + dz * dz < range * range) found[0] = true;
        };
        if (anyDistance) {
            services().forEachPlayer(scan);
        } else {
            double r = range + NEAR_MARGIN;
            forEachPlayerNear(x - r, z - r, x + r, z + r, scan);
        }
        return found[0];
    }

    @Override
    public void sendBlockUpdated(Pos pos) {
        if (hasChunkAt(pos)) markChanged(pos);
    }

    @Override
    public Player playerByUuid(java.util.UUID uuid) {
        Player[] found = new Player[1];
        services().forEachPlayer(p -> { if (!p.isDisconnected() && uuid.equals(p.uuid())) found[0] = p; });
        return found[0];
    }

    @Override
    public boolean commandBlockEnabled() { return services().commandBlockEnabled(); }

    @Override
    public void runBlockCommand(BlockCommandSink sink, String command, java.util.function.IntConsumer done) {
        services().runBlockCommand(sink, command, done);
    }

    @Override
    public void resolveLecternBook(Pos pos, ItemStack book, java.util.function.BiConsumer<BlockLevel, ItemStack> apply) {
        services().resolveLecternBook(this, pos.x(), pos.y(), pos.z(), book, apply);
    }

    @Override
    public void resolveSignLines(Pos pos, java.util.List<org.domi.furnace.api.text.Text> lines,
                                 java.util.function.BiConsumer<BlockLevel, java.util.List<org.domi.furnace.api.text.Text>> apply) {
        services().resolveSignLines(this, pos.x(), pos.y(), pos.z(), lines, apply);
    }

    @Override
    public void runSignCommand(BlockActor player, Pos pos, String command) { runSignCommand(player, pos, true, command); }

    @Override
    public boolean runSignCommand(BlockActor player, Pos pos, boolean front, String command) {
        Player p = player != null && player.handle() instanceof Player pl ? pl : null;
        return services().runSignCommand(p, pos.x(), pos.y(), pos.z(), front, command);
    }

    @Override
    public void blockEntityChanged(Pos pos) {
        if (hasChunkAt(pos)) markDirty(pos.x() >> 4, pos.z() >> 4);
    }

    @Override
    public Object getBlockEntity(Pos pos) {
        ChunkColumn c = chunkAt(pos);
        return c == null ? null : blockEntity(c, pos);
    }

    @Override
    public void removeBlockEntity(Pos pos) {
        ChunkColumn c = chunkAt(pos);
        if (c != null) removeBlockEntity(c, pos);
    }

    @Override
    public void sendActionBar(BlockActor actor, org.domi.furnace.api.text.Text text) {
        if (actor != null && actor.handle() instanceof Player p) p.sendSystemMessage(text, true);
    }

    /** Block.popResource(level, pos, stack): 칸 가운데 ±0.25(높이는 아이템 반만큼 내림)에 기본 속도로. 흩뿌림 난수는 게임룰과 무관하게 먼저 뽑는다. */
    @Override
    public void popResource(Pos pos, ItemStack stack) {
        RandomSource r = random();
        double half = EntityType_ITEM_HEIGHT / 2.0;
        double x = pos.x() + 0.5 + nextDouble(r, -0.25, 0.25);
        double y = pos.y() + 0.5 + nextDouble(r, -0.25, 0.25) - half;
        double z = pos.z() + 0.5 + nextDouble(r, -0.25, 0.25);
        if (stack.isEmpty() || !gameRule("doTileDrops")) return;
        ItemEntity e = new ItemEntity(this, nextEntityId(), x, y, z, stack);
        e.setDefaultPickUpDelay();
        addFreshEntity(e);
    }

    /** Block.popResourceFromFace: 그 면 바깥(아이템 반 폭)에서 면 쪽 0.1(위쪽이면 +0.1) 속도로. */
    @Override
    public void popResourceFromFace(Pos pos, int dir, ItemStack stack) {
        RandomSource r = random();
        int i = Dir.DX[dir], j = Dir.DY[dir], k = Dir.DZ[dir];
        double hw = EntityType_ITEM_WIDTH / 2.0, hh = EntityType_ITEM_HEIGHT / 2.0;
        double x = pos.x() + 0.5 + (i == 0 ? nextDouble(r, -0.25, 0.25) : i * (0.5 + hw));
        double y = pos.y() + 0.5 + (j == 0 ? nextDouble(r, -0.25, 0.25) : j * (0.5 + hh)) - hh;
        double z = pos.z() + 0.5 + (k == 0 ? nextDouble(r, -0.25, 0.25) : k * (0.5 + hw));
        double vx = i == 0 ? nextDouble(r, -0.1, 0.1) : i * 0.1;
        double vy = j == 0 ? nextDouble(r, 0.0, 0.1) : j * 0.1 + 0.1;
        double vz = k == 0 ? nextDouble(r, -0.1, 0.1) : k * 0.1;
        if (stack.isEmpty() || !gameRule("doTileDrops")) return;
        ItemEntity e = new ItemEntity(this, nextEntityId(), x, y, z, stack, vx, vy, vz);
        e.setDefaultPickUpDelay();
        addFreshEntity(e);
    }

    private static final double EntityType_ITEM_WIDTH = 0.25F, EntityType_ITEM_HEIGHT = 0.25F;

    @Override
    public void dropContents(Pos pos, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            dropItemStack(pos.x(), pos.y(), pos.z(), items[i] == null ? ItemStack.EMPTY : items[i]);
            items[i] = ItemStack.EMPTY;
        }
    }

    @Override
    public void unpackContainerLoot(Object container) { services().unpackContainerLoot(container); }

    @Override
    public Object recipes() { return services().recipes(); }

    /**
     * Containers.dropItemStack(level, x, y, z, stack): 칸 안 임의 위치(아이템 폭만큼 안쪽)에서 10~30 개씩 나눠, 속도는 삼각 분포
     * (0, 0.2, 0) ± 0.11485.
     */
    @Override
    public void spawnItemEntity(double x, double y, double z, ItemStack stack) {
        ItemEntity e = new ItemEntity(this, nextEntityId(), x, y, z, stack);
        e.setDefaultPickUpDelay();
        addFreshEntity(e);
    }

    @Override
    public void dropItemStack(double x, double y, double z, ItemStack stack) {
        RandomSource r = random();
        double w = EntityType_ITEM_WIDTH, e = 1.0 - w, f = w / 2.0;
        double gx = Math.floor(x) + r.nextDouble() * e + f;
        double gy = Math.floor(y) + r.nextDouble() * e;
        double gz = Math.floor(z) + r.nextDouble() * e + f;
        while (!stack.isEmpty()) {
            int n = Math.min(stack.count(), r.nextInt(21) + 10);
            ItemStack part = stack.withCount(n);
            stack = stack.withCount(stack.count() - n);
            ItemEntity ie = new ItemEntity(this, nextEntityId(), gx, gy, gz, part);
            ie.setDeltaMovement(r.triangle(0.0, 0.11485000171139836), r.triangle(0.2, 0.11485000171139836), r.triangle(0.0, 0.11485000171139836));
            addFreshEntity(ie);
        }
    }

    private static double nextDouble(RandomSource r, double min, double max) { return min >= max ? min : r.nextDouble() * (max - min) + min; }

    // ------------------------------------------------------------------ 엔티티

    private final EntityStore entities = new EntityStore();
    private static final java.util.concurrent.atomic.AtomicInteger FALLBACK_IDS = new java.util.concurrent.atomic.AtomicInteger(1 << 24);

    public EntityStore entities() { return entities; }

    /** 지금 스레드가 이 레벨을 가진 스레드인가(region 레벨은 region 틱 스레드, 시험용 레벨은 늘 그렇다고 본다). */
    public boolean isOwnerThread() { return true; }

    /** 엔티티 사망 전리품 평가(services 위임). */
    public java.util.List<ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z,
            String damageType, Player attacker, Player lastPlayer, long seed) {
        return services().entityDrops(lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
    }

    /** 낚시 전리품 평가(services 위임). */
    public java.util.List<ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z) {
        return services().giftLoot(lootTable, self, x, y, z);
    }

    /** 서명된 책 쪽 글 풀기(services 위임). */
    public java.util.concurrent.CompletableFuture<org.domi.furnace.api.text.Text> resolveBookText(Object player, org.domi.furnace.api.text.Text text) {
        return services().resolveBookText(player, text);
    }

    /** 고고학 전리품 평가(services 위임). */
    public java.util.List<ItemStack> chestLoot(String lootTable, long seed, double x, double y, double z, Object player, float luck) {
        return services().chestLoot(lootTable, seed, x, y, z, player, luck);
    }

    public java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool, float luck) {
        return services().fishingLoot(hook, x, y, z, tool, luck);
    }

    /** 일반 전리품 평가(services 위임): origin·this_entity·luck, 난수는 seed → random → 표 순. */
    public java.util.List<ItemStack> lootItems(String lootTable, double[] origin, org.domi.furnace.server.loot.LootEntity thisEntity, float luck,
            long seed, RandomSource random) {
        return services().lootItems(lootTable, origin, thisEntity, luck, seed, random);
    }

    /** 데이터 팩 전리품 데이터(인챈트 정의·마법 부여 제공자). 데이터 팩이 없으면 null. */
    public org.domi.furnace.server.loot.LootData lootData() { return services().loot(); }

    /** 탐험 지도 지원(없으면 null). */
    public org.domi.furnace.server.map.ExplorerMaps.Backend explorerMaps() { return services().explorerMaps(); }

    /** ExperienceOrb.award(level, 위치, amount). */
    @Override
    public void awardExperience(double x, double y, double z, int amount) {
        org.domi.furnace.server.entity.ExperienceOrb.award(this, x, y, z, amount);
    }

    public long worldSeed() { return services().seed(); }

    public int[] sharedSpawnPos() { return services().sharedSpawnPos(); }

    public boolean spawnMonstersAllowed() { return services().spawnMonsters(); }

    public boolean spawnAnimalsAllowed() { return services().spawnAnimals(); }

    public boolean spawnNpcsAllowed() { return services().spawnNpcs(); }

    /**
     * ServerLevel.shouldDiscardEntity: 동물 스폰이 꺼졌으면 동물·물 동물을 지우고, 그 밖에는 Npc 스폰이 꺼졌을 때 Npc(주민·떠돌이 상인)를 지운다.
     * 둘 다 켜져 있으면(기본) 호출하지 않는다.
     */
    static boolean shouldDiscardEntity(Entity e, boolean animalsOk, boolean npcsOk) {
        if (animalsOk || !(e instanceof org.domi.furnace.server.entity.Animal || e instanceof org.domi.furnace.server.entity.WaterAnimal)) {
            return !npcsOk && (e instanceof org.domi.furnace.server.entity.Villager || e instanceof org.domi.furnace.server.entity.WanderingTrader);
        }
        return true;
    }

    /** Level.getNearestPlayer(x, y, z, 거리, ignoreCreativeAndSpectators): 관전자(옵션으로 크리에이티브도) 제외 가장 가까운 플레이어. 거리가 음수면 제한 없음. */
    public Player getNearestPlayer(double x, double y, double z, double dist, boolean ignoreCreative) {
        Player[] best = new Player[1];
        double[] bestD = new double[1];
        java.util.function.Consumer<Player> scan = p -> {
            if (p.isDisconnected() || p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            if (ignoreCreative && p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE) return;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        };
        nearestScan(x, z, dist, best, bestD, scan);
        return best[0];
    }

    /** Level.getNearestPlayer(entity, 거리): 관전자가 아닌 가장 가까운 플레이어(거리 안에서), 없으면 null. */
    public Player getNearestPlayer(Entity from, double dist) {
        Player[] best = new Player[1];
        double[] bestD = new double[1];
        double fx = from.x(), fy = from.y(), fz = from.z();
        java.util.function.Consumer<Player> scan = p -> {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR || p.isDisconnected()) return;
            double dx = p.x() - fx, dy = p.y() - fy, dz = p.z() - fz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        };
        nearestScan(fx, fz, dist, best, bestD, scan);
        return best[0];
    }

    /** 근접 후보의 상자 여유(블록) — 거리 비교의 반올림·플레이어 몸 반폭을 덮는다. 격자 칸이 128 이라 거의 공짜다. */
    static final double NEAR_MARGIN = 2.0;

    /**
     * 근접 조회 후보: [x0,x1]×[z0,z1] 와 겹칠 수 있는 플레이어를 forEachPlayer 와 같은 순서로(상위 집합 — 부르는 쪽이 원래 판정을
     * 다시 한다). 색인이 없는 레벨(시험 등)은 forEachPlayer 그대로.
     */
    protected final void forEachPlayerNear(double x0, double z0, double x1, double z1, java.util.function.Consumer<Player> c) {
        var idx = services().playerIndex();
        if (idx == null) services().forEachPlayer(c);
        else idx.forEachNear(x0, z0, x1, z1, c);
    }

    /**
     * "가장 가까운 플레이어"(엄격한 &lt; 로 먼저 찾은 쪽) 훑기를 전체 훑기와 같은 결과로 줄인다. scan 은 bestD 보다 가까울 때만 고른다.
     * 거리 제한이 있으면 그 반경 상자 후보만. 없으면(음수) 먼저 반경 128 상자 후보만 보고, 그 안(≤ 128²)에서 찾았으면 그것이 전체의
     * 답이다 — 상자 밖 플레이어는 모두 128² 보다 멀어 이길 수도 비길 수도 없다. 못 찾았으면 전체를 다시 훑는다.
     */
    private void nearestScan(double x, double z, double dist, Player[] best, double[] bestD, java.util.function.Consumer<Player> scan) {
        if (!(dist < 0.0)) {   // NaN 도 여기(옛 코드처럼 bestD = NaN 이라 아무도 못 고른다 — 상자도 NaN 이라 전부 훑는다)
            bestD[0] = dist * dist;
            double r = dist + NEAR_MARGIN;
            forEachPlayerNear(x - r, z - r, x + r, z + r, scan);
            return;
        }
        bestD[0] = Double.MAX_VALUE;
        var idx = services().playerIndex();
        if (idx != null && idx.gridActive()) {
            double r0 = org.domi.furnace.server.world.PlayerRoster.NEAREST_FIRST_RADIUS;
            double r = r0 + NEAR_MARGIN;
            forEachPlayerNear(x - r, z - r, x + r, z + r, scan);
            if (best[0] != null && bestD[0] <= r0 * r0) return;
            best[0] = null;
            bestD[0] = Double.MAX_VALUE;
        }
        services().forEachPlayer(scan);
    }

    /** Level.getNearestEntity(class, 조건, 출처, x, y, z, 상자): 조건에 맞는 것 중 (x,y,z)에 가장 가까운 엔티티(같은 거리면 먼저 찾은 것). */
    public <T extends org.domi.furnace.server.entity.LivingEntity> T getNearestEntity(Class<T> cls,
            org.domi.furnace.server.entity.ai.targeting.TargetingConditions conditions, org.domi.furnace.server.entity.Mob source, double x, double y, double z, AABB box) {
        double best = -1.0;
        T result = null;
        for (T e : getEntitiesOfClass(cls, box, t -> true)) {
            if (!conditions.test(source, e)) continue;
            double dx = e.x() - x, dy = e.y() - y, dz = e.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (best == -1.0 || d < best) {
                best = d;
                result = e;
            }
        }
        return result;
    }

    @Override
    public String biomeAt(int x, int y, int z) { return services().biomeAt(x, y, z); }

    /** NaturalSpawner.getRoughBiome(pos, chunk): 확대 지터 없는 quart 바이옴. */
    public String roughBiomeAt(int x, int y, int z) { return services().roughBiomeAt(x, y, z); }

    // ------------------------------------------------------------------ 지형 생성 기능(묘목·뼛가루)

    /** 이 레벨이 쓰는 feature 표. 기본은 내장 1.21.1 데이터(노이즈 생성기가 있는 월드는 RegionLevel 이 그 표를 준다). */
    protected org.domi.furnace.server.world.gen.feature.BiomeFeatureData featureData() {
        return org.domi.furnace.server.world.LiveFeatures.fallbackData();
    }

    /** 바이옴 확대 지터 시드(feature 안의 바이옴 조회용). */
    protected long featureSeed() { return 0L; }

    @Override
    public LevelFeature configuredFeature(String id) { return org.domi.furnace.server.world.LiveFeatures.configured(featureData(), id); }

    @Override
    public LevelFeature placedFeature(String id) { return org.domi.furnace.server.world.LiveFeatures.placed(featureData(), id); }

    @Override
    public LevelFeature biomeFlowerFeature(String biome) { return org.domi.furnace.server.world.LiveFeatures.flower(featureData(), biome); }

    @Override
    public boolean placeFeature(LevelFeature feature, Pos pos, RandomSource random) {
        return feature instanceof org.domi.furnace.server.world.LiveFeatures.Handle h
                && org.domi.furnace.server.world.LiveFeatures.place(this, h, pos, random, featureSeed());
    }

    public void forEachPlayer(java.util.function.Consumer<Player> c) { services().forEachPlayer(c); }

    /** Level.isInWorldBounds: 높이 안이고 -30000000 <= x,z < 30000000(바닐라 isInWorldBoundsHorizontal — 음수 쪽 경계 칸은 안). */
    public boolean isInWorldBounds(Pos pos) {
        return pos.y() >= minBuildHeight() && pos.y() < maxBuildHeight()
                && pos.x() >= -30000000 && pos.z() >= -30000000 && pos.x() < 30000000 && pos.z() < 30000000;
    }

    @Override
    public void spawnGolem(boolean iron, Pos bottom) {
        org.domi.furnace.server.entity.Entity golem;
        if (iron) {
            var g = new org.domi.furnace.server.entity.IronGolem(this, nextEntityId());
            g.setPlayerCreated(true);
            golem = g;
        } else {
            golem = new org.domi.furnace.server.entity.SnowGolem(this, nextEntityId());
        }
        golem.moveTo(bottom.x() + 0.5, bottom.y() + 0.05, bottom.z() + 0.5, 0.0F, 0.0F);
        addFreshEntity(golem.spawnReason(iron ? "BUILD_IRONGOLEM" : "BUILD_SNOWMAN"));
        org.domi.furnace.server.entity.AdvTriggers.summonedNear(this, golem, 5.0);   // 바닐라: 골렘 상자 5칸 안 플레이어에게 summoned_entity
    }

    @Override
    public void spawnBabyTurtle(Pos pos, int index) {
        var turtle = new org.domi.furnace.server.entity.Turtle(this, nextEntityId());
        turtle.setAge(-24000);
        turtle.setHomePos(pos);
        turtle.moveTo(pos.x() + 0.3 * index + 0.2, pos.y(), pos.z() + 0.3, 0.0F, 0.0F);
        addFreshEntity(turtle.spawnReason("EGG"));
    }

    /** FrogspawnBlock.spawnTadpoles 의 한 마리: 위치는 칸 + 오프셋, y 는 칸 y − 0.5. persistenceRequired 를 건다. */
    @Override
    public void spawnTadpole(Pos pos, double xOffset, double zOffset, float yRot) {
        var tadpole = new org.domi.furnace.server.entity.Tadpole(this, nextEntityId());
        tadpole.moveTo(pos.x() + xOffset, pos.y() - 0.5, pos.z() + zOffset, yRot, 0.0F);
        tadpole.setPersistenceRequired();
        addFreshEntity(tadpole);
    }

    /** SnifferEggBlock.hatchSniffer: 아기 스니퍼를 칸 가운데(+0.5, +0.5, +0.5)에 만든다. */
    @Override
    public void spawnBabySniffer(Pos pos, float yRot) {
        var sniffer = new org.domi.furnace.server.entity.Sniffer(this, nextEntityId());
        sniffer.setBaby(true);
        sniffer.moveTo(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, yRot, 0.0F);
        addFreshEntity(sniffer);
    }

    @Override
    public void angerNearbyPiglins(BlockActor player, boolean requireLineOfSight) {
        if (player != null && player.handle() instanceof org.domi.furnace.server.entity.Player p) {
            org.domi.furnace.server.entity.PiglinAi.angerNearbyPiglins(this, p, requireLineOfSight);
        }
    }

    @Override
    public void spawnSilverfishAt(Pos pos) {
        var sf = new org.domi.furnace.server.entity.Silverfish(this, nextEntityId());
        sf.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0.0F, 0.0F);
        addFreshEntity(sf.spawnReason("SILVERFISH_BLOCK"));
        sf.spawnAnim();
    }

    @Override
    public Object spawnFallingBlock(Pos pos, int state) { return org.domi.furnace.server.entity.FallingBlockEntity.fall(this, pos, state); }

    @Override
    public void spawnWither(Pos bottomCenter, boolean axisX) {
        var wither = new org.domi.furnace.server.entity.WitherBoss(this, nextEntityId());
        float yaw = axisX ? 0.0F : 90.0F;
        wither.moveTo(bottomCenter.x() + 0.5, bottomCenter.y() + 0.55, bottomCenter.z() + 0.5, yaw, 0.0F);
        wither.setYBodyRot(yaw);
        wither.makeInvulnerable();
        org.domi.furnace.server.entity.AdvTriggers.summonedNear(this, wither, 50.0);   // 바닐라: 소환 전에 상자 50칸 안 플레이어에게 summoned_entity
        addFreshEntity(wither.spawnReason("BUILD_WITHER"));
    }

    /** TntBlock.explode: 점화된 TNT 엔티티를 칸 가운데 윗부분에 만든다(owner 는 점화한 생물). */
    @Override
    public void spawnPrimedTnt(Pos pos, BlockActor igniter, int fuse, org.domi.furnace.api.event.cause.CauseChain why) {
        spawnPrimedTntOwned(pos, igniter != null && igniter.handle() instanceof Player p ? p : null, fuse, why);
    }

    /** 점화한 생물(Targetable — 투사체를 쏜 몹 포함)을 바로 주인으로. */
    @Override
    public void spawnPrimedTntOwned(Pos pos, org.domi.furnace.server.entity.Targetable owner, int fuse, org.domi.furnace.api.event.cause.CauseChain why) {
        var tnt = new org.domi.furnace.server.entity.PrimedTnt(this, nextEntityId(), pos.x() + 0.5, pos.y(), pos.z() + 0.5, owner);
        if (why != null) tnt.setOrigin(why);   // 넣기 전에 — EntitySpawnEvent 도 원인을 본다
        if (fuse >= 0) tnt.setFuse(fuse);
        else if (fuse == -2) tnt.setFuse(random().nextInt(tnt.getFuse() / 4) + tnt.getFuse() / 8);   // 폭발에 휘말린 TNT: 생성 난수 다음에 도화선 난수
        addFreshEntity(tnt);
    }

    /** Level.explode(source, x, y, z, radius, fire, interaction): 폭발 계산 → 소리·패킷·블록 부수기. 만든 Explosion 을 돌려준다. */
    public Explosion explode(Entity source, double x, double y, double z, float radius, boolean fire, Explosion.BlockInteraction interaction) {
        Explosion e = new Explosion(this, source, x, y, z, radius, fire, interaction);
        e.explode();
        e.finalizeExplosion(true);
        return e;
    }

    /** explode 에 피해 원인 엔티티(causing)를 따로 준 것: 불화살로 터진 TNT 수레처럼 폭발 엔티티와 원인이 다를 때. */
    public Explosion explode(Entity source, org.domi.furnace.server.entity.Targetable causing, double x, double y, double z, float radius, boolean fire, Explosion.BlockInteraction interaction) {
        Explosion e = new Explosion(this, source, x, y, z, radius, fire, interaction).withCausing(causing);
        e.explode();
        e.finalizeExplosion(true);
        return e;
    }

    /**
     * 리스폰 지점 폭발(침대·정박기의 level.explode(null, badRespawnPointExplosion(center), 계산기, center, 5.0, 불, BLOCK)).
     * overridePos 가 null 이 아니면 그 칸의 폭발 저항을 resistance 로(정박기 옆 물).
     */
    public Explosion explodeBadRespawnPoint(double x, double y, double z, Pos overridePos, float resistance, int explodedState) {
        return explodeBadRespawnPoint(x, y, z, overridePos, resistance, explodedState, null);
    }

    /** causedBy: 블록을 터뜨린 쪽(쓴 플레이어 — 원인 사슬에만 쓰고 피해·게임 이벤트의 원인은 바닐라처럼 없음). */
    public Explosion explodeBadRespawnPoint(double x, double y, double z, Pos overridePos, float resistance, int explodedState,
                                            org.domi.furnace.api.event.cause.CauseChain causedBy) {
        Explosion e = new Explosion(this, null, x, y, z, 5.0F, true, destroyType("BLOCK")).badRespawnPoint(overridePos, resistance).explodedBlock(explodedState)
                .causedBy(causedBy);
        e.explode();
        e.finalizeExplosion(true);
        return e;
    }

    /** 계산기·입자·소리를 지정한 폭발(윈드 차지). */
    public Explosion explode(Entity source, double x, double y, double z, float radius, Explosion.Calculator calc, int small, int large, String sound) {
        Explosion e = new Explosion(this, source, x, y, z, radius, false, Explosion.BlockInteraction.TRIGGER_BLOCK).withCalculator(calc, small, large, sound);
        e.explode();
        e.finalizeExplosion(true);
        return e;
    }

    /** ExplosionInteraction → BlockInteraction (게임 규칙 반영). kind: NONE, BLOCK, MOB, TNT, TRIGGER. */
    public Explosion.BlockInteraction destroyType(String kind) {
        return switch (kind) {
            case "NONE" -> Explosion.BlockInteraction.KEEP;
            case "BLOCK" -> gameRule("blockExplosionDropDecay") ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
            case "MOB" -> gameRule("mobGriefing")
                    ? (gameRule("mobExplosionDropDecay") ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY)
                    : Explosion.BlockInteraction.KEEP;
            case "TNT" -> gameRule("tntExplosionDropDecay") ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
            default -> Explosion.BlockInteraction.TRIGGER_BLOCK;
        };
    }

    /** UUID 로 엔티티·플레이어를 찾는다(없으면 null). 플레이어는 이 region 이 보는 범위 밖이면 못 찾는다. */
    public org.domi.furnace.server.entity.Targetable getTargetableByUuid(java.util.UUID uuid) {
        var e = entities.get(uuid);
        if (e instanceof org.domi.furnace.server.entity.Targetable t) return t;
        org.domi.furnace.server.entity.Player[] found = new org.domi.furnace.server.entity.Player[1];
        services().forEachPlayer(p -> { if (uuid.equals(p.uuid())) found[0] = p; });
        return found[0];
    }

    /**
     * Block.pushEntitiesUp: joinUnoptimized(from 충돌, to 충돌, ONLY_SECOND) 을 칸으로 옮긴 모양이 비면 끝. 아니면 그 경계 상자와 겹치는
     * 관전자 아닌 엔티티마다 상자를 1 올려 모양 쪽으로 -1 만큼 내린 충돌 거리 d 를 구해 teleportRelative(0, 1 + d, 0).
     * 엔티티는 모양이 아니라 경계 상자와만 겹치면 된다(바닐라 getEntities(null, shape.bounds())).
     */
    @Override
    public int pushEntitiesUp(int from, int to, Pos pos) {
        Shape s = Shape.joinOnlySecond(B.behavior(from).getCollisionShape(from, this, pos, CollisionContext.EMPTY),
                B.behavior(to).getCollisionShape(to, this, pos, CollisionContext.EMPTY)).move(pos.x(), pos.y(), pos.z());
        if (s.isEmpty()) return to;
        double[] b = s.bounds();
        org.domi.furnace.server.entity.AABB box = new org.domi.furnace.server.entity.AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
        for (Entity e : getEntities(null, box, t -> !t.isSpectator())) {
            double d = s.collide(1, e.boundingBox().move(0.0, 1.0, 0.0), -1.0);
            e.teleportRelative(0.0, 1.0 + d, 0.0);
        }
        forEachPlayerIn(box, p -> {
            if (p.isSpectator()) return;
            double d = s.collide(1, p.boundingBox().move(0.0, 1.0, 0.0), -1.0);
            p.teleportRelative(0.0, 1.0 + d, 0.0);
        });
        return to;
    }

    /** 상자와 겹치는 플레이어들(Level.getEntitiesOfClass(Player.class, box) 의 플레이어 쪽). */
    @Override
    public void forEachPlayerIn(org.domi.furnace.server.entity.AABB box, java.util.function.Consumer<Player> c) {
        forEachPlayerNear(box.minX() - NEAR_MARGIN, box.minZ() - NEAR_MARGIN, box.maxX() + NEAR_MARGIN, box.maxZ() + NEAR_MARGIN, p -> {
            if (!p.isDisconnected() && p.boundingBox().intersects(box)) c.accept(p);
        });
    }

    /** Level.getNearestPlayer(조건, 출처, x, y, z): 조건에 맞는 플레이어 중 (x,y,z)에 가장 가까운 것. */
    public Player getNearestPlayer(org.domi.furnace.server.entity.ai.targeting.TargetingConditions conditions, org.domi.furnace.server.entity.Mob source,
            double x, double y, double z) {
        Player[] best = new Player[1];
        double[] bestD = {-1.0};
        // 사거리 밖(조건 검사가 어차피 거짓)인 플레이어는 조건을 부르지 않고 건너뛴다 — 검사 순서의 조건들은 모두 부수 효과 없는 판정이다(X1).
        final double far = source == null ? Double.POSITIVE_INFINITY : conditions.farLimitSqr();
        java.util.function.Consumer<Player> scan = p -> {
            if (p.isDisconnected()) return;
            if (far != Double.POSITIVE_INFINITY) {
                double fx = source.x() - p.x(), fy = source.y() - p.y(), fz = source.z() - p.z();
                if (fx * fx + fy * fy + fz * fz > far) return;
            }
            if (!conditions.test(source, p)) return;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (bestD[0] == -1.0 || d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        };
        if (far != Double.POSITIVE_INFINITY && far >= 0.0) {   // 사거리 밖은 위에서 어차피 건너뛴다 — 그 상자 후보만
            double r = Math.sqrt(far) + NEAR_MARGIN, sx = source.x(), sz = source.z();
            forEachPlayerNear(sx - r, sz - r, sx + r, sz + r, scan);
        } else {
            services().forEachPlayer(scan);
        }
        return best[0];
    }

    /**
     * EntityType.spawn(egg): create → moveTo(무작위 방향) → 머리·몸통 방향 → finalizeSpawn → 추가 → 주변 소리.
     * snapToGround 면 칸 위에서 충돌 모양에 닿을 때까지 내려놓는다(getYOffset).
     */
    @Override
    public boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown) {
        return spawnMobFromEgg(entityType, pos, snapToGround, extendDown, org.domi.furnace.server.entity.spawn.SpawnReason.SPAWN_EGG);
    }

    @Override
    public boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason) {
        return spawnMob(entityType, pos, snapToGround, extendDown, reason) != null;
    }

    /** EntityType.spawn(level, pos, reason, snapToGround, extendDown) — 만든 몹(몹이 아니면 null). */
    public org.domi.furnace.server.entity.Mob spawnMob(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason) {
        return spawnMob(entityType, pos, snapToGround, extendDown, reason,
                reason == org.domi.furnace.server.entity.spawn.SpawnReason.DISPENSER ? "DISPENSE_EGG" : "SPAWNER_EGG");
    }

    /** pluginReason: 플러그인 CreatureSpawnEvent 의 원인 이름(바닐라 동작에는 영향 없음). */
    public org.domi.furnace.server.entity.Mob spawnMob(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason, String pluginReason) {
        return spawnEntityFromStack(entityType, pos, snapToGround, extendDown, reason, pluginReason, null, null) instanceof
                org.domi.furnace.server.entity.Mob mob ? mob : null;
    }

    @Override
    public boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason, ItemStack stack, BlockActor player) {
        return spawnEntityFromStack(entityType, pos, snapToGround, extendDown, reason,
                reason == org.domi.furnace.server.entity.spawn.SpawnReason.DISPENSER ? "DISPENSE_EGG" : "SPAWNER_EGG", stack, player) != null;
    }

    /** EntityType.getYOffset: 칸 위에서 아래로 떨어뜨려 바닥에 맞출 높이(extendDown 이면 한 칸 아래까지). 충돌은 getCollisions(null, 칸) — 단단한 엔티티 몸 포함. */
    public double snapOffset(Entity e, Pos pos, boolean extendDown) {
        e.setPos(pos.x() + 0.5, pos.y() + 1, pos.z() + 0.5);
        AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
        if (extendDown) box = box.expandTowards(0.0, -1.0, 0.0);
        return 1.0 + Entity.collideWithShapes(0.0, extendDown ? -2.0 : -1.0, 0.0, e.boundingBox(), collisions(null, box))[1];
    }

    /**
     * EntityType.spawn(level, stack, player, pos, reason, snapToGround, extendDown): create → 바닥에 맞춤 → moveTo(무작위 방향) → (몹이면) 머리·몸통 방향·finalizeSpawn →
     * (몹이면) 주변 소리 → 아이템 설정(이름·entity_data, createDefaultStackConfig) → 추가. stack 이 없으면 몹만 만든다(몹이 아니면 null).
     * 알의 entity_data 가 몹이 아닌 종류(배·수레 등)를 가리키면 그것도 만든다.
     */
    public Entity spawnEntityFromStack(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason, String pluginReason, ItemStack stack, BlockActor player) {
        Entity e = org.domi.furnace.server.entity.Entities.create(this, entityType);
        if (e == null) return null;
        org.domi.furnace.server.entity.Mob mob = e instanceof org.domi.furnace.server.entity.Mob m ? m : null;
        if (mob == null && stack == null) return null;
        double d0 = snapToGround ? snapOffset(e, pos, extendDown) : 0.0;
        e.moveTo(pos.x() + 0.5, pos.y() + d0, pos.z() + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(random.nextFloat() * 360.0F), 0.0F);
        if (mob != null) {
            mob.setYHeadRot(mob.yRot());
            mob.setYBodyRot(mob.yRot());
            if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(reason);
            if (mob instanceof org.domi.furnace.server.entity.Raider r) r.setFinalizeReason(reason);   // 알·발사기 마녀도 습격에 참여
            mob.finalizeSpawnGroup(random, getCurrentDifficultyAt(mob.blockPosition()), null);   // 무리 데이터 경로(AgeableMob·말 능력치)
        }
        if (mob != null) mob.makeSound(mob.ambientSoundForSpawn());   // 바닐라 create: finalizeSpawn → 주변 소리 → 이름·entity_data
        org.domi.furnace.server.entity.EntityStackConfig.apply(this, stack, player, e);
        addFreshEntityWithPassengers(mob != null ? mob.spawnReason(pluginReason) : e);
        return e;
    }

    /**
     * MobBucketItem.spawn → EntityType.spawn(level, 양동이, null, pos, BUCKET, true, false): create → 칸 위에서 바닥에 맞춰 내림 → moveTo(무작위 방향) →
     * 머리·몸통 방향 → finalizeSpawn(BUCKET) → 주변 소리 → 이름(custom_name) → 추가 → loadFromBucketTag → setFromBucket(true).
     * 이름과 entity_data 는 createDefaultStackConfig 가 입힌다(EntityStackConfig).
     */
    public org.domi.furnace.server.entity.Mob spawnMobFromBucket(String entityType, ItemStack bucket, Pos pos) {
        Entity e = org.domi.furnace.server.entity.Entities.create(this, entityType);
        if (!(e instanceof org.domi.furnace.server.entity.Mob mob)) return null;
        mob.setPos(pos.x() + 0.5, pos.y() + 1, pos.z() + 0.5);
        AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
        double d0 = 1.0 + Entity.collideWithShapes(0.0, -1.0, 0.0, mob.boundingBox(), collisions(null, box))[1];   // getYOffset 과 같다
        mob.moveTo(pos.x() + 0.5, pos.y() + d0, pos.z() + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(random.nextFloat() * 360.0F), 0.0F);
        mob.setYHeadRot(mob.yRot());
        mob.setYBodyRot(mob.yRot());
        mob.finalizeSpawnGroup(random, getCurrentDifficultyAt(mob.blockPosition()), null, org.domi.furnace.server.entity.spawn.SpawnReason.BUCKET);
        mob.makeSound(mob.ambientSoundForSpawn());
        org.domi.furnace.server.entity.EntityStackConfig.apply(this, bucket, null, mob);
        addFreshEntityWithPassengers(mob);
        if (mob instanceof org.domi.furnace.server.entity.Bucketable b) {
            b.loadFromBucketTag(org.domi.furnace.server.entity.Bucketable.bucketTag(bucket));
            b.setFromBucket(true);
        }
        return mob;
    }

    // ------------------------------------------------------------------ 빛

    private org.domi.furnace.server.light.LevelLight lightEngine;

    private final LightWorldImpl lightWorld = new LightWorldImpl();

    private final class LightWorldImpl implements org.domi.furnace.server.light.LightWorld {
        // 빛 계산은 이웃 청크 칸을 번갈아 읽는다 — 한 계산 동안만 쓰는 4칸 직접 사상 캐시(청크 조회는 해시 맵이라 비싸다).
        private final int[] cacheX = new int[4], cacheZ = new int[4];
        private final ChunkColumn[] cacheCol = new ChunkColumn[4];
        private int depth;   // lightChunk 안에서 runUpdates 를 다시 부르므로 중첩 횟수로 센다

        private ChunkColumn col(int cx, int cz) {
            if (depth == 0) return chunk(cx, cz);
            int i = (cx & 1) | (cz & 1) << 1;
            ChunkColumn c = cacheCol[i];
            if (c != null && cacheX[i] == cx && cacheZ[i] == cz) return c;
            c = chunk(cx, cz);
            if (c != null) {
                cacheX[i] = cx;
                cacheZ[i] = cz;
                cacheCol[i] = c;
            }
            return c;
        }

        public void begin() {
            if (depth++ == 0) {
                java.util.Arrays.fill(cacheCol, null);
                lastBlockedX = Integer.MIN_VALUE;
            }
        }

        public void end() {
            if (--depth == 0) java.util.Arrays.fill(cacheCol, null);
        }

        public org.domi.furnace.server.light.ChunkLight light(int cx, int cz) {
            ChunkColumn c = col(cx, cz);
            return c == null ? null : c.light();
        }

        public int state(int x, int y, int z) {
            if (y < minBuildHeight() || y >= maxBuildHeight()) return 12958;   // void_air
            return col(x >> 4, z >> 4).getBlock(x & 15, y, z & 15);
        }

        public int unloadedState() { return bedrockState; }

        @Override
        public int uniformSectionState(int cx, int sy, int cz) {
            ChunkColumn c = col(cx, cz);
            int i = c == null ? -1 : sy - (c.minY() >> 4);
            return i < 0 || i >= c.sectionCount() ? -1 : c.sectionUniformBlock(i);
        }

        @Override
        public boolean sectionMayEmit(int cx, int sy, int cz) {
            ChunkColumn c = col(cx, cz);
            int i = c == null ? -1 : sy - (c.minY() >> 4);
            return i < 0 || i >= c.sectionCount() || c.sectionMaybeHas(i, org.domi.furnace.server.light.LightTables.EMITS);
        }

        @Override
        public boolean sectionHasOnlyAir(int cx, int sy, int cz) {
            ChunkColumn c = col(cx, cz);
            int i = c == null ? -1 : sy - (c.minY() >> 4);
            if (c == null) return org.domi.furnace.server.light.LightWorld.super.sectionHasOnlyAir(cx, sy, cz);
            return i < 0 || i >= c.sectionCount() || c.sectionOnlyAirExact(i);   // 월드 높이 밖 구역은 없다(void_air) — computeSources 는 묻지 않는다
        }

        public int extraEmission(int x, int y, int z) { return virtualLights().emission(x, y, z); }

        // 같은 섹션 연속 기록은 건너뛴다(전파 중 매 칸 불린다)
        private int lastX = Integer.MIN_VALUE, lastY, lastZ;
        private boolean lastSky;

        public void lightChanged(int x, int y, int z, boolean sky) {
            // SectionPos.aroundAndAtBlockPos: (x±1, y±1, z±1) 상자가 걸친 섹션 전부
            int x0 = (x - 1) >> 4, x1 = (x + 1) >> 4, y0 = (y - 1) >> 4, y1 = (y + 1) >> 4, z0 = (z - 1) >> 4, z1 = (z + 1) >> 4;
            if (x0 == x1 && y0 == y1 && z0 == z1) {
                if (x0 == lastX && y0 == lastY && z0 == lastZ && sky == lastSky) return;
                lastX = x0; lastY = y0; lastZ = z0; lastSky = sky;
            }
            for (int sx = x0; sx <= x1; sx++) for (int sy = y0; sy <= y1; sy++) for (int sz = z0; sz <= z1; sz++) markLightChanged(sx, sy, sz, sky);
        }

        void resetLast() { lastX = Integer.MIN_VALUE; }

        private int lastBlockedX = Integer.MIN_VALUE, lastBlockedZ;

        /**
         * 감소 전파가 안 올라온 청크 U 의 칸에서 멈췄다: U 와, U 를 거쳐 빛이 닿았을 수 있는(그 칸에서 14칸 안) 안 올라온 이웃을 "저장 빛을
         * 믿지 말 것"으로 적는다. 올라온 이웃은 고칠 길이 없다(지금도 그렇다 — 감소가 U 를 건너가지 못한다).
         */
        @Override
        public void decreaseBlocked(int x, int y, int z) {
            if (x == lastBlockedX && z == lastBlockedZ) return;   // 같은 열의 높이만 다른 칸은 같은 표시
            lastBlockedX = x;
            lastBlockedZ = z;
            var stale = lightStale();
            int ux = x >> 4, uz = z >> 4;
            stale.mark(ux, uz);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = ux + dx, nz = uz + dz;
                    int ddx = Math.max(0, Math.max(nx * 16 - x, x - (nx * 16 + 15)));
                    int ddz = Math.max(0, Math.max(nz * 16 - z, z - (nz * 16 + 15)));
                    if (ddx + ddz > 14) continue;   // 그 칸의 빛은 많아야 14 — 14칸 넘게는 못 간다
                    // 올라와 있는 이웃도 표시한다: 내려간 U 를 돌아 들어온 빛은 그 이웃에서도 줄지 않는다 — 다음에 올릴 때 다시 계산해야
                    // 지워진다(옛 경로는 올릴 때마다 다시 계산해 지웠다. PR #304 검토 R1)
                    stale.mark(nx, nz);
                }
            }
        }

        public int minY() { return minBuildHeight(); }

        public int maxY() { return maxBuildHeight(); }
    }
    private final int bedrockState = blocks.defaultState("minecraft:bedrock");

    public org.domi.furnace.server.light.LevelLight lightEngine() {
        if (lightEngine == null) {
            lightEngine = new org.domi.furnace.server.light.LevelLight(lightWorld, hasSkyLight());
            if (asyncLight()) lightEngine.deferSources(true);   // 빛 작업 모드: 원천 높이는 빛 작업이 고친다
        }
        return lightEngine;
    }

    /** 새로 올라온 청크의 빛을 만든다(ThreadedLevelLightEngine.lightChunk). 이미 빛이 있으면 가장자리만 이웃과 맞춘다. */
    public void lightNewChunk(int cx, int cz) {
        ChunkColumn col = chunk(cx, cz);
        if (col == null) return;
        awaitLightIdle();   // 빛 작업 모드: 그 자리 계산은 빛 작업이 쉴 때만(보이는 빛 배열을 그 자리에서 고친다)
        // 내려가 있는 동안 이웃에서 빛을 줄이는 전파가 이 청크에서 막혔다 → 디스크 빛은 낡았을 수 있어 믿지 않고 다시 계산한다
        // 표시 번호를 잡아 두고, 고친 빛이 디스크에 써지면 지운다(World.saveChunkAsync — 그 전에 죽어도 표시가 남는다)
        var staleSet = lightStale();
        boolean stale = staleSet.contains(cx, cz);
        long staleSeq = stale ? staleSet.markSeq(cx, cz) : 0;
        boolean stored = col.lightStored();
        col.lightStored(false);
        if (stored && stale) {
            // 옛 디스크 경로와 똑같이: 고립 계산 + 가장자리 맞추기(이 청크 칸을 처음부터 다시 쓰는 lightChunk 는 이웃 섹션까지 바뀜으로
            // 표시해 빛 갱신 패킷이 옛 경로보다 많아진다)
            col.light(null);
            col.lightPrecomputed(false);
            stored = false;
            precomputeLight(col, cx, cz, bedrockState, hasSkyLight());
        } else if (stored && !col.lightPrecomputed()) {
            precomputeLight(col, cx, cz, bedrockState, hasSkyLight());   // 동기 경로(생성 풀을 안 거침): 하늘빛 원천만 센다
        }
        boolean precomputed = col.lightPrecomputed();
        col.lightPrecomputed(false);
        lightNewChunk0(col, cx, cz, precomputed, stored, staleSeq);
    }

    private void lightNewChunk0(ChunkColumn col, int cx, int cz, boolean precomputed, boolean stored, long staleSeq) {
        initialLightChunk = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        initialLightChanged = false;
        try {
            if (precomputed) {   // 생성 스레드가 청크 안쪽 빛을 이미 계산했다 — 이웃과 가장자리만 맞춘다
                lightEngine().attachPrecomputed(cx, cz);
            } else {
                if (col.light() == null) col.light(new org.domi.furnace.server.light.ChunkLight());
                lightEngine().lightChunk(cx, cz);
            }
        } finally {
            initialLightChunk = Long.MIN_VALUE;
        }
        // 표시 때문에 다시 계산했거나(낡은 디스크 빛) 저장 빛을 가장자리 맞추기가 고쳤으면 디스크도 고치게 저장 대상으로
        if (staleSeq != 0) col.lightStaleSeq(staleSeq);
        if ((staleSeq != 0 || stored && initialLightChanged) && col.markLightDirty()) markDirty(cx, cz);
        reapplyVirtualLights(cx, cz);
    }

    private final org.domi.furnace.server.world.LightStaleChunks ownLightStale = org.domi.furnace.server.world.LightStaleChunks.detached();

    /** 저장 빛을 믿으면 안 되는 청크 표(region 레벨은 월드가 가진 하나 — 디스크에 남는다). */
    protected org.domi.furnace.server.world.LightStaleChunks lightStale() { return ownLightStale; }

    /** 다시 올라온 청크의 가짜 광원을 빛 엔진에 다시 건다(생성 스레드의 미리 계산은 그것을 모른다). 이웃으로 번지는 빛은 방송한다. */
    private void reapplyVirtualLights(int cx, int cz) {
        var vl = virtualLights();
        if (vl.isEmpty()) return;
        long[] src = vl.sourcesIn(cx, cz);
        if (src.length == 0) return;
        initialLightChunk = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        try {
            for (long p : src) {
                lightEngine().emissionChanged(org.domi.furnace.server.light.LightEngine.px(p), org.domi.furnace.server.light.LightEngine.py(p),
                        org.domi.furnace.server.light.LightEngine.pz(p));
            }
            lightEngine().runUpdates();
            // 내려가 있는 동안 지운 광원: 빛을 다 맞춘 지금 지운다 — 감소 전파가 로드된 이웃 청크에 남은 낡은 빛까지 정리한다
            long[] retired = vl.retiredIn(cx, cz);
            for (long p : retired) {
                int x = org.domi.furnace.server.light.LightEngine.px(p), y = org.domi.furnace.server.light.LightEngine.py(p),
                        z = org.domi.furnace.server.light.LightEngine.pz(p);
                if (vl.set(org.domi.furnace.server.light.VirtualLights.RETIRED, x, y, z, 0)) lightEngine().emissionChanged(x, y, z);
            }
            if (retired.length > 0) lightEngine().runUpdates();
        } finally {
            initialLightChunk = Long.MIN_VALUE;
        }
    }

    /** 생성 스레드에서 청크 안쪽 빛을 미리 계산한다(월드 상태를 읽지 않는다). 올릴 때 lightNewChunk 가 가장자리를 맞춘다. */
    public static void precomputeLight(ChunkColumn col, int cx, int cz, int bedrockState, boolean hasSkyLight) {
        col.motionBlockingHeightmap();   // 높이 지도도 첫 사용 때 틱 스레드에서 열마다 훑지 않게 미리 채운다
        final int base = col.minY() >> 4;
        var reader = new org.domi.furnace.server.light.LevelLight.StateReader() {
            @Override public int state(int x, int y, int z) { return col.getBlock(x & 15, y, z & 15); }

            @Override public int uniformSectionState(int sectionY) { return col.sectionUniformBlock(sectionY - base); }

            @Override public boolean sectionMayEmit(int sectionY) {
                return col.sectionMaybeHas(sectionY - base, org.domi.furnace.server.light.LightTables.EMITS);
            }

            @Override public boolean sectionHasOnlyAir(int chunkX, int sectionY, int chunkZ) { return col.sectionOnlyAirExact(sectionY - base); }
        };
        if (col.light() != null) {
            // 디스크에서 읽은 빛(T-012): 고립 계산은 건너뛰고 하늘빛 원천만 센다(바닐라 initializeLightSources). 가장자리는 올릴 때 맞춘다
            if (col.lightStored() && !col.lightPrecomputed()) {
                org.domi.furnace.server.light.LevelLight.computeStoredSources(col.light(), cx, cz, reader, col.minY(), col.maxY() + 1, hasSkyLight);
                col.lightPrecomputed(true);
            }
            return;
        }
        col.light(org.domi.furnace.server.light.LevelLight.computeIsolated(cx, cz, reader, bedrockState, col.minY(), col.maxY() + 1, hasSkyLight));
        col.lightPrecomputed(true);
    }

    public int lightBedrockState() { return bedrockState; }

    /** 그 청크가 로드돼 있고 빛이 만들어져 있는가. */
    public boolean hasLight(int cx, int cz) {
        ChunkColumn col = chunk(cx, cz);
        return col != null && col.light() != null;
    }

    private org.domi.furnace.server.light.VirtualLights ownVirtualLights;

    /** 이 차원의 가짜 광원 표(region 레벨은 월드가 가진 하나를 함께 쓴다 — region 이 합쳐지고 나뉘어도 따라간다). */
    public org.domi.furnace.server.light.VirtualLights virtualLights() {
        var v = ownVirtualLights;
        if (v == null) ownVirtualLights = v = new org.domi.furnace.server.light.VirtualLights();
        return v;
    }

    /**
     * 그 주인의 가짜 광원을 level(0 이면 지움)로 두고, 실제 발광이 바뀌었으면 바로 빛을 다시 계산한다(바뀐 섹션은 다음 방송에 실린다).
     * 소유 스레드에서. 청크가 없거나 빛이 아직 없거나 높이 밖이면 false. 실제 발광이 바뀌었으면 true.
     */
    public boolean setVirtualLight(Object owner, int x, int y, int z, int level) {
        if (y < minBuildHeight() || y >= maxBuildHeight()) return false;
        ChunkColumn col = chunk(x >> 4, z >> 4);
        if (col == null) return false;
        if (!virtualLights().set(owner, x, y, z, level)) return false;
        if (col.light() != null) {
            lightEngine().emissionChanged(x, y, z);
            runLightUpdates();
        }
        return true;
    }

    /**
     * 쌓인 빛 갱신을 지금 이 스레드에서 끝까지 실행(LevelLightEngine.runLightUpdates). 빛 작업 모드면 도는 묶음을 기다려 게시하고 밀린 일까지
     * 여기서 한다 — 빛을 곧바로 봐야 하는 경로(새 청크, 가짜 광원 API, 청크째 다시 보내기, 재생성, 시험)용. 틱 끝은 {@link #lightTickEnd}.
     */
    public void runLightUpdates() {
        awaitLightIdle();
        if (lightEngine != null && lightEngine.hasWork()) lightEngine.runUpdates();
    }

    // ------------------------------------------------------------------ 빛 작업 모드(T-064, furnace.properties light.async)
    //
    // 바닐라 ThreadedLevelLightEngine 처럼 region 은 블록 변경의 빛 검사를 모으기만 하고, 틱 끝에 한 묶음으로 빛 스레드에 넘긴다.
    // 빛 스레드는 넘길 때의 블록(얼린 구역 — 이후 region 의 쓰기는 구역을 복사해서 한다)과 보이는 빛의 쓰기 사본 위에서 계산하고,
    // region 스레드가 다음 틱 시작(또는 틱 끝)에 결과를 보이는 빛으로 바꿔 건다(게시). 한 레벨에 도는 묶음은 하나뿐이고, 그 동안 보이는
    // 빛 배열·원천 높이는 아무도 고치지 않는다 — 그 자리 계산이 필요한 경로(새 청크 빛 켜기 포함)는 먼저 awaitLightIdle 로 기다린다.
    // 새 청크 빛은 늘 그 자리에서 한다: 바닐라는 빛이 끝나야 청크가 FULL(틱·전송 대상)이 되므로, 빛 전의 청크가 틱·스폰·전송에 쓰이지 않게.
    // 끈 동안(기본)은 lightBatch 가 늘 null 이라 지금과 같다.

    /** 한 묶음이 다룰 청크 범위(칸 검사에서 체비셰프 거리, 청크). 빛은 15 칸 안에서 퍼지고 이웃 칸을 하나 더 읽어 2 면 넉넉하다. */
    private static final int LIGHT_BATCH_REACH = 2;

    private org.domi.furnace.server.light.LightBatch lightBatch;
    private java.util.concurrent.CompletableFuture<Void> lightBatchDone;
    /** 도는 묶음에 넘긴 미처리 일의 칸(실패하면 그 자리에서 다시 검사한다). */
    private long[] lightBatchSeeds;
    private final java.util.ArrayList<ChunkColumn.LightBlocks> lightFrozen = new java.util.ArrayList<>();
    private final java.util.HashSet<Long> lightBatchChunks = new java.util.HashSet<>();

    /** 빛 작업 모드인가(빛 계산 풀이 있는가). */
    public boolean asyncLight() { return services().lightExecutor() != null; }

    /** region 틱 시작: 끝난 묶음이 있으면 게시한다(그 틱의 무작위 틱·방송이 새 빛을 본다). */
    public void pollLight() {
        if (lightBatch != null && lightBatchDone.isDone()) publishLight();
    }

    /** region 틱 끝(바닐라 runLightUpdates 자리): 끈 동안은 그 자리에서 계산, 켜면 끝난 묶음을 게시하고 쉬고 있으면 밀린 일을 넘긴다. */
    public void lightTickEnd() {
        if (!asyncLight()) {
            runLightUpdates();
            return;
        }
        pollLight();
        if (lightBatch == null && lightEngine != null && lightEngine.hasWork()) dispatchLight();
    }

    /**
     * 도는 묶음이 있으면 끝날 때까지 기다려 게시한다(이 스레드를 막는다). 빛 스레드가 아직 시작하지 않았으면(공유 풀에서 남의 묶음 뒤에
     * 줄 서 있으면) 빼앗아 이 스레드에서 돌린다. 끝나면 보이는 빛을 그 자리에서 고쳐도 된다.
     */
    public void awaitLightIdle() {
        if (lightBatch != null) publishLight();
    }

    /** 도는 묶음을 기다려 게시하고, 밀린 일(원천 고치기·칸 검사)을 이 스레드에서 끝까지 계산한다. */
    public void drainLight() {
        runLightUpdates();
    }

    /**
     * 저장 스냅샷 전에: 이 청크의 빛을 바꿀 수 있는 밀린 빛 일(도는 묶음이 다루는 청크이거나, 밀린 칸 검사가 가까움)이 있으면 끝까지
     * 계산한다 — 옛 빛이 디스크에 굳지 않게(바닐라는 기다리지 않아 굳을 수 있다 — 그 결함은 따르지 않는다). 끈 동안은 아무것도 안 한다.
     */
    public void settleLightForSave(int cx, int cz) {
        if (lightBatch == null && (lightEngine == null || !lightEngine.hasWork())) return;
        if (!asyncLight()) return;
        boolean near = lightBatch != null && lightBatchChunks.contains((long) cx << 32 | (cz & 0xFFFFFFFFL));
        if (!near && lightEngine != null) {
            boolean[] hit = {false};
            lightEngine.forEachPendingPos(p -> {
                if (Math.abs((org.domi.furnace.server.light.LightEngine.px(p) >> 4) - cx) <= LIGHT_BATCH_REACH
                        && Math.abs((org.domi.furnace.server.light.LightEngine.pz(p) >> 4) - cz) <= LIGHT_BATCH_REACH) hit[0] = true;
            });
            near = hit[0];
        }
        if (near) drainLight();
    }

    /** 밀린 일을 한 묶음으로 빛 스레드에 넘긴다(도는 묶음이 없을 때만). */
    private void dispatchLight() {
        var executor = services().lightExecutor();
        var batch = new org.domi.furnace.server.light.LightBatch(minBuildHeight(), maxBuildHeight(), bedrockState, hasSkyLight(), virtualLights());
        long[][] seedPos = {new long[64]};
        int[] seedCount = {0};
        java.util.LinkedHashSet<Long> seeds = new java.util.LinkedHashSet<>();
        lightEngine.forEachPendingPos(p -> {
            if (seedCount[0] == seedPos[0].length) seedPos[0] = java.util.Arrays.copyOf(seedPos[0], seedCount[0] * 2);
            seedPos[0][seedCount[0]++] = p;
            seeds.add((long) (org.domi.furnace.server.light.LightEngine.px(p) >> 4) << 32
                    | ((org.domi.furnace.server.light.LightEngine.pz(p) >> 4) & 0xFFFFFFFFL));
        });
        for (long seed : seeds) {
            int sx = (int) (seed >> 32), sz = (int) seed;
            for (int dx = -LIGHT_BATCH_REACH; dx <= LIGHT_BATCH_REACH; dx++) {
                for (int dz = -LIGHT_BATCH_REACH; dz <= LIGHT_BATCH_REACH; dz++) {
                    int cx = sx + dx, cz = sz + dz;
                    if (batch.hasColumn(cx, cz)) continue;
                    ChunkColumn col = chunk(cx, cz);
                    if (col == null || col.light() == null) continue;
                    ChunkColumn.LightBlocks blocks = col.freezeForLight();
                    lightFrozen.add(blocks);
                    batch.addColumn(cx, cz, blocks, col.light());
                    lightBatchChunks.add((long) cx << 32 | (cz & 0xFFFFFFFFL));
                }
            }
        }
        lightEngine.moveWorkTo(batch.engine());
        lightBatch = batch;
        lightBatchSeeds = java.util.Arrays.copyOf(seedPos[0], seedCount[0]);
        var done = new java.util.concurrent.CompletableFuture<Void>();
        lightBatchDone = done;
        try {
            executor.execute(() -> {
                if (!batch.claim()) {   // region 스레드가 빼앗아 이미 돌렸다
                    done.complete(null);
                    return;
                }
                try {
                    runLightBatch(batch);
                    done.complete(null);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 풀이 닫히는 중 — 이 스레드에서 곧바로 돌려 끝낸다(다음 pollLight 가 게시하고, 다음 넘기기도 막히지 않는다)
            if (batch.claim()) {
                try {
                    runLightBatch(batch);
                    done.complete(null);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            }
        }
    }

    /** 묶음 계산(빛 스레드 또는 빼앗은 region 스레드에서). 시험이 실패를 흉내 낼 자리. */
    void runLightBatch(org.domi.furnace.server.light.LightBatch batch) { batch.run(); }

    /** 묶음이 끝나길 기다려(시작 전이면 빼앗아 이 스레드에서 돌려) 결과를 보이는 빛에 넣고 얼린 구역을 녹인다. 주인 스레드에서. */
    private void publishLight() {
        var batch = lightBatch;
        Throwable failed = null;
        if (batch.claim()) {   // 빛 스레드가 아직 집지 않았다 — 기다리지 않고 여기서
            try {
                runLightBatch(batch);
            } catch (RuntimeException | Error e) {
                failed = e;
            }
        } else {
            try {
                lightBatchDone.join();
            } catch (RuntimeException e) {
                failed = e;
            }
        }
        long[] seeds = lightBatchSeeds;
        lightBatch = null;
        lightBatchDone = null;
        lightBatchSeeds = null;
        if (failed == null) {
            batch.publish((key, skyMask, blockMask) -> {
                long[] m = lightChanges.computeIfAbsent(key, k -> new long[2]);
                m[0] |= skyMask;
                m[1] |= blockMask;
                int sx = (int) (key >> 32), sz = (int) key;
                ChunkColumn col = chunk(sx, sz);
                if (col != null) {
                    col.lightTouched();
                    if (col.markLightDirty()) markDirty(sx, sz);   // markLightChanged 와 같이(바닐라 sectionLightChanged → setUnsaved, T-012)
                }
            });
            // 묶음 안에서 안 올라온 청크에 막힌 감소 — 저장 빛을 믿지 않게 표시(T-012). 표시 표는 region 스레드 것이라 여기서
            batch.forEachBlocked(p -> lightWorld.decreaseBlocked(org.domi.furnace.server.light.LightEngine.px(p),
                    org.domi.furnace.server.light.LightEngine.py(p), org.domi.furnace.server.light.LightEngine.pz(p)));
        }
        for (ChunkColumn.LightBlocks b : lightFrozen) b.thaw();
        lightFrozen.clear();
        lightBatchChunks.clear();
        if (failed != null) {
            // 묶음의 사본을 버렸으니 보이는 빛은 넘기기 전 그대로다 — 넘겼던 칸을 다시 걸고 그 자리에서 계산한다(끈 것과 같은 결과)
            org.slf4j.LoggerFactory.getLogger(ChunkLevel.class).error("[LIGHT] 빛 작업 실패 — 그 묶음을 이 스레드에서 다시 계산한다", failed);
            var engine = lightEngine();
            for (long p : seeds) {
                engine.blockChanged(org.domi.furnace.server.light.LightEngine.px(p), org.domi.furnace.server.light.LightEngine.py(p),
                        org.domi.furnace.server.light.LightEngine.pz(p));
            }
            engine.runUpdates();
        }
    }

    @Override
    public int getRawBrightness(Pos pos, int skyDarken) {
        ChunkColumn col = chunkAt(pos);
        if (col == null || col.light() == null) return BlockLevel.super.getRawBrightness(pos, skyDarken);
        int sky = hasSkyLight() ? getSkyLight(pos) - skyDarken : 0;
        return Math.max(getBlockLight(pos), sky);
    }

    @Override
    public int getSkyLight(Pos pos) {
        ChunkColumn col = chunkAt(pos);
        if (col == null || col.light() == null) return BlockLevel.super.getSkyLight(pos);
        if (pos.y() >= org.domi.furnace.server.light.ChunkLight.MAX_Y) return 15;
        return lightEngine().skyLight(pos.x(), pos.y(), pos.z());
    }

    @Override
    public int getBlockLight(Pos pos) {
        ChunkColumn col = chunkAt(pos);
        if (col == null || col.light() == null) return BlockLevel.super.getBlockLight(pos);
        return lightEngine().blockLight(pos.x(), pos.y(), pos.z());
    }

    private static final float[] MOON_BRIGHTNESS_PER_PHASE = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};

    /** Level.getHeight(MOTION_BLOCKING, x, z): 움직임을 막는 가장 높은 블록 바로 위 y(청크가 없으면 최저 높이). */
    public int getMotionBlockingHeight(int x, int z) {
        ChunkColumn col = chunk(x >> 4, z >> 4);
        return col == null ? minBuildHeight() : col.motionBlockingBlockY(x & 15, z & 15) + 1;
    }

    /** Level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z): 잎을 뺀 움직임을 막는 가장 높은 블록 바로 위 y(청크가 없으면 최저 높이). */
    public int getMotionBlockingNoLeavesHeight(int x, int z) {
        ChunkColumn col = chunk(x >> 4, z >> 4);
        return col == null ? minBuildHeight() : col.motionBlockingNoLeavesBlockY(x & 15, z & 15) + 1;
    }

    /**
     * ServerLevel.isNaturalSpawningAllowed(BlockPos): 그 청크가 엔티티 틱(PersistentEntitySectionManager.canPositionTick)인가.
     * 엔티티 틱 개념이 없는 레벨(시험)은 청크가 올라와 있으면 참. RegionLevel 이 시야 거리·티켓으로 덮어쓴다.
     */
    public boolean isNaturalSpawningAllowed(int chunkX, int chunkZ) { return chunk(chunkX, chunkZ) != null; }

    /** Level.getHeight(WORLD_SURFACE, x, z): 가장 높은 블록 바로 위 y(청크가 없으면 최저 높이). */
    public int getWorldSurfaceHeight(int x, int z) {
        ChunkColumn col = chunk(x >> 4, z >> 4);
        return col == null ? minBuildHeight() : col.surfaceBlockY(x & 15, z & 15) + 1;
    }

    @Override public int worldSurfaceHeight(int x, int z) { return getWorldSurfaceHeight(x, z); }

    /**
     * Level.isRainingAt(pos): 비가 오고(isRaining), 하늘이 보이고(canSeeSky), MOTION_BLOCKING 높이 칸 위이며,
     * 그 바이옴의 강수 종류(Biome.getPrecipitationAt)가 RAIN 이다.
     */
    @Override
    public boolean isRainingAt(Pos pos) {
        if (!isRaining()) return false;
        if (!canSeeSky(pos)) return false;
        if (getMotionBlockingHeight(pos.x(), pos.z()) > pos.y()) return false;
        return org.domi.furnace.server.world.BiomeWeather.RAIN.equals(
                org.domi.furnace.server.world.BiomeWeather.of(services().biomeAt(pos.x(), pos.y(), pos.z())).precipitationAt(pos.x(), pos.y(), pos.z()));
    }

    /** LevelReader.canSeeSky(pos): 그 칸 위로 움직임을 막는 블록이 없다. */
    public boolean canSeeSky(Pos pos) { return getMotionBlockingHeight(pos.x(), pos.z()) <= pos.y(); }

    /** Level.isDay(): 낮(skyDarken < 4)이고 고정 시간 차원이 아니다. */
    public boolean isDay() { return hasSkyLight() && skyDarken() < 4; }

    /** Level.isNight: 고정 시간이 아니고 하늘 어둠이 4 이상. */
    public boolean isNight() { return hasSkyLight() && skyDarken() >= 4; }

    public float moonBrightness() { return MOON_BRIGHTNESS_PER_PHASE[(int) (dayTime() / 24000L % 8L + 8L) % 8]; }

    /** getCurrentDifficultyAt(pos): 청크가 있으면 그 청크의 머문 시간·달 밝기를 반영한다. */
    public org.domi.furnace.server.entity.DifficultyInstance getCurrentDifficultyAt(Pos pos) {
        long inhabited = 0L;
        float moon = 0.0F;
        ChunkColumn col = chunk(pos.x() >> 4, pos.z() >> 4);
        if (col != null) {
            moon = MOON_BRIGHTNESS_PER_PHASE[(int) (dayTime() / 24000L % 8L + 8L) % 8];
            inhabited = col.inhabitedTime();
        }
        return new org.domi.furnace.server.entity.DifficultyInstance(difficulty(), dayTime(), inhabited, moon,
                org.domi.furnace.server.world.DifficultyModifiers.local());
    }

    /** 새 엔티티 id(서버 전역 — 플레이어와 같은 발급기). 기본은 테스트용 따로 센 값. */
    public int nextEntityId() { return FALLBACK_IDS.incrementAndGet(); }

    /** 연달은 id count 개를 한꺼번에 잡고 첫 id 를 돌려준다(엔더 드래곤: 본체 + 파트 8 — 클라이언트가 id+1.. 을 파트에 쓴다). */
    public int nextEntityIds(int count) { return FALLBACK_IDS.getAndAdd(count) + 1; }

    private org.domi.furnace.server.entity.EndDragonFight dragonFight;

    /** ServerLevel.getDragonFight: 엔드 레벨의 드래곤 전투(없으면 null). */
    public org.domi.furnace.server.entity.EndDragonFight dragonFight() { return dragonFight; }

    public void setDragonFight(org.domi.furnace.server.entity.EndDragonFight f) { dragonFight = f; }

    /** 차원 이름으로 레벨: 자기 자신이거나 서버가 띄운 다른 차원(없으면 null). */
    public ChunkLevel levelFor(String dimension) { return dimension.equals(dimension()) ? this : services().otherLevel(dimension); }

    /** 추적 대상 플레이어(이 레벨을 돌리는 region 의 플레이어). 기본은 서비스의 접속자. */
    protected Iterable<Player> trackingPlayers() {
        java.util.List<Player> out = new java.util.ArrayList<>();
        services().forEachPlayer(out::add);
        return out;
    }

    /** ChunkMap.getPlayerViewDistance: clamp(요청 시야, 2, 서버 시야). */
    protected int viewDistanceFor(Player p) { return Math.max(2, p.viewDistance()); }

    /** ChunkMap.isChunkTracked: 그 플레이어가 그 청크를 받았는가. */
    protected boolean chunkTrackedBy(Player p, int chunkX, int chunkZ) { return true; }

    private final EntityTracker.Viewers viewers = new EntityTracker.Viewers() {
        public int viewDistance(Player p) { return viewDistanceFor(p); }

        public boolean chunkTracked(Player p, int cx, int cz) { return chunkTrackedBy(p, cx, cz); }

        @Override
        public void startedSeeing(Player p, Entity e) { noteSeen(p, e); }
    };

    public EntityTracker.Viewers viewers() { return viewers; }

    /** addFreshEntity: 넣고(같은 UUID 가 있으면 거부) 추적을 시작한다 — 볼 수 있는 플레이어에게 바로 스폰. */
    public boolean addFreshEntity(Entity e) { return addFreshEntity(e, true, false); }

    /** claimed: 승객 묶음이 이미 UUID 를 잡아 둔 경우(여기서 다시 잡지 않고, 실패하면 놓는다). */
    private boolean addFreshEntity(Entity e, boolean fireEvent, boolean claimed) {
        if (e.isRemoved()) {
            if (claimed) indexEntity(e, false);
            return false;
        }
        ChunkLevel before = e.level();
        e.setLevel(this);
        long addedKey = e.sectionKey();
        if (!entities.add(e)) {
            if (claimed) indexEntity(e, false);
            return false;
        }
        if (!claimed && !claimUuid(e, false)) {   // 바닐라 "UUID of added entity already exists": 다른 region 이 같은 UUID 를 이미 쥐고 있다
            entities.remove(e);
            e.setLevel(before);
            return false;
        }
        if (fireEvent && !gameEvents().entitySpawn(e)) {   // 플러그인 EntitySpawnEvent 취소: 추적·리스너 등록 전이라 목록과 레벨만 되돌린다
            entities.remove(e);
            indexEntity(e, false);   // 위에서 잡은 UUID 자리를 놓는다
            e.setLevel(before);
            retireApiTasks(e);
            return false;
        }
        if (e.isRemoved()) {   // 스폰 이벤트 처리기가 지웠다: 이미 목록에서 빠졌으니 추적·리스너를 붙이지 않는다(유령 엔티티 방지)
            indexEntity(e, false);
            retireApiTasks(e);
            return false;
        }
        // 처리기가 옮겼으면 구역 목록을 맞춘다 — 추적기가 없는 동안에는 setPosRaw 가 구역 이동을 알리지 않는다(Paper 도 이벤트 뒤 위치로 넣는다)
        if (e.sectionKey() != addedKey) entities.moved(e);
        startTracking(e);
        for (var l : e.dynamicGameEventListeners()) l.add(this);
        indexEntity(e, true);
        return true;
    }

    /** id → 엔티티 색인(월드마다 하나, 어느 스레드에서나 읽는다)에 넣거나 뺀다. 색인이 없는 레벨(시험 레벨)은 아무것도 안 한다. */
    protected void indexEntity(Entity e, boolean add) {}

    /**
     * adopting: region 사이를 넘어오는 같은 객체(이미 색인에 있음)는 통과, 새로 넣는 쪽은 거부.
     * 월드 전체에서 e 의 UUID 를 e 가 쥔다(바닐라 PersistentEntitySectionManager.knownUuids — 레벨 단위 중복 거부). 이미 다른 살아 있는 엔티티가 쥐고 있으면 거짓.
     * 성공하면 UUID 색인에 e 가 들어가 있다. 색인이 없는 레벨(시험 레벨)은 늘 참.
     */
    protected boolean claimUuid(Entity e, boolean adopting) { return true; }

    /** claimUuid 가 실패할 상태인가(넣지 않고 미리 보기만 — 승객 묶음 검사용). */
    protected boolean uuidTakenElsewhere(Entity e) { return false; }

    /** tryAddFreshEntityWithPassengers: 승객의 승객까지 하나라도 UUID 가 겹치면 아무것도 넣지 않는다. */
    public boolean addFreshEntityWithPassengers(Entity e) {
        java.util.List<org.domi.furnace.server.entity.Rider> all = new java.util.ArrayList<>();
        all.add(e);
        e.collectIndirectPassengers(all);
        for (var r : all) if (r instanceof Entity x && (entities.containsUuid(x.uuid()) || uuidTakenElsewhere(x))) return false;
        // 이벤트 전에 전원의 UUID 를 월드에서 잡는다 — 하나라도 못 잡으면 전부 놓고 그만둔다(전부 아니면 전무, 다른 region 과의 경쟁 틈 없이)
        java.util.List<Entity> fired = new java.util.ArrayList<>();
        for (var r : all) {
            if (!(r instanceof Entity x)) continue;
            if (!claimUuid(x, false)) {
                for (Entity c : fired) indexEntity(c, false);
                return false;
            }
            fired.add(x);
        }
        // 플러그인 EntitySpawnEvent: 하나라도 취소하면 전부 넣지 않는다(전부 아니면 전무)
        java.util.List<Entity> eventsFired = new java.util.ArrayList<>();
        java.util.List<ChunkLevel> before = new java.util.ArrayList<>();
        for (Entity x : fired) {
            eventsFired.add(x);
            before.add(x.level());
            x.setLevel(this);
            if (!gameEvents().entitySpawn(x)) {
                for (int i = 0; i < eventsFired.size(); i++) {
                    eventsFired.get(i).setLevel(before.get(i));
                    retireApiTasks(eventsFired.get(i));
                }
                for (Entity c : fired) indexEntity(c, false);
                return false;
            }
        }
        for (Entity x : fired) addFreshEntity(x, false, true);
        return true;
    }

    /** ChunkMap.addEntity: 추적 거리(종류 × 16 × 방송 비율) 가 0 이면 추적하지 않는다. */
    protected void startTracking(Entity e) {
        int range = e.type().clientTrackingRange() * 16;
        if (range == 0) return;
        e.tracker = new EntityTracker(e, services().entityBroadcastRangePercentage() * range / 100);
        maxTrackRange = Math.max(maxTrackRange, e.tracker.range());
        e.tracker.updatePlayers(trackingPlayers(), viewers);
    }

    /** 스폰이 취소된 엔티티: 이벤트 처리기가 핸들로 예약한 플러그인 작업은 영영 돌 수 없으니 retired 로 끝낸다. */
    private static void retireApiTasks(Entity e) {
        var t = e.apiTasksOrNull();
        if (t != null) t.retire();
    }

    /** 다른 region 에서 넘어온 엔티티(추적 상태째). */
    public void adoptEntity(Entity e) {
        if (e.isRemoved()) return;
        e.setLevel(this);
        if (!entities.add(e)) {
            if (!entities.contains(e)) indexEntity(e, false);   // 같은 UUID 가 이미 있어 못 받았다 — 색인에 유령을 남기지 않는다
            return;
        }
        if (!claimUuid(e, true)) {   // 같은 UUID 의 다른 엔티티가 다른 region 에 살아 있다 — 받지 않는다(조건부 remove 라 그쪽 색인은 그대로)
            entities.remove(e);
            return;
        }
        if (e.tracker == null) startTracking(e);
        else adoptTracker(e);
        for (var l : e.dynamicGameEventListeners()) l.add(this);
        indexEntity(e, true);
    }

    /** 엔티티가 지워졌다(setRemoved) — 저장소에서 빼고 보는 사람에게 지운다. */
    public void entityRemoved(Entity e) {
        indexEntity(e, false);   // region 사이를 넘어가는 중(저장소에서만 빠진 상태)에 지워져도 색인이 남지 않게 먼저
        if (!entities.remove(e)) return;
        for (var l : e.dynamicGameEventListeners()) l.remove(this);
        if (e.tracker != null) {
            e.tracker.broadcastRemoved();
            e.tracker = null;
        }
    }

    /**
     * 엔티티의 구역이 바뀌었다(setPosRaw). 구역 목록은 추적 여부와 상관없이 맞춘다(바닐라 onMove — 추적 거리 0 인 마커도 옮기면 목록이 따라간다).
     * 게임 이벤트 리스너는 추적을 시작해 리스너를 단 뒤에만 옮긴다(스폰 이벤트 처리기가 옮기는 동안 미리 등록되지 않게).
     */
    public void entitySectionChanged(Entity e) {
        entities.moved(e);
        if (e.tracker != null) for (var l : e.dynamicGameEventListeners()) l.move(this);   // onSectionChange
    }

    public java.util.List<Entity> getEntities(Entity except, AABB box, java.util.function.Predicate<? super Entity> filter) {
        return entities.getEntities(except, box, filter);
    }

    /** getEntities 와 같은 결과를 그 종류(인터페이스 포함)만 훑어서. */
    public java.util.List<Entity> getEntitiesOfType(Class<?> cls, AABB box, java.util.function.Predicate<? super Entity> filter) {
        return entities.getEntitiesOfType(cls, box, filter);
    }
    public <T extends Entity> java.util.List<T> getEntitiesOfClass(Class<T> cls, AABB box, java.util.function.Predicate<? super T> filter) {
        return entities.getEntitiesOfClass(cls, box, filter);
    }

    /**
     * 플러그인 EntityScheduler 작업(Folia 처럼 엔티티 틱과 별개로, 이 레벨이 가진 모든 엔티티 — 틱 청크 밖이어도).
     * 대기 작업이 있는 엔티티가 서버 어디에도 없으면 훑지 않는다.
     */
    public void runEntityTasks() {
        if (entities.isEmpty() || !org.domi.furnace.server.entity.EntityTasks.anyPending()) return;
        for (Entity e : entities.tickSnapshot()) {
            var t = e.apiTasksOrNull();
            if (t != null && !e.isRemoved()) t.tick();
        }
    }

    /**
     * 엔티티 틱(바닐라 ServerLevel.tick 의 entityTickList.forEach): 지워진 것은 건너뛰고, 엔티티 틱 청크(ticking — 청크 키
     * (x << 32 | z))에 있는 것만 setOldPosAndRot · tickCount++ · tick.
     */
    public void tickEntities(java.util.function.LongPredicate ticking) { tickEntities(ticking, ticking); }

    /** 엔티티 활성 범위 상태(Spigot·Paper EAR). 이 레벨의 주인 스레드만. */
    private final org.domi.furnace.server.entity.EntityActivation.LevelState activation = new org.domi.furnace.server.entity.EntityActivation.LevelState();

    public org.domi.furnace.server.entity.EntityActivation.LevelState activation() { return activation; }

    /**
     * Spigot ActivationRange.activateEntities(ServerLevel.tick 의 엔티티 틱 바로 앞): 설정이 켜져 있으면 players 둘레의 엔티티를 이번 틱 활성으로
     * 표시하고, 이번 tickEntities 가 범위 밖 엔티티에 inactiveTick 을 돌리게 한다. 꺼져 있으면 엔티티 틱은 바닐라 그대로다.
     */
    public void activateEntities(Iterable<org.domi.furnace.server.entity.Player> players) {
        org.domi.furnace.server.entity.EntityActivation.activate(this, activation, players, services().simulationDistance());
    }

    /**
     * ServerLevel.tick 의 엔티티 람다(aqu.a(brc,bnf,bsr)): entityTickList(섹션이 ENTITY_TICKING — sectionTicking)의 엔티티마다
     * 버리기(shouldDiscardEntity) → checkDespawn → inEntityTickingRange(ticking)일 때만 탈것 처리·tick. 그래서 시뮬레이션 거리 밖·시야 안의
     * 얼어붙은 몹도 사라짐 검사는 받는다(A10 검토 R1). 키는 (x << 32 | z).
     */
    public void tickEntities(java.util.function.LongPredicate sectionTicking, java.util.function.LongPredicate ticking) {
        if (entities.isEmpty()) return;
        boolean animalsOk = spawnAnimalsAllowed(), npcsOk = spawnNpcsAllowed();
        for (Entity e : entities.tickSnapshot()) {
            if (e.isRemoved()) continue;
            long key = (long) e.chunkX() << 32 | e.chunkZ() & 0xFFFFFFFFL;
            if (!sectionTicking.test(key)) continue;
            if ((!animalsOk || !npcsOk) && shouldDiscardEntity(e, animalsOk, npcsOk)) {   // spawn-animals / spawn-npcs=false
                e.discard();
                continue;
            }
            try {
                e.checkDespawn();   // 바닐라도 범위 검사 앞에서 부르고, 지워져도 그 틱의 tick 은 그대로 간다
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(ChunkLevel.class).error("[ENTITY] 사라짐 검사 실패 {} — 지운다", e, ex);
                e.discard();
                continue;
            }
            if (!ticking.test(key)) continue;
            Entity v = e.vehicle();
            if (v != null) {   // 탄 것은 탈것이 틱한다
                if (!v.isRemoved() && v.hasPassenger(e)) continue;
                Entity.stopRidingForced(e);
            }
            try {
                // Paper EAR: tickCount 를 올리기 전에 정한다. 비활성이면 tick 대신 inactiveTick
                boolean active = !activation.on() || org.domi.furnace.server.entity.EntityActivation.checkIfActive(e, activation);
                e.setOldPosAndRot();
                e.tickCount++;
                if (active) e.tick();
                else e.inactiveTick();
                if (e.isVehicle()) for (var r : e.passengers()) tickPassenger(e, r);
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(ChunkLevel.class).error("[ENTITY] 엔티티 틱 실패 {} — 지운다", e, ex);
                e.discard();
            }
        }
    }

    /** ServerLevel.tickPassenger: 지워졌거나 다른 것에 탔으면 내리고, 아니면 rideTick 뒤 그 승객의 승객. 플레이어는 자기 틱 대신 탑승 처리만. */
    private void tickPassenger(Entity vehicle, org.domi.furnace.server.entity.Rider r) {
        if (r instanceof Player p) {
            // 끊긴 플레이어는 주인 region 이 퇴장 처리에서 탈것을 저장하고 내린다(PlayerVehicleSave) — 그전에 내리면 탈것이 저장되지 않는다
            if (p.isDisconnected()) return;
            if (p.vehicle() != vehicle) Entity.stopRidingForced(p);
            else p.rideTick();
            return;
        }
        Entity e = (Entity) r;
        if (e.isRemoved() || e.vehicle() != vehicle) {
            Entity.stopRidingForced(e);
        } else if (entities.contains(e)) {
            boolean active = !activation.on() || org.domi.furnace.server.entity.EntityActivation.checkIfActive(e, activation);   // Paper EAR(승객은 따로)
            e.setOldPosAndRot();
            e.tickCount++;
            if (active) e.rideTick();
            else {   // Paper: 멈추고 inactiveTick, 자리만 탈것에 맞춘다
                e.setDeltaMovement(0.0, 0.0, 0.0);
                e.inactiveTick();
                vehicle.positionRider(e);
            }
            if (e.isVehicle()) for (var r2 : e.passengers()) tickPassenger(e, r2);
        }
    }

    /**
     * 추적 갱신(ChunkMap.tick 의 엔티티 부분): 구역이 바뀐 엔티티는 모든 플레이어에 대해 다시 보고, 바뀌었거나 엔티티 틱 청크면 변화를 보낸다.
     * present 는 이 region 에 있는 플레이어인가(없는 보는 사람은 grace 뒤 뺀다).
     */
    public void trackEntities(java.util.function.LongPredicate ticking, java.util.function.Predicate<Player> present) {
        if (entities.isEmpty()) {
            seenIndex.clear();
            return;
        }
        Iterable<Player> players = null;
        for (Entity e : entities.tickSnapshot()) {
            EntityTracker t = e.tracker;
            if (t == null || e.isRemoved()) continue;
            t.dropAbsent(present, 3);
            boolean changed = t.lastSectionKey != e.sectionKey();
            if (changed) {
                if (players == null) players = trackingPlayers();
                t.updatePlayers(players, viewers);
                t.lastSectionKey = e.sectionKey();
            }
            if (changed || ticking.test((long) e.chunkX() << 32 | e.chunkZ() & 0xFFFFFFFFL)) t.sendChanges();
        }
        pruneAbsentSeen(present);
    }

    /**
     * ChunkMap.move(player): 플레이어가 움직였다 — 엔티티마다 그 플레이어를 다시 본다(바닐라는 모든 엔티티를 돈다).
     * 결과가 바뀔 수 있는 엔티티는 (a) 지금 p 를 보는 것과 (b) p 에서 추적 거리 안에 있는 것뿐이다 — 둘 다 아니면 updatePlayer 는
     * 거리 판정에서 "안 보임"이 되고 seenBy 에 p 가 없어 아무것도 하지 않는다. 그래서 엔티티가 많고 p 가 그 일부만 볼 때는
     * (a)∪(b) 만 틱 목록 순서로 돈다(T-017). 보내는 패킷·순서·seenBy 는 전체 훑기와 같다(EntityTrackerCandidatesIdentityTest).
     */
    public void playerMoved(Player p) {
        if (VERIFY_TRACK_CANDIDATES && !seenIndexCovers(p)) throw new AssertionError("추적 역색인에 빠진 엔티티가 있다: " + p);
        int n = entities.size();
        SeenList seen = seenIndex.get(p);
        // 엔티티가 적거나, p 가 이미 절반 넘게 본다(몰림 — 후보가 거의 전부라 모으고 늘어놓는 값만 더 든다)면 전체 훑기
        if (n < CANDIDATE_MIN_ENTITIES || seen != null && seen.size * 2 >= n || !playerMovedCandidates(p)) playerMovedFullScan(p);
    }

    /**
     * 시험(단언 켬)에서 역색인·후보가 빠짐없는지 매번 전체로 확인한다(O(엔티티)). 운영(단언 꺼짐)과 벤치(furnace.bench — 단언은 켜고 돈다)에서는 끈다.
     */
    private static final boolean VERIFY_TRACK_CANDIDATES = assertionsOn() && !Boolean.getBoolean("furnace.bench");

    @SuppressWarnings("AssertWithSideEffects")
    private static boolean assertionsOn() {
        boolean on = false;
        assert on = true;
        return on;
    }

    /** 엔티티가 이보다 적으면 후보를 모으는 것보다 다 도는 편이 싸다. */
    private static final int CANDIDATE_MIN_ENTITIES = 64;

    /**
     * 옛 길(바닐라 그대로): 이 레벨의 모든 엔티티를 틱 목록 순서로. 후보 길의 비교 기준(시험)이기도 하다.
     * 도는 김에 p 의 역색인을 정확히 다시 만든다(틱 목록 순서·겹침 없음).
     */
    public void playerMovedFullScan(Player p) {
        SeenList fresh = takeSeenList(p);
        fresh.size = 0;   // 옛 칸은 덮어쓴다(전체를 도니 옛 목록은 필요 없다)
        boolean done = false;
        try {
            passPlayer = p;
            for (Entity e : entities.all()) {
                passEntity = e;
                if (e.tracker != null && !e.isRemoved() && e.tracker.updatePlayer(p, viewers)) fresh.add(e);
            }
            done = true;
        } finally {
            passPlayer = null;
            passEntity = null;
            if (!done) {   // 도중 예외: 아직 안 본 것 중 p 를 보던 것도 빠짐없이
                fresh.size = 0;
                for (Entity e : entities.all()) if (e.tracker != null && e.tracker.isSeenBy(p)) fresh.add(e);
            }
            putSeenList(p, fresh);
        }
    }

    /**
     * 후보 길: (a) 역색인 + (b) 추적 거리 안 청크 열의 엔티티를 틱 목록 순서로 늘어놓고 겹친 것을 지워 updatePlayer.
     * 범위가 너무 넓으면(좌표가 유한하지 않거나 열이 아주 많음) 아무것도 하지 않고 거짓 — 부른 쪽이 전체를 훑는다.
     */
    public boolean playerMovedCandidates(Player p) {
        double px = p.x(), pz = p.z();
        int vd = viewDistanceFor(p);
        // updatePlayer 의 거리 r = min(엔티티 추적 거리, 시야 × 16) ≤ 이 값. 1 블록은 부동소수 경계 여유
        double reach = Math.min(vd * 16.0, maxTrackRange) + 1.0;
        if (!(Math.abs(px) < 3.0E7) || !(Math.abs(pz) < 3.0E7)) return false;
        int cx0 = Entity.floor(px - reach) >> 4, cx1 = Entity.floor(px + reach) >> 4;
        int cz0 = Entity.floor(pz - reach) >> 4, cz1 = Entity.floor(pz + reach) >> 4;
        if (cx1 - cx0 > MAX_CANDIDATE_COLUMNS || cz1 - cz0 > MAX_CANDIDATE_COLUMNS) return false;
        SeenList fresh = takeSeenList(p);   // 옛 목록을 후보로 읽은 뒤 같은 배열에 새 목록을 쓴다
        Entity[] buf = candBuf;
        int n = 0;
        boolean done = false;
        passPlayer = p;
        try {
            candN = 0;
            for (int i = 0; i < fresh.size; i++) {
                Entity e = fresh.items[i];
                int ecx = e.chunkX(), ecz = e.chunkZ();
                if (ecx >= cx0 && ecx <= cx1 && ecz >= cz0 && ecz <= cz1) continue;   // 범위 안이면 열 훑기가 찾는다(저장소에 있다면)
                if (e.tracker != null && !e.isRemoved() && entities.holds(e)) addCandidate(e);
            }
            fresh.size = 0;
            entities.forEachInColumns(cx0, cx1, cz0, cz1, candidateSink);
            buf = candBuf;
            n = entities.orderAndDedupe(buf, candN);
            candN = 0;
            if (VERIFY_TRACK_CANDIDATES && !candidatesComplete(p, buf, n)) throw new AssertionError("추적 후보에 빠진 엔티티가 있다: " + p);
            long mods = entities.modCount();
            for (int i = 0; i < n; i++) {
                Entity e = buf[i];
                passEntity = e;
                if (e.tracker != null && !e.isRemoved() && e.tracker.updatePlayer(p, viewers)) fresh.add(e);
                // 판정 도중 처리기(플러그인 PlayerTrackEntityEvent·onStartSeenBy)가 엔티티를 넣고 빼거나 옮겼거나 p 를 옮겼으면
                // 미리 정한 후보가 더는 맞지 않는다 — 옛 길처럼 이 엔티티 뒤(틱 목록 순서)를 모두 돈다
                if (entities.modCount() != mods || p.x() != px || p.z() != pz || viewDistanceFor(p) != vd) {
                    long after = org.domi.furnace.server.entity.EntityStore.orderKey(e);
                    for (Entity x : entities.all()) {
                        if (org.domi.furnace.server.entity.EntityStore.orderKey(x) <= after) continue;
                        passEntity = x;
                        if (x.tracker != null && !x.isRemoved() && x.tracker.updatePlayer(p, viewers)) fresh.add(x);
                    }
                    break;
                }
            }
            done = true;
        } finally {
            passPlayer = null;
            passEntity = null;
            java.util.Arrays.fill(buf, 0, n, null);
            candN = 0;
            if (!done) {   // 도중 예외: p 를 보는 것을 빠짐없이 다시 모은다(역색인을 잃으면 먼 엔티티의 제거 패킷을 놓친다)
                fresh.size = 0;
                for (Entity e : entities.all()) if (e.tracker != null && e.tracker.isSeenBy(p)) fresh.add(e);
            }
            putSeenList(p, fresh);
        }
        return true;
    }

    /** 시야 32 청크(512 블록) × 2 를 넉넉히 넘는 열 수 — 이보다 넓으면 전체 훑기. */
    private static final int MAX_CANDIDATE_COLUMNS = 80;

    private Entity[] candBuf = new Entity[64];
    private int candN;
    private final java.util.function.Consumer<Entity> candidateSink = e -> {
        if (e.tracker != null && !e.isRemoved()) addCandidate(e);
    };

    private void addCandidate(Entity e) {
        if (candN == candBuf.length) candBuf = java.util.Arrays.copyOf(candBuf, candN * 2);
        candBuf[candN++] = e;
    }

    // ---- 플레이어별 "보는 엔티티" 역색인(T-017)

    /** 플레이어 → 그 플레이어를 seenBy 에 넣은 이 레벨 엔티티(상위 집합 — 지금은 안 보거나 다른 레벨로 간 것이 섞일 수 있어 쓸 때 거른다). */
    private final java.util.IdentityHashMap<Player, SeenList> seenIndex = new java.util.IdentityHashMap<>();
    /** 지금 판정 중인 (플레이어, 엔티티) — 그 쌍의 콜백은 판정 결과가 대신한다. */
    private Player passPlayer;
    private Entity passEntity;
    /** 이 레벨에 들어온 추적기 거리의 최댓값(블록). 줄지 않는다 — 후보 범위의 상한으로만 쓴다. */
    private int maxTrackRange;

    private static final class SeenList {
        Entity[] items = new Entity[8];
        int size;
        int compactAt = 32;

        void add(Entity e) {
            if (size == items.length) items = java.util.Arrays.copyOf(items, size * 2);
            items[size++] = e;
        }
    }

    /** p 의 목록을 떼어 온다(없으면 새로). 판정 중 새로 보기 시작한 것은 noteSeen 이 임시 목록에 붙이고, putSeenList 가 갈아 끼운다. */
    private SeenList takeSeenList(Player p) {
        SeenList l = seenIndex.remove(p);
        return l != null ? l : new SeenList();
    }

    /**
     * 판정이 만든 목록을 단다(비었으면 뺀다). 판정 도중 다른 길(플러그인 처리기가 부른 refreshViews 등)로 p 를 보기 시작한 엔티티는
     * noteSeen 이 임시 목록에 붙였으니 합친다(상위 집합이어도 된다). 쓰지 않는 칸은 비워 엔티티를 붙잡지 않는다.
     */
    private void putSeenList(Player p, SeenList l) {
        SeenList during = seenIndex.get(p);
        if (during != null) for (int i = 0; i < during.size; i++) l.add(during.items[i]);
        int cap = l.items.length;
        if (cap > 64 && l.size * 4 < cap) l.items = java.util.Arrays.copyOf(l.items, Math.max(8, l.size * 2));
        else java.util.Arrays.fill(l.items, l.size, cap, null);
        l.compactAt = Math.max(32, l.size * 2);
        if (l.size == 0) seenIndex.remove(p);
        else seenIndex.put(p, l);
    }

    /** p 가 e 를 보기 시작했다. 목록이 쌓이면(안 움직이는 플레이어 곁을 몹이 드나듦) 지금 보는 것만 남긴다. */
    private void noteSeen(Player p, Entity e) {
        if (p == passPlayer && e == passEntity) return;   // 판정 중인 바로 그 쌍 — 판정이 결과로 올린다
        SeenList l = seenIndex.computeIfAbsent(p, k -> new SeenList());
        l.add(e);
        if (l.size >= l.compactAt) compactSeen(p, l);
    }

    /** 지금 이 저장소에 있고 p 를 보는 것만, 겹치지 않게 남긴다. */
    private void compactSeen(Player p, SeenList l) {
        int m = 0;
        for (int i = 0; i < l.size; i++) {
            Entity e = l.items[i];
            if (e.tracker != null && e.tracker.isSeenBy(p) && entities.holds(e)) l.items[m++] = e;
        }
        m = entities.orderAndDedupe(l.items, m);
        java.util.Arrays.fill(l.items, m, l.size, null);
        l.size = m;
        l.compactAt = Math.max(32, m * 2);
    }

    /** 다른 레벨에서 추적 상태째 들어온 엔티티: 보는 사람을 이 레벨 색인에 올린다. */
    private void adoptTracker(Entity e) {
        maxTrackRange = Math.max(maxTrackRange, e.tracker.range());
        e.tracker.forEachViewer(p -> noteSeen(p, e));
    }

    private void absorbSeenIndex(ChunkLevel other) {
        maxTrackRange = Math.max(maxTrackRange, other.maxTrackRange);
        for (var en : other.seenIndex.entrySet()) {
            SeenList src = en.getValue();
            for (int i = 0; i < src.size; i++) noteSeen(en.getKey(), src.items[i]);
        }
        other.seenIndex.clear();
    }

    /** 이 region 에 없는 플레이어의 목록: grace 동안은 남기고(돌아오면 그대로 쓴다), 아무도 안 보게 되면 지운다. */
    private void pruneAbsentSeen(java.util.function.Predicate<Player> present) {
        if (seenIndex.isEmpty()) return;
        for (var it = seenIndex.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = en.getKey();
            if (present.test(p) && !p.isDisconnected()) continue;
            compactSeen(p, en.getValue());
            if (en.getValue().size == 0) it.remove();
        }
    }

    /** 단언용: 이 저장소에서 p 를 보는 엔티티가 모두 역색인에 있는가. */
    private boolean seenIndexCovers(Player p) {
        SeenList l = seenIndex.get(p);
        java.util.Set<Entity> in = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (l != null) for (int i = 0; i < l.size; i++) in.add(l.items[i]);
        for (Entity e : entities.all()) {
            if (e.tracker != null && !e.isRemoved() && e.tracker.isSeenBy(p) && !in.contains(e)) return false;
        }
        return true;
    }

    /** 단언용: updatePlayer 가 무엇이든 바꿀 수 있는 엔티티(p 를 보거나 시야 거리 안)가 모두 후보(순서대로 정렬됨)에 있는가. */
    private boolean candidatesComplete(Player p, Entity[] buf, int n) {
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) keys[i] = org.domi.furnace.server.entity.EntityStore.orderKey(buf[i]);
        for (int i = 1; i < n; i++) if (keys[i] <= keys[i - 1]) return false;
        for (Entity e : entities.all()) {
            if (e.tracker == null || e.isRemoved()) continue;
            double r = Math.min(e.tracker.range(), viewDistanceFor(p) * 16);   // updatePlayer 와 같은 식
            double dx = p.x() - e.x(), dz = p.z() - e.z();
            boolean mayChange = e.tracker.isSeenBy(p) || dx * dx + dz * dz <= r * r;
            if (mayChange && java.util.Arrays.binarySearch(keys, org.domi.furnace.server.entity.EntityStore.orderKey(e)) < 0) return false;
        }
        return true;
    }

    /**
     * 플러그인 보이기·변장 규칙이 바뀌었다(엔티티를 가진 region 스레드에서): viewer(null 이면 모두)에 대해 보임을 다시 정하고,
     * respawn 이면 지금 보는 사람에게 지웠다 다시 스폰해 새 모습을 보낸다.
     */
    public void refreshViews(Entity e, Player viewer, boolean respawn) {
        EntityTracker t = e.tracker;
        if (t == null || e.isRemoved() || e.level() != this) return;
        if (respawn) {
            for (Player p : t.viewersSnapshot()) if (viewer == null || viewer == p) t.removePlayer(p);
        }
        for (Player p : trackingPlayers()) if (viewer == null || viewer == p) t.updatePlayer(p, viewers);   // 이 region 의 플레이어만(다른 차원 좌표와 섞이지 않게)
    }

    /** 플레이어가 나갔다 — 보는 목록에서 뺀다(패킷 없이). */
    public void playerLeft(Player p) {
        for (Entity e : entities.all()) if (e.tracker != null) e.tracker.removePlayer(p);
        seenIndex.remove(p);
    }

    /** 플레이어가 다른 차원으로 갔다 — 추적만 지운다(클라이언트는 리스폰 패킷에서 엔티티를 모두 버리므로 제거 패킷은 보내지 않는다). */
    public void playerChangedDimension(Player p) {
        for (Entity e : entities.all()) if (e.tracker != null) e.tracker.forgetPlayer(p);
        seenIndex.remove(p);
    }

    // ------------------------------------------------------------------ 엔티티 저장(entities/ — 바닐라 EntityStorage)

    /** 불러온 엔티티 묶음을 넣는다(addLegacyChunkEntities). 다루지 못하는 종류는 원본 NBT 로 청크에 둔다. */
    /**
     * ChunkMap.postLoadProtoChunk → ServerLevel.addWorldGenChunkEntities: 월드젠(구조물 템플릿·지형 지물)이 청크 NBT 로 남긴 엔티티를
     * 청크를 올릴 때 한 번 실제 엔티티로 넣고 목록을 비운다(옛 저장의 chunk "entities" 도 같은 길 — 바닐라 postLoadChunk).
     * 모르는 종류는 버리지 않고 엔티티 묶음 저장으로 넘긴다. 넣었으면 참(청크를 저장 대상으로).
     */
    public boolean addWorldGenChunkEntities(ChunkColumn col) {
        var list = col.generatedEntities();
        if (list.isEmpty()) return false;
        var copy = new java.util.ArrayList<>(list);
        col.clearGeneratedEntities();
        // 청크 파일을 다시 써야 "entities" 가 지워진다 — 아니면 디스크 청크를 올릴 때마다 또 넣어 복제된다(넣은 것은 entities/ 에 저장된다)
        col.markDirty();
        for (var tag : copy) {
            Entity e = org.domi.furnace.server.entity.Entities.load(this, tag);
            if (e == null) col.foreignEntities().add(tag);
            else addFreshEntityWithPassengers(e);
        }
        return true;
    }

    public void loadChunkEntities(ChunkColumn col, org.domi.furnace.server.net.io.nbt.NbtCompound root) {
        col.entitiesLoaded(true);
        if (root == null || !(root.tags().get("Entities") instanceof org.domi.furnace.server.net.io.nbt.NbtList list) || list.size() == 0) return;
        col.entitiesOnDisk(true);
        for (Object o : list.items()) {
            if (!(o instanceof org.domi.furnace.server.net.io.nbt.NbtCompound tag)) continue;
            Entity e = org.domi.furnace.server.entity.Entities.load(this, tag);
            if (e == null) col.foreignEntities().add(tag);
            else addFreshEntityWithPassengers(e);
        }
    }

    /**
     * 그 청크의 엔티티를 저장할 묶음({DataVersion, Position, Entities}). 비었고 디스크에도 없으면 null(쓸 것 없음).
     * unload 면 저장한 뒤 엔티티를 UNLOADED_TO_CHUNK 로 뺀다. 디스크의 것을 아직 못 불러왔으면 덮어쓰지 않게 null.
     */
    public org.domi.furnace.server.net.io.nbt.NbtCompound chunkEntitiesForSave(ChunkColumn col, int cx, int cz, boolean unload) {
        if (!col.entitiesLoaded()) {
            // 디스크 것을 아직 못 읽었다 — 덮어쓰지 않는다. 내리는 중이면 그사이 생긴 엔티티는 디스크 묶음에 덧붙인다
            if (unload) {
                java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> orphans = new java.util.ArrayList<>();
                for (Entity e : entities.inChunk(cx, cz)) {
                    var tag = e.savedWithChunk() ? e.save() : null;
                    if (tag != null) orphans.add(tag);
                    e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                }
                if (!orphans.isEmpty()) appendEntitiesToDisk(cx, cz, orphans);
            }
            return null;
        }
        var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        java.util.List<Entity> in = entities.inChunk(cx, cz);
        for (Entity e : in) {
            var tag = e.savedWithChunk() ? e.save() : null;   // 플레이어 하나만 태운 탈것은 그 플레이어 데이터에(RootVehicle)
            if (tag != null) list.add(tag);
        }
        for (var tag : col.foreignEntities()) list.add(tag);
        if (unload) for (Entity e : in) e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        if (list.size() == 0) {
            if (!col.entitiesOnDisk()) return null;
            col.entitiesOnDisk(false);
        } else {
            col.entitiesOnDisk(true);
        }
        var root = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        root.putInt("DataVersion", org.domi.furnace.server.world.anvil.AnvilChunkCodec.DATA_VERSION);
        root.putIntArray("Position", new int[] {cx, cz});
        root.putList("Entities", list);
        return root;
    }

    /** 로드되지 않은 청크의 디스크 엔티티 묶음에 덧붙인다(기본: 저장소 없음 — 버린다). */
    protected void appendEntitiesToDisk(int cx, int cz, java.util.List<org.domi.furnace.server.net.io.nbt.NbtCompound> tags) {}

    /**
     * 아무 region 도 로드하지 않은 청크로 들어간 엔티티: 저장해 그 청크의 디스크 묶음에 덧붙이고 뺀다(바닐라는 숨은 구역에 두었다가
     * 청크와 함께 저장한다 — 여기서는 주인이 없는 청크를 들 수 없다).
     */
    public void evictToDisk(Entity e) {
        if (e.isPassenger()) return;   // 뿌리 탈것이 승객째 저장한다
        var tag = e.save();
        if (e.isVehicle()) {
            java.util.List<org.domi.furnace.server.entity.Rider> riders = new java.util.ArrayList<>();
            e.collectIndirectPassengers(riders);
            for (int i = riders.size() - 1; i >= 0; i--) if (riders.get(i) instanceof Entity pe) pe.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
        e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        if (tag != null) appendEntitiesToDisk(e.chunkX(), e.chunkZ(), java.util.List.of(tag));
    }

    // ------------------------------------------------------------------ 엔티티 충돌(바닐라 BlockCollisions)

    /**
     * 칸 하나의 충돌 결과를 받는다. shape 는 칸 원점 기준(옮기지 않은) 모양이고 (x, y, z)만큼 옮긴 것이 월드 모양이다 — 모양 객체를 칸마다
     * 새로 만들지 않으려고 옮기지 않은 채 넘긴다. false 면 수집을 멈춘다.
     */
    @FunctionalInterface
    public interface CollisionSink {
        boolean accept(int x, int y, int z, Shape shape);
    }

    /**
     * 상태별 충돌 분류(T-018). 블록 동작이 getCollisionShape(문맥) 를 덮어쓰지 않으면 모양은 상태만으로 정해진다
     * (BlockBehavior 기본 = BlockInfo.collisionShape) — 그 모양을 미리 들고, 비었으면 칸을 읽자마자 건너뛴다(공기·동굴 공기·풀·물 등).
     * 덮어쓰는 동작(움직이는 피스톤, 가루눈, 비계, 물·용암)만 칸마다 동작을 부른다. 동작 표는 처음 만든 뒤 바뀌지 않는다(BlockBehaviors).
     * 액체(FLUID)는 유체 위에 설 수 있는 문맥(스트라이더)에서만 모양이 생기므로 그 밖의 문맥은 동작을 부르지 않고 건너뛴다.
     */
    static final class CollisionTable {
        static final byte NONE = 0, STATIC = 1, DYNAMIC = 2, KIND = 3, LARGE = 4, PISTON = 8, FLUID = 16;
        static final byte[] FLAGS;
        static final Shape[] SHAPES;

        static {
            Blocks blocks = Blocks.get();
            BlockInfo info = BlockInfo.get();
            int n = blocks.stateCount;
            byte[] f = new byte[n];
            Shape[] s = new Shape[n];
            int piston = B.id("moving_piston");
            // 덮어쓰기 판정은 블록마다 한 번(동작 클래스가 같으면 리플렉션도 한 번) — 상태마다 하면 표 만들기가 0.2초 걸렸다(T-018 R2)
            java.util.Map<Class<?>, Boolean> byClass = new java.util.IdentityHashMap<>();
            boolean[] ctxBlock = new boolean[blocks.blockCount()];
            for (int b = 0; b < ctxBlock.length; b++) ctxBlock[b] = byClass.computeIfAbsent(BlockBehaviors.ofBlock(b).getClass(), CollisionTable::contextual);
            for (int st = 0; st < n; st++) {
                int block = blocks.blockOf(st);
                Shape shape = info.collisionShape(st);
                // hasLargeCollisionShape: 충돌 모양이 칸 밖으로 나가거나 모양이 바뀌는 블록
                boolean large = info.dynamicShape(block);
                double[] b = shape.bounds();
                if (b != null) large |= b[0] < 0 || b[1] < 0 || b[2] < 0 || b[3] > 1 || b[4] > 1 || b[5] > 1;
                int v;
                if (ctxBlock[block]) v = BlockBehaviors.ofBlock(block) instanceof org.domi.furnace.server.block.types.LiquidBlock ? DYNAMIC | FLUID : DYNAMIC;
                else if (shape.isEmpty()) v = NONE;
                else { v = STATIC; s[st] = shape; }
                if (large) v |= LARGE;
                if (block == piston) v |= PISTON;
                f[st] = (byte) v;
            }
            FLAGS = f;
            SHAPES = s;
        }

        /** 충돌 모양을 칸·문맥에 따라 정하는 동작인가(BlockBehavior 기본 구현을 덮어썼는가). */
        static boolean contextual(Class<?> c) {
            try {
                return c.getMethod("getCollisionShape", int.class, BlockLevel.class, Pos.class, CollisionContext.class).getDeclaringClass() != BlockBehavior.class;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 충돌 상태 표를 지금 만든다(서버 시작 때 — 블록 동작 표와 아이템 번호표가 깔린 뒤). */
    public static void warmCollisionTable() {
        if (CollisionTable.FLAGS.length == 0) throw new IllegalStateException("충돌 상태 표가 비었다");
    }

    /**
     * BlockCollisions(바닐라 Cursor3D: x 가 가장 빨리, 그다음 y, z) 순서로 몸통과 겹치는 칸 충돌 모양을 넘긴다. 모서리 칸(세 축 끝)은 건너뛰고,
     * 면 칸은 큰 모양 블록만, 변 칸은 움직이는 피스톤만 본다. 문맥에 따라 모양이 다른 블록(비계·액체·가루눈)은 self 의 문맥
     * (바닐라 BlockCollisions 의 CollisionContext.of(entity), self 가 없으면 empty)으로 묻는다. 문맥은 그런 칸을 처음 만날 때 만든다.
     * 칸 순서·판정은 칸마다 읽던 예전 구현(T-018 전)과 같고, 구역 단위로 읽어 공기뿐인 구역은 통째로 건너뛴다.
     */
    public void scanCollisions(AABB box, Rider self, CollisionSink out) { scanCollisions(box, self, Double.NaN, out); }

    /**
     * scanCollisions 이되 문맥의 발 높이를 selfBottom 으로(NaN 이면 self.y()) — 바닐라가 엔티티를 옮긴 뒤 묻는 곳(플레이어 이동 검증)용.
     */
    public void scanCollisions(AABB box, Rider self, double selfBottom, CollisionSink out) {
        scanCollisions(box, self, selfBottom, self == null ? CollisionContext.EMPTY : null, out);   // self 가 있으면 문맥 블록을 처음 만날 때 만든다
    }

    /** scanCollisions 이되 이미 만든 문맥으로(다른 스레드에서 떠 온 스냅샷 — 포털 출구 자리 찾기). */
    public void scanCollisionsWith(AABB box, CollisionContext ctx, CollisionSink out) { scanCollisions(box, null, Double.NaN, ctx, out); }

    /** ctx 가 null 이면 self 로 처음 쓸 때 만든다(그때 self 는 null 이 아니다). */
    private void scanCollisions(AABB box, Rider self, double selfBottom, CollisionContext ctx, CollisionSink out) {
        // 액체는 원천 유체 위에 설 수 있는 문맥(스트라이더)에서만 모양이 생긴다 — 나머지(물속 몹·배·플레이어·문맥 없음)는 액체 칸을 공기처럼
        // 첫 검사에서 건너뛴다(문맥도 만들지 않는다). CollisionContext.of 와 같은 판정(Mob.canStandOnFluid(원천))
        boolean standsOnFluid = ctx != null ? ctx.standsOnAnyFluid()
                : self instanceof org.domi.furnace.server.entity.Mob m && (m.canStandOnFluid(Fluids.WATER_SOURCE) || m.canStandOnFluid(Fluids.LAVA_SOURCE));
        int x0 = Entity.floor(box.minX() - 1.0E-7) - 1, x1 = Entity.floor(box.maxX() + 1.0E-7) + 1;
        int y0 = Entity.floor(box.minY() - 1.0E-7) - 1, y1 = Entity.floor(box.maxY() + 1.0E-7) + 1;
        int z0 = Entity.floor(box.minZ() - 1.0E-7) - 1, z1 = Entity.floor(box.maxZ() + 1.0E-7) + 1;
        int minY = minBuildHeight(), maxY = maxBuildHeight();
        byte[] flags = CollisionTable.FLAGS;
        Shape[] shapes = CollisionTable.SHAPES;
        int cx0 = x0 >> 4, nCols = (x1 >> 4) - cx0 + 1;
        ChunkColumn c0 = null, c1 = null;              // 흔한 경우(청크 두 개 이하)는 배열 없이
        ChunkColumn[] cols = nCols > 2 ? new ChunkColumn[nCols] : null;
        int fetched = 0;                               // 지금 z 청크에서 찾아본 열 수(앞에서부터) — 예전처럼 처음 닿을 때 찾는다
        int colsCz = Integer.MIN_VALUE;
        for (int z = z0; z <= z1; z++) {
            int cz = z >> 4;
            if (cz != colsCz) {
                colsCz = cz;
                fetched = 0;
            }
            int lz = z & 15;
            int zEdge = z == z0 || z == z1 ? 1 : 0;
            for (int y = y0; y <= y1; y++) {
                int yzEdges = zEdge + (y == y0 || y == y1 ? 1 : 0);
                boolean inWorld = y >= minY && y < maxY;
                for (int ci = 0; ci < nCols; ci++) {
                    ChunkColumn c;
                    if (ci < fetched) {
                        c = cols != null ? cols[ci] : ci == 0 ? c0 : c1;
                    } else {
                        c = chunk(cx0 + ci, cz);
                        if (cols != null) cols[ci] = c;
                        else if (ci == 0) c0 = c;
                        else c1 = c;
                        fetched = ci + 1;
                    }
                    if (c == null || !inWorld) continue;   // 세계 높이 밖은 공기(충돌 없음)
                    int local = y - c.minY();
                    org.domi.furnace.server.world.ChunkSection sec = c.sectionForRead(local >> 4);
                    // 공기·동굴 공기·빈 공간 공기뿐 — 충돌 모양이 하나도 없다. scratch 가 열린 구역(월드젠 표면 단계)은 비공기 수가 낡았을 수 있어 칸을 읽는다(T-018 R3)
                    if (sec.hasOnlyAir() && !sec.scratchOpen()) continue;
                    int ly = local & 15;
                    int bx = (cx0 + ci) << 4;
                    int xs = Math.max(x0, bx), xe = Math.min(x1, bx + 15);
                    for (int x = xs; x <= xe; x++) {
                        int edges = yzEdges + (x == x0 || x == x1 ? 1 : 0);
                        if (edges == 3) continue;
                        int state = sec.getBlock(x & 15, ly, lz);
                        int f = flags[state];
                        int kind = f & CollisionTable.KIND;
                        if (kind == CollisionTable.NONE || (f & CollisionTable.FLUID) != 0 && !standsOnFluid) continue;
                        if (edges == 1 && (f & CollisionTable.LARGE) == 0 || edges == 2 && (f & CollisionTable.PISTON) == 0) continue;
                        Shape shape;
                        if (kind == CollisionTable.STATIC) {
                            shape = shapes[state];
                        } else {
                            if (ctx == null) ctx = CollisionContext.of(self, Double.isNaN(selfBottom) ? self.y() : selfBottom);
                            shape = BlockBehaviors.of(state).getCollisionShape(state, this, new Pos(x, y, z), ctx);
                            if (shape.isEmpty()) continue;
                        }
                        if (shape == Shape.BLOCK) {
                            if (!box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) continue;
                        } else if (!shape.overlaps(box, x, y, z)) {
                            continue;
                        }
                        if (!out.accept(x, y, z, shape)) return;
                    }
                }
            }
        }
    }

    /** getBlockCollisions(entity, box): 몸통과 겹치는 블록 충돌 모양들. */
    public java.util.List<Shape> blockCollisions(AABB box) { return blockCollisions(null, box); }

    /** getBlockCollisions(entity, box): 문맥 엔티티가 있으면 그 엔티티 기준 충돌 모양(비계·가루눈 등). 월드 좌표로 옮긴 모양 목록. */
    public java.util.List<Shape> blockCollisions(Rider entity, AABB box) { return blockCollisions(entity, Double.NaN, box); }

    /** blockCollisions 이되 문맥 발 높이를 selfBottom 으로(NaN 이면 entity.y()). */
    public java.util.List<Shape> blockCollisions(Rider entity, double selfBottom, AABB box) {
        java.util.List<Shape> out = new java.util.ArrayList<>();
        scanCollisions(box, entity, selfBottom, (x, y, z, s) -> out.add(s.move(x, y, z)));
        return out;
    }

    /** getBlockCollisions 를 이미 만든 문맥으로(포털 출구 자리 찾기 — 떠나는 엔티티의 출발 쪽 문맥 스냅샷). */
    public java.util.List<Shape> blockCollisionsWith(CollisionContext ctx, AABB box) {
        java.util.List<Shape> out = new java.util.ArrayList<>();
        scanCollisionsWith(box, ctx, (x, y, z, s) -> out.add(s.move(x, y, z)));
        return out;
    }

    /** blockCollisions 를 모양 객체 없이 충돌 목록(옮기지 않은 모양 + 칸)에 덧붙인다 — 엔티티 이동(collide)이 쓴다. */
    public void blockCollisions(Rider entity, AABB box, Colliders out) {
        scanCollisions(box, entity, out);
    }

    /** BlockGetter.containsAnyLiquid(box): 상자가 유체 칸과 겹치나. */
    public boolean containsAnyLiquid(AABB box) {
        int x0 = Entity.floor(box.minX()), x1 = (int) Math.ceil(box.maxX());
        int y0 = Entity.floor(box.minY()), y1 = (int) Math.ceil(box.maxY());
        int z0 = Entity.floor(box.minZ()), z1 = (int) Math.ceil(box.maxZ());
        for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) {
            if (!Fluids.isEmpty(getFluidState(new Pos(x, y, z)))) return true;
        }
        return false;
    }

    /** CollisionGetter.noCollision(box) = noCollision(null, box): 블록 충돌과 단단한 엔티티(배·셜커) 몸이 없는가. 월드 보더는 보지 않는다. */
    public boolean noCollision(AABB box) { return noCollision(null, box); }

    /** CollisionGetter.noCollision(self, box): 블록(self 문맥) → 엔티티(self.canCollideWith) → self 가 있으면 월드 보더(self 위치 기준). */
    public boolean noCollision(org.domi.furnace.server.entity.Rider self, AABB box) {
        return self == null ? noCollisionAt(null, 0.0, Double.NaN, 0.0, box, true) : noCollisionAt(self, self.x(), Double.NaN, self.z(), box, true);
    }

    /**
     * noCollision(self, box) 이되 self 가 (sx, sy, sz) 에 있는 것으로 본다 — 보더 가까움(isInsideCloseToBorder)과 블록 문맥의 발 높이(비계·가루눈).
     * 바닐라가 self 를 이미 옮긴 뒤 부르는 곳(플레이어 이동 검증)에서 Furnace 는 옮기기 전에 계산하기 때문.
     */
    public boolean noCollision(org.domi.furnace.server.entity.Rider self, double sx, double sy, double sz, AABB box) {
        return noCollisionAt(self, sx, sy, sz, box, true);
    }

    /**
     * 길찾기의 noCollision(mob, box): 바닐라는 PathNavigationRegion 위에서 부르고, 그 getEntityCollisions 는 언제나 빈 목록이다 —
     * 블록(mob 문맥)과 월드 보더만 본다.
     */
    public boolean noCollisionIgnoringEntities(Entity self, AABB box) { return noCollisionAt(self, self.x(), Double.NaN, self.z(), box, false); }

    /**
     * noCollision(플레이어, box) 를 Player 객체 없이: 블록은 플레이어 문맥(ctx — 떠 둔 스냅샷·갓 만든 플레이어), 탈것에 타지 않은 플레이어는
     * canCollideWith 가 canBeCollidedWith 와 같아 엔티티 몫은 noCollision(null, box) 와 같다. 보더 가까움은 (sx, sz) 로 본다(스폰 자리 흩뿌리기).
     */
    public boolean noCollisionForUnmountedPlayer(CollisionContext ctx, double sx, double sz, AABB box) {
        boolean[] hit = {false};
        scanCollisionsWith(box, ctx, (x, y, z, s) -> {
            hit[0] = true;
            return false;
        });
        if (hit[0] || !entityCollisions(null, box).isEmpty()) return false;
        return borderClear(sx, sz, box);
    }

    private boolean noCollisionAt(org.domi.furnace.server.entity.Rider self, double sx, double sy, double sz, AABB box, boolean withEntities) {
        if (!noBlockCollision(self, sy, box)) return false;
        if (withEntities && !entityCollisions(self, box).isEmpty()) return false;
        return self == null || borderClear(sx, sz, box);
    }

    private boolean borderClear(double sx, double sz, AABB box) {
        Shape border = borderCollision(sx, sz, box);
        // Shapes.joinIsNotEmpty(border, Shapes.create(box), AND) — 한 변이 1e-7 미만인 상자는 빈 모양이라 겹치지 않는다
        return border == null || Shape.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).isEmpty()
                || !border.overlaps(box, 0.0, 0.0, 0.0);
    }

    /** CollisionGetter.noBlockCollision(self, box): 블록 충돌 모양(self 가 있으면 그 문맥 — 비계·가루눈 등)이 없는가. 엔티티·보더는 보지 않는다. */
    public boolean noBlockCollision(Rider self, AABB box) { return noBlockCollision(self, Double.NaN, box); }

    /** noBlockCollision 이되 문맥 발 높이를 selfBottom 으로(NaN 이면 self.y()). */
    public boolean noBlockCollision(Rider self, double selfBottom, AABB box) {
        boolean[] hit = {false};
        scanCollisions(box, self, selfBottom, (x, y, z, s) -> {
            hit[0] = true;
            return false;
        });
        return !hit[0];
    }

    /** CollisionGetter.getCollisions(self, box): 엔티티 충돌 모양 다음 블록 충돌 모양(보더 없음). */
    public java.util.List<Shape> collisions(org.domi.furnace.server.entity.Rider self, AABB box) { return collisions(self, Double.NaN, box); }

    /** collisions 이되 블록 문맥의 발 높이를 selfBottom 으로(NaN 이면 self.y()) — 플레이어 이동 검증이 옮긴 자리에서 묻는다. */
    public java.util.List<Shape> collisions(org.domi.furnace.server.entity.Rider self, double selfBottom, AABB box) {
        java.util.List<Shape> ents = entityCollisions(self, box);
        java.util.List<Shape> blocks = blockCollisions(self, selfBottom, box);
        if (ents.isEmpty()) return blocks;
        java.util.List<Shape> out = new java.util.ArrayList<>(ents.size() + blocks.size());
        out.addAll(ents);
        out.addAll(blocks);
        return out;
    }

    /**
     * EntityGetter.getEntityCollisions(self, box): 상자 크기(세 변 평균)가 1e-7 미만이면 빈 목록. 아니면 1e-7 넓힌 상자와 겹치는 관전자 아닌 엔티티 중
     * self 가 없으면 canBeCollidedWith, 있으면 self.canCollideWith(그것)인 것의 몸 모양(Shapes.create — 빈 모양도 목록에 든다).
     * 순서는 바닐라 getEntities 의 구역 순·넣은 순이고, 플레이어(배·수레만 부딪힌다)는 Furnace 에서 따로 들고 있어 엔티티 뒤에 온다.
     * self 가 상대의 canBeCollidedWith 만 보는 경우(배·수레 말고 전부)는 HardCollider 구역 목록만 훑는다(같은 결과·같은 순서).
     */
    public java.util.List<Shape> entityCollisions(org.domi.furnace.server.entity.Rider self, AABB box) {
        if (box.size() < 1.0E-7) return java.util.List.of();
        AABB q = box.inflate(1.0E-7);
        Entity selfEntity = self instanceof Entity e ? e : null;
        boolean hardOnly = selfEntity == null || selfEntity.collidesOnlyWithHardColliders();
        if (hardOnly) {
            // 몹·투사체 등 대부분: 단단한 엔티티(배·셜커) 구역 목록만, 목록은 찾았을 때만 만든다(getEntitiesOfType 과 같은 순서·조건)
            if (entities.isEmpty()) return java.util.List.of();
            java.util.ArrayList<Shape>[] hit = new java.util.ArrayList[1];
            entities.forEachIn(q, org.domi.furnace.server.entity.HardCollider.class, e -> {
                if (e instanceof org.domi.furnace.server.entity.HardCollider && e != self && !e.isSpectator() && canCollide(self, e)) {
                    AABB b = e.boundingBox();
                    (hit[0] == null ? hit[0] = new java.util.ArrayList<>(2) : hit[0]).add(Shape.box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                }
            });
            return hit[0] == null ? java.util.List.of() : hit[0];
        }
        java.util.List<Shape> out = new java.util.ArrayList<>(2);
        if (!entities.isEmpty()) {
            java.util.function.Predicate<Entity> ok = e -> e != self && !e.isSpectator() && canCollide(self, e);
            for (Entity e : entities.getEntities(selfEntity, q, ok)) {
                AABB b = e.boundingBox();
                out.add(Shape.box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
        }
        {
            forEachPlayerIn(q, p -> {
                if (p != self && !p.isSpectator() && selfEntity.canCollideWithPlayer(p)) {
                    AABB b = p.boundingBox();
                    out.add(Shape.box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                }
            });
        }
        return out.isEmpty() ? java.util.List.of() : out;
    }

    /** self.canCollideWith(e): self 없음 = canBeCollidedWith, 엔티티 = 그 덮어쓰기, 플레이어 = Entity 기본(단단하고 같은 탈것 무리가 아님). */
    private static boolean canCollide(org.domi.furnace.server.entity.Rider self, Entity e) {
        if (self == null) return e.canBeCollidedWith();
        if (self instanceof Entity s) return s.canCollideWith(e);
        return e.canBeCollidedWith() && !e.isPassengerOfSameVehicle(self);
    }

    /** 마지막 보더 모양과 그 정수 경계. 여러 region 스레드가 읽으므로 한 객체로 바꿔 끼운다. */
    private record BorderShapeMemo(double x0, double z0, double x1, double z1, Shape shape) {}
    private volatile BorderShapeMemo borderShapeMemo;

    /**
     * CollisionGetter.borderCollision → WorldBorder.isInsideCloseToBorder(self, box): d = max(|가로 길이|, |세로 길이|, 1) 일 때
     * self 위치 (sx, sz)가 보더까지 2d 미만이고 보더를 d 만큼 넓힌 안이면 보더 바깥 모양(정수로 넓힌 보더 기둥 밖), 아니면 null.
     */
    public Shape borderCollision(double sx, double sz, AABB box) {
        double[] b = borderBounds();
        double d = Math.max(Math.max(Math.abs(box.getXsize()), Math.abs(box.getZsize())), 1.0);
        double dist = Math.min(Math.min(Math.min(sx - b[0], b[2] - sx), sz - b[1]), b[3] - sz);
        if (!(dist < d * 2.0) || !(sx >= b[0] - d && sx < b[2] + d && sz >= b[1] - d && sz < b[3] + d)) return null;
        double x0 = Math.floor(b[0]), z0 = Math.floor(b[1]), x1 = Math.ceil(b[2]), z1 = Math.ceil(b[3]);
        BorderShapeMemo m = borderShapeMemo;
        // -0.0 과 0.0 을 가른다(Math.ceil(-0.3) = -0.0 이 모양 좌표에 그대로 남아 collide 결과의 부호가 된다).
        if (m == null || !same(m.x0, x0) || !same(m.z0, z0) || !same(m.x1, x1) || !same(m.z1, z1)) {
            borderShapeMemo = m = new BorderShapeMemo(x0, z0, x1, z1, Shape.outsideBorder(x0, z0, x1, z1));
        }
        return m.shape;
    }

    private static boolean same(double a, double b) { return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b); }

    /** findSupportingBlock(entity, box): 겹치는 충돌 블록 중 엔티티 위치에서 가운데가 가장 가까운 것(같으면 compareTo 가 큰 쪽). */
    public Pos findSupportingBlock(AABB box, double ex, double ey, double ez) { return findSupportingBlock(null, box, ex, ey, ez); }

    public Pos findSupportingBlock(Rider entity, AABB box, double ex, double ey, double ez) {
        SupportSearch s = new SupportSearch(ex, ey, ez);
        scanCollisions(box, entity, s);
        return s.found ? new Pos(s.bx, s.by, s.bz) : null;
    }

    /** findSupportingBlock 의 가장 가까운 칸 찾기(칸 객체 없이). 거리가 같으면 Vec3i.compareTo(y, z, x 순)가 큰 쪽. */
    private static final class SupportSearch implements CollisionSink {
        final double ex, ey, ez;
        boolean found;
        int bx, by, bz;
        double bestDist = Double.MAX_VALUE;

        SupportSearch(double ex, double ey, double ez) {
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
        }

        @Override
        public boolean accept(int x, int y, int z, Shape shape) {
            double dx = x + 0.5 - ex, dy = y + 0.5 - ey, dz = z + 0.5 - ez;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist || d == bestDist && (!found || compareVec(bx, by, bz, x, y, z) < 0)) {
                found = true;
                bx = x;
                by = y;
                bz = z;
                bestDist = d;
            }
            return true;
        }
    }

    /** Vec3i.compareTo: y, z, x 순. */
    private static int compareVec(int ax, int ay, int az, int bx, int by, int bz) {
        if (ay != by) return Integer.compare(ay, by);
        if (az != bz) return Integer.compare(az, bz);
        return Integer.compare(ax, bx);
    }

    @Override
    public int itemFrameAnalogOutput(Pos pos, int facing) {
        var l = entities.getEntitiesOfClass(org.domi.furnace.server.entity.ItemFrame.class,
                new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0), f -> f != null && f.direction() == facing);
        return l.size() == 1 ? l.get(0).getAnalogOutput() : Integer.MIN_VALUE;
    }

    @Override
    public Object firstArrowIn(double x0, double y0, double z0, double x1, double y1, double z1) {
        var l = entities.getEntitiesOfClass(org.domi.furnace.server.entity.AbstractArrow.class, new AABB(x0, y0, z0, x1, y1, z1), e -> !e.isSpectator());
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public boolean isCatSittingAbove(Pos pos) {
        var l = entities.getEntitiesOfClass(org.domi.furnace.server.entity.Cat.class,
                new AABB(pos.x(), pos.y() + 1, pos.z(), pos.x() + 1, pos.y() + 2, pos.z() + 1), c -> c.isInSittingPose());
        return !l.isEmpty();
    }

    @Override
    public int countTriggeringEntities(double x0, double y0, double z0, double x1, double y1, double z1, boolean livingOnly) {
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);
        int[] n = {entities.getEntities(null, box, e -> !e.isIgnoringBlockTriggers() && !e.isSpectator() && (!livingOnly || e.isLiving())).size()};
        services().forEachPlayer(p -> {
            if (p.isDisconnected() || p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            double h = p.bbHeight();
            if (box.intersects(p.x() - 0.3, p.y(), p.z() - 0.3, p.x() + 0.3, p.y() + h, p.z() + 0.3)) n[0]++;
        });
        return n[0];
    }

    /** hasChunksAt(minX, minZ, maxX, maxZ) — 블록 좌표. */
    public boolean hasChunksAt(int minX, int minZ, int maxX, int maxZ) {
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) if (chunk(cx, cz) == null) return false;
        }
        return true;
    }
}
