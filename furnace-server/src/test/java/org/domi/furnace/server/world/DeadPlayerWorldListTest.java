// 죽은 플레이어를 20틱 뒤 차원 레벨 목록에서 빼기(open item #7): 바닐라 LivingEntity.tickDeath → remove(KILLED) → ServerLevel EntityCallbacks.onTrackingEnd.
// 잠 비율·사라짐·근접 조회는 빠진 목록을 보고, 소리·레벨 이벤트 방송(PlayerList.broadcast)은 죽음 화면 플레이어도 받는다. 리스폰이 끝에 다시 올린다.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LevelEventPacket;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeadPlayerWorldListTest {

    private static final RegionPos A = new RegionPos(0, 0);

    private FurnaceSchedulerImpl sched;
    private World w;
    private Player near, far, other;
    private EmbeddedChannel chNear;

    @BeforeEach
    void setup() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 20, 20, reg.directBits(), 7, Executors.newSingleThreadExecutor());
        chNear = new ConcurrentEmbeddedChannel();
        near = new Player(1, UUID.randomUUID(), "near", chNear);
        far = new Player(2, UUID.randomUUID(), "far", new ConcurrentEmbeddedChannel());
        other = new Player(3, UUID.randomUUID(), "other", new ConcurrentEmbeddedChannel());
        place(near, 10, 10);
        place(far, 100, 10);
        place(other, 12, 12);
        for (Player p : List.of(near, far, other)) {
            w.playerOnline(p);
            sched.region(A).execute(() -> w.regionAt(A).adopt(p)).get();
        }
    }

    private static void place(Player p, double x, double z) {
        p.publishPosition(x, -60, z, 0, 0, true);
        p.drainPending();
    }

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    private void tick(int n) throws Exception {
        for (int i = 0; i < n; i++) sched.region(A).execute(() -> w.regionAt(A).tick()).get();
    }

    private List<Player> level() { return w.playersHere(); }

    private List<Player> connected() {
        List<Player> out = new ArrayList<>();
        w.forEachConnectedPlayerHere(out::add);
        return out;
    }

    @Test
    void deadPlayerLeavesLevelListAfterTwentyTicksButStaysConnected() throws Exception {
        assertEquals(List.of(near, far, other), level());
        near.vitals().kill();
        tick(19);
        assertTrue(level().contains(near), "죽고 19틱까지는 목록에 있다");
        tick(1);
        assertEquals(List.of(far, other), level(), "20틱째에 레벨 목록에서 빠진다");
        assertEquals(3, w.onlinePlayers().size(), "접속(서버 전체 목록)은 그대로");
        assertTrue(w.onlinePlayers().contains(near));
        assertTrue(connected().containsAll(List.of(near, far, other)), "방송 대상에는 남는다");
        tick(5);
        assertEquals(List.of(far, other), level(), "그 뒤에도 그대로(다시 넣지 않는다)");
        assertEquals(3, connected().size(), "방송 목록에 두 번 들지 않는다");
    }

    @Test
    void nightSkipIgnoresDeadPlayer() throws Exception {
        // 4명 중 3명이 자고 1명이 죽음 화면: 바닐라는 죽은 사람을 빼 3/3 이라 밤이 넘어간다
        Player dead = new Player(4, UUID.randomUUID(), "dead", new ConcurrentEmbeddedChannel());
        place(dead, 11, 11);
        w.playerOnline(dead);
        sched.region(A).execute(() -> w.regionAt(A).adopt(dead)).get();
        var st = w.sleepStatus();
        w.updateSleepingPlayerList();
        assertEquals(4, st.activePlayers());
        dead.vitals().kill();
        tick(20);   // region 틱이 침대 없는 잠을 깨우므로 잠은 제거 뒤에 건다
        assertFalse(level().contains(dead));
        assertEquals(3, st.activePlayers(), "빠진 즉시 잠 목록을 다시 센다(onTrackingEnd → updateSleepingPlayerList)");
        near.setSleepingPos(new Pos(10, -60, 10));
        other.setSleepingPos(new Pos(12, -60, 12));
        far.setSleepingPos(new Pos(100, -60, 10));
        w.updateSleepingPlayerList();
        assertEquals(3, st.amountSleeping());
        assertTrue(st.areEnoughSleeping(100), "3/3 — 죽은 사람은 세지 않아 밤을 넘길 수 있다");
        assertEquals(3, st.sleepersNeeded(100));
    }

    @Test
    void deadPlayerStillCountedWhileInLevelList() throws Exception {
        // 대조: 죽은 사람이 목록에 있는 동안(19틱)은 4명으로 세서 3명이 자도 100% 는 모자라다
        Player dead = new Player(4, UUID.randomUUID(), "dead", new ConcurrentEmbeddedChannel());
        place(dead, 11, 11);
        w.playerOnline(dead);
        sched.region(A).execute(() -> w.regionAt(A).adopt(dead)).get();
        near.setSleepingPos(new Pos(10, -60, 10));
        other.setSleepingPos(new Pos(12, -60, 12));
        far.setSleepingPos(new Pos(100, -60, 10));
        w.updateSleepingPlayerList();
        assertEquals(4, w.sleepStatus().activePlayers());
        assertFalse(w.sleepStatus().areEnoughSleeping(100), "3/4");
    }

    @Test
    void nearestPlayerAndNearbyChecksSkipDeadPlayer() throws Exception {
        near.vitals().kill();
        tick(19);
        assertSame(near, nearest(10, -60, 10, -1), "19틱: 아직 가장 가까운 플레이어로 잡힌다(바닐라 isAlive 를 보지 않는 조회)");
        tick(1);
        assertSame(other, nearest(10, -60, 10, -1), "제거된 뒤: 사라짐 판정이 죽은 플레이어를 보지 않는다");
        assertSame(other, nearest(10, -60, 10, 64.0));
        near.vitals().respawn();
        w.playerRespawned(near);
        assertSame(near, nearest(10, -60, 10, -1), "리스폰하면 다시 센다");
    }

    private Player nearest(double x, double y, double z, double dist) throws Exception {
        return sched.region(A).submit(() -> w.regionAt(A).level().getNearestPlayer(x, y, z, dist, false)).get();
    }

    @Test
    void respawnPutsPlayerBackAtEndOfLevelList() throws Exception {
        near.vitals().kill();
        tick(20);
        assertEquals(List.of(far, other), level());
        near.vitals().respawn();
        w.playerRespawned(near);
        assertEquals(List.of(far, other, near), level(), "PlayerList.respawn: 새 플레이어는 목록 끝에");
        assertEquals(3, connected().size());
        tick(3);
        assertEquals(List.of(far, other, near), level());
    }

    @Test
    void disconnectWhileOnDeathScreenCleansUp() throws Exception {
        near.vitals().kill();
        tick(20);
        assertEquals(3, connected().size());
        w.playerOffline(near.entityId());
        assertEquals(List.of(far, other), level());
        assertEquals(List.of(far, other), connected(), "접속 종료하면 방송 목록에서도 빠진다");
    }

    @Test
    void levelEventStillReachesDeadPlayer() throws Exception {
        near.vitals().kill();
        tick(20);
        assertFalse(level().contains(near));
        drain(chNear);
        sched.region(A).execute(() -> w.regionAt(A).level().levelEvent(null, 1000, new Pos(10, -60, 10), 0)).get();
        assertTrue(drain(chNear).contains(LevelEventPacket.ID), "죽음 화면 플레이어도 레벨 이벤트(PlayerList.broadcast)를 받는다");
    }

    @Test
    void emptyDimensionCountsDeadPlayerAsGone() throws Exception {
        // 바닐라 !players.isEmpty(): 모두 죽어 빠지면 차원은 비었다(emptyTime 이 오른다)
        for (Player p : List.of(near, far, other)) p.vitals().kill();
        tick(20);
        assertTrue(level().isEmpty());
        assertEquals(3, w.onlinePlayers().size());
        assertTrue(w.playerIndex().isEmpty());
    }

    private boolean natural(int cx, int cz) throws Exception {
        return sched.region(A).submit(() -> w.regionAt(A).naturalSpawningAllowed(cx, cz)).get();
    }

    // ------------------------------------------------------------------ 청크 추적(바닐라 ChunkMap.removeEntity — 죽고 20틱 뒤만)

    @Test
    void deadPlayerLosesChunkTrackingAndRegainsItOnRespawn() throws Exception {
        tick(3);
        ChunkPos c0 = new ChunkPos(0, 0);
        assertTrue(w.subscribers(c0).contains(near), "죽기 전: 시야 청크를 구독한다");
        assertFalse(near.loadedChunks().isEmpty());
        drain(chNear);
        near.vitals().kill();
        tick(19);
        assertTrue(w.subscribers(c0).contains(near), "19틱까지는 추적 중");
        tick(1);
        assertFalse(w.subscribers(c0).contains(near), "20틱째: 구독(블록 변경·빛 방송, 플레이어 티켓)을 푼다");
        assertTrue(near.loadedChunks().isEmpty());
        assertTrue(w.subscribers(c0).contains(other), "다른 플레이어의 구독은 그대로");
        assertFalse(drain(chNear).contains(UnloadChunk.ID), "바닐라 PlayerChunkSender.dropChunk 는 죽은 플레이어에게 청크 잊기를 보내지 않는다");
        tick(5);
        assertTrue(near.loadedChunks().isEmpty(), "죽은 동안 시야를 다시 쌓지 않는다");
        near.vitals().respawn();
        w.playerRespawned(near);
        tick(2);
        assertFalse(near.loadedChunks().isEmpty(), "리스폰하면 새 플레이어처럼 시야 전체를 처음부터 다시 구독한다");
        assertTrue(w.subscribers(c0).contains(near));
        assertTrue(drain(chNear).contains(SetCenterChunk.ID), "시야 중심도 다시 보낸다");
    }

    @Test
    void untrackedDeadPlayerHoldsNoTicketAndSeesNoPlayers() throws Exception {
        tick(3);
        assertFalse(near.tracked().isEmpty(), "죽기 전: 다른 플레이어를 보고 있다");
        assertTrue(natural(0, 0));
        for (Player p : List.of(near, far, other)) p.vitals().kill();
        tick(19);
        assertTrue(natural(0, 0), "19틱: 아직 티켓이 있다");
        tick(1);
        assertFalse(natural(0, 0), "모두 죽어 추적에서 빠지면 그 자리 청크에 플레이어 티켓이 없다(엔티티 틱·자연 스폰 범위 밖)");
        assertTrue(near.tracked().isEmpty(), "추적에서 빠진 관찰자는 보던 플레이어를 잃는다(TrackedEntity.removePlayer)");
        tick(3);
        assertTrue(near.tracked().isEmpty(), "다시 보지 않는다");
        near.vitals().respawn();
        w.playerRespawned(near);
        tick(3);
        assertTrue(natural(0, 0), "리스폰한 플레이어가 티켓을 되찾는다");
    }

    @Test
    void endCreditsPlayerKeepsChunkTracking() throws Exception {
        // 엔드 크레딧의 removePlayerImmediately(CHANGED_DIMENSION)은 onDestroyed 가 아니라 레벨 목록에서만 빠진다
        tick(3);
        assertTrue(w.onlinePlayers().contains(near));
        w.playerRemovedFromLevel(near, false);
        assertFalse(level().contains(near));
        assertTrue(connected().contains(near));
        assertFalse(near.chunkUntracked());
        tick(3);
        assertTrue(w.subscribers(new ChunkPos(0, 0)).contains(near));
        assertFalse(near.loadedChunks().isEmpty());
    }

    /** 독립 검토 F1: region 이 removedAfterDeath 를 읽은 뒤 IO 스레드 리스폰이 끝난 순서 — 살아난 플레이어는 빠지지 않는다. */
    @Test
    void lateRemovalAfterRespawnKeepsAliveRespawnedPlayer() throws Exception {
        near.vitals().kill();
        tick(20);
        assertEquals(List.of(far, other), level());
        near.vitals().respawn();          // IO 스레드: finishRespawn
        w.playerRespawned(near);
        assertEquals(List.of(far, other, near), level());
        w.playerRemovedFromLevel(near, true);   // region 스레드: 이미 읽은 removedAfterDeath=true 로 뒤늦게 호출
        assertEquals(List.of(far, other, near), level(), "살아 리스폰한 플레이어는 레벨 목록에 남는다");
        assertFalse(near.removedFromLevel());
        assertFalse(near.chunkUntracked());
    }

    /** 독립 검토 F2: 죽어서 빠진 플레이어를 다른 차원으로 옮겨도(/tp) 새 차원 레벨 목록에 오르지 않고, 리스폰이 올린다. */
    @Test
    void dimensionChangeOfRemovedDeadPlayerStaysOutOfLevelList() throws Exception {
        near.vitals().kill();
        tick(20);
        assertEquals(List.of(far, other), level());
        near.dimension("minecraft:the_nether");
        List<Player> nether = new ArrayList<>();
        near.rosterLevel().forEach(nether::add);
        assertTrue(nether.isEmpty(), "새 차원 레벨 목록에 죽은 플레이어가 올라갔다: " + nether);
        assertTrue(near.removedFromLevel());
        assertTrue(near.chunkUntracked(), "추적 해제 상태도 그대로");
        List<Player> connectedNether = new ArrayList<>();
        near.rosterLevel().forEachConnected(connectedNether::add);
        assertEquals(List.of(near), connectedNether, "방송 대상(접속자)으로는 새 차원에 있다");
        assertEquals(List.of(far, other), level(), "옛 차원 목록·방송에서는 빠진다");
        assertEquals(2, connected().size());
        near.vitals().respawn();
        w.playerRespawned(near);   // finishRespawn: p.dimension(target) 뒤 relist
        nether.clear();
        near.rosterLevel().forEach(nether::add);
        assertEquals(List.of(near), nether, "리스폰하면 새 차원 레벨 목록에 오른다");
        assertFalse(near.removedFromLevel());
        assertFalse(near.chunkUntracked());
        List<Player> connectedNether2 = new ArrayList<>();
        near.rosterLevel().forEachConnected(connectedNether2::add);
        assertEquals(List.of(near), connectedNether2, "방송 목록에 두 번 들지 않는다");
    }

    /** 엔드 크레딧 경로: 즉시 레벨 목록에서 빠지고(추적은 유지), 크레딧 중 퇴장·크레딧 뒤 리스폰이 정리한다. */
    @Test
    void endCreditsRemovalThenDisconnectOrRespawn() throws Exception {
        w.playerRemovedFromLevel(near, false);
        assertEquals(List.of(far, other), level());
        assertTrue(connected().contains(near));
        w.playerRespawned(near);   // 크레딧 뒤 finishWinRespawn
        assertEquals(List.of(far, other, near), level());
        assertFalse(near.removedFromLevel());
        w.playerRemovedFromLevel(far, false);
        w.playerOffline(far.entityId());   // 크레딧 중 퇴장
        assertEquals(List.of(other, near), level());
        assertEquals(List.of(other, near), connected(), "퇴장하면 방송 목록에서도 빠진다");
    }

    private static List<Integer> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(Types.readVarInt(b));
            b.release();
        }
        return out;
    }
}
