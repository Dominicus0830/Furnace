// 온라인 모드 로그인 전 과정: 암호화 요청 → (클라) RSA 로 공유 비밀 전송 → AES/CFB8 스트림 → 세션 서버 hasJoined → 로그인 게이트
// → Login Success(프로필 속성 포함). 세션 서버는 로컬 HTTP 가짜를 쓴다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.auth.Crypt;
import org.domi.furnace.server.net.auth.SessionService;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.UserBanListEntry;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OnlineLoginTest {

    private static final KeyPair KEYS = Crypt.generateKeyPair();
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir Path tmp;
    private HttpServer session;
    private FurnaceSchedulerImpl sched;
    private NetworkServer server;
    private final AtomicReference<String> seenServerId = new AtomicReference<>();
    private final AtomicReference<String> seenQuery = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop();
        if (sched != null) sched.close();
        if (session != null) session.stop(0);
    }

    /** status 200 이면 Notch 프로필(textures 속성 포함), 204 면 세션 없음. */
    private String startSession(int status) throws Exception {
        session = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        session.createContext("/session/minecraft/hasJoined", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            seenQuery.set(q);
            for (String kv : q.split("&")) if (kv.startsWith("serverId=")) seenServerId.set(kv.substring(9));
            if (status == 200) {
                byte[] body = ("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":"
                        + "[{\"name\":\"textures\",\"value\":\"dGV4\",\"signature\":\"c2ln\"}]}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } else {
                ex.sendResponseHeaders(status, -1);
            }
            ex.close();
        });
        session.start();
        return "http://127.0.0.1:" + session.getAddress().getPort();
    }

    private int startServer(String sessionHost, PlayerLists lists, int maxPlayers, boolean preventProxy) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, org.domi.furnace.server.TestServers.ioPool());
        world.worldDir(tmp);
        ServerLoginContext ctx = new ServerLoginContext(true, preventProxy, false, true, false, false, true, maxPlayers,
                "A Minecraft Server", KEYS, new SessionService(sessionHost), lists, null, null);
        server = new NetworkServer(0, -1, ctx, sched, world, new EntityIdAllocator());
        return server.start();
    }

    private PlayerLists lists() {
        return new PlayerLists(tmp, new ServerPropertiesFile(tmp.resolve("server.properties")));
    }

    /** 로그인해서 암호화까지 마친 뒤 첫 (암호화된) 패킷을 돌려준다: {packetId, body}. */
    private Object[] loginOnline(int port) throws Exception {
        return loginOnline(port, true);
    }

    private Object[] loginOnline(int port, boolean checkHash) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            writeFrame(out, 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
            writeFrame(out, 0x00, b -> { Types.writeString(b, "Notch"); Types.writeUuid(b, NOTCH); });
            ByteBuf req = readFrame(in);
            assertEquals(0x01, Types.readVarInt(req), "Encryption Request");
            assertEquals("", Types.readString(req, 20));
            byte[] pub = new byte[Types.readVarInt(req)];
            req.readBytes(pub);
            byte[] token = new byte[Types.readVarInt(req)];
            req.readBytes(token);
            assertTrue(req.readBoolean(), "shouldAuthenticate");
            PublicKey serverKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pub));
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(128);
            SecretKey secret = kg.generateKey();
            byte[] encSecret = Crypt.encryptUsingKey(serverKey, secret.getEncoded());
            byte[] encToken = Crypt.encryptUsingKey(serverKey, token);
            writeFrame(out, 0x01, b -> {
                Types.writeVarInt(b, encSecret.length); b.writeBytes(encSecret);
                Types.writeVarInt(b, encToken.length); b.writeBytes(encToken);
            });
            InputStream cin = new CipherInputStream(in, Crypt.getCipher(Cipher.DECRYPT_MODE, secret));
            ByteBuf reply = readFrame(cin);
            if (checkHash) assertEquals(Crypt.serverIdHash("", serverKey, secret), seenServerId.get(), "세션 서버에 보낸 serverId 해시");
            int id = Types.readVarInt(reply);
            return new Object[]{id, reply};
        }
    }

    @Test
    void onlineLoginSucceedsWithProfileProperties() throws Exception {
        int port = startServer(startSession(200), null, 20, false);
        Object[] r = loginOnline(port);
        assertEquals(0x02, r[0], "Login Success");
        ByteBuf b = (ByteBuf) r[1];
        assertEquals(NOTCH, Types.readUuid(b));
        assertEquals("Notch", Types.readString(b, 16));
        assertEquals(1, Types.readVarInt(b));
        assertEquals("textures", Types.readString(b, 64));
        assertEquals("dGV4", Types.readString(b, 32767));
        assertTrue(b.readBoolean());
        assertEquals("c2ln", Types.readString(b, 1024));
        assertTrue(b.readBoolean(), "strict error handling");
        assertFalse(seenQuery.get().contains("ip="), "prevent-proxy-connections 꺼짐이면 ip 를 보내지 않는다");
    }

    @Test
    void preventProxyConnectionsSendsIp() throws Exception {
        int port = startServer(startSession(200), null, 20, true);
        loginOnline(port);
        assertTrue(seenQuery.get().contains("ip=127.0.0.1"), seenQuery.get());
    }

    @Test
    void noSessionIsUnverified() throws Exception {
        int port = startServer(startSession(204), null, 20, false);
        Object[] r = loginOnline(port);
        assertEquals(0x00, r[0], "Login Disconnect");
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.unverified_username"));
    }

    @Test
    void sessionServerDownIsAuthServersDown() throws Exception {
        int port = startServer("http://127.0.0.1:1", null, 20, false);
        Object[] r = loginOnline(port, false);
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.authservers_down"));
    }

    @Test
    void bannedPlayerIsRejectedWithReason() throws Exception {
        PlayerLists lists = lists();
        lists.getBans().add(new UserBanListEntry(new GameProfile(NOTCH, "Notch"), null, "Server", null, "griefing"));
        int port = startServer(startSession(200), lists, 20, false);
        Object[] r = loginOnline(port);
        assertEquals(0x00, r[0]);
        String json = Types.readString((ByteBuf) r[1], 262144);
        assertTrue(json.contains("multiplayer.disconnect.banned.reason") && json.contains("griefing"), json);
    }

    @Test
    void whitelistAndFullServer() throws Exception {
        PlayerLists lists = lists();
        lists.setUsingWhiteList(true);
        int port = startServer(startSession(200), lists, 20, false);
        Object[] r = loginOnline(port);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.not_whitelisted"));
        stop();
        server = null; sched = null; session = null;
        PlayerLists open = new PlayerLists(tmp.resolve("x"), new ServerPropertiesFile(tmp.resolve("p2")));
        java.nio.file.Files.createDirectories(tmp.resolve("x"));
        port = startServer(startSession(200), open, 0, false);
        r = loginOnline(port);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.server_full"));
    }

    @Test
    void statusJsonHasVanillaShape() throws Exception {
        int port = startServer(startSession(200), null, 37, false);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(5000);
            writeFrame(s.getOutputStream(), 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 1); });
            writeFrame(s.getOutputStream(), 0x00, null);
            ByteBuf f = readFrame(s.getInputStream());
            assertEquals(0x00, Types.readVarInt(f));
            assertEquals("{\"description\":\"A Minecraft Server\",\"players\":{\"max\":37,\"online\":0},"
                    + "\"version\":{\"name\":\"1.21.1\",\"protocol\":767},\"enforcesSecureChat\":false}", Types.readString(f, 32767));
        }
    }

    private int startVelocityServer(String secret) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, org.domi.furnace.server.TestServers.ioPool());
        world.worldDir(tmp);
        PlayerLists lists = lists();
        lists.getIpBans().add(new org.domi.furnace.server.players.IpBanListEntry("203.0.113.9"));
        ServerLoginContext ctx = new ServerLoginContext(false, false, false, true, false, false, true, 20,
                "A Minecraft Server", KEYS, null, lists, null, null).velocitySecret(secret);
        server = new NetworkServer(0, -1, ctx, sched, world, new EntityIdAllocator());
        return server.start();
    }

    /** 프록시를 흉내 내 포워딩 응답을 보내고 첫 응답 패킷을 받는다. */
    private Object[] loginViaVelocity(int port, String signingSecret, String clientIp) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            writeFrame(out, 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
            writeFrame(out, 0x00, b -> { Types.writeString(b, "Notch"); Types.writeUuid(b, NOTCH); });
            ByteBuf q = readFrame(s.getInputStream());
            assertEquals(0x04, Types.readVarInt(q), "Login Plugin Request");
            int messageId = Types.readVarInt(q);
            assertEquals("velocity:player_info", Types.readString(q, 256));
            assertEquals(4, q.readByte());
            ByteBuf payload = Unpooled.buffer();
            Types.writeVarInt(payload, 4);
            Types.writeString(payload, clientIp);
            Types.writeUuid(payload, NOTCH);
            Types.writeString(payload, "Notch");
            Types.writeVarInt(payload, 1);
            Types.writeString(payload, "textures"); Types.writeString(payload, "dGV4");
            payload.writeBoolean(true); Types.writeString(payload, "c2ln");
            byte[] raw = new byte[payload.readableBytes()];
            payload.readBytes(raw);
            byte[] signed = new org.domi.furnace.server.net.auth.VelocityForwarding(signingSecret).sign(raw);
            writeFrame(out, 0x02, b -> { Types.writeVarInt(b, messageId); b.writeBoolean(true); b.writeBytes(signed); });
            ByteBuf reply = readFrame(s.getInputStream());
            return new Object[]{Types.readVarInt(reply), reply};
        }
    }

    @Test
    void velocityForwardingUsesProxyProfile() throws Exception {
        int port = startVelocityServer("s3cret");
        Object[] r = loginViaVelocity(port, "s3cret", "203.0.113.5");
        assertEquals(0x02, r[0], "Login Success");
        ByteBuf b = (ByteBuf) r[1];
        assertEquals(NOTCH, Types.readUuid(b));
        assertEquals("Notch", Types.readString(b, 16));
        assertEquals(1, Types.readVarInt(b));
    }

    @Test
    void velocityBadSignatureOrBannedForwardedIp() throws Exception {
        int port = startVelocityServer("s3cret");
        Object[] r = loginViaVelocity(port, "wrong", "203.0.113.5");
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("Unable to verify player details"));
        r = loginViaVelocity(port, "s3cret", "203.0.113.9");
        assertEquals(0x00, r[0]);
        assertTrue(Types.readString((ByteBuf) r[1], 262144).contains("multiplayer.disconnect.banned_ip.reason"),
                "IP 밴은 프록시가 알려 준 실제 주소로 검사");
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
