// region 그리드 셀: 자기 플레이어를 소유하고 매 틱 청크/가시성/핸드오프를 처리한다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
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

    private final RegionPos pos;
    private final World world;
    private final Map<Integer, Player> players = new HashMap<>();
    private final Map<ChunkPos, ChunkColumn> chunks = new HashMap<>();

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
        for (Player sub : world.subscribers(cp)) sub.send(pkt);
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
                broadcastRemove(p.entityId());
                continue;
            }
            p.drainPending();
            RegionPos now = p.regionPos(world.regionSize());
            if (!now.equals(pos)) {     // 경계 넘음 → 핸드오프
                it.remove();
                broadcastRemove(p.entityId());
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
        p.send(new SetCenterChunk(center.x(), center.z()));
        streamLoads(p, toLoad);
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
            world.unsubscribeChunk(cp, p);
        }
        p.lastCenter(center);
    }

    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.subscribeChunk(cp, p);
            ChunkSnapshot snap = ChunkSnapshot.of(cp.x(), cp.z(), chunkAt(cp)); // region 스레드서 카피
            world.chunkPool().submit(() -> {
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
                return body;
            }).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
    }

    private void updateVisibility(Player a, List<EntityView> candidates) {
        double range = world.effectiveEntityChunks() * 16.0;
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
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
