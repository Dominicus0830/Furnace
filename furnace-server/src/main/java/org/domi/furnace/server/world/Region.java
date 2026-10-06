// region 그리드 셀: 자기 플레이어를 소유하고 매 틱 청크/가시성/핸드오프를 처리한다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Region {

    private static final Logger log = LoggerFactory.getLogger(Region.class);

    /** 청크 구독 상태. 청크가 클라에 전달되기 전(ready=false)의 변경은 쌓았다가 청크 뒤에 보낸다. */
    private static final class Subscription {
        boolean ready;
        final List<BlockUpdate> pending = new ArrayList<>();
    }

    private final RegionPos pos;
    private final World world;
    private final Map<Integer, Player> players = new HashMap<>();
    private final Set<Integer> justAdopted = new HashSet<>(); // 이번 틱 스냅샷에 실린 뒤 transit 해제할 엔티티
    private final Map<ChunkPos, ChunkColumn> chunks = new HashMap<>();
    // 소유 청크별 구독자(소유 스레드 전용). 구독·스냅샷·전송·블록변경이 한 스레드서 직렬화된다.
    private final Map<ChunkPos, Map<Player, Subscription>> subscribers = new HashMap<>();

    public Region(RegionPos pos, World world) {
        this.pos = pos;
        this.world = world;
    }

    public RegionPos pos() { return pos; }
    public Map<Integer, Player> players() { return players; }

    /** 소유 스레드서 청크 get-or-load(보관). */
    public ChunkColumn chunkAt(ChunkPos cp) {
        return chunks.computeIfAbsent(cp, p -> world.worldSource().generate(p.x(), p.z()));
    }

    /** 소유 스레드서 블록 파괴(air) + 청크 구독자에게 BlockUpdate. */
    public void breakBlock(int bx, int by, int bz) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, ChunkSection.AIR);
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, ChunkSection.AIR);
        Map<Player, Subscription> subs = subscribers.get(cp);
        if (subs == null) return;
        for (Map.Entry<Player, Subscription> e : subs.entrySet()) {
            Subscription sub = e.getValue();
            if (sub.ready) e.getKey().send(pkt);
            else sub.pending.add(pkt);   // 청크가 아직 안 갔다 — 청크 뒤에 보낸다
        }
    }

    /**
     * 소유 스레드서 플레이어에게 청크를 연다: 구독 등록 + 스냅샷을 한 번에 하고(이후 변경은 전부 구독을 거침),
     * 직렬화는 풀서 한 뒤 소유 스레드로 돌아와 전송 → 청크가 항상 뒤따르는 BlockUpdate보다 먼저 간다.
     */
    public void openChunk(ChunkPos cp, Player p) {
        world.assertOwns(pos);
        Subscription sub = new Subscription();
        subscribers.computeIfAbsent(cp, k -> new HashMap<>()).put(p, sub);
        ChunkSnapshot snap = ChunkSnapshot.of(cp.x(), cp.z(), chunkAt(cp));
        world.chunkPool().submit(() -> {
            ByteBuf body = Unpooled.buffer();
            ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
            return body;
        }).whenComplete((body, err) ->
                world.runOn(pos, () -> deliverChunk(cp, p, sub, body, err)));
    }

    private void deliverChunk(ChunkPos cp, Player p, Subscription sub, ByteBuf body, Throwable err) {
        Map<Player, Subscription> subs = subscribers.get(cp);
        boolean current = subs != null && subs.get(p) == sub;
        if (err != null) {
            log.error("[CHUNK] {} 직렬화 실패(플레이어 {})", cp, p.name(), err);
            if (current) subs.remove(p);
            return;
        }
        if (!current) {            // 전달 전에 언로드/재구독됨 — 이 청크는 버린다
            body.release();
            return;
        }
        p.send(new LevelChunkWithLight(body));
        sub.ready = true;
        for (BlockUpdate u : sub.pending) p.send(u);
        sub.pending.clear();
    }

    /** 소유 스레드서 구독 해제. */
    public void closeChunk(ChunkPos cp, Player p) {
        world.assertOwns(pos);
        Map<Player, Subscription> subs = subscribers.get(cp);
        if (subs == null) return;
        subs.remove(p);
        if (subs.isEmpty()) subscribers.remove(cp);
    }

    /** 소유 스레드서 플레이어 편입. */
    public void adopt(Player p) {
        world.assertOwns(pos);
        p.currentRegion(pos);
        players.put(p.entityId(), p);
        justAdopted.add(p.entityId());
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
                for (ChunkPos cp : p.loadedChunks()) world.releaseChunk(cp, p);
                continue;   // 관찰자는 스냅샷서 사라진 걸 보고 디스폰한다(후보 부재 = 퇴장)
            }
            p.drainPending();
            RegionPos now = p.regionPos(world.regionSize());
            if (!now.equals(pos)) {     // 경계 넘음 → 핸드오프
                it.remove();
                // 가시성은 스냅샷 구동. 새 region이 편입·공개하기 전까지 transit으로 후보에 남겨
                // 관찰자가 끊김 없이 추적(깜빡임 방지)하되, 진짜 사라진 엔티티는 후보서 빠져 디스폰된다.
                world.markTransit(EntityView.of(p));
                handoff.add(p);
                continue;
            }
            updateChunks(p);
        }
        // 자기 플레이어 스냅샷 공개(불변 리스트).
        List<EntityView> views = new ArrayList<>(players.size());
        for (Player p : players.values()) views.add(EntityView.of(p));
        world.publishSnapshot(pos, List.copyOf(views));
        for (int eid : justAdopted) world.clearTransit(eid);   // 스냅샷에 실린 뒤에만 해제(공백 없음)
        justAdopted.clear();
        // 인접 region 스냅샷까지 후보로 가시성 계산.
        List<EntityView> candidates = world.candidatesAround(pos, world.neighborRadius());
        for (Player p : players.values()) {
            updateVisibility(p, candidates);
        }
        for (Player p : handoff) {
            RegionPos target = p.regionPos(world.regionSize());
            world.runOn(target, () -> world.regionAt(target).adopt(p));
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
        p.send(new SetCenterChunk(center.x(), center.z()));
        streamLoads(p, toLoad);
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
            world.releaseChunk(cp, p);
        }
        p.lastCenter(center);
    }

    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.requestChunk(cp, p);
        }
    }

    private void updateVisibility(Player a, List<EntityView> candidates) {
        double range = world.effectiveEntityChunks() * 16.0;
        Set<Integer> present = new HashSet<>();
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
            present.add(b.entityId());
            boolean near = within(a, b, range);
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && !tracking) {
                a.send(new PlayerInfoUpdate(b.uuid(), b.name()));
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                a.tracked().put(b.entityId(), new double[]{b.x(), b.y(), b.z(), b.yaw(), b.pitch()});
                a.trackedUuids().put(b.entityId(), b.uuid());
            } else if (!near && tracking) {
                despawn(a, b.entityId());
            } else if (near) {
                broadcastMove(a, b, a.tracked().get(b.entityId()));
            }
        }
        // 후보서 사라진 추적 대상(퇴장·원거리 이동) — 스냅샷에 없으면 이미 없는 것이다.
        List<Integer> gone = null;
        for (Integer eid : a.tracked().keySet()) {
            if (!present.contains(eid)) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(eid);
            }
        }
        if (gone != null) for (int eid : gone) despawn(a, eid);
    }

    private void despawn(Player a, int eid) {
        a.tracked().remove(eid);
        UUID uuid = a.trackedUuids().remove(eid);
        a.send(new RemoveEntities(List.of(eid)));
        if (uuid != null) a.send(new PlayerInfoRemove(List.of(uuid)));
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
