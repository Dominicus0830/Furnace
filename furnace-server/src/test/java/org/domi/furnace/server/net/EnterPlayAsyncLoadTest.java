// D-14: PlayerData.load 를 IO 풀로 옮긴 뒤에도 접속 순서(읽기 → onEnterPlay → 그 사이 도착한 play 패킷)와 실패·끊김 처리가 유지되는지.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnterPlayAsyncLoadTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;

    /** 손으로 돌리는 IO 풀(제출만 쌓고 runAll 때 실행). */
    static final class ManualPool extends AbstractExecutorService {
        final ArrayDeque<Runnable> q = new ArrayDeque<>();
        volatile boolean shut;
        @Override public void execute(Runnable r) { if (shut) throw new java.util.concurrent.RejectedExecutionException(); synchronized (q) { q.add(r); } }
        int runAll() { int n = 0; Runnable r; while (true) { synchronized (q) { r = q.poll(); } if (r == null) return n; r.run(); n++; } }
        @Override public void shutdown() { shut = true; }
        @Override public List<Runnable> shutdownNow() { shut = true; return List.of(); }
        @Override public boolean isShutdown() { return shut; }
        @Override public boolean isTerminated() { return shut; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private World world(ManualPool pool) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, pool);
        w.worldDir(tmp);
        return w;
    }

    private static Object field(ConnectionHandler h, String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(h);
    }

    /** 구성 단계까지 진행하고 PLAY 진입 ack 를 보낸다. */
    private static void toAck(EmbeddedChannel ch) {
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));   // → PLAY (저장 데이터 읽기 제출)
    }

    private static ByteBuf clientInfo(int viewDistance, String locale) {
        return frame(0x0A, b -> {
            Types.writeString(b, locale);
            b.writeByte(viewDistance);
            Types.writeVarInt(b, 0); b.writeBoolean(true); b.writeByte(0x7F); Types.writeVarInt(b, 1);
            b.writeBoolean(false); b.writeBoolean(true);
        });
    }

    @AfterEach
    void close() { if (sched != null) sched.close(); }

    @Test
    void playPacketsDuringLoadAreHandledAfterEnterPlayInOrder() throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:Tester".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        PlayerData.save(tmp, uuid, 3.5, -60, 4.5, 90f, 0f, true, slots);   // 저장된 위치가 접속에 반영돼야 한다
        ManualPool pool = new ManualPool();
        World w = world(pool);
        ConnectionHandler h = new ConnectionHandler("t", -1, sched, w, new EntityIdAllocator());
        EmbeddedChannel ch = new EmbeddedChannel(h);
        toAck(ch);
        // 읽기가 끝나기 전: 플레이어 없음, play 패킷은 보관
        ch.writeInbound(clientInfo(5, "ko_kr"));
        ch.writeInbound(clientInfo(6, "ja_jp"));
        ch.runPendingTasks();
        assertNull(field(h, "player"), "읽기 전엔 Player 가 없다");
        assertEquals(0, w.onlinePlayers().size());
        assertEquals(1, pool.runAll(), "읽기 작업 1개가 IO 풀에 제출돼야 한다");
        ch.runPendingTasks();
        Player p = (Player) field(h, "player");
        assertNotNull(p, "읽기 뒤 onEnterPlay 가 돌아야 한다");
        assertEquals("ja_jp", p.locale(), "보관한 패킷은 도착 순서대로 onEnterPlay 뒤에 처리된다");
        assertNull(field(h, "pendingPlayFrames"));
        assertEquals(3.5, (double) field(h, "ix"), 1e-9, "저장 위치가 반영돼야 한다");
        ch.finishAndReleaseAll();
    }

    @Test
    void disconnectDuringLoadCreatesNoPlayerAndReleasesFrames() throws Exception {
        ManualPool pool = new ManualPool();
        World w = world(pool);
        ConnectionHandler h = new ConnectionHandler("t", -1, sched, w, new EntityIdAllocator());
        EmbeddedChannel ch = new EmbeddedChannel(h);
        toAck(ch);
        ByteBuf held = clientInfo(5, "ko_kr");
        held.retain();   // 해제 여부를 보려고 하나 더 쥔다
        ch.writeInbound(held);
        ch.close();
        assertEquals(1, held.refCnt(), "끊기면 보관 패킷을 해제해야 한다");
        held.release();
        pool.runAll();
        ch.runPendingTasks();
        assertNull(field(h, "player"), "끊긴 연결엔 Player 를 만들지 않는다");
        assertEquals(0, w.onlinePlayers().size());
    }

    @Test
    void rejectedPoolFallsBackToSyncLoad() throws Exception {
        ManualPool pool = new ManualPool();
        World w = world(pool);
        pool.shutdown();
        ConnectionHandler h = new ConnectionHandler("t", -1, sched, w, new EntityIdAllocator());
        EmbeddedChannel ch = new EmbeddedChannel(h);
        toAck(ch);
        ch.runPendingTasks();
        assertNotNull(field(h, "player"), "IO 풀이 거부하면 예전처럼 동기로 읽고 진입한다");
        assertNull(field(h, "pendingPlayFrames"));
        ch.finishAndReleaseAll();
    }

    // -Dfurnace.bench=true 일 때만. 접속 1회당 이벤트루프가 막히던 시간(PlayerData.load) vs 제출 비용.
    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        UUID uuid = UUID.randomUUID();
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        PlayerData.save(tmp, uuid, 1, 2, 3, 0f, 0f, true, slots);
        var exec = java.util.concurrent.Executors.newSingleThreadExecutor();
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 2000; i++) PlayerData.load(tmp, uuid);   // 워밍업
            long t = System.nanoTime();
            for (int i = 0; i < 2000; i++) PlayerData.load(tmp, uuid);
            double loadUs = (System.nanoTime() - t) / 1000.0 / 2000;
            for (int i = 0; i < 2000; i++) exec.execute(() -> { });
            t = System.nanoTime();
            for (int i = 0; i < 2000; i++) exec.execute(() -> PlayerData.load(tmp, uuid));
            double submitUs = (System.nanoTime() - t) / 1000.0 / 2000;
            exec.submit(() -> { }).get();
            System.out.printf("[BENCH] D-14 round=%d 전(이벤트루프 동기 load) %.1f us/접속, 후(이벤트루프 제출) %.2f us/접속%n", round, loadUs, submitUs);
        }
        exec.shutdown();
    }
}
