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
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
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
