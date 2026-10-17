// region 하나의 블록 레벨: 그 region 구역의 로드된 청크만 다룬다. 다른 region 의 칸은 로드되지 않은 것으로 본다(합쳐지기 전 1~2틱만 생길 수 있다).
// 블록 변경 방송은 청크 구독(전달 전이면 청크 뒤로 보류)을 따르고, 블록 요청 ack 는 틱 끝 방송 뒤에 보낸다(바닐라 순서).
package org.domi.furnace.server.world;

import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;

public final class RegionLevel extends ChunkLevel {

    private final Region region;
    private final World world;
    private final int chest;
    /** 이번 틱에 ack 할 플레이어 → 가장 큰 sequence(바닐라 ackBlockChangesUpTo). */
    private final Map<Player, Integer> pendingAcks = new LinkedHashMap<>();
    // 같은 청크 연속 조회를 싸게(퇴출·주인 변경 때 비운다)
    private int lastCx = Integer.MIN_VALUE, lastCz;
    private ChunkColumn lastCol;

    RegionLevel(Region region, World world) {
        super(world.levelServices().maxChainedNeighborUpdates());
        this.region = region;
        this.world = world;
        this.chest = blocks.block("minecraft:chest");
    }

    @Override
    protected ChunkColumn chunk(int chunkX, int chunkZ) {
        if (chunkX == lastCx && chunkZ == lastCz && lastCol != null) return lastCol;
        ChunkColumn c = region.ownedChunk(chunkX, chunkZ);
        if (c != null) {
            lastCx = chunkX;
            lastCz = chunkZ;
            lastCol = c;
        }
        return c;
    }

    /** 청크가 내려가거나 주인이 바뀌면 조회 캐시를 비운다. */
    void chunkUnloaded() { lastCol = null; }

    @Override
    protected void markDirty(int chunkX, int chunkZ) { region.markChunkDirty(new ChunkPos(chunkX, chunkZ)); }

    @Override
    protected void broadcastToChunk(int chunkX, int chunkZ, Packet packet) { world.broadcastBlockUpdate(new ChunkPos(chunkX, chunkZ), packet); }

    @Override
    protected LevelServices services() { return world.levelServices(); }

    @Override
    protected void createBlockEntity(ChunkColumn col, Pos p, int state) {
        // 블록 엔티티 체계 전에는 상자만
        if (blocks.blockOf(state) == chest && col.chestAt(p.x(), p.y(), p.z()) == null) col.putChest(ChestBlockEntity.empty(p.x(), p.y(), p.z()));
    }

    @Override
    protected void removeBlockEntity(ChunkColumn col, Pos p) {
        col.removeBlockEntityAt(p.x(), p.y(), p.z());
        region.closeChestScreens(p.x(), p.y(), p.z());
    }

    // ------------------------------------------------------------------ 합치기·나누기

    void absorbFrom(RegionLevel o) {
        absorb(o);
        for (var e : o.pendingAcks.entrySet()) pendingAcks.merge(e.getKey(), e.getValue(), Math::max);
        o.pendingAcks.clear();
        lastCol = null;
    }

    void splitInto(RegionLevel target, ChunkFilter inTarget) {
        splitTo(target, inTarget);
        for (var it = pendingAcks.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ChunkPos c = e.getKey().chunkPos();
            if (inTarget.test(c.x(), c.z())) {
                target.pendingAcks.merge(e.getKey(), e.getValue(), Math::max);
                it.remove();
            }
        }
        lastCol = null;
    }

    // ------------------------------------------------------------------ ack

    /** 블록 요청의 sequence 를 받아 둔다 — 이번 틱 방송 뒤에 한 번(가장 큰 값). 음수는 호출 쪽에서 거른다. */
    public void ackBlockChangesUpTo(Player p, int sequence) { pendingAcks.merge(p, sequence, Math::max); }

    boolean hasPendingAcks() { return !pendingAcks.isEmpty(); }

    void flushAcks() {
        if (pendingAcks.isEmpty()) return;
        for (var e : pendingAcks.entrySet()) e.getKey().send(new BlockChangedAck(e.getValue()));
        pendingAcks.clear();
    }

    /** 레벨 틱(바닐라 ServerLevel.tick 의 블록 부분): 예약 틱 → 변경 방송 → 블록 이벤트. */
    void tickBlocks() {
        lastCol = null;   // 틱 사이에 합치기·나누기가 있었을 수 있다
        runScheduledTicks();
        broadcastChanges();
        runBlockEvents();
    }
}
