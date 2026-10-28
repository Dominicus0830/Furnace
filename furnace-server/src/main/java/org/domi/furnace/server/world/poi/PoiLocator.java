// 바닐라 PoiManager.findClosestWithType(/locate poi) 를 여러 region 에 걸쳐 한다.
// 청크 사각(ChunkPos.rangeClosed: x 안쪽, z 바깥)마다 POI 를 모은다: 올라온 청크는 주인 region 스레드에서 복사해 오고,
// 주인이 없거나 안 올라온 청크는 바닐라 getOrLoad 처럼 poi/ 지역 파일에서 읽는다(locate 작업 스레드). 고르는 것은
// 바닐라 순서 그대로: 가로 사각(|dx|,|dz| ≤ r) → distSqr ≤ r² → Stream.min(먼저 나온 것 우선).
package org.domi.furnace.server.world.poi;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.AnvilWorldSource;

public final class PoiLocator {
    private PoiLocator() { }

    /** 복사한 POI 한 개(종류, 칸). */
    public record Entry(PoiType type, Pos pos) {}

    /** 청크 사각 안의 청크 좌표(바닐라 순서). */
    public static List<ChunkPos> square(Pos origin, int radius) {
        int r = Math.floorDiv(radius, 16) + 1;
        int cx = origin.x() >> 4, cz = origin.z() >> 4;
        List<ChunkPos> out = new ArrayList<>();
        for (int z = cz - r; z <= cz + r; z++) for (int x = cx - r; x <= cx + r; x++) out.add(new ChunkPos(x, z));
        return out;
    }

    /** 청크 순서대로 놓인 POI 목록에서 findClosestWithType 의 답. 없으면 null. */
    public static Entry closest(List<ChunkPos> order, Map<ChunkPos, List<Entry>> byChunk, Pos origin, int radius) {
        long r2 = (long) radius * radius;
        Entry best = null;
        long bd = Long.MAX_VALUE;
        for (ChunkPos cp : order) {
            List<Entry> l = byChunk.get(cp);
            if (l == null) continue;
            for (Entry e : l) {
                Pos p = e.pos();
                if (Math.abs(p.x() - origin.x()) > radius || Math.abs(p.z() - origin.z()) > radius) continue;
                long dx = p.x() - origin.x(), dy = p.y() - origin.y(), dz = p.z() - origin.z();
                long d = dx * dx + dy * dy + dz * dz;
                if (d > r2) continue;
                if (d < bd) {   // Stream.min: 같으면 먼저 나온 것
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** 한 청크 묶음의 섹션 순서(아래부터) POI. */
    static List<Entry> collect(PoiChunk c, Predicate<PoiType> type) {
        List<Entry> out = new ArrayList<>();
        if (c == null) return out;
        for (int y = c.minSection(); y <= c.maxSection(); y++) {
            PoiSection s = c.section(y);
            if (s != null) s.forEachRecord(type, PoiManager.Occupancy.ANY, r -> out.add(new Entry(r.type(), r.pos())));
        }
        return out;
    }

    /**
     * findClosestWithType(type, origin, radius, ANY). 결과는 io 스레드에서 끝난다.
     * region 상태는 주인 region 스레드에서만 읽는다(복사본만 넘긴다).
     */
    public static CompletableFuture<Entry> findClosest(World world, Predicate<PoiType> type, Pos origin, int radius, Executor io) {
        List<ChunkPos> order = square(origin, radius);
        Map<Region, List<ChunkPos>> byOwner = new java.util.LinkedHashMap<>();
        List<ChunkPos> disk = java.util.Collections.synchronizedList(new ArrayList<>());
        for (ChunkPos cp : order) {
            Region owner = world.regionizer().ownerOf(cp.x(), cp.z());
            if (owner == null) disk.add(cp);
            else byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(cp);
        }
        Map<ChunkPos, List<Entry>> found = new java.util.concurrent.ConcurrentHashMap<>();
        List<CompletableFuture<Void>> parts = new ArrayList<>();
        for (List<ChunkPos> group : byOwner.values()) {
            parts.add(world.submitAtChunk(group.get(0), region -> {
                for (ChunkPos cp : group) {
                    // 그사이 주인이 바뀌었거나 안 올라왔으면 디스크에서
                    if (!world.ownsChunkOnCurrentThread(cp.x(), cp.z()) || !region.chunkLoaded(cp)) {
                        disk.add(cp);
                        continue;
                    }
                    List<Entry> l = new ArrayList<>();
                    region.level().poiManager().getInChunk(type, cp.x(), cp.z(), PoiManager.Occupancy.ANY, r -> l.add(new Entry(r.type(), r.pos())));
                    found.put(cp, l);
                }
                return null;
            }));
        }
        int minSection = world.dimensionType().minY() >> 4, sectionCount = world.dimensionType().height() >> 4;
        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApplyAsync(v -> {
            List<ChunkPos> todo;
            synchronized (disk) { todo = new ArrayList<>(disk); }
            for (ChunkPos cp : todo) found.put(cp, collect(readDisk(world, cp, minSection, sectionCount), type));
            return closest(order, new HashMap<>(found), origin, radius);
        }, io);
    }

    /** poi/ 지역 파일의 그 청크 묶음(없으면 null). */
    private static PoiChunk readDisk(World world, ChunkPos cp, int minSection, int sectionCount) {
        if (!(world.worldSource() instanceof AnvilWorldSource anvil) || anvil.poiStore() == null) return null;
        try {
            byte[] bytes;
            synchronized (anvil.poiLock(cp.x(), cp.z())) {
                bytes = anvil.poiStore().readChunk(cp.x(), cp.z());
            }
            if (bytes == null) return null;
            var nbt = org.domi.furnace.server.net.io.nbt.NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
            return PoiChunk.load(nbt, minSection, sectionCount);
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger(PoiLocator.class).warn("[POI] {},{} 읽기 실패", cp.x(), cp.z(), e);
            return null;
        }
    }
}
