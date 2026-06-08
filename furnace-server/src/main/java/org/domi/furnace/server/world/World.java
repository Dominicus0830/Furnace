// region 컨테이너 + 공유 자원. 스레드별 region 맵을 들고 매 틱 자기 region들을 틱한다.
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.WorldSource;

public final class World {

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final FurnaceScheduler scheduler;
    private final int regionThreads;
    private final int regionSize;
    private final int viewDistance;
    private final int entityViewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;

    // 인덱스 i 맵은 region 스레드 i만 접근(스레드 확정). 동기화 불필요.
    private final Map<RegionPos, Region>[] regionsByThread;

    // 교차region 가시성 스냅샷: region 스레드가 공개, 인접 region 스레드가 락프리로 읽음.
    private final ConcurrentHashMap<RegionPos, List<EntityView>> snapshots = new ConcurrentHashMap<>();

    // 청크별 구독자(그 청크를 로드한 플레이어). 블록 변경 시 이들에게만 전송.
    private final ConcurrentHashMap<ChunkPos, Set<Player>> chunkSubscribers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits) {
        this.worldSource = worldSource;
        this.chunkPool = chunkPool;
        this.scheduler = scheduler;
        this.regionThreads = regionThreads;
        this.regionSize = regionSize;
        this.viewDistance = viewDistance;
        this.entityViewDistance = entityViewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        this.regionsByThread = new Map[regionThreads];
        for (int i = 0; i < regionThreads; i++) regionsByThread[i] = new HashMap<>();
    }

    /** region 스레드 i가 매 틱 호출 — 자기 region들 틱. */
    public void tickThread(int index) {
        for (Region r : regionsByThread[index].values()) {
            try {
                r.tick();
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(World.class)
                        .error("[TICK] region {} 실패", r.pos(), t);
            }
        }
    }

    /** pos의 region을 그 소유 스레드서 get/create. */
    public Region regionAt(RegionPos pos) {
        int idx = RegionGrid.threadIndex(pos, regionThreads);
        return regionsByThread[idx].computeIfAbsent(pos, p -> new Region(p, this));
    }

    /** 디버그 소유권 가드: pos를 소유한 스레드서 호출 중인지 확인. */
    public void assertOwns(RegionPos pos) {
        ThreadOwner owner = (ThreadOwner) scheduler.region(pos);
        assert owner.isOwnerThread()
                : "region " + pos + " 를 비소유 스레드서 접근: " + Thread.currentThread().getName();
    }

    /** region이 자기 플레이어 스냅샷을 공개(덮어쓰기). views는 불변이어야 한다. */
    public void publishSnapshot(RegionPos pos, List<EntityView> views) {
        snapshots.put(pos, views);
    }

    /** center±radius region들의 스냅샷을 모아 반환. */
    public List<EntityView> candidatesAround(RegionPos center, int radius) {
        List<EntityView> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                List<EntityView> s = snapshots.get(new RegionPos(center.x() + dx, center.z() + dz));
                if (s != null) out.addAll(s);
            }
        }
        return out;
    }

    /** 플레이어가 청크를 로드 — 블록 변경 수신 등록. */
    public void subscribeChunk(ChunkPos cp, Player p) {
        chunkSubscribers.computeIfAbsent(cp, k -> ConcurrentHashMap.newKeySet()).add(p);
    }

    /** 플레이어가 청크를 언로드 — 등록 해제. */
    public void unsubscribeChunk(ChunkPos cp, Player p) {
        Set<Player> s = chunkSubscribers.get(cp);
        if (s != null) s.remove(p);
    }

    /** 청크 구독자(블록 변경 브로드캐스트 대상). 비었으면 빈 Set. */
    public Set<Player> subscribers(ChunkPos cp) {
        Set<Player> s = chunkSubscribers.get(cp);
        return s == null ? Set.of() : s;
    }

    /** 엔티티 가시 거리(view-distance로 클램프)가 닿는 인접 region 반경(region 단위). */
    public int neighborRadius() {
        return (int) Math.ceil((double) effectiveEntityChunks() / regionSize) + 1;
    }

    /** 실제 엔티티 가시 거리(청크). view-distance를 넘으면 미렌더 청크 방지 위해 클램프. */
    public int effectiveEntityChunks() {
        return Math.min(entityViewDistance, viewDistance);
    }

    public FurnaceScheduler scheduler() { return scheduler; }
    public WorldSource worldSource() { return worldSource; }
    public ChunkWorkerPool chunkPool() { return chunkPool; }
    public int regionSize() { return regionSize; }
    public int viewDistance() { return viewDistance; }
    public int entityViewDistance() { return entityViewDistance; }
    public int blockDirectBits() { return blockDirectBits; }
    public int biomeDirectBits() { return biomeDirectBits; }
}
