// BungeeCord 레거시 포워딩 로그인: 프록시처럼 주소 칸에 IP·UUID·속성을 실어 접속해 Login Success 프로필·IP 밴·거절 문구를 본다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.auth.BungeeForwarding;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeeLoginTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    /** 실제 스킨 textures 값처럼 길어서 바닐라 주소 상한(255자)을 넘는 속성. */
    private static final String LONG_TEXTURE = "eyJ0".repeat(400);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private NetworkServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop();
        if (sched != null) sched.close();
    }

    private int start(boolean bungee) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, org.domi.furnace.server.TestServers.ioPool());
        world.worldDir(tmp);
        PlayerLists lists = new PlayerLists(tmp, new ServerPropertiesFile(tmp.resolve("server.properties")));
        lists.getIpBans().add(new org.domi.furnace.server.players.IpBanListEntry("203.0.113.9"));
        ServerLoginContext ctx = new ServerLoginContext(false, false, false, true, false, false, true, 20,
                "A Minecraft Server", null, null, lists, null, null).bungeecord(bungee);
        server = new NetworkServer(0, -1, ctx, sched, world, new EntityIdAllocator());
        return server.start();
    }

    private static String forwarded(String ip) {
        return "mc.example.com\0" + ip + "\0" + "069a79f444e94726a5befca90e38aaf5\0"
                + "[{\"name\":\"textures\",\"value\":\"" + LONG_TEXTURE + "\",\"signature\":\"c2ln\"}]";
    }

    /** 핸드셰이크(주소 칸 hostField) + Login Start 를 보내고 첫 응답 {packetId, body}. */
    private static Object[] login(int port, String hostField) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            writeFrame(out, 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, hostField); b.writeShort(25565); Types.writeVarInt(b, 2); });
            writeFrame(out, 0x00, b -> { Types.writeString(b, "Notch"); Types.writeUuid(b, UUID.randomUUID()); });
            ByteBuf reply = readFrame(s.getInputStream());
            return new Object[]{Types.readVarInt(reply), reply};
        }
    }

    @Test
    void forwardedUuidAndPropertiesBecomeProfile() throws Exception {
        int port = start(true);
        Object[] r = login(port, forwarded("203.0.113.5"));
        assertEquals(0x02, r[0], "Login Success");
        ByteBuf b = (ByteBuf) r[1];
        assertEquals(NOTCH, Types.readUuid(b), "프록시가 알려 준 UUID(오프라인 UUID 가 아님)");
        assertEquals("Notch", Types.readString(b, 16));
        assertEquals(1, Types.readVarInt(b));
        assertEquals("textures", Types.readString(b, 64));
        assertEquals(LONG_TEXTURE, Types.readString(b, Short.MAX_VALUE), "255자 넘는 속성도 핸드셰이크로 받는다");
        assertTrue(b.readBoolean());
        assertEquals("c2ln", Types.readString(b, Short.MAX_VALUE));
    }

    @Test
    void ipBanUsesForwardedAddress() throws Exception {
        int port = start(true);
        Object[] r = login(port, forwarded("203.0.113.9"));
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.banned_ip.reason"),
                "IP 밴은 프록시가 알려 준 실제 주소로 검사");
    }

    @Test
    void directConnectionRejectedWhenEnabled() throws Exception {
        int port = start(true);
        Object[] r = login(port, "localhost");
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains(BungeeForwarding.NOT_FORWARDED));
    }

    @Test
    void forwardedDataRejectedWhenDisabled() throws Exception {
        int port = start(false);
        Object[] r = login(port, "mc.example.com\0" + "203.0.113.5\0" + "069a79f444e94726a5befca90e38aaf5");
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains(BungeeForwarding.UNEXPECTED_FORWARD));
        r = login(port, "localhost");
        assertEquals(0x02, r[0], "꺼져 있으면 평범한 오프라인 로그인");
        assertEquals(org.domi.furnace.server.players.GameProfile.offline("Notch").id(), Types.readUuid((ByteBuf) r[1]));
    }

    @Test
    void cannotEnableWithVelocity() {
        ServerLoginContext ctx = ServerLoginContext.offline("m").velocitySecret("s");
        assertThrows(IllegalStateException.class, () -> ctx.bungeecord(true));
        ServerLoginContext ctx2 = ServerLoginContext.offline("m").bungeecord(true);
        assertThrows(IllegalStateException.class, () -> ctx2.velocitySecret("s"));
    }

    private static void writeFrame(OutputStream out, int id, Consumer<ByteBuf> body) throws Exception {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        ByteBuf f = Unpooled.buffer();
        Types.writeVarInt(f, b.readableBytes());
        f.writeBytes(b);
        byte[] bytes = new byte[f.readableBytes()];
        f.readBytes(bytes);
        out.write(bytes);
        out.flush();
    }

    private static ByteBuf readFrame(InputStream in) throws Exception {
        DataInputStream d = new DataInputStream(in);
        int len = 0, shift = 0, b;
        do {
            b = d.readUnsignedByte();
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        byte[] data = new byte[len];
        d.readFully(data);
        return Unpooled.wrappedBuffer(data);
    }
}
