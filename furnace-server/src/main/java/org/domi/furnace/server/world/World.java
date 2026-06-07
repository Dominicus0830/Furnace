// region 소유자: 플레이어를 소유하고 매 틱 청크/가시성/이동을 처리한다. M3a는 region 1개.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.world.gen.WorldSource;

public final class World {

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final int viewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;

    private final Map<Integer, Player> players = new HashMap<>();

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, int viewDistance,
                 int blockDirectBits, int biomeDirectBits) {
        this.worldSource = worldSource;
        this.chunkPool = chunkPool;
        this.viewDistance = viewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
    }

    public void addPlayer(Player p) { players.put(p.entityId(), p); }

    public void removePlayer(int entityId) { players.remove(entityId); }

    public Map<Integer, Player> players() { return players; }

    /** region 스레드서 매 틱 호출. */
    public void tick() {
        for (Player p : players.values()) {
            updateChunks(p);
        }
        for (Player p : players.values()) {
            updateVisibility(p);
        }
    }

    /** 플레이어 제거 + 그를 추적하던 모두에게 디스폰 전송(region 스레드). */
    public void removePlayerAndBroadcast(int entityId) {
        players.remove(entityId);
        for (Player other : players.values()) {
            if (other.tracked().remove(entityId) != null) {
                other.send(new RemoveEntities(java.util.List.of(entityId)));
            }
        }
    }

    private void updateVisibility(Player a) {
        double range = viewDistance * 16.0;
        for (Player b : players.values()) {
            if (b.entityId() == a.entityId()) continue;
            boolean near = within(a, b, range);
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && !tracking) {            // 신규 진입 → 스폰
                a.send(new PlayerInfoUpdate(b.uuid(), b.name()));
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                a.tracked().put(b.entityId(), new double[]{b.x(), b.y(), b.z()});
            } else if (!near && tracking) {     // 이탈 → 제거
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                a.tracked().remove(b.entityId());
            } else if (near) {                  // 유지 → 이동 갱신
                broadcastMove(a, b, a.tracked().get(b.entityId()));
            }
        }
    }

    private static boolean within(Player a, Player b, double range) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz <= range * range;
    }

    private void broadcastMove(Player a, Player b, double[] last) {
        double ddx = b.x() - last[0], ddy = b.y() - last[1], ddz = b.z() - last[2];
        boolean big = Math.abs(ddx) >= 8 || Math.abs(ddy) >= 8 || Math.abs(ddz) >= 8;
        if (big) {
            a.send(new TeleportEntity(b.entityId(), b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.onGround()));
        } else if (ddx != 0 || ddy != 0 || ddz != 0) {
            short sdx = (short) Math.round(ddx * 4096);
            short sdy = (short) Math.round(ddy * 4096);
            short sdz = (short) Math.round(ddz * 4096);
            a.send(new MoveEntityPosRot(b.entityId(), sdx, sdy, sdz, b.yaw(), b.pitch(), b.onGround()));
        }
        a.send(new SetHeadRotation(b.entityId(), b.yaw()));
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z();
    }

    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter())) return; // 센터 안 바뀌면 no-op

        // 새 반경 집합
        List<ChunkPos> toLoad = new ArrayList<>();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!p.loadedChunks().contains(cp)) toLoad.add(cp);
            }
        }
        // 언로드: 새 반경 밖
        List<ChunkPos> toUnload = new ArrayList<>();
        for (ChunkPos cp : p.loadedChunks()) {
            if (Math.abs(cp.x() - center.x()) > viewDistance
                    || Math.abs(cp.z() - center.z()) > viewDistance) {
                toUnload.add(cp);
            }
        }

        p.send(new SetCenterChunk(center.x(), center.z()));
        streamLoads(p, toLoad);
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
        }
        p.lastCenter(center);
    }

    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        if (toLoad.isEmpty()) return;
        p.send(new ChunkBatchStart());
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            chunkPool.submit(() -> {
                ChunkColumn col = worldSource.generate(cp.x(), cp.z());
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, cp.x(), cp.z(), col, blockDirectBits, biomeDirectBits);
                return body;
            }).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
        p.send(new ChunkBatchFinished(toLoad.size()));
    }
}
