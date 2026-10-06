// ConnectionHandler 입력 처리: 부분 이동 패킷이 서로 덮어쓰지 않는지, 비정상 이동/파괴 요청이 걸러지는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionInputTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private FurnaceSchedulerImpl sched;
    private World world;
    private EmbeddedChannel ch;
    private Player p;

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    @BeforeEach
    void join() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7);
        ConnectionHandler h = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        ch = new EmbeddedChannel(h);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));                       // → PLAY, 플레이어 편입
        sched.region(R00).execute(() -> { }).get();
        Field f = ConnectionHandler.class.getDeclaredField("player");
        f.setAccessible(true);
        p = (Player) f.get(h);
        sched.region(R00).execute(() -> p.drainPending()).get(); // 접속 시 발행분 적용
    }

    @AfterEach
    void close() {
        sched.close();
    }

    private void drain() throws Exception {
        sched.region(R00).execute(() -> p.drainPending()).get();
    }

    @Test
    void positionThenRotationInSameTick_keepsBoth() throws Exception {
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(10); b.writeDouble(-60); b.writeDouble(3); b.writeBoolean(true); }));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(45f); b.writeFloat(10f); b.writeBoolean(true); }));
        drain();
        assertEquals(10.0, p.x());
        assertEquals(45f, p.yaw());
    }

    @Test
    void rotationThenPositionInSameTick_keepsBoth() throws Exception {
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(90f); b.writeFloat(0f); b.writeBoolean(true); }));
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(20); b.writeDouble(-60); b.writeDouble(0); b.writeBoolean(true); }));
        drain();
        assertEquals(20.0, p.x());
        assertEquals(90f, p.yaw());
    }

    @Test
    void teleportAndNonFinitePositions_areRejectedAndCorrected() throws Exception {
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(5_000_000); b.writeDouble(-60); b.writeDouble(0); b.writeBoolean(true); }));
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(Double.NaN); b.writeDouble(-60); b.writeDouble(0); b.writeBoolean(true); }));
        drain();
        assertEquals(0.0, p.x(), "급이동/NaN은 반영되지 않는다");
        ch.runPendingTasks();
        int syncs = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            if (Types.readVarInt(bb) == 0x40) syncs++;   // SyncPlayerPosition (입장 시 1개 + 거부 2개)
            bb.release();
        }
        assertEquals(3, syncs);
    }

    @Test
    void digRequests_farAwayOrOutOfRange_doNotCreateChunks() throws Exception {
        int[][] bad = {{5000, -61, 0}, {0, 1000, 0}, {0, -1000, 0}, {100, -61, 100}};
        for (int[] b : bad) {
            ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, b[0], b[1], b[2]);
                f.writeByte(1); Types.writeVarInt(f, 1); }));
        }
        sched.region(R00).execute(() -> { }).get();
        // 도달 거리 밖/범위 밖은 region까지 가지도 않고, 가도 로드 안 된 청크는 만들지 않는다
        boolean[] any = new boolean[1];
        sched.region(R00).execute(() -> {
            for (int[] b : bad) any[0] |= world.regionAt(R00).chunkLoaded(ChunkPos.ofBlock(b[0], b[2]));
        }).get();
        assertFalse(any[0]);
    }

    @Test
    void digWithinReachOnLoadedChunk_breaksBlock() throws Exception {
        sched.region(R00).execute(() -> world.regionAt(R00).chunkAt(new ChunkPos(0, 0))).get();
        ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, 1, -61, 1);
            f.writeByte(1); Types.writeVarInt(f, 1); }));
        int[] state = new int[1];
        sched.region(R00).execute(() -> { }).get();
        sched.region(R00).execute(() -> state[0] = world.regionAt(R00).chunkAt(new ChunkPos(0, 0)).getBlock(1, -61, 1)).get();
        assertEquals(0, state[0], "도달 거리 안의 로드된 청크 블록은 파괴된다");
    }
}
