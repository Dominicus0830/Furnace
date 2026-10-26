// 바닐라 PoiManager: 섹션별 POI 조회·티켓·마을 거리(SectionTracker).
// 차이: 바닐라는 내려간 청크의 POI 섹션도 디스크에서 읽어(getOrLoad) 조회한다. 여기서는 이 region 이 올려 둔 청크만 본다.
// 차이: 바닐라 onBlockStateChange 는 갱신을 server.execute 로 미룬다(같은 틱 뒤쪽). 여기서는 바로 반영한다.
// 마을 거리(sectionsToVillage)는 DistanceTracker 의 고정점(마을 POI 섹션 0, 이웃 26칸 +1, 최대 7)과 같은 값을
// 둘레 섹션을 직접 훑어 계산한다(체비셰프 섹션 거리).
package org.domi.furnace.server.world.poi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public final class PoiManager {
    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;

    /** PoiManager.Occupancy. */
    public enum Occupancy {
        HAS_SPACE, IS_OCCUPIED, ANY;

        boolean test(PoiRecord r) {
            return switch (this) {
                case HAS_SPACE -> r.hasSpace();
                case IS_OCCUPIED -> r.isOccupied();
                case ANY -> true;
            };
        }
    }

    /** 올라온 청크의 POI 묶음(없거나 내려갔으면 null). */
    @FunctionalInterface
    public interface Columns {
        PoiChunk poi(int chunkX, int chunkZ);
    }

    private final Columns columns;

    public PoiManager(Columns columns) { this.columns = columns; }

    private PoiSection section(int x, int y, int z) {
        PoiChunk c = columns.poi(x >> 4, z >> 4);
        return c == null ? null : c.section(y >> 4);
    }

    // ------------------------------------------------------------------ 추가·제거

    /** add(pos, type). */
    public void add(Pos pos, PoiType type) {
        PoiChunk c = columns.poi(pos.x() >> 4, pos.z() >> 4);
        if (c == null) return;
        PoiSection s = c.getOrCreate(pos.y() >> 4);
        if (s != null) s.add(pos, type);
    }

    /** remove(pos). */
    public void remove(Pos pos) {
        PoiSection s = section(pos.x(), pos.y(), pos.z());
        if (s != null) s.remove(pos);
    }

    /** ServerLevel.onBlockStateChange: 종류가 바뀌면 옛 POI 를 지우고 새 POI 를 넣는다. */
    public void onBlockStateChange(Pos pos, int oldState, int newState) {
        PoiType a = PoiTypes.forState(oldState), b = PoiTypes.forState(newState);
        if (a == b) return;
        if (a != null) remove(pos);
        if (b != null) add(pos, b);
    }

    // ------------------------------------------------------------------ 조회

    /** getInChunk: 아래 섹션부터. */
    public void getInChunk(Predicate<PoiType> typePred, int chunkX, int chunkZ, Occupancy occ, Consumer<PoiRecord> out) {
        PoiChunk c = columns.poi(chunkX, chunkZ);
        if (c == null) return;
        for (int y = c.minSection(); y <= c.maxSection(); y++) {
            PoiSection s = c.section(y);
            if (s != null) s.forEachRecord(typePred, occ, out);
        }
    }

    /** getInSquare: 청크 사각(x 먼저, 그다음 z) → 가로 거리 radius 안. */
    public List<PoiRecord> getInSquare(Predicate<PoiType> typePred, Pos pos, int radius, Occupancy occ) {
        int r = Math.floorDiv(radius, 16) + 1;
        int cx = pos.x() >> 4, cz = pos.z() >> 4;
        List<PoiRecord> out = new ArrayList<>();
        for (int z = cz - r; z <= cz + r; z++) {
            for (int x = cx - r; x <= cx + r; x++) {
                getInChunk(typePred, x, z, occ, rec -> {
                    Pos p = rec.pos();
                    if (Math.abs(p.x() - pos.x()) <= radius && Math.abs(p.z() - pos.z()) <= radius) out.add(rec);
                });
            }
        }
        return out;
    }

    /** getInRange: distSqr ≤ radius². */
    public List<PoiRecord> getInRange(Predicate<PoiType> typePred, Pos pos, int radius, Occupancy occ) {
        long j = (long) radius * radius;
        List<PoiRecord> sq = getInSquare(typePred, pos, radius, occ);
        sq.removeIf(rec -> rec.distSqr(pos) > j);
        return sq;
    }

    /** findAll. */
    public List<Pos> findAll(Predicate<PoiType> typePred, Predicate<Pos> posPred, Pos pos, int radius, Occupancy occ) {
        List<Pos> out = new ArrayList<>();
        for (PoiRecord r : getInRange(typePred, pos, radius, occ)) if (posPred.test(r.pos())) out.add(r.pos());
        return out;
    }

    /** find: 조건을 만족하는 첫 POI(가까운 순 아님 — 청크·섹션 순회 순서). */
    public Pos find(Predicate<PoiType> typePred, Predicate<Pos> posPred, Pos pos, int radius, Occupancy occ) {
        for (PoiRecord r : getInRange(typePred, pos, radius, occ)) if (posPred.test(r.pos())) return r.pos();
        return null;
    }

    /** findAllClosestFirstWithType: 가까운 순(같은 거리는 원래 순서 — Stream.sorted 는 안정). */
    public List<PoiRecord> findAllClosestFirstWithType(Predicate<PoiType> typePred, Predicate<Pos> posPred, Pos pos, int radius, Occupancy occ) {
        List<PoiRecord> out = new ArrayList<>();
        for (PoiRecord r : getInRange(typePred, pos, radius, occ)) if (posPred.test(r.pos())) out.add(r);
        out.sort(java.util.Comparator.comparingDouble(r -> r.distSqr(pos)));
        return out;
    }

    /** findClosest: 가장 가까운 것(Stream.min — 같은 거리면 먼저 나온 것). */
    public Pos findClosest(Predicate<PoiType> typePred, Pos pos, int radius, Occupancy occ) {
        return findClosest(typePred, p -> true, pos, radius, occ);
    }

    public Pos findClosest(Predicate<PoiType> typePred, Predicate<Pos> posPred, Pos pos, int radius, Occupancy occ) {
        PoiRecord r = findClosestRecord(typePred, posPred, pos, radius, occ);
        return r == null ? null : r.pos();
    }

    /** findClosestWithType. */
    public PoiRecord findClosestRecord(Predicate<PoiType> typePred, Predicate<Pos> posPred, Pos pos, int radius, Occupancy occ) {
        PoiRecord best = null;
        long bd = Long.MAX_VALUE;
        for (PoiRecord r : getInRange(typePred, pos, radius, occ)) {
            if (!posPred.test(r.pos())) continue;
            long d = r.distSqr(pos);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    /** take: 빈자리가 있는 첫 POI 의 티켓을 잡는다. 없으면 null. */
    public Pos take(Predicate<PoiType> typePred, BiPredicate<PoiType, Pos> combined, Pos pos, int radius) {
        for (PoiRecord r : getInRange(typePred, pos, radius, Occupancy.HAS_SPACE)) {
            if (!combined.test(r.type(), r.pos())) continue;
            r.acquireTicket();
            return r.pos();
        }
        return null;
    }

    /** getRandom: Util.toShuffledList(뒤에서부터 nextInt(j) 와 맞바꾸기) 뒤 posPred 를 처음 만족하는 것. */
    public Pos getRandom(Predicate<PoiType> typePred, Predicate<Pos> posPred, Occupancy occ, Pos pos, int radius, RandomSource random) {
        List<PoiRecord> list = getInRange(typePred, pos, radius, occ);
        for (int j = list.size(); j > 1; j--) {
            int k = random.nextInt(j);
            list.set(j - 1, list.set(k, list.get(j - 1)));
        }
        for (PoiRecord r : list) if (posPred.test(r.pos())) return r.pos();
        return null;
    }

    /** release: 티켓을 돌려준다. 등록되지 않은 칸이면 바닐라처럼 예외. */
    public boolean release(Pos pos) {
        PoiSection s = section(pos.x(), pos.y(), pos.z());
        if (s == null) throw new IllegalStateException("POI never registered at " + pos);
        return s.release(pos);
    }

    public boolean exists(Pos pos, Predicate<PoiType> typePred) {
        PoiSection s = section(pos.x(), pos.y(), pos.z());
        return s != null && s.exists(pos, typePred);
    }

    /** existsAtPosition. */
    public boolean existsAtPosition(PoiType type, Pos pos) { return exists(pos, t -> t == type); }

    public PoiType getType(Pos pos) {
        PoiSection s = section(pos.x(), pos.y(), pos.z());
        return s == null ? null : s.getType(pos);
    }

    /** getFreeTickets(바닐라는 섹션이 없으면 0). */
    public int getFreeTickets(Pos pos) {
        PoiSection s = section(pos.x(), pos.y(), pos.z());
        return s == null ? 0 : s.getFreeTickets(pos);
    }

    public long getCountInRange(Predicate<PoiType> typePred, Pos pos, int radius, Occupancy occ) {
        return getInRange(typePred, pos, radius, occ).size();
    }

    // ------------------------------------------------------------------ 마을 거리

    /** sectionsToVillage: 마을 POI 가 있는 가장 가까운 섹션까지 체비셰프 섹션 거리, 6 넘으면 7. */
    public int sectionsToVillage(int sx, int sy, int sz) { return sectionsToVillage(sx, sy, sz, MAX_VILLAGE_DISTANCE); }

    private int sectionsToVillage(int sx, int sy, int sz, int limit) {
        for (int d = 0; d <= limit; d++) {
            for (int x = sx - d; x <= sx + d; x++) {
                for (int z = sz - d; z <= sz + d; z++) {
                    PoiChunk c = columns.poi(x, z);
                    if (c == null) continue;
                    boolean edgeXZ = Math.abs(x - sx) == d || Math.abs(z - sz) == d;
                    for (int y = sy - d; y <= sy + d; y++) {
                        if (!edgeXZ && Math.abs(y - sy) != d) continue;   // 껍질만
                        PoiSection s = c.section(y);
                        if (s != null && hasVillage(s)) return d;
                    }
                }
            }
        }
        return MAX_VILLAGE_DISTANCE + 1;
    }

    private static boolean hasVillage(PoiSection s) {
        boolean[] f = {false};
        s.forEachRecord(PoiTypes.VILLAGE, Occupancy.ANY, r -> f[0] = true);
        return f[0];
    }

    /** ServerLevel.isCloseToVillage(pos, i). */
    public boolean isCloseToVillage(Pos pos, int i) {
        if (i > MAX_VILLAGE_DISTANCE) return false;
        return sectionsToVillage(pos.x() >> 4, pos.y() >> 4, pos.z() >> 4, i) <= i;
    }

    /** ServerLevel.isVillage(pos) = isCloseToVillage(pos, 1). */
    public boolean isVillage(Pos pos) { return isCloseToVillage(pos, VILLAGE_SECTION_SIZE); }

    // ------------------------------------------------------------------ 청크 불러오기

    /**
     * 청크를 올릴 때(ChunkSerializer.read 의 checkConsistencyWithBlocks): 섹션이 없고 POI 블록이 있으면 블록에서 만들고,
     * 있지만 무효(Valid=0)면 블록에서 다시 만든다. 칸 순서는 x → y → z(BlockPos.betweenClosed).
     */
    public static PoiChunk restore(ChunkColumn col, int chunkX, int chunkZ, org.domi.furnace.server.net.io.nbt.NbtCompound saved) {
        int minSection = col.minY() >> 4;
        PoiChunk chunk = PoiChunk.load(saved, minSection, col.sectionCount());
        boolean wasDirty = saved == null;
        ChunkSection[] secs = col.sections();
        for (int i = 0; i < secs.length; i++) {
            ChunkSection cs = secs[i];
            int sy = minSection + i;
            PoiSection s = chunk.section(sy);
            boolean may = cs != null && !cs.hasOnlyAir() && mayHavePoi(cs);
            if (s != null) {
                final int fi = i;
                s.refresh(add -> { if (may) scan(secs[fi], chunkX, sy, chunkZ, add); });
            } else if (may) {
                boolean[] found = {false};
                scan(cs, chunkX, sy, chunkZ, (p, t) -> found[0] = true);
                if (found[0]) {
                    PoiSection ns = chunk.getOrCreate(sy);
                    scan(cs, chunkX, sy, chunkZ, ns::add);
                }
            }
        }
        if (!wasDirty && !chunk.dirty()) chunk.clearDirty();
        return chunk;
    }

    private static boolean mayHavePoi(ChunkSection cs) {
        for (int s : cs.blocks()) if (PoiTypes.hasPoi(s)) return true;
        return false;
    }

    private static void scan(ChunkSection cs, int chunkX, int sy, int chunkZ, java.util.function.BiConsumer<Pos, PoiType> add) {
        int bx = chunkX << 4, by = sy << 4, bz = chunkZ << 4;
        for (int z = 0; z < 16; z++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    PoiType t = PoiTypes.forState(cs.getBlock(x, y, z));
                    if (t != null) add.accept(new Pos(bx + x, by + y, bz + z), t);
                }
            }
        }
    }
}
