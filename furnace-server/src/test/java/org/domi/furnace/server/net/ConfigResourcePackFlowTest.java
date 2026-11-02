// 구성 단계 리소스 팩: push → (대기 중 keep-alive) → 끝 상태 응답 → Finish. 필수 팩 거절은 킥. 팩이 없으면 순서가 그대로다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.domi.furnace.server.config.ServerProperties;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigResourcePackFlowTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World world;

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private EmbeddedChannel connect(ServerProperties.ResourcePackInfo pack) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, org.domi.furnace.server.TestServers.ioPool());
        world.worldDir(tmp);
        var login = ServerLoginContext.offline("t").resourcePack(pack);
        EmbeddedChannel ch = new EmbeddedChannel(new ConnectionHandler(login, -1, sched, world, new EntityIdAllocator()));
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));   // → 구성
        drain(ch);
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));   // Known Packs 응답
        return ch;
    }

    private static List<Integer> ids(EmbeddedChannel ch) {
        return drain(ch).stream().map(b -> Types.readVarInt(b)).toList();
    }

    private static List<ByteBuf> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<ByteBuf> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) out.add((ByteBuf) o);
        return out;
    }

    private static ServerProperties.ResourcePackInfo pack(boolean required) {
        return new ServerProperties.ResourcePackInfo(new UUID(1, 2), "https://example.invalid/p.zip", "", required, null);
    }

    private static void respond(EmbeddedChannel ch, int action) {
        ch.writeInbound(frame(0x06, b -> { Types.writeUuid(b, new UUID(1, 2)); Types.writeVarInt(b, action); }));
    }

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    @Test
    void noPack_finishesRightAfterTags() {
        EmbeddedChannel ch = connect(null);
        List<Integer> ids = ids(ch);
        assertEquals(0x03, ids.get(ids.size() - 1), "팩이 없으면 태그 바로 뒤에 Finish: " + ids);
        assertFalse(ids.contains(0x09));
    }

    @Test
    void pack_pushesThenWaitsForTerminalResponse() {
        EmbeddedChannel ch = connect(pack(false));
        List<Integer> ids = ids(ch);
        assertEquals(0x09, ids.get(ids.size() - 1), "push 가 마지막: " + ids);
        assertFalse(ids.contains(0x03), "응답 전에는 Finish 를 보내지 않는다");
        respond(ch, 3);   // ACCEPTED — 중간 상태
        respond(ch, 4);   // DOWNLOADED — 중간 상태
        assertTrue(ids(ch).isEmpty());
        respond(ch, 0);   // SUCCESSFULLY_LOADED
        assertEquals(List.of(0x03), ids(ch));
    }

    @Test
    void waitingForPack_sendsConfigKeepAliveEvery15Seconds() {
        EmbeddedChannel ch = connect(pack(false));
        ids(ch);
        ch.advanceTimeBy(15, TimeUnit.SECONDS);
        ch.runScheduledPendingTasks();
        assertEquals(List.of(0x04), ids(ch));
        ch.advanceTimeBy(15, TimeUnit.SECONDS);
        ch.runScheduledPendingTasks();
        assertEquals(List.of(0x04), ids(ch));
        respond(ch, 0);
        assertEquals(List.of(0x03), ids(ch));
        ch.advanceTimeBy(15, TimeUnit.SECONDS);
        ch.runScheduledPendingTasks();
        assertTrue(ids(ch).isEmpty(), "응답을 받은 뒤에는 더 보내지 않는다");
    }

    @Test
    void declinedRequiredPack_kicks() {
        EmbeddedChannel ch = connect(pack(true));
        ids(ch);
        respond(ch, 1);   // DECLINED
        List<ByteBuf> out = drain(ch);
        assertFalse(out.isEmpty());
        assertEquals(0x02, Types.readVarInt(out.get(0)), "구성 Disconnect");
        assertFalse(ch.isActive());
    }

    @Test
    void declinedOptionalPack_continues() {
        EmbeddedChannel ch = connect(pack(false));
        ids(ch);
        respond(ch, 1);
        assertEquals(List.of(0x03), ids(ch));
    }

    @Test
    void outOfRangeStatus_closesConnection() {
        EmbeddedChannel ch = connect(pack(false));
        ids(ch);
        respond(ch, 99);
        assertFalse(ch.isActive());
    }
}
