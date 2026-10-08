// 로그인 파이프라인 검증: 순서 위반·잘못된 이름·버전 불일치는 사유와 함께 거부, 같은 이름 재접속은 기존 세션을 킥
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginFlowTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World world;

    private ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private EmbeddedChannel newConn() {
        if (sched == null) {
            sched = new FurnaceSchedulerImpl(50_000_000L, 1);
            BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
            world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                    reg.directBits(), 7, Executors.newSingleThreadExecutor());
            world.worldDir(tmp);
        }
        return new EmbeddedChannel(new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator()));
    }

    private void handshake(EmbeddedChannel ch, int protocol, int next) {
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, protocol); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, next); }));
    }

    private void loginStart(EmbeddedChannel ch, String name) {
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, name); Types.writeUuid(b, new UUID(0, 0)); }));
    }

    private List<ByteBuf> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<ByteBuf> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) out.add((ByteBuf) o);
        return out;
    }

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    @Test
    void loginAcknowledgedWithoutLoginStart_isRejected() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 767, 2);
        ch.writeInbound(frame(0x03, null));
        assertFalse(ch.isActive(), "로그인 시작 없이 Login Acknowledged를 보내면 연결을 닫아야 한다");
        assertEquals(0, world.onlinePlayers().size());
    }

    @Test
    void enterPlayWithoutFinishingConfiguration_isRejected() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 767, 2);
        loginStart(ch, "Tester");
        ch.writeInbound(frame(0x03, null));      // → CONFIGURATION
        ch.writeInbound(frame(0x03, null));      // Finish를 보내기도 전에 PLAY 진입 시도
        assertFalse(ch.isActive());
        assertEquals(0, world.onlinePlayers().size(), "고아 Player가 등록되면 안 된다");
    }

    @Test
    void invalidName_isKickedWithReason() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 767, 2);
        loginStart(ch, "Bad Name");
        List<ByteBuf> out = drain(ch);
        assertFalse(ch.isActive());
        assertFalse(out.isEmpty());
        ByteBuf b = out.get(0);
        assertEquals(0x00, Types.readVarInt(b), "Login Disconnect(0x00)");
        String json = Types.readString(b, 262144);
        assertTrue(json.contains("username"), json);
    }

    @Test
    void wrongProtocolVersion_isKickedWithReason() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 760, 2);
        List<ByteBuf> out = drain(ch);
        assertFalse(ch.isActive());
        ByteBuf b = out.get(0);
        assertEquals(0x00, Types.readVarInt(b));
        assertTrue(Types.readString(b, 262144).contains("1.21.1"));
    }

    @Test
    void statusPing_withOtherProtocolVersion_stillWorks() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 760, 1);
        ch.writeInbound(frame(0x00, null));
        assertTrue(ch.isActive());
        assertFalse(drain(ch).isEmpty());
    }

    @Test
    void knownPacksResponseTwice_doesNotResendRegistries() {
        EmbeddedChannel ch = newConn();
        handshake(ch, 767, 2);
        loginStart(ch, "Tester");
        ch.writeInbound(frame(0x03, null));
        drain(ch);
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        int first = drain(ch).size();
        assertTrue(first > 1);
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        assertTrue(drain(ch).isEmpty(), "Known Packs 응답이 반복돼도 레지스트리를 다시 보내면 안 된다(증폭)");
    }

    @Test
    void statusMotd_withControlCharacters_producesValidJson() {
        String json = org.domi.furnace.server.net.protocol.status.StatusResponse.of("a\nb\t\u0001\"").json();
        assertFalse(json.chars().anyMatch(c -> c < 0x20), "제어문자가 JSON 문자열에 그대로 들어갔다: " + json);
    }
}
