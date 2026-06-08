// region 그리드 셀: 자기 플레이어를 소유하고 매 틱 청크/가시성/핸드오프를 처리한다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
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

    private final RegionPos pos;
    private final World world;
    private final Map<Integer, Player> players = new HashMap<>();
    private final Map<ChunkPos, ChunkColumn> chunks = new HashMap<>();
    private final Map<ChunkPos, CompletableFuture<ChunkColumn>> loading = new HashMap<>();
    private final Map<ChunkPos, Integer> evictGrace = new HashMap<>();
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
        CompletableFuture<ChunkColumn> f = CompletableFuture
                .supplyAsync(() -> world.worldSource().generate(cp.x(), cp.z()), world.ioPool())
                .thenApplyAsync(col -> {
                    col.clearDirty();
                    chunks.put(cp, col);
                    loading.remove(cp);
                    return col;
                }, r -> world.scheduler().region(pos).execute(r));
        loading.put(cp, f);
        return f;
    }

    /** 소유 스레드서 블록 파괴(air) + 청크 구독자에게 BlockUpdate. */
    public void breakBlock(int bx, int by, int bz) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, ChunkSection.AIR);
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, ChunkSection.AIR);
        for (Player sub : world.subscribers(cp)) sub.send(pkt);
    }

    /** 로드된 dirty 청크 전부 저장(스냅샷→IO write), dirty clear. */
    public void saveDirtyChunks() {
        for (var e : chunks.entrySet()) {
            ChunkColumn col = e.getValue();
            if (col.dirty()) {
                world.saveChunkAsync(ChunkSnapshot.of(e.getKey().x(), e.getKey().z(), col));
                col.clearDirty();
            }
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
            }
        }
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
        }
        // 자기 플레이어 스냅샷 공개(불변 리스트).
        List<EntityView> views = new ArrayList<>(players.size());
        for (Player p : players.values()) views.add(EntityView.of(p));
        world.publishSnapshot(pos, List.copyOf(views));
        // 인접 region 스냅샷까지 후보로 가시성 계산.
        List<EntityView> candidates = world.candidatesAround(pos, world.neighborRadius());
        for (Player p : players.values()) {
            updateVisibility(p, candidates);
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

    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter())) return;
        int vd = world.viewDistance();
        List<ChunkPos> toLoad = new ArrayList<>();
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!p.loadedChunks().contains(cp)) toLoad.add(cp);
            }
        }
        List<ChunkPos> toUnload = new ArrayList<>();
        for (ChunkPos cp : p.loadedChunks()) {
            if (Math.abs(cp.x() - center.x()) > vd || Math.abs(cp.z() - center.z()) > vd) {
                toUnload.add(cp);
            }
        }
        orderNearestFirst(center, toLoad);
        p.send(new SetCenterChunk(center.x(), center.z()));
        streamLoads(p, toLoad);
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
            world.unsubscribeChunk(cp, p);
        }
        p.lastCenter(center);
    }

    /** toLoad를 중심 청크 거리 오름차순 정렬 — 스폰/근접 청크 먼저 전송해 클라가 즉시 "로드된 청크"에 스폰(낙하 방지). */
    static void orderNearestFirst(ChunkPos center, List<ChunkPos> chunks) {
        chunks.sort(java.util.Comparator.comparingInt(cp -> {
            int dx = cp.x() - center.x(), dz = cp.z() - center.z();
            return dx * dx + dz * dz;
        }));
    }

    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        if (toLoad.isEmpty()) return;
        // 배치로 감싸 클라가 첫 배치 도착까지 물리 정지(스폰 낙하 방지).
        p.send(new ChunkBatchStart());
        List<CompletableFuture<?>> sends = new ArrayList<>(toLoad.size());
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.subscribeChunk(cp, p);
            // 청크 소유 region에서 스냅샷을 받아(단일 소유) pool서 직렬화 후 전송.
            CompletableFuture<?> f = world.snapshotChunk(cp).thenCompose(snap -> world.chunkPool().submit(() -> {
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
                return body;
            })).thenAccept(body -> p.send(new LevelChunkWithLight(body)))
                    .exceptionally(ex -> {           // 실패해도 배치 닫아 클라 무한 로딩 방지
                        org.slf4j.LoggerFactory.getLogger(Region.class)
                                .warn("[CHUNK] 전송 실패 {}", cp, ex);
                        return null;
                    });
            sends.add(f);
        }
        CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
                .thenRun(() -> p.send(new ChunkBatchFinished(toLoad.size())));
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
