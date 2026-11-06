// 접속 단계 플러그인 이벤트(실제 소켓): 핸드셰이크 → 사전 로그인 → 로그인 → 구성 → Player 생성 → 첫 위치 → 입장이 한 연결 번호로
// 순서대로 불리는지, 스레드 계약(비동기 이벤트는 가상 스레드), 핸드셰이크 취소·구성 거부의 끊기 패킷, Player 생성 때 붙인 권한이
// 첫 명령 트리부터 반영되는지, 첫 위치 바꾸기. 구독자가 없을 때의 바닐라 흐름은 LoginFlowTest·TagQueryFlowTest 가 지킨다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.player.AsyncPlayerConfigureEvent;
import org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent;
import org.domi.furnace.api.event.player.PlayerCreateEvent;
import org.domi.furnace.api.event.player.PlayerHandshakeEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.event.player.PlayerLoginEvent;
import org.domi.furnace.api.event.player.PlayerSpawnLocationEvent;
import org.domi.furnace.api.net.ConnectionPhase;
import org.domi.furnace.api.net.PlayerConnection;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandTreePacket;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginPhaseEventsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;
    private final FurnacePlugin plugin = new FurnacePlugin() {};

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().startTicking();
        var host = core.plugins();
        plugin.init(new PluginContext() {
            final PluginDescription d = new PluginDescription("lpe", "1", "x", "", List.of(), null, null, List.of(), List.of(), List.of(),
                    null, java.util.Map.of(), List.of(), null);
            public PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("lpe"); }
            public Path configFolder() { return tmp.resolve("lpe"); }
            public Path dataFolder() { return tmp.resolve("lpe"); }
            public Path file() { return tmp.resolve("lpe.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
        // 목록 검사(PlayerLoginEvent)가 돌도록 서버의 PlayerLists 를 넣은 오프라인 로그인 문맥
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        core.plugins().commands().unregisterAll(plugin);
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private <E extends Event> void on(Class<E> type, Consumer<? super E> h) {
        core.plugins().eventsImpl().subscribe(plugin, type, h);
    }

    private static void handshake(TestClient c, String host, int hostPort) throws Exception {
        c.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, host); b.writeShort(hostPort); Types.writeVarInt(b, 2); });
    }

    /** 로그인 → 구성 → 플레이(LoginPlay 를 받을 때까지). */
    private static void login(TestClient c, String name) throws Exception {
        c.write(0x00, b -> { Types.writeString(b, name); Types.writeUuid(b, new UUID(0, 0)); });
        c.await(0x02);                                   // Login Success
        c.write(0x03, null);                             // Login Acknowledged → 구성
        c.write(0x07, b -> Types.writeVarInt(b, 0));     // Known Packs
        c.await(0x03);                                   // Finish Configuration
        c.write(0x03, null);                             // ack → 플레이
        c.await(LoginPlay.ID);
    }

    private Player awaitPlayer(String name) throws InterruptedException {
        for (int i = 0; i < 500 && core.playerByName(name) == null; i++) Thread.sleep(20);
        Player p = core.playerByName(name);
        assertNotNull(p, "접속 처리 완료: " + name);
        return p;
    }

    @Test
    void phasesFireInOrderOnOneConnection() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        List<PlayerConnection> conns = new CopyOnWriteArrayList<>();
        on(PlayerHandshakeEvent.class, e -> {
            log.add("handshake:" + e.connection().hostname() + ":" + e.connection().hostPort() + ":" + e.connection().protocolVersion()
                    + ":" + e.connection().phase() + ":virtual=" + Thread.currentThread().isVirtual());
            conns.add(e.connection());
        });
        on(AsyncPlayerPreLoginEvent.class, e -> {
            log.add("prelogin:" + e.name() + ":virtual=" + Thread.currentThread().isVirtual());
            conns.add(e.connection());
        });
        on(PlayerLoginEvent.class, e -> {
            log.add("login:" + e.name() + ":" + e.result());
            conns.add(e.connection());
        });
        on(AsyncPlayerConfigureEvent.class, e -> {
            log.add("configure:" + e.connection().name() + ":" + e.connection().phase() + ":virtual=" + Thread.currentThread().isVirtual());
            conns.add(e.connection());
        });
        on(PlayerCreateEvent.class, e -> {
            log.add("create:" + e.player().name() + ":" + e.connection().phase());
            conns.add(e.connection());
            conns.add(e.player().connection());
        });
        on(PlayerSpawnLocationEvent.class, e -> log.add("spawn:" + e.spawnLocation().world() + ":first=" + e.isFirstJoin()));
        on(PlayerJoinEvent.class, e -> {
            log.add("join:" + e.player().name());
            conns.add(e.player().connection());
        });
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "Play.Example.com.", 25570);
            login(c, "Alex");
            Player p = awaitPlayer("Alex");
            for (int i = 0; i < 500 && log.stream().noneMatch(s -> s.startsWith("join:")); i++) Thread.sleep(20);
            assertEquals(List.of(
                    "handshake:Play.Example.com:25570:767:LOGIN:virtual=false",
                    "prelogin:Alex:virtual=true",
                    "login:Alex:ALLOWED",
                    "configure:Alex:CONFIGURATION:virtual=true",
                    "create:Alex:PLAY",
                    "spawn:minecraft:overworld:first=true",
                    "join:Alex"), log);
            PlayerConnection first = conns.get(0);
            for (PlayerConnection pc : conns) assertSame(first, pc, "모든 단계가 같은 연결 객체");
            assertSame(first, core.plugins().player(p).connection());
            assertEquals("Alex", first.name());
            assertEquals(p.uuid(), first.uniqueId());
            assertEquals(ConnectionPhase.PLAY, first.phase());
            assertTrue(first.isConnected());
            assertNotNull(first.address());
            assertTrue(first.id() > 0);
        }
    }

    @Test
    void cancelledHandshakeDisconnectsBeforeLogin() throws Exception {
        on(PlayerHandshakeEvent.class, e -> {
            if (!"allowed.host".equals(e.connection().hostname())) e.disallow(Text.text("wrong host"));
        });
        List<String> later = new CopyOnWriteArrayList<>();
        on(AsyncPlayerPreLoginEvent.class, e -> later.add(e.name()));
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "other.host", 25565);
            ByteBuf d = c.await(0x00);   // Login Disconnect
            assertTrue(Types.readString(d, 262144).contains("wrong host"));
        }
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "allowed.host", 25565);
            login(c, "Steve");
            awaitPlayer("Steve");
        }
        assertEquals(List.of("Steve"), later, "취소된 연결은 로그인 단계 이벤트까지 가지 않는다");
    }

    @Test
    void configureMayBlockThenDisallow() throws Exception {
        on(AsyncPlayerConfigureEvent.class, e -> {
            try {
                Thread.sleep(300);   // 블로킹 허용(가상 스레드) — 그동안 구성이 멈춰 있어야 한다
            } catch (InterruptedException ignored) {
            }
            if (e.connection().name().equals("Bad")) e.disallow(Text.text("no config for you"));
        });
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "localhost", 25565);
            c.write(0x00, b -> { Types.writeString(b, "Bad"); Types.writeUuid(b, new UUID(0, 0)); });
            c.await(0x02);
            c.write(0x03, null);
            c.write(0x07, b -> Types.writeVarInt(b, 0));
            // 구성 끝(0x03) 전에 구성 끊기(0x02)가 와야 한다
            while (true) {
                ByteBuf f = c.read();
                int id = Types.readVarInt(f);
                assertNotEquals(0x03, id, "거부된 연결에 구성 끝을 보내면 안 된다");
                if (id == 0x02) {   // 구성 끊기: NBT 텍스트 — 사유 문자열이 들어 있다
                    byte[] rest = new byte[f.readableBytes()];
                    f.readBytes(rest);
                    if (new String(rest, StandardCharsets.UTF_8).contains("no config for you")) break;
                }
            }
        }
        try (TestClient c = TestClient.open(port)) {   // 허용된 연결은 기다린 뒤 정상 진행
            handshake(c, "localhost", 25565);
            login(c, "Good");
            awaitPlayer("Good");
        }
    }

    @Test
    void permissionAttachedAtCreateAppearsInFirstCommandTree() throws Exception {
        core.plugins().commands().register(plugin, "lpsecret", new org.domi.furnace.api.command.BasicCommand() {
            public void execute(org.domi.furnace.api.command.CommandSender sender, String[] args) {}

            public String permission() { return "lpe.secret"; }
        });
        on(PlayerCreateEvent.class, e -> {
            if (e.player().name().equals("Granted")) e.player().addAttachment(plugin).setPermission("lpe.secret", true);
        });
        assertFalse(firstTreeHas("Plain", "lpsecret"), "권한 없으면 명령 트리에 없다");
        assertTrue(firstTreeHas("Granted", "lpsecret"), "PlayerCreateEvent 에서 붙인 권한이 첫 명령 트리부터 반영된다");
    }

    private boolean firstTreeHas(String name, String literal) throws Exception {
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "localhost", 25565);
            login(c, name);
            ByteBuf tree = c.await(CommandTreePacket.ID);
            byte[] bytes = new byte[tree.readableBytes()];
            tree.readBytes(bytes);
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
        }
    }

    @Test
    void spawnLocationCanBeChanged() throws Exception {
        on(PlayerSpawnLocationEvent.class, e -> e.spawnLocation(new Location("minecraft:overworld", 100.5, -50, -30.5, 90f, 10f)));
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "localhost", 25565);
            login(c, "Mover");
            Player p = awaitPlayer("Mover");
            for (int i = 0; i < 500 && p.x() != 100.5; i++) Thread.sleep(20);   // 위치는 소유 region 이 받아들인 뒤 보인다
            assertEquals(100.5, p.x(), 1e-6);
            assertEquals(-30.5, p.z(), 1e-6);
            assertEquals(90f, p.yaw(), 1e-6);
        }
    }

    @Test
    void spawnLocationInUnknownDimensionIsIgnored() throws Exception {
        on(PlayerSpawnLocationEvent.class, e -> e.spawnLocation(new Location("minecraft:nowhere", 1e6, 0, 1e6)));
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "localhost", 25565);
            login(c, "Stayer");
            Player p = awaitPlayer("Stayer");
            for (int i = 0; i < 500 && p.x() == 0 && p.z() == 0; i++) Thread.sleep(20);
            assertEquals("minecraft:overworld", p.dimension());
            assertTrue(Math.abs(p.x()) < 1000, "기본 위치로 들어간다: " + p.x());
        }
    }

    @Test
    void connectionDisconnectFromPluginKicksDuringConfiguration() throws Exception {
        on(AsyncPlayerConfigureEvent.class, e -> e.connection().disconnect(Text.text("bye from plugin")));
        try (TestClient c = TestClient.open(port)) {
            handshake(c, "localhost", 25565);
            c.write(0x00, b -> { Types.writeString(b, "Kicked"); Types.writeUuid(b, new UUID(0, 0)); });
            c.await(0x02);
            c.write(0x03, null);
            c.write(0x07, b -> Types.writeVarInt(b, 0));
            boolean got = false;
            try {
                while (true) {
                    ByteBuf f = c.read();
                    int id = Types.readVarInt(f);
                    if (id == 0x02) {
                        byte[] rest = new byte[f.readableBytes()];
                        f.readBytes(rest);
                        if (new String(rest, StandardCharsets.UTF_8).contains("bye from plugin")) { got = true; break; }
                    }
                }
            } catch (java.io.EOFException | java.net.SocketException closed) {
                // 닫힘
            }
            assertTrue(got, "구성 단계 끊기 패킷으로 사유를 보낸다");
        }
        assertNull(core.playerByName("Kicked"));
    }
}
