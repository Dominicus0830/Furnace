// 바닐라 1.21.1 ChunkTrackingView(둥근 시야 모양)와 같은 청크 집합을 보내는지 — 판정 함수·보낼 집합·내릴 집합·실제 접속 전송 수.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkTrackingViewTest {

    /**
     * 바닐라 net.minecraft.server.level.ChunkTrackingView.isWithinDistance(1.21.1) 를 원문 그대로(변수 순서·형 변환까지) 옮긴 기준 구현.
     * 서버 쪽 구현과 일부러 따로 쓴다(서로 맞는지 보는 시험이라서).
     */
    private static boolean vanillaIsWithinDistance(int var0, int var1, int var2, int var3, int var4, boolean var5) {
        int var6 = Math.max(0, Math.abs(var3 - var0) - 1);
        int var7 = Math.max(0, Math.abs(var4 - var1) - 1);
        long var8 = (long) Math.max(0, Math.max(var6, var7) - (var5 ? 1 : 0));
        long var10 = (long) Math.min(var6, var7);
        long var12 = var10 * var10 + var8 * var8;
        int var14 = var2 * var2;
        return var12 < (long) var14;
    }

    /** 바닐라 Positioned.forEach — 중심 둘레 ±(시야+1) 상자 안에서 contains 인 칸. */
    static Set<ChunkPos> vanillaSet(int cx, int cz, int vd) {
        Set<ChunkPos> out = new HashSet<>();
        for (int x = cx - vd - 1; x <= cx + vd + 1; x++)
            for (int z = cz - vd - 1; z <= cz + vd + 1; z++)
                if (vanillaIsWithinDistance(cx, cz, vd, x, z, true)) out.add(new ChunkPos(x, z));
        return out;
    }

    /** 다른 시험이 시야 vd 에서 보낼 청크 수를 정사각 공식 대신 쓴다. */
    static int vanillaCount(int vd) { return vanillaSet(0, 0, vd).size(); }

    @Test
    void goldenCounts_matchVanilla() {
        // 바닐라 시야 거리별 보낼 청크 수(O03 보고서 C1: 바닐라 ChunkTrackingView 원문으로 센 값). 정사각이면 25/81/169/289/441/625/1089/2401/4225.
        int[][] golden = {{2, 45}, {4, 109}, {6, 201}, {8, 313}, {10, 453}, {12, 609}, {16, 1021}, {24, 2125}, {32, 3653}};
        for (int[] g : golden) assertEquals(g[1], vanillaCount(g[0]), "기준 구현 시야 " + g[0]);
    }

    @Test
    void contains_matchesVanillaFormula_forEveryCell_everyViewDistance_andOffsetCenters() {
        int[][] centers = {{0, 0}, {7, -3}, {-1000, 2500}, {1_875_000, -1_875_000}};
        for (int vd = 2; vd <= 32; vd++) {
            for (int[] c : centers) {
                for (int dx = -(vd + 3); dx <= vd + 3; dx++) {
                    for (int dz = -(vd + 3); dz <= vd + 3; dz++) {
                        int x = c[0] + dx, z = c[1] + dz;
                        assertEquals(vanillaIsWithinDistance(c[0], c[1], vd, x, z, true), ChunkTrackingView.contains(c[0], c[1], vd, x, z),
                                "contains vd=" + vd + " d=(" + dx + "," + dz + ")");
                        assertEquals(vanillaIsWithinDistance(c[0], c[1], vd, x, z, false), ChunkTrackingView.containsInner(c[0], c[1], vd, x, z),
                                "containsInner vd=" + vd + " d=(" + dx + "," + dz + ")");
                    }
                }
            }
        }
    }

    @Test
    void shape_isRoundNotSquare() {
        // 시야 10: 축 방향은 11칸(시야+1)까지, 모서리는 안 간다. 정사각 21×21=441 과 다르게 453.
        assertTrue(ChunkTrackingView.contains(0, 0, 10, 11, 0));
        assertTrue(ChunkTrackingView.contains(0, 0, 10, 0, -11));
        assertFalse(ChunkTrackingView.contains(0, 0, 10, 12, 0));
        assertFalse(ChunkTrackingView.contains(0, 0, 10, 10, 10), "정사각 모서리는 바닐라가 보내지 않는다");
        assertEquals(453, vanillaCount(10));
        assertEquals(11, ChunkTrackingView.reach(10));
        // 시야 10: 정사각에만 있는 칸 32, 바닐라에만 있는 칸 44(O03 보고서)
        int squareOnly = 0, vanillaOnly = 0;
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            boolean sq = Math.abs(x) <= 10 && Math.abs(z) <= 10, v = ChunkTrackingView.contains(0, 0, 10, x, z);
            if (sq && !v) squareOnly++;
            if (!sq && v) vanillaOnly++;
        }
        assertEquals(32, squareOnly);
        assertEquals(44, vanillaOnly);
    }

    // ---- 실제 서버 경로: 접속 전송 수, 움직임의 보냄·내림 차이, 시야 변경 ----

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private record Seen(Set<ChunkPos> chunks, Set<ChunkPos> unloads) {}

    private static Seen drain(World w, EmbeddedChannel ch) throws Exception {
        return w.submitAtChunk(C00, r -> {
            ch.runPendingTasks();
            Set<ChunkPos> chunks = new HashSet<>(), unloads = new HashSet<>();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                if (id == LevelChunkWithLight.ID) {
                    int x = b.readInt(), z = b.readInt();
                    chunks.add(new ChunkPos(x, z));
                } else if (id == UnloadChunk.ID) {
                    long l = b.readLong();   // forget_level_chunk: (x & 0xFFFFFFFF) | (z << 32)
                    unloads.add(new ChunkPos((int) l, (int) (l >> 32)));
                }
                b.release();
            }
            return new Seen(chunks, unloads);
        }).get(10, TimeUnit.SECONDS);
    }

    private static Seen settle(World w, EmbeddedChannel ch, int quietRounds) throws Exception {
        Set<ChunkPos> chunks = new HashSet<>(), unloads = new HashSet<>();
        int quiet = 0;
        while (quiet < quietRounds) {
            Thread.sleep(100);
            Seen s = drain(w, ch);
            chunks.addAll(s.chunks());
            unloads.addAll(s.unloads());
            quiet = s.chunks().isEmpty() && s.unloads().isEmpty() ? quiet + 1 : 0;
        }
        return new Seen(chunks, unloads);
    }

    @Test
    void server_sendsExactlyTheVanillaSet_onJoin_move_andViewChange() throws Exception {
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

            Set<ChunkPos> expectJoin = vanillaSet(0, 0, vd);
            assertEquals(201, expectJoin.size());
            Seen join = settle(w, ch, 8);
            assertEquals(expectJoin, join.chunks(), "접속 때 보낸 청크 = 바닐라 집합(정사각 169 아님)");
            assertEquals(expectJoin, new HashSet<>(a.loadedChunks()));

            // 동쪽으로 한 청크 이동: 보낼 것·내릴 것 = 바닐라 difference
            a.publishPosition(24.5, -60, 8.5, 0, 0, true);
            a.drainPending();
            Set<ChunkPos> after = vanillaSet(1, 0, vd);
            Set<ChunkPos> add = new HashSet<>(after); add.removeAll(expectJoin);
            Set<ChunkPos> drop = new HashSet<>(expectJoin); drop.removeAll(after);
            Seen moved = settle(w, ch, 8);
            assertEquals(add, moved.chunks(), "이동 때 새로 보낸 청크");
            assertEquals(drop, moved.unloads(), "이동 때 내린 청크");
            assertEquals(after, new HashSet<>(a.loadedChunks()));

            // 시야 6 → 3: 바깥을 내린다(보내는 것 없음) → 다시 6: 같은 집합을 되돌려 받는다
            a.viewDistance(3);
            Set<ChunkPos> small = vanillaSet(1, 0, 3);
            Set<ChunkPos> dropped = new HashSet<>(after); dropped.removeAll(small);
            Seen shrink = settle(w, ch, 8);
            assertEquals(Set.of(), shrink.chunks());
            assertEquals(dropped, shrink.unloads(), "줄일 때 내린 청크");
            assertEquals(small, new HashSet<>(a.loadedChunks()));
            a.viewDistance(vd);
            Seen grow = settle(w, ch, 8);
            assertEquals(dropped, grow.chunks(), "늘릴 때 다시 보낸 청크");
            assertEquals(Set.of(), grow.unloads());
            assertEquals(after, new HashSet<>(a.loadedChunks()));
        } finally {
            sched.close();
        }
    }
}
