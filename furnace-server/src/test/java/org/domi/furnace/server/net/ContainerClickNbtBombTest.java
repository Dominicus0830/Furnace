// 생존 플레이어의 컨테이너 클릭(0x0E) 한 개(약 40바이트)가 네트워크 스레드에서 거대 할당을 일으키지 않는지(A02-01 회귀 시험).
// 경로: handlePlay 0x0E → readSlot → ItemStackCodecs.readStack → custom_data(PCodec.COMPOUND) → NbtVals.readNetwork. 음수 길이 리스트는 바로 디코드 실패.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerClickNbtBombTest {

    @BeforeAll
    static void items() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private java.util.concurrent.ExecutorService io;

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

    private void join() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, io = org.domi.furnace.server.TestServers.ioPool());
        world.configurePersistence(Integer.MAX_VALUE, 100);
        world.worldDir(tmp);
        world.defaultSpawn(new SpawnPoint(0, -60, 0, 0f));
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        handler = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        ch = new ConcurrentEmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));
        ch.runPendingTasks();
    }

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        if (io != null) { io.shutdown(); io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS); }
    }

    private static void utf(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        b.writeShort(x.length);
        b.writeBytes(x);
    }

    @Test
    void survivalContainerClickWithNegativeListDoesNotAllocate() throws Exception {
        join();
        int stone = org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:stone");
        int customData = org.domi.furnace.server.item.DataComponents.networkId(org.domi.furnace.server.item.DataComponents.CUSTOM_DATA);
        ByteBuf pkt = frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeShort(-999); b.writeByte(0);   // 창 0, 상태 0, 칸 -999, 버튼 0
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);                                      // PICKUP, 바뀐 칸 0개
            Types.writeVarInt(b, 1); Types.writeVarInt(b, stone);                                  // 들고 있는 아이템: 돌 1개
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, customData);    // 패치 +1 -0: custom_data
            b.writeByte(10);                                                                       // compound
            b.writeByte(9); utf(b, "a"); b.writeByte(0); b.writeInt(Integer.MIN_VALUE);            // 음수 길이 리스트 → 바로 거절(예전: 한도 +8 GiB)
            b.writeByte(12); utf(b, "b"); b.writeInt(134_217_728);                                 // long[134M] = 1 GiB 주장
            b.writeByte(0);
        });
        int size = pkt.readableBytes();
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        long a0 = mx.getThreadAllocatedBytes(tid);
        long t0 = System.nanoTime();
        ch.writeInbound(pkt);   // EmbeddedChannel: 이 스레드가 이벤트루프 역할
        long ms = (System.nanoTime() - t0) / 1_000_000;
        long mib = (mx.getThreadAllocatedBytes(tid) - a0) >> 20;
        ch.runPendingTasks();
        assertTrue(mib < 16, size + "바이트 패킷 하나가 네트워크 스레드에서 " + mib + " MiB 를 할당하고 " + ms + " ms 붙잡았다");
        assertFalse(ch.isActive(), "바닐라처럼 디코드 실패로 연결을 끊어야 한다");
    }
}
