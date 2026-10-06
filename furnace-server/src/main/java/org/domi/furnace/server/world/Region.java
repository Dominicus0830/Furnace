// region 그리드 셀: 자기 플레이어를 소유하고 매 틱 청크/가시성/핸드오프를 처리한다.
package org.domi.furnace.server.world;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;

public final class Region {

    // 추적 엔티티가 후보 스냅샷서 사라진 뒤 despawn까지 견디는 틱 수(핸드오프 갭 흡수).
    private static final int ENTITY_DESPAWN_GRACE = 3;
    private static final int MAX_PENDING_CHUNKS_PER_PLAYER = 512;

    private final RegionPos pos;
    private final World world;
    private final Map<Integer, Player> players = new HashMap<>();
    private final Map<ChunkPos, ChunkColumn> chunks = new HashMap<>();
    private final Map<ChunkPos, CompletableFuture<ChunkColumn>> loading = new HashMap<>();
    private final Map<ChunkPos, Integer> evictGrace = new HashMap<>();
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();
    private int autosaveCounter; // region별 독립 autosave 카운터(스레드간 공유 플래그 없음 — 경쟁 제거).

    public Region(RegionPos pos, World world) {
        this.pos = pos;
        this.world = world;
    }

    public RegionPos pos() { return pos; }
    public Map<Integer, Player> players() { return players; }

    /** 소유 스레드서 로드된 청크 반환. 없으면 동기 generate 폴백(편집은 로드된 청크 대상). */
    public ChunkColumn chunkAt(ChunkPos cp) {
        return chunks.computeIfAbsent(cp, p -> {
            ChunkColumn c = world.worldSource().generate(p.x(), p.z());
            c.clearDirty();
            return c;
        });
    }

    /** 소유 스레드서 비동기 청크 로드(IO 풀 read+decode). in-flight dedup. */
    public CompletableFuture<ChunkColumn> loadChunk(ChunkPos cp) {
        world.assertOwns(pos);
        ChunkColumn present = chunks.get(cp);
        if (present != null) return CompletableFuture.completedFuture(present);
        CompletableFuture<ChunkColumn> inflight = loading.get(cp);
        if (inflight != null) return inflight;
        CompletableFuture<ChunkColumn> sourceFuture =
                world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource anvil
                        ? anvil.loadChunkAsync(cp.x(), cp.z(), world.ioPool(), world.genPool())
                        : CompletableFuture.supplyAsync(() -> world.worldSource().generate(cp.x(), cp.z()), world.genPool());
        CompletableFuture<ChunkColumn> f = sourceFuture.thenApplyAsync(col -> {
            col.clearDirty();
            chunks.put(cp, col);
            loading.remove(cp);
            return col;
        }, r -> world.scheduler().region(pos).execute(r));
        loading.put(cp, f);
        return f;
    }

