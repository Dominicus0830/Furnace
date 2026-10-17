// BlockLevel 구현의 뼈대 — 바닐라 Level.setBlock / LevelChunk.setBlockState / 이웃 갱신 / 신호 / 소리·이벤트 방송을 청크 열 위에서.
// 청크를 어디서 얻는지(소유 region 의 로드된 청크)와 변경 방송·저장 표시는 하위 클래스가 정한다.
package org.domi.furnace.server.block;

import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.server.block.entity.BlockEntity;
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
        be.clearRemoved();
        col.putBlockEntity(be);
        if (be.ticking()) addTicker(be);
    }

    protected void removeBlockEntity(ChunkColumn col, Pos pos) { col.removeBlockEntityAt(pos.x(), pos.y(), pos.z()); }

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
        for (BlockEntity be : col.blockEntities().values()) if (be.ticking() && !be.isRemoved()) addTicker(be);
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
    public int minBuildHeight() { return ChunkColumn.MIN_Y; }

    @Override
    public int maxBuildHeight() { return ChunkColumn.MAX_Y + 1; }

    @Override
    public boolean hasChunkAt(Pos pos) { return chunkAt(pos) != null; }

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
        // TODO(엔티티): ExperienceOrb.award(level, 칸 가운데, amount) — 구슬 나누기·합치기. 구슬은 자기 난수만 쓴다.
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
            // TODO(엔티티): 번개 — findLightningTargetAround(getBlockRandomPos) → 비 오는 칸이면 해골 말 덫(1%·난이도)·번개 엔티티.
            blockRandomPos(x0, 0, z0);
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
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:sound_event", sound) : null;
        double range = volume > 1.0F ? 16.0 * volume : 16.0;
        SoundPacket pkt = new SoundPacket(id == null ? -1 : id, sound, SoundPacket.source(source), x, y, z, volume, pitch, soundRandom.nextLong());
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
    public void gameEvent(String event, Pos pos, BlockActor source, int state) {}

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

    @Override
    public int difficulty() { return services().difficulty(); }

    @Override
    public String dimension() { return services().dimension(); }

    @Override
    public boolean biomeIs(Pos pos, String tag) { return services().biomeIs(pos.x(), pos.y(), pos.z(), tag); }

    @Override
    public boolean ultraWarm() { return services().ultraWarm(); }

    @Override
    public boolean hasSkyLight() { return services().hasSkyLight(); }

    @Override
    public boolean withinBorder(Pos pos) { return services().withinBorder(pos.x(), pos.z()); }

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

    @Override
    public void popResource(Pos pos, ItemStack stack) {
        RandomSource r = random();
        nextDouble(r, -0.25, 0.25);   // x·y·z 흩뿌림(Mth.nextDouble) — 게임룰과 무관하게 먼저 뽑는다
        nextDouble(r, -0.25, 0.25);
        nextDouble(r, -0.25, 0.25);
        spawnItem(stack, true);
    }

    @Override
    public void popResourceFromFace(Pos pos, int dir, ItemStack stack) {
        RandomSource r = random();
        if (Dir.DX[dir] == 0) nextDouble(r, -0.25, 0.25);
        if (Dir.DY[dir] == 0) nextDouble(r, -0.25, 0.25);
        if (Dir.DZ[dir] == 0) nextDouble(r, -0.25, 0.25);
        if (Dir.DX[dir] == 0) nextDouble(r, -0.1, 0.1);
        if (Dir.DY[dir] == 0) nextDouble(r, 0.0, 0.1);
        if (Dir.DZ[dir] == 0) nextDouble(r, -0.1, 0.1);
        spawnItem(stack, false);
    }

    private static double nextDouble(RandomSource r, double min, double max) { return min >= max ? min : r.nextDouble() * (max - min) + min; }

    /** 아이템 엔티티를 만든다(doTileDrops). 기본 속도 생성자는 난수 두 번(ItemEntity(level, x, y, z, stack)). TODO(엔티티): 실제 생성. */
    private void spawnItem(ItemStack stack, boolean defaultVelocity) {
        if (stack.isEmpty() || !gameRule("doTileDrops")) return;
        if (defaultVelocity) {
            random().nextDouble();
            random().nextDouble();
        }
    }
}
