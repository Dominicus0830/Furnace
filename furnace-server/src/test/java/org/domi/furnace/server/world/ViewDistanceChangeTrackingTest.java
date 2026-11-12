// 시야 거리만 바뀌고 플레이어가 청크 경계를 넘지 않아도 다음 틱에 추적 범위를 다시 잡는지(바닐라 ChunkMap.updateChunkTracking).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ViewDistanceChangeTrackingTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    /** 채널은 region 스레드에서만 만진다. 청크·언로드·중심 패킷 이름만 모은다. */
    private static List<String> drain(World w, EmbeddedChannel ch) throws Exception {
        return w.submitAtChunk(C00, r -> {
            ch.runPendingTasks();
            List<String> out = new ArrayList<>();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                if (id == LevelChunkWithLight.ID) out.add("chunk");
                else if (id == UnloadChunk.ID) out.add("unload");
                else if (id == SetCenterChunk.ID) out.add("center");
                b.release();
            }
            return out;
        }).get(10, TimeUnit.SECONDS);
    }

    private static List<String> settle(World w, EmbeddedChannel ch) throws Exception {
        List<String> all = new ArrayList<>();
        int quiet = 0;
        while (quiet < 6) {
            Thread.sleep(100);
            List<String> got = drain(w, ch);
            all.addAll(got);
            quiet = got.isEmpty() ? quiet + 1 : 0;
        }
        return all;
    }

    private static long count(List<String> seq, String name) { return seq.stream().filter(name::equals).count(); }

    @Test
    void viewDistanceChangeWithoutMoving_reTracksNextTick() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 4, 4,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        try {
            EmbeddedChannel ch = new EmbeddedChannel();
            Player a = new Player(1, UUID.randomUUID(), "A", ch);
            a.viewDistance(4);
            a.publishPosition(8.5, -60, 8.5, 0, 0, true);
            a.drainPending();
            w.playerOnline(a);
            w.submitAtChunk(C00, r -> { r.adopt(a); return null; }).get(10, TimeUnit.SECONDS);
            w.startTicking();
            List<String> join = settle(w, ch);
            assertEquals(ChunkTrackingViewTest.vanillaCount(4), count(join, "chunk"), "접속 시야 4 = 바닐라 둥근 모양 109: " + join);

            // 클라이언트 렌더 거리 4 → 2: 움직이지 않아도 바깥 청크(109 - 45 = 64)를 내린다. 중심은 그대로라 center 는 다시 안 보낸다.
            a.viewDistance(2);
            List<String> shrink = settle(w, ch);
            assertEquals(ChunkTrackingViewTest.vanillaCount(4) - ChunkTrackingViewTest.vanillaCount(2), count(shrink, "unload"), "줄임: " + shrink);
            assertEquals(0, count(shrink, "chunk"), "줄임: " + shrink);
            assertFalse(shrink.contains("center"), "줄임: " + shrink);
            assertEquals(ChunkTrackingViewTest.vanillaCount(2), a.loadedChunks().size());

            // 2 → 4: 움직이지 않아도 다시 보낸다.
            a.viewDistance(4);
            List<String> grow = settle(w, ch);
            assertEquals(ChunkTrackingViewTest.vanillaCount(4) - ChunkTrackingViewTest.vanillaCount(2), count(grow, "chunk"), "늘림: " + grow);
            assertEquals(0, count(grow, "unload"), "늘림: " + grow);
            assertFalse(grow.contains("center"), "늘림: " + grow);
            assertEquals(ChunkTrackingViewTest.vanillaCount(4), a.loadedChunks().size());

            // 서버 상한(4)보다 크게 요청해도 실제 거리는 그대로 — 아무것도 안 나간다.
            a.viewDistance(10);
            assertEquals(List.of(), settle(w, ch));
        } finally {
            sched.close();
        }
    }
}