    /** 소유 스레드서 블록 파괴(air) + 청크 구독자에게 BlockUpdate. 체스트면 엔티티 제거 + 열어둔 플레이어 강제닫기. */
    public void breakBlock(int bx, int by, int bz) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, ChunkSection.AIR);
        chunkAt(cp).removeBlockEntityAt(bx, by, bz);
        dirtyChunks.add(cp);
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, ChunkSection.AIR);
        world.broadcastBlockUpdate(cp, pkt);   // 청크가 아직 안 간 구독자는 청크 뒤로 보류
        // 이 region 소속 플레이어 중 그 체스트를 열어둔 사람에게 강제 ContainerClose(§3.5).
        // 다른 region 소속 플레이어가 열어둔 경우는 범위 밖(design result.md Issues #5).
        for (Player pl : players.values()) {
            int[] open = pl.openChestPos();
            if (open != null && open[0] == bx && open[1] == by && open[2] == bz) {
                pl.send(new org.domi.furnace.server.net.protocol.play.ContainerClose(pl.openContainerId()));
                pl.closeChest();
            }
        }
    }

    /** 소유 스레드서 블록 조회(0x38 체스트 판별용). */
    public int getBlock(int x, int y, int z) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        return chunkAt(cp).getBlock(x & 15, y, z & 15);
    }

    /** 소유 스레드서 체스트 블록엔티티 조회(없으면 null). */
    public ChestBlockEntity chestAt(int x, int y, int z) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        return chunkAt(cp).chestAt(x, y, z);
    }

    /** 체스트 슬롯 갱신(Container Click). 존재하지 않으면 무시. 청크 dirty 마크(영속, §3.6). */
    public void setChestItem(int cx, int cy, int cz, int slot, ItemStack item) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(cx, cz);
        ChestBlockEntity chest = chunkAt(cp).chestAt(cx, cy, cz);
        if (chest == null || slot < 0 || slot >= chest.items.length) return;
        chest.items[slot] = item;
        chunkAt(cp).markDirty();
        dirtyChunks.add(cp);
    }

    /** 면 인덱스(0~5) → 설치 위치 오프셋 {dx,dy,dz}. 0down 1up 2north 3south 4west 5east. */
    public static int[] placeOffset(int face) {
        return switch (face) {
            case 0 -> new int[]{0, -1, 0};
            case 1 -> new int[]{0, 1, 0};
            case 2 -> new int[]{0, 0, -1};
            case 3 -> new int[]{0, 0, 1};
            case 4 -> new int[]{-1, 0, 0};
            case 5 -> new int[]{1, 0, 0};
            default -> new int[]{0, 0, 0};
        };
    }

    /** 소유 스레드서 블록 설치. 플레이어 바운딩박스와 겹치면 거부(false). 성공 시 setBlock+BlockUpdate. */
    public boolean placeBlock(int bx, int by, int bz, int stateId) {
        world.assertOwns(pos);
        if (stateId == ChunkSection.AIR) return false;
        // 플레이어 충돌 거부: 블록 AABB [bx,bx+1]×[by,by+1]×[bz,bz+1] vs 플레이어(가로±0.3,높이1.8).
        for (Player pl : players.values()) {
            double px = pl.x(), py = pl.y(), pz = pl.z();
            boolean overlap = px + 0.3 > bx && px - 0.3 < bx + 1
                    && py + 1.8 > by && py < by + 1
                    && pz + 0.3 > bz && pz - 0.3 < bz + 1;
            if (overlap) return false;
        }
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, stateId);
        dirtyChunks.add(cp);
        BlockRegistry breg = world.blockRegistry();
        if (breg != null && "minecraft:chest".equals(breg.nameOf(stateId))) {
            chunkAt(cp).putChest(ChestBlockEntity.empty(bx, by, bz));
        }
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, stateId);
        world.broadcastBlockUpdate(cp, pkt);   // 청크가 아직 안 간 구독자는 청크 뒤로 보류
        return true;
    }

    /** 로드된 dirty 청크 전부 저장(스냅샷→IO write), dirty clear. */
    public void saveDirtyChunks() {
        if (dirtyChunks.isEmpty()) return;
        for (ChunkPos cp : new ArrayList<>(dirtyChunks)) {
            ChunkColumn col = chunks.get(cp);
            if (col != null && col.dirty()) {
                world.saveChunkAsync(ChunkSnapshot.of(cp.x(), cp.z(), col));
                col.clearDirty();
            }
            dirtyChunks.remove(cp);
        }
    }

    /** 구독자 0인 청크를 grace 후 퇴출(dirty면 저장). 매 틱 호출. */
    private void evictIdleChunks() {
        var it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ChunkPos cp = e.getKey();
            if (!world.subscribers(cp).isEmpty()) { evictGrace.remove(cp); continue; }
            int g = evictGrace.merge(cp, 1, Integer::sum);
            if (g >= world.chunkEvictionGraceTicks()) {
                ChunkColumn col = e.getValue();
                if (col.dirty()) {
                    world.saveChunkAsync(ChunkSnapshot.of(cp.x(), cp.z(), col));
                    col.clearDirty();
                }
                it.remove();
                evictGrace.remove(cp);
                dirtyChunks.remove(cp);
            }
        }
    }

    int dirtyChunkCount() {
        return dirtyChunks.size();
    }

    /** 플레이어·청크·로딩중 모두 없으면 true — tickThread가 빈 region을 regionsByThread서 제거. */
    boolean isEmpty() {
        return players.isEmpty() && chunks.isEmpty() && loading.isEmpty();
    }

    /** 접속 중 플레이어 위치 저장(종료 flush용). */
    public void savePlayers(java.nio.file.Path worldDir, java.util.concurrent.ExecutorService io) {
        for (Player p : players.values()) {
            final Player fp = p;
            io.execute(() -> org.domi.furnace.server.entity.PlayerData.save(
                    worldDir, fp.uuid(), fp.x(), fp.y(), fp.z(), fp.yaw(), fp.pitch(), fp.onGround()));
        }
    }

    /** 소유 스레드서 플레이어 편입. */
    public void adopt(Player p) {
        world.assertOwns(pos);
        p.currentRegion(pos);
        players.put(p.entityId(), p);
    }

    /** 소유 스레드서 매 틱 호출. */
    public void tick() {
        world.assertOwns(pos);
        List<Player> handoff = new ArrayList<>();
        var it = players.values().iterator();
        while (it.hasNext()) {
            Player p = it.next();
            if (p.isDisconnected()) {
                it.remove();
                for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
                final Player fp = p;
                world.ioPool().execute(() -> org.domi.furnace.server.entity.PlayerData.save(
                        world.worldDir(), fp.uuid(), fp.x(), fp.y(), fp.z(), fp.yaw(), fp.pitch(), fp.onGround()));
                broadcastRemove(p.entityId());
                continue;
            }
            p.drainPending();
            RegionPos now = p.regionPos(world.regionSize());
            if (!now.equals(pos)) {     // 경계 넘음 → 핸드오프
                it.remove();
                // broadcastRemove 안 함 — 가시성은 스냅샷 구동. 인접 region으로 넘어가도
                // candidatesAround가 계속 후보로 보므로 관찰자는 끊김 없이 추적 유지(깜빡임 방지).
                handoff.add(p);
                continue;
            }
            updateChunks(p);
            sendQueuedChunks(p);
        }
        // 플레이어 없는 region은 가시성 계산(candidatesAround)·스냅샷 빌드 생략 — 빈 region tick 비용 제거.
        // 스냅샷은 빈 리스트로 갱신해 이전 틱 엔티티가 스테일로 남지 않게 한다.
        if (!players.isEmpty()) {
            List<EntityView> views = new ArrayList<>(players.size());
            for (Player p : players.values()) views.add(EntityView.of(p));
            world.publishSnapshot(pos, List.copyOf(views));
            // 인접 region 스냅샷까지 후보로 가시성 계산.
            List<EntityView> candidates = world.candidatesAround(pos, world.neighborRadius());
            for (Player p : players.values()) {
                updateVisibility(p, candidates);
            }
        } else {
            world.publishSnapshot(pos, List.of());
        }
        for (Player p : handoff) {
            RegionPos target = p.regionPos(world.regionSize());
            world.scheduler().region(target).execute(() -> world.regionAt(target).adopt(p));
        }
        evictIdleChunks();
        if (++autosaveCounter >= world.autosaveTicks()) {
            autosaveCounter = 0;
            saveDirtyChunks();
        }
    }

    /** 이 region의 타 플레이어들이 eid를 추적 중이면 디스폰. */
    private void broadcastRemove(int eid) {
        for (Player other : players.values()) {
            if (other.tracked().remove(eid) != null) {
                other.send(new RemoveEntities(java.util.List.of(eid)));
            }
        }
    }

    /** 중심 이동 시 시야 청크를 pending 큐에 적재(+구독), 시야 밖은 언로드. 전송은 sendQueuedChunks가 틱당 스로틀. */
    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter())) return;
        if (p.pendingChunks().size() > MAX_PENDING_CHUNKS_PER_PLAYER) {
            p.lastCenter(center);
            p.send(new SetCenterChunk(center.x(), center.z()));
            return;
        }
        int vd = Math.min(world.viewDistance(), p.viewDistance()); // 서버 상한 + 클라 요청
        List<ChunkPos> toQueue = new ArrayList<>();
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (p.loadedChunks().add(cp)) {   // 새로 시야 진입
                    world.subscribeChunk(cp, p);
                    toQueue.add(cp);              // 전송 대기열(아직 미전송)
                }
            }
        }
        List<ChunkPos> toForget = new ArrayList<>();
        var it = p.loadedChunks().iterator();
        while (it.hasNext()) {
            ChunkPos cp = it.next();
            if (Math.abs(cp.x() - center.x()) > vd || Math.abs(cp.z() - center.z()) > vd) {
                it.remove();
                world.unsubscribeChunk(cp, p);
                // 아직 안 보낸(pending) 청크면 forget 패킷 불필요 — 큐서만 제거.
                if (!p.pendingChunks().remove(cp)) toForget.add(cp);
            }
        }
        orderNearestFirst(center, toQueue);
        p.pendingChunks().addAll(toQueue);
        reorderPendingNearestFirst(center, p.pendingChunks());
        p.send(new SetCenterChunk(center.x(), center.z()));
        for (ChunkPos cp : toForget) p.send(new UnloadChunk(cp.x(), cp.z()));
        p.lastCenter(center);
    }

    /** chunks를 중심 거리 오름차순 정렬 — 스폰/근접 청크 먼저 전송해 클라가 즉시 "로드된 청크"에 스폰(낙하 방지). */
    static void orderNearestFirst(ChunkPos center, List<ChunkPos> chunks) {
        chunks.sort(java.util.Comparator.comparingInt(cp -> {
            int dx = cp.x() - center.x(), dz = cp.z() - center.z();
            return dx * dx + dz * dz;
        }));
    }

    static void reorderPendingNearestFirst(ChunkPos center, java.util.Set<ChunkPos> pending) {
        if (pending.size() < 2) return;
        List<ChunkPos> ordered = new ArrayList<>(pending);
        orderNearestFirst(center, ordered);
        pending.clear();
        pending.addAll(ordered);
    }

    /** 매 틱: pending 큐서 최대 CHUNKS_PER_TICK개를 nearest-first로 한 배치 전송(vanilla PlayerChunkSender식 스로틀).
     *  스폰 청크가 첫 틱 소량 배치에 들어가 빨리 도착 → 클라가 즉시 안착(289개 일괄 async fan-out 회피). */
    private void sendQueuedChunks(Player p) {
        if (p.pendingChunks().isEmpty()) return;
        int perTick = world.chunksPerTick();
        List<ChunkPos> batch = new ArrayList<>(Math.min(perTick, p.pendingChunks().size()));
        var it = p.pendingChunks().iterator();
        while (it.hasNext() && batch.size() < perTick) {
            batch.add(it.next());
            it.remove();
        }

        p.send(new ChunkBatchStart());
        // 실제 전송 수(stale 드롭으로 batch.size와 다를 수 있음) — ChunkBatchFinished에 정확히 보고.
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        List<CompletableFuture<?>> sends = new ArrayList<>(batch.size());
        for (ChunkPos cp : batch) {
            // 구독은 updateChunks에서 큐에 넣을 때 했다. 이 구독이 청크 전달 전까지 블록 변경을 보류한다.
            World.Subscription sub = world.subscriptionOf(cp, p);
            if (sub == null) continue;   // 그 사이 언로드됨 — 보낼 것 없음
            CompletableFuture<?> f = world.serializedChunkBody(cp)
                    .thenApply(Unpooled::wrappedBuffer)
                    .thenAccept(body -> {
                        // gen 지연 동안 플레이어가 시야 밖으로 이동했거나(재구독 포함) 언로드됐으면 보내봐야 클라가
                        // "not in view range"로 버림 → 드롭. 위치는 volatile이라 완료 스레드서 안전하게 읽음.
                        if (inView(p, cp) && world.isSubscribed(cp, p, sub)) {
                            p.send(new LevelChunkWithLight(body));
                            sent.incrementAndGet();
                            world.chunkDelivered(cp, p, sub);   // 청크가 채널 큐에 들어간 뒤 보류된 변경을 흘려보낸다
                        }
                    })
                    .exceptionally(ex -> {           // 실패해도 배치 닫아 클라 무한 로딩 방지
                        org.slf4j.LoggerFactory.getLogger(Region.class)
                                .warn("[CHUNK] 전송 실패 {}", cp, ex);
                        return null;
                    });
            sends.add(f);
        }
        // allOf로 모든 future(전송/예외) 완료 후 Finished 1회 — 마샬 누락에 의한 batch 미종료(클라 stall) 방지.
        CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
                .thenRun(() -> p.send(new ChunkBatchFinished(sent.get())));
    }

    /** 청크가 플레이어의 현재 시야(per-player view distance, Chebyshev) 안인지. stale 드롭·전송 게이트용. */
    private boolean inView(Player p, ChunkPos cp) {
        ChunkPos c = p.chunkPos();
        int vd = Math.min(world.viewDistance(), p.viewDistance());
        return Math.abs(cp.x() - c.x()) <= vd && Math.abs(cp.z() - c.z()) <= vd;
    }

    private void updateVisibility(Player a, List<EntityView> candidates) {
        double range = world.effectiveEntityChunks() * 16.0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
            seen.add(b.entityId());
            boolean near = within(a, b, range);
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && !tracking) {
                a.send(new PlayerInfoUpdate(b.uuid(), b.name()));
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                a.tracked().put(b.entityId(), new double[]{b.x(), b.y(), b.z(), b.yaw(), b.pitch()});
            } else if (!near && tracking) {
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                a.tracked().remove(b.entityId());
            } else if (near) {
                broadcastMove(a, b, a.tracked().get(b.entityId()));
            }
        }
        // 후보서 완전히 사라진 추적 엔티티: grace 틱 후 despawn(퇴장·스냅샷 범위 이탈).
        // 핸드오프는 인접 region 스냅샷 갭이 1틱 정도라 grace 미만 → 깜빡임 없음.
        var ti = a.tracked().keySet().iterator();
        while (ti.hasNext()) {
            int eid = ti.next();
            if (seen.contains(eid)) { a.missingTicks().remove(eid); continue; }
            if (a.missingTicks().merge(eid, 1, Integer::sum) >= ENTITY_DESPAWN_GRACE) {
                a.send(new RemoveEntities(java.util.List.of(eid)));
                ti.remove();
                a.missingTicks().remove(eid);
            }
        }
    }

    private static boolean within(Player a, EntityView b, double range) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz <= range * range;
    }

    private void broadcastMove(Player a, EntityView b, double[] last) {
        double ddx = b.x() - last[0], ddy = b.y() - last[1], ddz = b.z() - last[2];
        boolean posChanged = ddx != 0 || ddy != 0 || ddz != 0;
        boolean rotChanged = b.yaw() != last[3] || b.pitch() != last[4];
        if (!posChanged && !rotChanged) return;
        boolean big = Math.abs(ddx) >= 8 || Math.abs(ddy) >= 8 || Math.abs(ddz) >= 8;
        if (big) {
            a.send(new TeleportEntity(b.entityId(), b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.onGround()));
        } else if (posChanged) {
            short sdx = (short) Math.round(ddx * 4096);
            short sdy = (short) Math.round(ddy * 4096);
            short sdz = (short) Math.round(ddz * 4096);
            a.send(new MoveEntityPosRot(b.entityId(), sdx, sdy, sdz, b.yaw(), b.pitch(), b.onGround()));
        } else {
            a.send(new MoveEntityRot(b.entityId(), b.yaw(), b.pitch(), b.onGround()));
        }
        a.send(new SetHeadRotation(b.entityId(), b.yaw()));
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z(); last[3] = b.yaw(); last[4] = b.pitch();
    }
}
