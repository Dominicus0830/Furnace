// 패킷 API 를 실제 연결 흐름에서: 클라이언트 패킷이 리스너를 거쳐 바뀌거나 버려지고, receive 로 넣은 패킷이 진짜처럼 처리된다.
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
import java.util.function.Consumer;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.serverbound.ServerboundSetCarriedItemPacket;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.plugin.PluginHost;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PacketApiFlowTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
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

    @AfterEach
    void close() throws Exception {
        if (sched != null) sched.close();
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        TestServers.drainIoPools();
    }

    @Test
    void clientPacketsPassThroughListenersAndReceiveInjects() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp.resolve("core"), sched);
        PluginHost host = core.plugins();
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = TestServers.ioPool();
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        world.configurePersistence(Integer.MAX_VALUE, 100);
        world.worldDir(tmp);
        handler = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));   // → PLAY
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (field("player") == null) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "play 진입이 끝나지 않음");
            Thread.sleep(20);
        }
        Player p = (Player) field("player");
        host.packetsImpl().attach(p);   // 서버 핸들 없는 시험 연결 — 직접 붙인다(실서버는 onEnterPlay 가 붙인다)
        org.domi.furnace.server.entity.PlayerInventory inv = (org.domi.furnace.server.entity.PlayerInventory) field("inv");
        TestPluginsAccess plugin = new TestPluginsAccess();
        List<Integer> seen = new ArrayList<>();
        host.packets().listen(plugin.a, PacketTypes.Serverbound.SET_CARRIED_ITEM, e -> {
            seen.add(e.packet().slot() * (e.isPluginPacket() ? -1 : 1));
            if (e.packet().slot() == 7) e.setCancelled(true);
            if (e.packet().slot() == 5) e.setPacket(new ServerboundSetCarriedItemPacket(6));
        });
        ch.writeInbound(frame(0x2F, b -> b.writeShort(3)));
        ch.runPendingTasks();
        assertEquals(3, inv.selected());
        ch.writeInbound(frame(0x2F, b -> b.writeShort(5)));
        ch.runPendingTasks();
        assertEquals(6, inv.selected(), "리스너가 5 → 6 으로 바꿨다");
        ch.writeInbound(frame(0x2F, b -> b.writeShort(7)));
        ch.runPendingTasks();
        assertEquals(6, inv.selected(), "취소된 패킷은 처리하지 않는다");
        host.packets().receive(host.player(p), new ServerboundSetCarriedItemPacket(2));
        ch.runPendingTasks();
        assertEquals(2, inv.selected(), "receive 로 넣은 패킷은 진짜처럼 처리된다");
        host.packets().receiveSilently(host.player(p), new ServerboundSetCarriedItemPacket(7));
        ch.runPendingTasks();
        assertEquals(7, inv.selected(), "조용히 넣으면 리스너를 거치지 않는다");
        assertEquals(List.of(3, 5, 7, -2), seen);
        ch.close();
    }

    /** 플러그인 시험 클래스는 plugin 패키지 안이라 여기서 쓸 소유자 하나를 만든다. */
    private static final class TestPluginsAccess {
        final org.domi.furnace.api.plugin.FurnacePlugin a = new org.domi.furnace.api.plugin.FurnacePlugin() { };
    }
}
