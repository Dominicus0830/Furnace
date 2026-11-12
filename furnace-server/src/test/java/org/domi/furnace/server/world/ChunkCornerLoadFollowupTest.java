// PR #270 사후 재검토 후속: 제자리에서 청크 생성 자격이 바뀌어도 모서리가 올라온다(S1), 실패한 모서리는 다시 시도한다(S2),
// 클라 시야가 서버 시야보다 작아도 서버 시야 정사각을 올린다(S4 — 바닐라 플레이어 티켓은 서버 시야 정사각).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.Test;

class ChunkCornerLoadFollowupTest {
    static final class Rig {
        FurnaceSchedulerImpl sched; World w; EmbeddedChannel ch; Player p; java.util.concurrent.ExecutorService io;
        final boolean[] rule = {false};
        void close() { io.shutdown(); sched.close(); }
        void ticks(int n) throws Exception {
            RegionPos rp = new RegionPos(0, 0);
            for (int t = 0; t < n; t++) {
                sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
                for (int i = 0; i < 3; i++) {
                    ch.runPendingTasks();
                    Object o;
                    while ((o = ch.readOutbound()) != null) ((ByteBuf) o).release();
                    Thread.sleep(2);
                }
            }
        }
    }

    static Rig rig(GameMode mode, int serverVd, int clientVd, boolean failFirstCorner) throws Exception {
        Rig r = new Rig();
        r.sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        r.io = Executors.newSingleThreadExecutor();
        WorldSource flat = new SuperflatWorldSource(reg, 0);
        AtomicInteger failures = new AtomicInteger();
        WorldSource src = failFirstCorner
                ? (cx, cz) -> {
                    if (cx == serverVd && cz == serverVd && failures.getAndIncrement() == 0) throw new IllegalStateException("테스트용 첫 생성 실패");
                    return flat.generate(cx, cz);
                }
                : flat;
        r.w = new World(src, new ChunkWorkerPool(2), r.sched, 1, 16, serverVd, serverVd, reg.directBits(), 7, r.io);
        r.w.configurePersistence(1_000_000, 20);
        r.w.levelServices(new LevelServices.Delegating(r.w.levelServices()) {
            @Override public boolean gameRule(String name) {
                return name.equals("spectatorsGenerateChunks") ? r.rule[0] : super.gameRule(name);
            }
        });
        r.ch = new ConcurrentEmbeddedChannel();
        r.p = new Player(1, UUID.randomUUID(), "A", r.ch);
        r.p.vitals().loadGameTypes(null, mode, true);
        r.p.viewDistance(clientVd);
        r.p.applyMove(8, -60, 8, 0, 0, true);
        RegionPos rp = new RegionPos(0, 0);
        r.sched.region(rp).execute(() -> r.w.regionAt(rp).adopt(r.p)).get();
        return r;
    }

    static List<ChunkPos> missingSquare(World w, int cx, int cz, int vd) {
        List<ChunkPos> out = new ArrayList<>();
        for (int x = cx - vd; x <= cx + vd; x++) for (int z = cz - vd; z <= cz + vd; z++)
            if (w.loadedChunk(x, z) == null) out.add(new ChunkPos(x, z));
        return out;
    }

    /** 관전자(규칙 꺼짐)였다가 제자리에서 생존으로 바꾸면 모서리도 올라온다(시야 10 에서 32칸). */
    @Test
    void spectatorToSurvival_withoutMoving_loadsCorners() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, 10, 10, false);
        try {
            r.ticks(20);
            assertTrue(r.w.loadedChunk(10, 10) == null, "관전자(규칙 꺼짐)는 모서리를 올리지 않는다");
            r.p.vitals().loadGameTypes(null, GameMode.SURVIVAL, true);
            r.ticks(200);
            assertEquals(List.of(), missingSquare(r.w, 0, 0, 10), "모드를 바꾼 뒤 정사각 전부 로드");
        } finally {
            r.close();
        }
    }

    /** 게임룰을 켜도 같다(관전자 그대로, 제자리). */
    @Test
    void ruleTurnedOn_withoutMoving_loadsCorners() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, 6, 6, false);
        try {
            r.ticks(20);
            r.rule[0] = true;
            r.ticks(200);
            assertEquals(List.of(), missingSquare(r.w, 0, 0, 6), "규칙을 켠 뒤 정사각 전부 로드");
        } finally {
            r.close();
        }
    }

    /** 관전자(규칙 꺼짐)는 계속 아무것도 올리지 않는다 — 구독도 구역 확보도 없다. */
    @Test
    void spectator_ruleOff_loadsNothingNew() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, 6, 6, false);
        try {
            r.ticks(150);
            assertEquals(0, ((6 * 2 + 1) * (6 * 2 + 1)) - missingSquare(r.w, 0, 0, 6).size(), "관전자는 청크를 만들지 않는다");
        } finally {
            r.close();
        }
    }

    /** 클라 시야 < 서버 시야: 서버 시야 정사각 전부(바닐라 플레이어 티켓). */
    @Test
    void clientVdBelowServer_loadsServerSquare() throws Exception {
        Rig r = rig(GameMode.SURVIVAL, 6, 3, false);
        try {
            r.ticks(200);
            assertEquals(List.of(), missingSquare(r.w, 0, 0, 6), "서버 시야 정사각");
        } finally {
            r.close();
        }
    }

    /** 모서리 로드가 한 번 실패해도 잠시 뒤 다시 시도해 올라온다. */
    @Test
    void failedCornerLoad_isRetried() throws Exception {
        Rig r = rig(GameMode.SURVIVAL, 6, 6, true);
        try {
            r.ticks(60);
            assertTrue(r.w.loadedChunk(6, 6) == null, "첫 시도는 실패해 아직 없다");
            r.ticks(300);
            assertEquals(List.of(), missingSquare(r.w, 0, 0, 6), "재시도로 정사각 전부 로드");
        } finally {
            r.close();
        }
    }
}
