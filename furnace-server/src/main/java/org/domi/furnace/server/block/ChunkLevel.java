// BlockLevel 구현의 뼈대 — 바닐라 Level.setBlock / LevelChunk.setBlockState / 이웃 갱신 / 신호 / 소리·이벤트 방송을 청크 열 위에서.
// 청크를 어디서 얻는지(소유 region 의 로드된 청크)와 변경 방송·저장 표시는 하위 클래스가 정한다.
package org.domi.furnace.server.block;

import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityStore;
import org.domi.furnace.server.entity.EntityTracker;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
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
    private final RandomSource random = RandomSource.create();
    private final RandomSource soundRandom = RandomSource.create();
    private final BlockTicks blockTicks = new BlockTicks();
    private final BlockTicks fluidTicks = new BlockTicks();
    private long subTickCount;
    /** Level.randValue — 무작위 칸 고르기 LCG(getBlockRandomPos). 바닐라처럼 처음 값은 임의. */
    private int randValue = new java.util.Random().nextInt();
    /** 지역 틱 시계(예약 틱 기준 — Folia 의 redstoneTime 처럼 region 마다 따로 간다. 월드 시간은 gameTime()). */
    private long levelTime;
    /** ServerLevel.handlingTick — 예약 틱을 돌리는 동안 참. */
    private boolean handlingTick;
    /** 방송할 변경: 구역 키(청크 x,z,구역 y) → 구역 안 칸 번호 집합(바닐라 ChunkHolder.changedBlocksPerSection). */
    private final TreeMap<Long, TreeSet<Short>> changed = new TreeMap<>();
    /** 바닐라 ObjectLinkedOpenHashSet<BlockEventData>: 넣은 순서대로, 같은 이벤트가 큐에 있으면 다시 넣지 않는다. */
    private final java.util.LinkedHashSet<BlockEventData> blockEvents = new java.util.LinkedHashSet<>();

    private record BlockEventData(int x, int y, int z, int block, int type, int data) {}

    protected ChunkLevel(int maxChainedNeighborUpdates) {
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
        if (old != null && old != be) removeGameEventListener(col, old);
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
     * tickBlockEntities: 지워진 것은 빼고, 틱 받는 청크에 있고 칸의 상태가 그 종류에 맞는 것만 tick.
     * ticking 은 그 칸의 청크가 이번 틱에 블록 엔티티 틱을 받는가.
     */
    public void tickBlockEntities(java.util.function.LongPredicate ticking) {
        tickingBlockEntities = true;
        if (!pendingTickers.isEmpty()) {
            blockEntityTickers.addAll(pendingTickers);
            pendingTickers.clear();
        }
        int w = 0;
        for (int i = 0; i < blockEntityTickers.size(); i++) {
            BlockEntity be = blockEntityTickers.get(i);
            if (be.isRemoved()) continue;
            blockEntityTickers.set(w++, be);
            Pos p = be.pos();
            if (!ticking.test((long) (p.x() >> 4) << 32 | (p.z() >> 4) & 0xFFFFFFFFL)) continue;
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
            if ((flags & UPDATE_CLIENTS) != 0) markChanged(pos);
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
        int s = getBlockState(pos);
        if (info.isAir(s)) return false;
        int f = getFluidState(pos);
        if (!B.isTag(s, "minecraft:fire")) levelEvent(null, 2001, pos, s);
        if (dropBlock) dropResources(s, pos, info.hasBlockEntity(s) ? getBlockEntity(pos) : null, breaker, ItemStack.EMPTY);
        boolean ok = setBlock(pos, Fluids.createLegacyBlock(f), UPDATE_ALL, recursionLeft);
        if (ok) gameEvent("minecraft:block_destroy", pos, breaker, s);
        return ok;
    }

    @Override
    public java.util.List<ItemStack> lootDrops(String lootTable, int state, Pos pos, BlockActor entity, ItemStack tool, Float explosionRadius) {
        return services().blockDrops(lootTable, state, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, tool,
                entity == null ? null : entity.handle(), explosionRadius);
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

    @Override
    public void scheduleTick(Pos pos, int block, int delay, int priority) {
        blockTicks.schedule(pos, block, levelTime + delay, priority, subTickCount++);
        markDirty(pos.x() >> 4, pos.z() >> 4);   // 예약도 청크와 함께 저장한다
    }

    @Override
    public void scheduleFluidTick(Pos pos, int fluidType, int delay) {
        fluidTicks.schedule(pos, fluidType, levelTime + delay, 0, subTickCount++);
        markDirty(pos.x() >> 4, pos.z() >> 4);
    }

    // ------------------------------------------------------------------ 예약 틱 저장(청크 NBT block_ticks·fluid_ticks — SavedTick)

    private static final String[] FLUID_NAMES = {"minecraft:empty", "minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"};

    /** 그 청크에 예약이 있는가. */
    public boolean hasChunkTicks(int chunkX, int chunkZ) {
        return !blockTicks.chunkTicks(chunkX, chunkZ).isEmpty() || !fluidTicks.chunkTicks(chunkX, chunkZ).isEmpty();
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

    /** 불러온 청크 NBT 의 예약을 다시 건다(목록 순서대로 예약 순번) — 모르는 종류는 건너뛴다. 읽은 목록은 지운다. */
    public void loadChunkTicks(org.domi.furnace.server.net.io.nbt.NbtCompound root) {
        if (root == null) return;
        for (String key : new String[] {"block_ticks", "fluid_ticks"}) {
            if (!(root.tags().get(key) instanceof org.domi.furnace.server.net.io.nbt.NbtList list)) continue;
            boolean fluid = key.equals("fluid_ticks");
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
                (fluid ? fluidTicks : blockTicks).schedule(p, type, levelTime + e.getIntOr0("t"), e.getIntOr0("p"), subTickCount++);
            }
            root.tags().remove(key);
        }
    }

    @Override
    public boolean hasScheduledTick(Pos pos, int block) { return blockTicks.hasScheduledTick(pos, block); }

    @Override
    public boolean willTickThisTick(Pos pos, int block) { return blockTicks.willTickThisTick(pos, block); }

    /** 레벨 틱의 블록 틱 → 유체 틱(바닐라 ServerLevel.tick 순서). 지역 시계를 한 틱 진행한다. */
    public void runScheduledTicks() {
        long now = ++levelTime;
        handlingTick = true;
        blockTicks.tick(now, 65536, (pos, type) -> {
            int s = getBlockState(pos);
            if (blocks.blockOf(s) == type) BlockBehaviors.of(s).tick(s, this, pos, random);
        });
        fluidTicks.tick(now, 65536, (pos, type) -> {
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
    private Pos findLightningTargetAround(Pos pos) {
        Pos top = new Pos(pos.x(), getMotionBlockingHeight(pos.x(), pos.z()), pos.z());
        Pos rod = poiManager.findClosest(t -> t == org.domi.furnace.server.world.poi.PoiTypes.LIGHTNING_ROD,
                p -> p.y() == getWorldSurfaceHeight(p.x(), p.z()) - 1, top, 128, org.domi.furnace.server.world.poi.PoiManager.Occupancy.ANY);
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
    private Pos blockRandomPos(int x, int y, int z) {
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
        ChunkSection[] sections = col.sections();
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || !sec.isRandomlyTicking()) continue;
            int y0 = col.minY() + (si << 4);
            for (int n = 0; n < randomTickSpeed; n++) {
                Pos p = blockRandomPos(x0, y0, z0);
                int st = sec.getBlock(p.x() - x0, p.y() - y0, p.z() - z0);
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
        if (shouldFreeze(climate, below)) setBlockAndUpdate(below, blocks.defaultState("minecraft:ice"));
        if (isRaining()) {
            int max = gameRuleInt("snowAccumulationHeight");
            if (max > 0 && shouldSnow(climate, top)) {
                int s = getBlockState(top);
                if (blocks.blockOf(s) == SNOW) {
                    Prop layers = blocks.prop("layers");
                    int j = layers.intValue(s);
                    if (j < Math.min(max, 8)) {
                        // TODO(엔티티): Block.pushEntitiesUp(눈, 한 겹 더 쌓인 눈, level, pos)
                        setBlockAndUpdate(top, layers.withInt(s, j + 1));
                    }
                } else {
                    setBlockAndUpdate(top, blocks.defaultState(SNOW));
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

    // ------------------------------------------------------------------ region 합치기·나누기

    /** 다른 레벨의 자료를 모두 흡수한다(그 레벨은 버려진다). 예약 틱은 두 지역 시계 차이만큼 보정한다. */
    public void absorb(ChunkLevel other) {
        long offset = levelTime - other.levelTime;
        other.blockTicks.moveTo(blockTicks, p -> true, offset);
        other.fluidTicks.moveTo(fluidTicks, p -> true, offset);
        subTickCount = Math.max(subTickCount, other.subTickCount);
        for (var e : other.changed.entrySet()) changed.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).addAll(e.getValue());
        other.changed.clear();
        blockEvents.addAll(other.blockEvents);
        other.blockEvents.clear();
        blockEntityTickers.addAll(other.blockEntityTickers);
        blockEntityTickers.addAll(other.pendingTickers);
        other.blockEntityTickers.clear();
        other.pendingTickers.clear();
        for (Entity e : other.entities.all()) e.setLevel(this);
        entities.absorb(other.entities);
        raids.absorb(other.raids, this);
    }

    /** 청크 조건(청크 x, z)에 맞는 자료를 target 으로 옮긴다. target 은 이 레벨의 시계를 이어받는다. */
    public void splitTo(ChunkLevel target, ChunkFilter inTarget) {
        target.levelTime = levelTime;
        target.subTickCount = subTickCount;
        java.util.function.LongPredicate byPos = packed -> {
            Pos p = Pos.of(packed);
            return inTarget.test(p.x() >> 4, p.z() >> 4);
        };
        blockTicks.moveTo(target.blockTicks, byPos, 0);
        fluidTicks.moveTo(target.fluidTicks, byPos, 0);
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
        for (Entity e : entities.splitTo(target.entities, inTarget::test)) e.setLevel(target);
        raids.splitTo(target.raids, target, inTarget);
        var bit = blockEvents.iterator();
        while (bit.hasNext()) {
            BlockEventData ev = bit.next();
            if (inTarget.test(ev.x() >> 4, ev.z() >> 4)) {
                target.blockEvents.add(ev);
                bit.remove();
            }
        }
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
        changed.computeIfAbsent(key, k -> new TreeSet<>()).add(local);
    }

    /** 틱 끝에 모아 둔 변경을 보낸다: 칸 하나면 블록 갱신, 여럿이면 구역 갱신(그때의 상태로). */
    public void broadcastChanges() {
        if (changed.isEmpty()) return;
        for (var e : changed.entrySet()) {
            long key = e.getKey();
            int cx = (int) (key >> 42), cz = (int) (key << 22 >> 42), sy = (int) (key << 44 >> 44);
            if (chunk(cx, cz) == null) continue;
            TreeSet<Short> set = e.getValue();
            if (set.size() == 1) {
                short l = set.first();
                int x = cx << 4 | l >> 8 & 15, z = cz << 4 | l >> 4 & 15, y = sy << 4 | l & 15;
                broadcastToChunk(cx, cz, new BlockUpdate(x, y, z, getBlockState(new Pos(x, y, z))));
            } else {
                short[] pos = new short[set.size()];
                int[] states = new int[set.size()];
                int i = 0;
                for (short l : set) {
                    pos[i] = l;
                    states[i++] = getBlockState(new Pos(cx << 4 | l >> 8 & 15, sy << 4 | l & 15, cz << 4 | l >> 4 & 15));
                }
                broadcastToChunk(cx, cz, new SectionBlocksUpdate(cx, sy, cz, pos, states));
            }
        }
        changed.clear();
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

    /** 틱 끝 블록 이벤트 처리(바닐라 runBlockEvents): 블록이 그대로면 triggerEvent, 참이면 64칸 안에 방송. */
    public void runBlockEvents() {
        while (!blockEvents.isEmpty()) {
            var first = blockEvents.iterator();
            BlockEventData e = first.next();
            first.remove();
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
     * TODO(입자): 입자 패킷 자체는 아직 보내지 않는다(진동 입자의 재전송 표시 상태만 맞춘다).
     */
    public int countParticleViewers(double x, double y, double z) {
        int[] n = {0};
        services().forEachPlayer(p -> {
            if (p.isDisconnected()) return;
            double dx = Math.floor(p.x()) + 0.5 - x, dy = Math.floor(p.y()) + 0.5 - y, dz = Math.floor(p.z()) + 0.5 - z;
            if (dx * dx + dy * dy + dz * dz < 32.0 * 32.0) n[0]++;
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
            double h = p.sneaking() ? 1.5 : 1.8;
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

    @Override
    public int gameRuleInt(String name) { return services().gameRuleInt(name); }

    /** ServerLevel.canSleepThroughNights: playersSleepingPercentage ≤ 100. */
    public boolean canSleepThroughNights() { return gameRuleInt("playersSleepingPercentage") <= 100; }

    /** ServerLevel.updateSleepingPlayerList(차원 전체 — 월드가 없는 레벨은 아무것도 안 한다). */
    public void updateSleepingPlayerList() {}

    @Override
    public int difficulty() { return services().difficulty(); }

    @Override
    public String dimension() { return services().dimension(); }

    /** DimensionType.piglinSafe: 네더만 참(TODO: 커스텀 차원 타입). */
    public boolean piglinSafe() { return "minecraft:the_nether".equals(dimension()); }

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

    /** 엔티티 사망 전리품 평가(services 위임). */
    public java.util.List<ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z,
            String damageType, Player attacker, Player lastPlayer, long seed) {
        return services().entityDrops(lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
    }

    /** 낚시 전리품 평가(services 위임). */
    public java.util.List<ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z) {
        return services().giftLoot(lootTable, self, x, y, z);
    }

    public java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool, float luck) {
        return services().fishingLoot(hook, x, y, z, tool, luck);
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

    /** Level.getNearestPlayer(x, y, z, 거리, ignoreCreativeAndSpectators): 관전자(옵션으로 크리에이티브도) 제외 가장 가까운 플레이어. 거리가 음수면 제한 없음. */
    public Player getNearestPlayer(double x, double y, double z, double dist, boolean ignoreCreative) {
        Player[] best = new Player[1];
        double[] bestD = {dist < 0.0 ? Double.MAX_VALUE : dist * dist};
        services().forEachPlayer(p -> {
            if (p.isDisconnected() || p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            if (ignoreCreative && p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE) return;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        });
        return best[0];
    }

    /** Level.getNearestPlayer(entity, 거리): 관전자가 아닌 가장 가까운 플레이어(거리 안에서), 없으면 null. */
    public Player getNearestPlayer(Entity from, double dist) {
        Player[] best = new Player[1];
        double[] bestD = {dist < 0.0 ? Double.MAX_VALUE : dist * dist};
        services().forEachPlayer(p -> {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR || p.isDisconnected()) return;
            double dx = p.x() - from.x(), dy = p.y() - from.y(), dz = p.z() - from.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        });
        return best[0];
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

    public String biomeAt(int x, int y, int z) { return services().biomeAt(x, y, z); }

    public void forEachPlayer(java.util.function.Consumer<Player> c) { services().forEachPlayer(c); }

    public boolean isInWorldBounds(Pos pos) { return pos.y() >= minBuildHeight() && pos.y() < maxBuildHeight() && Math.abs(pos.x()) < 30000000 && Math.abs(pos.z()) < 30000000; }

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
        addFreshEntity(golem);
        // TODO(발전 과제): 5칸 안 플레이어에게 summoned_entity
    }

    @Override
    public void spawnBabyTurtle(Pos pos, int index) {
        var turtle = new org.domi.furnace.server.entity.Turtle(this, nextEntityId());
        turtle.setAge(-24000);
        turtle.setHomePos(pos);
        turtle.moveTo(pos.x() + 0.3 * index + 0.2, pos.y(), pos.z() + 0.3, 0.0F, 0.0F);
        addFreshEntity(turtle);
    }

    @Override
    public void spawnSilverfishAt(Pos pos) {
        var sf = new org.domi.furnace.server.entity.Silverfish(this, nextEntityId());
        sf.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0.0F, 0.0F);
        addFreshEntity(sf);
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
        addFreshEntity(wither);
    }

    /** TntBlock.explode: 점화된 TNT 엔티티를 칸 가운데 윗부분에 만든다(owner 는 점화한 생물). */
    @Override
    public void spawnPrimedTnt(Pos pos, BlockActor igniter, int fuse) {
        org.domi.furnace.server.entity.Targetable owner = igniter != null && igniter.handle() instanceof Player p ? p : null;
        var tnt = new org.domi.furnace.server.entity.PrimedTnt(this, nextEntityId(), pos.x() + 0.5, pos.y(), pos.z() + 0.5, owner);
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

    /**
     * 리스폰 지점 폭발(침대·정박기의 level.explode(null, badRespawnPointExplosion(center), 계산기, center, 5.0, 불, BLOCK)).
     * overridePos 가 null 이 아니면 그 칸의 폭발 저항을 resistance 로(정박기 옆 물).
     */
    public Explosion explodeBadRespawnPoint(double x, double y, double z, Pos overridePos, float resistance) {
        Explosion e = new Explosion(this, null, x, y, z, 5.0F, true, destroyType("BLOCK")).badRespawnPoint(overridePos, resistance);
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

    /** 상자와 겹치는 플레이어들(Level.getEntitiesOfClass(Player.class, box) 의 플레이어 쪽). */
    @Override
    public void forEachPlayerIn(org.domi.furnace.server.entity.AABB box, java.util.function.Consumer<Player> c) {
        services().forEachPlayer(p -> {
            if (!p.isDisconnected() && p.boundingBox().intersects(box)) c.accept(p);
        });
    }

    /** Level.getNearestPlayer(조건, 출처, x, y, z): 조건에 맞는 플레이어 중 (x,y,z)에 가장 가까운 것. */
    public Player getNearestPlayer(org.domi.furnace.server.entity.ai.targeting.TargetingConditions conditions, org.domi.furnace.server.entity.Mob source,
            double x, double y, double z) {
        Player[] best = new Player[1];
        double[] bestD = {-1.0};
        services().forEachPlayer(p -> {
            if (p.isDisconnected() || !conditions.test(source, p)) return;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (bestD[0] == -1.0 || d < bestD[0]) {
                bestD[0] = d;
                best[0] = p;
            }
        });
        return best[0];
    }

    /**
     * EntityType.spawn(egg): create → moveTo(무작위 방향) → 머리·몸통 방향 → finalizeSpawn → 추가 → 주변 소리.
     * snapToGround 면 칸 위에서 충돌 모양에 닿을 때까지 내려놓는다(getYOffset).
     */
    @Override
    public boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown) {
        Entity e = org.domi.furnace.server.entity.Entities.create(this, entityType);
        if (!(e instanceof org.domi.furnace.server.entity.Mob mob)) return false;
        double d0 = 0.0;
        if (snapToGround) {
            mob.setPos(pos.x() + 0.5, pos.y() + 1, pos.z() + 0.5);
            AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
            if (extendDown) box = box.expandTowards(0.0, -1.0, 0.0);
            d0 = 1.0 + Entity.collideWithShapes(0.0, extendDown ? -2.0 : -1.0, 0.0, mob.boundingBox(), blockCollisions(box))[1];
        }
        mob.moveTo(pos.x() + 0.5, pos.y() + d0, pos.z() + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(random.nextFloat() * 360.0F), 0.0F);
        mob.setYHeadRot(mob.yRot());
        mob.setYBodyRot(mob.yRot());
        if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.SPAWN_EGG);
        mob.finalizeSpawn(random, getCurrentDifficultyAt(mob.blockPosition()));
        addFreshEntityWithPassengers(mob);
        mob.makeSound(mob.ambientSoundForSpawn());
        return true;
    }

    // ------------------------------------------------------------------ 빛

    private org.domi.furnace.server.light.LevelLight lightEngine;

    private final org.domi.furnace.server.light.LightWorld lightWorld = new org.domi.furnace.server.light.LightWorld() {
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
            if (depth++ == 0) java.util.Arrays.fill(cacheCol, null);
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

        public int minY() { return minBuildHeight(); }

        public int maxY() { return maxBuildHeight(); }
    };
    private final int bedrockState = blocks.defaultState("minecraft:bedrock");

    public org.domi.furnace.server.light.LevelLight lightEngine() {
        if (lightEngine == null) lightEngine = new org.domi.furnace.server.light.LevelLight(lightWorld, hasSkyLight());
        return lightEngine;
    }

    /** 새로 올라온 청크의 빛을 만든다(ThreadedLevelLightEngine.lightChunk). 이미 빛이 있으면 가장자리만 이웃과 맞춘다. */
    public void lightNewChunk(int cx, int cz) {
        ChunkColumn col = chunk(cx, cz);
        if (col == null) return;
        if (col.lightPrecomputed()) {   // 생성 스레드가 청크 안쪽 빛을 이미 계산했다 — 이웃과 가장자리만 맞춘다
            col.lightPrecomputed(false);
            lightEngine().attachPrecomputed(cx, cz);
            return;
        }
        if (col.light() == null) col.light(new org.domi.furnace.server.light.ChunkLight());
        lightEngine().lightChunk(cx, cz);
    }

    /** 생성 스레드에서 청크 안쪽 빛을 미리 계산한다(월드 상태를 읽지 않는다). 올릴 때 lightNewChunk 가 가장자리를 맞춘다. */
    public static void precomputeLight(ChunkColumn col, int cx, int cz, int bedrockState, boolean hasSkyLight) {
        col.motionBlockingHeightmap();   // 높이 지도도 첫 사용 때 틱 스레드에서 열마다 훑지 않게 미리 채운다
        if (col.light() != null) return;
        col.light(org.domi.furnace.server.light.LevelLight.computeIsolated(cx, cz, (x, y, z) -> col.getBlock(x & 15, y, z & 15), bedrockState,
                col.minY(), col.maxY() + 1, hasSkyLight));
        col.lightPrecomputed(true);
    }

    public int lightBedrockState() { return bedrockState; }

    /** 틱 끝: 쌓인 빛 갱신 실행(LevelLightEngine.runLightUpdates). */
    public void runLightUpdates() {
        if (lightEngine != null && lightEngine.hasWork()) lightEngine.runUpdates();
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
        return new org.domi.furnace.server.entity.DifficultyInstance(difficulty(), dayTime(), inhabited, moon);
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
    };

    public EntityTracker.Viewers viewers() { return viewers; }

    /** addFreshEntity: 넣고(같은 UUID 가 있으면 거부) 추적을 시작한다 — 볼 수 있는 플레이어에게 바로 스폰. */
    public boolean addFreshEntity(Entity e) {
        if (e.isRemoved()) return false;
        e.setLevel(this);
        if (!entities.add(e)) return false;
        startTracking(e);
        if (e.dynamicGameEventListener() != null) e.dynamicGameEventListener().add(this);
        return true;
    }

    /** tryAddFreshEntityWithPassengers: 승객의 승객까지 하나라도 UUID 가 겹치면 아무것도 넣지 않는다. */
    public boolean addFreshEntityWithPassengers(Entity e) {
        java.util.List<org.domi.furnace.server.entity.Rider> all = new java.util.ArrayList<>();
        all.add(e);
        e.collectIndirectPassengers(all);
        for (var r : all) if (r instanceof Entity x && entities.containsUuid(x.uuid())) return false;
        for (var r : all) if (r instanceof Entity x) addFreshEntity(x);
        return true;
    }

    /** ChunkMap.addEntity: 추적 거리(종류 × 16 × 방송 비율) 가 0 이면 추적하지 않는다. */
    protected void startTracking(Entity e) {
        int range = e.type().clientTrackingRange() * 16;
        if (range == 0) return;
        e.tracker = new EntityTracker(e, services().entityBroadcastRangePercentage() * range / 100);
        e.tracker.updatePlayers(trackingPlayers(), viewers);
    }

    /** 다른 region 에서 넘어온 엔티티(추적 상태째). */
    public void adoptEntity(Entity e) {
        if (e.isRemoved()) return;
        e.setLevel(this);
        if (!entities.add(e)) return;
        if (e.tracker == null) startTracking(e);
        if (e.dynamicGameEventListener() != null) e.dynamicGameEventListener().add(this);
    }

    /** 엔티티가 지워졌다(setRemoved) — 저장소에서 빼고 보는 사람에게 지운다. */
    public void entityRemoved(Entity e) {
        if (!entities.remove(e)) return;
        if (e.dynamicGameEventListener() != null) e.dynamicGameEventListener().remove(this);
        if (e.tracker != null) {
            e.tracker.broadcastRemoved();
            e.tracker = null;
        }
    }

    /** 엔티티의 구역이 바뀌었다(setPosRaw). */
    public void entitySectionChanged(Entity e, long oldKey) {
        entities.moved(e, oldKey);
        if (e.dynamicGameEventListener() != null) e.dynamicGameEventListener().move(this);   // onSectionChange
    }

    public java.util.List<Entity> getEntities(Entity except, AABB box, java.util.function.Predicate<? super Entity> filter) {
        return entities.getEntities(except, box, filter);
    }

    public <T extends Entity> java.util.List<T> getEntitiesOfClass(Class<T> cls, AABB box, java.util.function.Predicate<? super T> filter) {
        return entities.getEntitiesOfClass(cls, box, filter);
    }

    /**
     * 엔티티 틱(바닐라 ServerLevel.tick 의 entityTickList.forEach): 지워진 것은 건너뛰고, 엔티티 틱 청크(ticking — 청크 키
     * (x << 32 | z))에 있는 것만 setOldPosAndRot · tickCount++ · tick.
     */
    public void tickEntities(java.util.function.LongPredicate ticking) {
        if (entities.isEmpty()) return;
        for (Entity e : entities.tickSnapshot()) {
            if (e.isRemoved()) continue;
            if (!ticking.test((long) e.chunkX() << 32 | e.chunkZ() & 0xFFFFFFFFL)) continue;
            Entity v = e.vehicle();
            if (v != null) {   // 탄 것은 탈것이 틱한다
                if (!v.isRemoved() && v.hasPassenger(e)) continue;
                e.stopRiding();
            }
            try {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
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
            if (p.isDisconnected()) p.detachFromVehicle();
            else if (p.vehicle() != vehicle) p.stopRiding();
            else p.rideTick();
            return;
        }
        Entity e = (Entity) r;
        if (e.isRemoved() || e.vehicle() != vehicle) {
            e.stopRiding();
        } else if (entities.contains(e)) {
            e.setOldPosAndRot();
            e.tickCount++;
            e.rideTick();
            if (e.isVehicle()) for (var r2 : e.passengers()) tickPassenger(e, r2);
        }
    }

    /**
     * 추적 갱신(ChunkMap.tick 의 엔티티 부분): 구역이 바뀐 엔티티는 모든 플레이어에 대해 다시 보고, 바뀌었거나 엔티티 틱 청크면 변화를 보낸다.
     * present 는 이 region 에 있는 플레이어인가(없는 보는 사람은 grace 뒤 뺀다).
     */
    public void trackEntities(java.util.function.LongPredicate ticking, java.util.function.Predicate<Player> present) {
        if (entities.isEmpty()) return;
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
    }

    /** ChunkMap.move(player): 플레이어가 움직였다 — 모든 엔티티에 대해 그 플레이어를 다시 본다. */
    public void playerMoved(Player p) {
        for (Entity e : entities.all()) if (e.tracker != null && !e.isRemoved()) e.tracker.updatePlayer(p, viewers);
    }

    /** 플레이어가 나갔다 — 보는 목록에서 뺀다(패킷 없이). */
    public void playerLeft(Player p) {
        for (Entity e : entities.all()) if (e.tracker != null) e.tracker.removePlayer(p);
    }

    /** 플레이어가 다른 차원으로 갔다 — 추적만 지운다(클라이언트는 리스폰 패킷에서 엔티티를 모두 버리므로 제거 패킷은 보내지 않는다). */
    public void playerChangedDimension(Player p) {
        for (Entity e : entities.all()) if (e.tracker != null) e.tracker.forgetPlayer(p);
    }

    // ------------------------------------------------------------------ 엔티티 저장(entities/ — 바닐라 EntityStorage)

    /** 불러온 엔티티 묶음을 넣는다(addLegacyChunkEntities). 다루지 못하는 종류는 원본 NBT 로 청크에 둔다. */
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
                    var tag = e.save();
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
            var tag = e.save();
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

    private static final int MOVING_PISTON = B.id("moving_piston"), AIR_STATE = B.defaultState("air");
    private static byte[] largeCollision;

    /** hasLargeCollisionShape: 충돌 모양이 칸 밖으로 나가거나 모양이 바뀌는 블록. */
    private boolean hasLargeCollisionShape(int state) {
        byte[] c = largeCollision;
        if (c == null) largeCollision = c = new byte[blocks.stateCount];
        byte v = c[state];
        if (v == 0) {
            boolean large = info.dynamicShape(blocks.blockOf(state));
            double[] b = info.collisionShape(state).bounds();
            if (b != null) large |= b[0] < 0 || b[1] < 0 || b[2] < 0 || b[3] > 1 || b[4] > 1 || b[5] > 1;
            c[state] = v = (byte) (large ? 2 : 1);
        }
        return v == 2;
    }

    /** 칸마다 충돌 모양(월드 좌표, 몸통과 겹치는 것만)을 돌려준다. stopAtFirst 면 첫 것에서 멈춘다. */
    private void collectCollisions(AABB box, java.util.function.BiPredicate<Pos, Shape> out) {
        int x0 = Entity.floor(box.minX() - 1.0E-7) - 1, x1 = Entity.floor(box.maxX() + 1.0E-7) + 1;
        int y0 = Entity.floor(box.minY() - 1.0E-7) - 1, y1 = Entity.floor(box.maxY() + 1.0E-7) + 1;
        int z0 = Entity.floor(box.minZ() - 1.0E-7) - 1, z1 = Entity.floor(box.maxZ() + 1.0E-7) + 1;
        int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        ChunkColumn c = null;
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int edges = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
                    if (edges == 3) continue;
                    if ((x >> 4) != lastCx || (z >> 4) != lastCz) {
                        lastCx = x >> 4;
                        lastCz = z >> 4;
                        c = chunk(lastCx, lastCz);
                    }
                    if (c == null) continue;
                    int state = y < minBuildHeight() || y >= maxBuildHeight() ? AIR_STATE : c.getBlock(x & 15, y, z & 15);
                    if (edges == 1 && !hasLargeCollisionShape(state) || edges == 2 && blocks.blockOf(state) != MOVING_PISTON) continue;
                    Pos p = new Pos(x, y, z);
                    Shape shape = BlockBehaviors.of(state).getCollisionShape(state, this, p, null);
                    if (shape.isEmpty()) continue;
                    if (shape == Shape.BLOCK) {
                        if (!box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) continue;
                    } else if (!shape.overlaps(box, x, y, z)) {
                        continue;
                    }
                    if (!out.test(p, shape.move(x, y, z))) return;
                }
            }
        }
    }

    /** getBlockCollisions(entity, box): 몸통과 겹치는 블록 충돌 모양들. */
    public java.util.List<Shape> blockCollisions(AABB box) {
        java.util.List<Shape> out = new java.util.ArrayList<>();
        collectCollisions(box, (p, s) -> out.add(s));
        return out;
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

    /** noCollision(entity, box): 블록 충돌이 없는가(엔티티 충돌·월드 보더는 아직 없다). */
    public boolean noCollision(AABB box) {
        boolean[] hit = {false};
        collectCollisions(box, (p, s) -> {
            hit[0] = true;
            return false;
        });
        return !hit[0];
    }

    /** findSupportingBlock(entity, box): 겹치는 충돌 블록 중 엔티티 위치에서 가운데가 가장 가까운 것(같으면 compareTo 가 큰 쪽). */
    public Pos findSupportingBlock(AABB box, double ex, double ey, double ez) {
        Pos[] best = {null};
        double[] bestDist = {Double.MAX_VALUE};
        collectCollisions(box, (p, s) -> {
            double dx = p.x() + 0.5 - ex, dy = p.y() + 0.5 - ey, dz = p.z() + 0.5 - ez;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist[0] || d == bestDist[0] && (best[0] == null || compareVec(best[0], p) < 0)) {
                best[0] = p;
                bestDist[0] = d;
            }
            return true;
        });
        return best[0];
    }

    /** Vec3i.compareTo: y, z, x 순. */
    private static int compareVec(Pos a, Pos b) {
        if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
        if (a.z() != b.z()) return Integer.compare(a.z(), b.z());
        return Integer.compare(a.x(), b.x());
    }

    @Override
    public int countTriggeringEntities(double x0, double y0, double z0, double x1, double y1, double z1, boolean livingOnly) {
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);
        int[] n = {entities.getEntities(null, box, e -> !e.isIgnoringBlockTriggers() && !e.isSpectator() && (!livingOnly || e.isLiving())).size()};
        services().forEachPlayer(p -> {
            if (p.isDisconnected() || p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            double h = p.sneaking() ? 1.5 : 1.8;
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
