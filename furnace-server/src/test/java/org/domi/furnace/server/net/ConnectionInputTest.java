// ConnectionHandler 입력 검증: 비정상 이동은 거르고 한 번만 보정, 임의 좌표 블록 요청은 청크를 생성하지 않고, 임의 스폰 좌표에서도 정상 이동은 수락
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.RegionGrid;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionInputTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World world;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private Object field(String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(handler);
    }

    /** spawn 좌표로 접속 → 스폰 청크 전달 완료 → 스폰 텔레포트 confirm → 게이트 해제까지 진행한다. */
    private void join(SpawnPoint spawn) throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        world.worldDir(tmp);                 // 저장 위치/인벤토리를 실제 world/ 가 아닌 임시 폴더에서 읽는다
        world.defaultSpawn(spawn);
        handler = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        ch = new EmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));  // → PLAY
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "스폰 청크 전달이 끝나지 않음");
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));   // 스폰 텔레포트 confirm → 게이트 해제
        ch.runPendingTasks();
        assertEquals(-1, (int) field("pendingTeleportId"), "스폰 게이트가 풀려야 한다");
        p = (Player) field("player");
        RegionPos sp = RegionGrid.regionPos(spawn.playerX(), spawn.playerZ(), 16);
        sched.region(sp).execute(() -> { }).get();
        p.drainPending();
        drainOutbound();   // 접속 중 나간 패킷 비우기
    }

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    private List<int[]> drainOutbound() {
        ch.runPendingTasks();
        List<int[]> out = new ArrayList<>();   // {packetId, 첫 정수(있으면)}
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            out.add(new int[]{id});
            bb.release();
        }
        return out;
    }

    private static long count(List<int[]> pkts, int id) {
        return pkts.stream().filter(a -> a[0] == id).count();
    }

    private static void move(EmbeddedChannel ch, double x, double y, double z) {
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeBoolean(true); }));
    }

    /** 위치 패킷과 회전 전용 패킷이 region이 drain하기 전에 연달아 오면 서로의 값을 덮어쓰면 안 된다. */
    @Test
    void positionThenRotationInSameTick_keepsBoth() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        move(ch, 10, -60, 3);
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(45f); b.writeFloat(10f); b.writeBoolean(true); }));
        p.drainPending();
        assertEquals(10.0, p.x(), "회전 전용 패킷이 대기 중인 새 위치를 옛 위치로 덮어썼다");
        assertEquals(45f, p.yaw());
    }

    @Test
    void rotationThenPositionInSameTick_keepsBoth() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(90f); b.writeFloat(0f); b.writeBoolean(true); }));
        move(ch, 20, -60, 0);
        p.drainPending();
        assertEquals(20.0, p.x());
        assertEquals(90f, p.yaw(), "위치 전용 패킷이 대기 중인 새 회전을 옛 회전으로 덮어썼다");
    }

    @Test
    void positionRotationPacket_thenRotationOnly_keepsPosition() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x1B, b -> { b.writeDouble(7); b.writeDouble(-60); b.writeDouble(7);
            b.writeFloat(30f); b.writeFloat(5f); b.writeBoolean(true); }));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(60f); b.writeFloat(15f); b.writeBoolean(true); }));
        p.drainPending();
        assertEquals(7.0, p.x());
        assertEquals(7.0, p.z());
        assertEquals(60f, p.yaw());
    }

    @Test
    void teleportAndNonFinite_areRejected_withSingleCorrection_thenResumeAfterConfirm() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        double x0 = p.x();
        move(ch, 5_000_000, -60, 0);
        move(ch, Double.NaN, -60, 0);
        move(ch, 0.5, Double.POSITIVE_INFINITY, 0.5);
        p.drainPending();
        assertEquals(x0, p.x(), "급이동/NaN/무한대는 반영되지 않는다");
        // 보정 텔레포트는 연쇄로 쏟아지지 않고 한 번만 나간다(확인 전의 옛 위치 패킷은 조용히 버림)
        assertEquals(1, count(drainOutbound(), SyncPlayerPosition.ID), "보정은 한 번만 보내야 한다");

        move(ch, x0 + 1, -60, 0);                         // 확인 전엔 정상 이동도 무시
        p.drainPending();
        assertEquals(x0, p.x(), "보정 확인 전엔 위치 패킷을 받지 않는다");

        int tpId = (int) field("correctionTpId");
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, tpId)));   // 클라가 보정 확인
        move(ch, x0 + 1, -60, 0);
        p.drainPending();
        assertEquals(x0 + 1, p.x(), "확인 후엔 정상 이동 수락 재개");
    }

    /** 월드 기본 스폰/저장 위치는 임의 좌표다 — 기준 위치를 (0,0)에서 시작하면 스폰이 100블록 밖일 때 첫 이동부터 거부된다. */
    @Test
    void farFromOriginSpawn_normalMoveIsAccepted() throws Exception {
        join(new SpawnPoint(5000, -60, 5000, 0f));
        double x0 = p.x();
        move(ch, x0 + 0.3, p.y(), p.z());
        p.drainPending();
        assertEquals(x0 + 0.3, p.x(), 1e-9, "스폰 근처의 정상 이동은 수락돼야 한다");
        assertEquals(0, count(drainOutbound(), SyncPlayerPosition.ID), "정상 이동에 보정이 나가면 안 된다");
    }

    private static boolean loaded(World world, RegionPos rp, int bx, int bz) throws Exception {
        boolean[] r = new boolean[1];
        world.scheduler().region(rp).execute(() -> r[0] = world.regionAt(rp).chunkLoaded(ChunkPos.ofBlock(bx, bz))).get();
        return r[0];
    }

    @Test
    void blockRequests_farAwayOrOutOfRange_doNotCreateChunks_andAreAcked() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        // 스폰 청크(0,0 근방, 반경 2)가 아닌 곳의 요청 — 거부되고 청크가 새로 생기면 안 된다.
        int[][] bad = {{1_000_000, -61, 1_000_000}, {5000, -61, 0}, {100, -61, 100}, {100, 1000, 100}, {100, -1000, 100}};
        // 스폰 청크 안이지만 월드 높이 밖 — 예외 없이 거부+ack만 돼야 한다(청크는 원래 로드돼 있음).
        int[][] outOfHeight = {{0, 1000, 0}, {0, -1000, 0}};
        int[][] all = java.util.stream.Stream.concat(java.util.Arrays.stream(bad), java.util.Arrays.stream(outOfHeight)).toArray(int[][]::new);
        for (int[] b : all) {
            ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, b[0], b[1], b[2]);
                f.writeByte(1); Types.writeVarInt(f, 7); }));                       // 파괴
            ch.writeInbound(frame(0x38, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, b[0], b[1], b[2]);
                Types.writeVarInt(f, 1); f.writeFloat(0.5f); f.writeFloat(0.5f); f.writeFloat(0.5f);
                f.writeBoolean(false); Types.writeVarInt(f, 8); }));                // 설치/상자 열기
        }
        for (int i = 0; i < 20; i++) { ch.runPendingTasks(); Thread.sleep(25); }
        for (int[] b : bad) {
            RegionPos rp = RegionGrid.regionPos(b[0], b[2], 16);
            assertFalse(loaded(world, rp, b[0], b[2]), "거부된 요청이 청크를 생성했다: " + java.util.Arrays.toString(b));
        }
        assertEquals(2 * all.length, count(drainOutbound(), BlockChangedAck.ID), "거부해도 예측 정리용 ack는 보내야 한다");
    }

    @Test
    void digWithinReachOnLoadedChunk_breaksBlock() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, 1, -61, 1);
            f.writeByte(1); Types.writeVarInt(f, 3); }));
        RegionPos r00 = new RegionPos(0, 0);
        int[] state = new int[1];
        for (int i = 0; i < 20; i++) {
            ch.runPendingTasks();
            sched.region(r00).execute(() -> state[0] = world.regionAt(r00).chunkAt(new ChunkPos(0, 0)).getBlock(1, -61, 1)).get();
            if (state[0] == ChunkSection.AIR) break;
            Thread.sleep(25);
        }
        assertEquals(ChunkSection.AIR, state[0], "도달 거리 안의 로드된 청크 블록은 파괴된다");
    }
}
