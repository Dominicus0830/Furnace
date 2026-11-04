// 접속 흐름: 구성 단계에서 받은 minecraft:brand·minecraft:register 가 play 진입 때 Player 로 넘어가고, play 단계 unregister 도 반영된다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientChannelRegisterFlowTest {

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

    private static ByteBuf payload(int id, String channel, byte[] data) {
        return frame(id, b -> { Types.writeString(b, channel); b.writeBytes(data); });
    }

    private Object field(String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(handler);
    }

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void configurationRegisterAndBrandReachPlayer_playUnregisterApplies() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = org.domi.furnace.server.TestServers.ioPool();
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        world.configurePersistence(Integer.MAX_VALUE, 100);
        world.worldDir(tmp);
        handler = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));   // → 구성
        ch.writeInbound(frame(0x02, b -> { Types.writeString(b, "minecraft:brand"); Types.writeString(b, "fabric"); }));
        ch.writeInbound(payload(0x02, PluginChannels.REGISTER, "mod:a\0mod:b\0".getBytes(StandardCharsets.UTF_8)));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));   // → PLAY
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (field("player") == null) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "play 진입이 끝나지 않음");
            Thread.sleep(20);
        }
        Player p = (Player) field("player");
        assertEquals("fabric", p.clientBrand(), "구성 단계 brand 가 Player 에 넘어간다");
        assertEquals(Set.of("mod:a", "mod:b"), p.pluginChannels().snapshot());
        ch.writeInbound(payload(0x12, PluginChannels.UNREGISTER, "mod:a".getBytes(StandardCharsets.UTF_8)));
        ch.writeInbound(payload(0x12, PluginChannels.REGISTER, "mod:c".getBytes(StandardCharsets.UTF_8)));
        ch.runPendingTasks();
        assertEquals(Set.of("mod:b", "mod:c"), p.pluginChannels().snapshot());
        ch.close();
    }
}
