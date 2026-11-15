// 죽은 플레이어를 20틱 뒤 차원 레벨 목록에서 빼기(open item #7): 바닐라 LivingEntity.tickDeath → remove(KILLED) → ServerLevel EntityCallbacks.onTrackingEnd.
// 잠 비율·사라짐·근접 조회는 빠진 목록을 보고, 소리·레벨 이벤트 방송(PlayerList.broadcast)은 죽음 화면 플레이어도 받는다. 리스폰이 끝에 다시 올린다.
package org.domi.furnace.server.world;

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
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
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
