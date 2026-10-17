// BlockLevel 구현의 뼈대 — 바닐라 Level.setBlock / LevelChunk.setBlockState / 이웃 갱신 / 신호 / 소리·이벤트 방송을 청크 열 위에서.
// 청크를 어디서 얻는지(소유 region 의 로드된 청크)와 변경 방송·저장 표시는 하위 클래스가 정한다.
package org.domi.furnace.server.block;

import java.util.ArrayDeque;
import java.util.TreeMap;
import java.util.TreeSet;
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

public abstract class ChunkLevel implements BlockLevel {

    protected final Blocks blocks = Blocks.get();
    protected final BlockInfo info = BlockInfo.get();
    private final NeighborUpdater neighborUpdater;
    private final RandomSource random = RandomSource.create();
    private final RandomSource soundRandom = RandomSource.create();
    private final BlockTicks blockTicks = new BlockTicks();
    private final BlockTicks fluidTicks = new BlockTicks();
    private long subTickCount;
    /** 지역 틱 시계(예약 틱 기준 — Folia 의 redstoneTime 처럼 region 마다 따로 간다. 월드 시간은 gameTime()). */
    private long levelTime;
    /** 방송할 변경: 구역 키(청크 x,z,구역 y) → 구역 안 칸 번호 집합(바닐라 ChunkHolder.changedBlocksPerSection). */
    private final TreeMap<Long, TreeSet<Short>> changed = new TreeMap<>();
    private final ArrayDeque<int[]> blockEvents = new ArrayDeque<>();

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

    /** 블록 엔티티 만들기·지우기(상태가 블록 엔티티를 가질 때). */
    protected void createBlockEntity(ChunkColumn col, Pos pos, int state) {}

    protected void removeBlockEntity(ChunkColumn col, Pos pos) { col.removeBlockEntityAt(pos.x(), pos.y(), pos.z()); }

    protected Object blockEntity(ChunkColumn col, Pos pos) { return col.chestAt(pos.x(), pos.y(), pos.z()); }

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
        if (dropBlock) dropResources(s, pos, breaker);
        boolean ok = setBlock(pos, Fluids.createLegacyBlock(f), UPDATE_ALL, recursionLeft);
        if (ok) gameEvent("minecraft:block_destroy", pos, breaker, s);
        return ok;
    }

    /** Block.dropResources — 전리품·아이템 엔티티가 붙으면 채운다. */
    protected void dropResources(int state, Pos pos, BlockActor breaker) {}

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
    }

    @Override
    public void scheduleFluidTick(Pos pos, int fluidType, int delay) {
        fluidTicks.schedule(pos, fluidType, levelTime + delay, 0, subTickCount++);
    }

    @Override
    public boolean hasScheduledTick(Pos pos, int block) { return blockTicks.hasScheduledTick(pos, block); }

    @Override
    public boolean willTickThisTick(Pos pos, int block) { return blockTicks.willTickThisTick(pos, block); }

    /** 레벨 틱의 블록 틱 → 유체 틱(바닐라 ServerLevel.tick 순서). 지역 시계를 한 틱 진행한다. */
    public void runScheduledTicks() {
        long now = ++levelTime;
        blockTicks.tick(now, 65536, (pos, type) -> {
            int s = getBlockState(pos);
            if (blocks.blockOf(s) == type) BlockBehaviors.of(s).tick(s, this, pos, random);
        });
        fluidTicks.tick(now, 65536, (pos, type) -> {
            int f = getFluidState(pos);
            if (Fluids.type(f) == type) FluidTicker.tick(this, pos, f);
        });
    }

    public BlockTicks blockTicks() { return blockTicks; }

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
        var bit = blockEvents.iterator();
        while (bit.hasNext()) {
            int[] ev = bit.next();
            if (inTarget.test(ev[0] >> 4, ev[2] >> 4)) {
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
        blockEvents.add(new int[] {pos.x(), pos.y(), pos.z(), block, type, data});
    }

    /** 틱 끝 블록 이벤트 처리(바닐라 runBlockEvents): 블록이 그대로면 triggerEvent, 참이면 64칸 안에 방송. */
    public void runBlockEvents() {
        while (!blockEvents.isEmpty()) {
            int[] e = blockEvents.poll();
            Pos pos = new Pos(e[0], e[1], e[2]);
            int s = getBlockState(pos);
            if (blocks.blockOf(s) != e[3]) continue;
            if (BlockBehaviors.of(s).triggerEvent(s, this, pos, e[4], e[5])) {
                BlockEventPacket pkt = new BlockEventPacket(e[0], e[1], e[2], e[4], e[5], e[3]);
                services().forEachPlayer(p -> {
                    double dx = e[0] - p.x(), dy = e[1] - p.y(), dz = e[2] - p.z();
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
    public boolean withinBorder(Pos pos) { return services().withinBorder(pos.x(), pos.z()); }

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
