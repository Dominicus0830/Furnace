// 시야 정사각의 모서리(둥근 모양 밖) 청크도 바닐라처럼 로드·틱 대상으로 올려 두되, 보내지도 구독하지도 않는다(PR #270 독립 검토 R1).
// 바닐라 DistanceManager.PlayerTicketTracker 는 서버 시야 안 체비셰프 정사각 전부에 플레이어 티켓(엔티티 틱 단계)을 달고,
// 보내는 것(ChunkTrackingView)만 둥근 모양이다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkCornerLoadTest {
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private static void pump(World w, EmbeddedChannel ch, Set<ChunkPos> sent, int rounds) throws Exception {
        for (int i = 0; i < rounds; i++) {
            Thread.sleep(100);
            w.submitAtChunk(C00, r -> {
                ch.runPendingTasks();
                Object o;
                while ((o = ch.readOutbound()) != null) {
                    ByteBuf b = (ByteBuf) o;
                    if (Types.readVarInt(b) == LevelChunkWithLight.ID) sent.add(new ChunkPos(b.readInt(), b.readInt()));
                    b.release();
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
        }
    }

    private static List<String> notLoadedInSquare(World w, int cx, int cz, int vd) {
        List<String> out = new ArrayList<>();
        for (int x = cx - vd; x <= cx + vd; x++) for (int z = cz - vd; z <= cz + vd; z++)
            if (w.loadedChunk(x, z) == null) out.add("(" + x + "," + z + ")");
        return out;
    }

    @Test
    void squareCorners_areLoadedAndTicking_butNotSentNorSubscribed() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int vd = 6;
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 2, 16, vd, vd,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        try {
            EmbeddedChannel ch = new EmbeddedChannel();
            Player a = new Player(1, UUID.randomUUID(), "A", ch);
            a.viewDistance(vd);
            a.publishPosition(8.5, -60, 8.5, 0, 0, true);
            a.drainPending();
            w.playerOnline(a);
            w.submitAtChunk(C00, r -> { r.adopt(a); return null; }).get(10, TimeUnit.SECONDS);
            w.startTicking();
            Set<ChunkPos> sent = new HashSet<>();
            pump(w, ch, sent, 60);   // 6초: 보낼 청크가 모두 나가고 모서리가 올라올 때까지

            assertEquals(ChunkTrackingViewTest.vanillaSet(0, 0, vd), sent, "보낸 것은 바닐라 둥근 모양 그대로");
            assertEquals(List.of(), notLoadedInSquare(w, 0, 0, vd), "바닐라는 시야 정사각 전부를 플레이어 티켓으로 올린다");
            int corners = 0;
            for (int x = -vd; x <= vd; x++) for (int z = -vd; z <= vd; z++) {
                ChunkPos cp = new ChunkPos(x, z);
                if (ChunkTrackingView.contains(0, 0, vd, x, z)) continue;
                corners++;
                int fx = x, fz = z;
                assertTrue(w.subscribers(cp).isEmpty(), "모서리는 구독하지 않는다(블록 변경 방송·빛 경계에 끼지 않는다) " + cp);
                assertTrue(w.submitAtChunk(C00, r -> r.naturalSpawningAllowed(fx, fz)).get(10, TimeUnit.SECONDS), "모서리는 엔티티 틱 대상 " + cp);
            }
            assertTrue(corners > 0, "시야 6 은 정사각에만 있는 모서리가 있다");

            // 한 청크 이동: 새 정사각이 모두 올라오고, 둥근 모양을 막 벗어난 칸(여전히 정사각 안)은 내리지 않는다
            a.publishPosition(24.5, -60, 8.5, 0, 0, true);
            a.drainPending();
            pump(w, ch, sent, 60);
            assertEquals(List.of(), notLoadedInSquare(w, 1, 0, vd), "이동 뒤에도 정사각 전부 로드");
        } finally {
            sched.close();
        }
    }
}
