// N-10 선행 시험(perf-c4): 같은 틱에 블록 변경·이동(청크 전송 요청)·엔티티 이동이 섞일 때 클라이언트로 나가는 패킷 열을 고정한다.
// 본문 캐시가 모두 적중하는 상태(같은 청크를 이미 직렬화)에서 실제 게임 틱(startTicking)으로 돌린다 — 청크 본문 홉 수를 바꾸는
// 최적화가 청크와 다른 패킷 사이의 순서를 바꾸면 이 시험이 깨진다. 기대 열은 현재 구현으로 기록한 것이다 —
// 금 파일과 같은 규칙: 최적화 PR 은 기대 열을 바꾸지 않고, 바꾸려면 docs/testing/identitykit.md "금 파일 갱신" 절차를 따른다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.identity.PacketTape;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSendPacketOrderGoldenTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private static final Map<Integer, String> NAMES = Map.of(
            LevelChunkWithLight.ID, "chunk", BlockUpdate.ID, "block", ChunkBatchStart.ID, "batchStart",
            ChunkBatchFinished.ID, "batchEnd", SetCenterChunk.ID, "center", UnloadChunk.ID, "unload",
            MoveEntityPosRot.ID, "movePosRot", MoveEntityRot.ID, "moveRot", SetHeadRotation.ID, "head",
            SpawnEntity.ID, "spawn");

    /** 채널은 region 스레드에서만 만진다(EmbeddedEventLoop 큐는 스레드 안전하지 않다). 꺼내기는 IdentityKit PacketTape. */
    private static List<String> drain(World w, EmbeddedChannel ch) throws Exception {
        return w.submitAtChunk(C00, r -> {
            PacketTape tape = new PacketTape();
            tape.drain(ch, PacketTape.Wire.PLAIN);
            List<String> out = new ArrayList<>();
            for (PacketTape.Packet p : tape.packets()) {
                String n = NAMES.get(p.id());
                if (n == null) continue;
                ByteBuf b = Unpooled.wrappedBuffer(p.body());
                if (p.id() == BlockUpdate.ID) {
                    int[] pos = Types.readBlockPos(b);
                    n = "block@" + pos[0] + "," + pos[1] + "," + pos[2];
                } else if (p.id() == LevelChunkWithLight.ID) {
                    n = "chunk@" + b.readInt() + "," + b.readInt();
                }
                out.add(n);
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

    @org.junit.jupiter.api.RepeatedTest(10)
    void sameTickBlockChangeMoveAndChunkSend_packetSequenceIsFixed() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        try {
            // 무작위 틱을 끈다 — 부순 칸 옆 잔디가 노출된 흙으로 번지면(바닐라 동작) 그 칸의 block 패킷이 시험 열 뒤에 불규칙하게 붙는다
            var base = w.levelServices();
            w.levelServices(new org.domi.furnace.server.block.LevelServices.Delegating(base) {
                @Override
                public int gameRuleInt(String name) { return name.equals("randomTickSpeed") ? 0 : super.gameRuleInt(name); }
            });
            // 본문 캐시를 모두 채운다(-3..4 × -3..3 ⊇ 바닐라 둥근 모양 두 중심) — 이후 요청은 모두 캐시 적중
            List<CompletableFuture<byte[]>> warm = new ArrayList<>();
            for (int x = -3; x <= 4; x++) for (int z = -3; z <= 3; z++) warm.add(w.serializedChunkBody(new ChunkPos(x, z)));
            for (var f : warm) f.get(30, TimeUnit.SECONDS);

            EmbeddedChannel chA = new ConcurrentEmbeddedChannel(), chB = new ConcurrentEmbeddedChannel();
            Player a = new Player(1, UUID.randomUUID(), "A", chA);
            Player b = new Player(2, UUID.randomUUID(), "B", chB);
            a.publishPosition(8.5, -60, 8.5, 0, 0, true);
            a.drainPending();
            b.publishPosition(10.5, -60, 8.5, 0, 0, true);
            b.drainPending();
            w.playerOnline(a);
            w.playerOnline(b);
            w.submitAtChunk(C00, r -> { r.adopt(a); r.adopt(b); return null; }).get(10, TimeUnit.SECONDS);
            w.startTicking();
            List<String> join = settle(w, chA);
            assertEquals(ChunkTrackingViewTest.vanillaCount(2), join.stream().filter(x -> x.startsWith("chunk")).count(), "접속 시야 2 = 바닐라 둥근 모양 45: " + join);
            settle(w, chB);

            // 한 인박스 작업(= 다음 틱 드레인, 그 틱의 게임 틱 직전): 이미 받은 청크 (0,0) 편집, A 는 청크 (1,0) 으로 이동,
            // B 는 조금 이동·회전, 다음 틱에 새로 구독될 청크 (4,0) 편집(시야 2 의 바닐라 둥근 모양은 (3,0) 이 이미 받은 칸이다)을 1틱 지연 작업으로.
            w.runAtChunk(C00, r -> {
                r.breakBlock(1, -61, 1);
                a.publishPosition(24.5, -60, 8.5, 0, 0, true);
                a.pushMove(24.5, -60, 8.5, true);
                b.publishPosition(11.0, -60, 8.5, 45, 0, true);
                b.pushMove(11.0, -60, 8.5, true);
                r.schedule(Regionizer.chunkKey(4, 0), r2 -> r2.breakBlock(64, -61, 1), 1);
            });
            List<String> seq = settle(w, chA);
            // 현재 구현: 청크 본문은 틱 안에서 요청만 하고(배치 시작은 그 자리), 본문 홉은 다음 틱 드레인에서 돈다 —
            // 그래서 같은 틱의 엔티티 이동 패킷이 청크보다 앞선다. 새 구독 청크 (4,0) 편집(1틱 지연 작업)은 전달 뒤라 즉시 나간다.
            assertEquals(List.of("block@1,-61,1", "center", "unload", "unload", "unload", "unload", "unload", "unload", "unload",
                    "batchStart", "movePosRot", "head",
                    "chunk@4,0", "chunk@4,-1", "chunk@4,1", "chunk@3,-3", "chunk@3,3", "chunk@4,-2", "chunk@4,2", "batchEnd",
                    "block@64,-61,1"), seq);
        } finally {
            sched.close();
        }
    }
}
