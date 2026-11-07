// N1: 구성 단계에서 플러그인이 리소스 팩·쿠키·전송을 주고받는 API(PlayerConnection)와 연결이 끊길 때의 정리, 끊긴 이유(QuitReason).
// 실제 소켓으로 AsyncPlayerConfigureEvent(가상 스레드)에서 Future 를 기다리게 하고, 클라이언트가 답하는 순서를 확인한다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.player.AsyncPlayerConfigureEvent;
import org.domi.furnace.api.event.player.PlayerQuitEvent;
import org.domi.furnace.api.net.ConnectionClosedException;
import org.domi.furnace.api.net.PlayerConnection;
import org.domi.furnace.api.net.QuitReason;
import org.domi.furnace.api.net.ResourcePack;
import org.domi.furnace.api.net.ResourcePackStatus;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionStateApiTest {

    private static final UUID PACK = new UUID(7, 8);

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
            final PluginDescription d = new PluginDescription("csa", "1", "x", "", List.of(), null, null, List.of(), List.of(), List.of(),
                    null, java.util.Map.of(), List.of(), null);
            public PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("csa"); }
            public Path configFolder() { return tmp.resolve("csa"); }
            public Path dataFolder() { return tmp.resolve("csa"); }
            public Path file() { return tmp.resolve("csa.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private <E extends Event> void on(Class<E> type, Consumer<? super E> h) {
        core.plugins().eventsImpl().subscribe(plugin, type, h);
    }

    /** 핸드셰이크 → 로그인 시작 → 로그인 확인 → Known Packs 응답까지(구성 이벤트가 불리는 지점). */
    private void toConfigure(TestClient c, String name) throws Exception {
        c.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
        c.write(0x00, b -> { Types.writeString(b, name); Types.writeUuid(b, new UUID(0, 0)); });
        c.await(0x02);
        c.write(0x03, null);
        c.write(0x07, b -> Types.writeVarInt(b, 0));
    }

    private static void toPlay(TestClient c) throws Exception {
        c.await(0x03);        // Finish Configuration
        c.write(0x03, null);  // ack → 플레이
        c.await(LoginPlay.ID);
    }

    private static void packResponse(TestClient c, UUID id, int action) throws Exception {
        c.write(0x06, b -> { Types.writeUuid(b, id); Types.writeVarInt(b, action); });
    }

    private Player awaitPlayer(String name) throws InterruptedException {
        for (int i = 0; i < 500 && core.playerByName(name) == null; i++) Thread.sleep(20);
        Player p = core.playerByName(name);
        assertNotNull(p, "접속 처리 완료: " + name);
        return p;
    }

    /** 플러그인 스레드가 값을 넣을 때까지 기다린다. */
    private static <T> T awaitValue(AtomicReference<T> ref) throws InterruptedException {
        for (int i = 0; i < 500 && ref.get() == null; i++) Thread.sleep(20);
        return ref.get();
    }

    private static boolean contains(ByteBuf body, String text) {
        byte[] rest = new byte[body.readableBytes()];
        body.duplicate().readBytes(rest);
        return new String(rest, StandardCharsets.UTF_8).contains(text);
    }

    // ---------------------------------------------------------------- 리소스 팩

    @Test
    void pluginPackBlocksConfigurationUntilTerminalResponse() throws Exception {
        AtomicReference<CompletableFuture<ResourcePackStatus>> fut = new AtomicReference<>();
        AtomicReference<Boolean> virtual = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            virtual.set(Thread.currentThread().isVirtual());
            var f = e.connection().sendResourcePack(new ResourcePack(PACK, "https://example.invalid/a.zip", "", false, Text.text("hello")));
            fut.set(f);
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Packer");
            ByteBuf push = c.await(0x09);                 // add_resource_pack(구성)
            assertEquals(PACK, Types.readUuid(push));
            assertEquals("https://example.invalid/a.zip", Types.readString(push, 32767));
            packResponse(c, PACK, 3);                      // ACCEPTED — 중간 상태
            packResponse(c, PACK, 4);                      // DOWNLOADED — 중간 상태
            Thread.sleep(300);
            assertFalse(fut.get().isDone(), "중간 상태에서는 Future 가 끝나지 않는다");
            packResponse(c, PACK, 0);                      // SUCCESSFULLY_LOADED
            toPlay(c);
            assertEquals(ResourcePackStatus.SUCCESSFULLY_LOADED, fut.get().get(5, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, virtual.get(), "가상 스레드에서 기다린다(네트워크 스레드를 막지 않는다)");
        }
        awaitPlayer("Packer");
    }

    @Test
    void declinedRequiredPluginPackKicksButOptionalDoesNot() throws Exception {
        AtomicReference<ResourcePackStatus> got = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            boolean required = e.connection().name().equals("Strict");
            try {
                got.set(e.connection().sendResourcePack(new ResourcePack(PACK, "https://example.invalid/a.zip", "", required)).get(10, TimeUnit.SECONDS));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Strict");
            c.await(0x09);
            packResponse(c, PACK, 1);                      // DECLINED
            ByteBuf d = c.await(0x02, b -> contains(b, "requiredTexturePrompt"));   // 구성 끊기
            assertNotNull(d);
        }
        assertEquals(ResourcePackStatus.DECLINED, awaitValue(got));
        assertNull(core.playerByName("Strict"));
        got.set(null);
        try (TestClient c = TestClient.open(port)) {   // 필수가 아니면 거절해도 들어온다
            toConfigure(c, "Relaxed");
            c.await(0x09);
            packResponse(c, PACK, 1);
            toPlay(c);
            // 연결을 닫기 전에 본다 — 닫은 뒤면 서버가 먼저 나간 처리를 해 목록에서 빠질 수 있다(부하에서 흔들림)
            awaitPlayer("Relaxed");
        }
        assertEquals(ResourcePackStatus.DECLINED, awaitValue(got));
    }

    @Test
    void responseToPluginPackDoesNotAdvanceServerPackWait() throws Exception {
        // 서버 리소스 팩(server.properties)이 없는 서버: 플러그인 팩 응답이 구성을 끝내 버리면 안 된다(아직 이벤트 처리기가 기다린다).
        CompletableFuture<Void> release = new CompletableFuture<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            e.connection().sendResourcePack(new ResourcePack(PACK, "https://example.invalid/a.zip", "", false));
            release.join();
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Waiter");
            c.await(0x09);
            packResponse(c, PACK, 0);
            c.socketTimeout(400);
            assertThrows(java.net.SocketTimeoutException.class, () -> c.await(0x03), "처리기가 돌아오기 전에는 구성 끝이 오지 않는다");
            c.socketTimeout(0);
            release.complete(null);
            toPlay(c);
        }
    }

    @Test
    void removeResourcePackSendsPopAndDiscardsWaiter() throws Exception {
        AtomicReference<ResourcePackStatus> got = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            var f = e.connection().sendResourcePack(new ResourcePack(PACK, "https://example.invalid/a.zip", "", false));
            e.connection().removeResourcePack(PACK);
            try { got.set(f.get(10, TimeUnit.SECONDS)); } catch (Exception ex) { throw new RuntimeException(ex); }
            e.connection().removeAllResourcePacks();
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Popper");
            c.await(0x09);
            ByteBuf pop = c.await(0x08);                   // remove_resource_pack(구성)
            assertTrue(pop.readBoolean());
            assertEquals(PACK, Types.readUuid(pop));
            ByteBuf popAll = c.await(0x08);
            assertFalse(popAll.readBoolean(), "id 없이 부르면 전부");
            toPlay(c);
        }
        assertEquals(ResourcePackStatus.DISCARDED, awaitValue(got));
    }

    // ---------------------------------------------------------------- 쿠키·전송

    @Test
    void cookieRoundTripAndStore() throws Exception {
        AtomicReference<byte[]> have = new AtomicReference<>();
        AtomicReference<byte[]> missing = new AtomicReference<>(new byte[]{1});
        on(AsyncPlayerConfigureEvent.class, e -> {
            try {
                have.set(e.connection().requestCookie("test:session").get(10, TimeUnit.SECONDS));
                missing.set(e.connection().requestCookie("test:none").get(10, TimeUnit.SECONDS));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            e.connection().storeCookie("test:seen", "ok".getBytes(StandardCharsets.UTF_8));
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Cookie");
            c.write(0x01, b -> { Types.writeString(b, "test:unasked"); b.writeBoolean(false); });   // 요청한 적 없는 응답은 무시된다
            ByteBuf req = c.await(0x00);                  // cookie_request
            assertEquals("test:session", Types.readString(req, 32767));
            c.write(0x01, b -> { Types.writeString(b, "test:session"); b.writeBoolean(true); Types.writeVarInt(b, 3); b.writeBytes(new byte[]{9, 8, 7}); });
            req = c.await(0x00);
            assertEquals("test:none", Types.readString(req, 32767));
            c.write(0x01, b -> { Types.writeString(b, "test:none"); b.writeBoolean(false); });
            ByteBuf store = c.await(0x0A);                // store_cookie
            assertEquals("test:seen", Types.readString(store, 32767));
            assertEquals(2, Types.readVarInt(store));
            toPlay(c);
        }
        assertArrayEquals(new byte[]{9, 8, 7}, awaitValue(have));
        for (int i = 0; i < 500 && missing.get() != null; i++) Thread.sleep(20);
        assertNull(missing.get(), "없는 쿠키는 null");
    }

    @Test
    void cookieArgumentsAreValidated() throws Exception {
        AtomicReference<Throwable> bad = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            try { e.connection().requestCookie("NoColon"); } catch (IllegalArgumentException ex) { bad.set(ex); }
            assertThrows(IllegalArgumentException.class, () -> e.connection().storeCookie("test:big", new byte[5121]));
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Valid");
            toPlay(c);
        }
        assertInstanceOf(IllegalArgumentException.class, bad.get());
    }

    @Test
    void transferDuringConfiguration() throws Exception {
        on(AsyncPlayerConfigureEvent.class, e -> e.connection().transfer("example.org", 25566));
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Mover");
            ByteBuf t = c.await(0x0B);                    // transfer(구성)
            assertEquals("example.org", Types.readString(t, 32767));
            assertEquals(25566, Types.readVarInt(t));
        }
    }

    @Test
    void configOnlyCallsFailOutsideConfiguration() throws Exception {
        AtomicReference<PlayerConnection> conn = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> conn.set(e.connection()));
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Late");
            toPlay(c);
            awaitPlayer("Late");
            var f = conn.get().requestCookie("test:x");
            assertTrue(f.isCompletedExceptionally());
            assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, f::get).getCause());
            assertThrows(IllegalStateException.class, () -> conn.get().storeCookie("test:x", new byte[0]));
        }
    }

    // ---------------------------------------------------------------- 끊김 정리(스레드가 매달리지 않는다)

    @Test
    void disconnectReleasesWaitingFutureAndBlockedHandler() throws Exception {
        AtomicReference<Throwable> futureError = new AtomicReference<>();
        CountDownLatch futureHandler = new CountDownLatch(1);
        CountDownLatch sleeperInterrupted = new CountDownLatch(1);
        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(AsyncPlayerConfigureEvent.class, e -> {
            // 핸들러 하나가 쿠키 응답을 기다리고(Future 가 예외로 끝남), 다음 핸들러는 그냥 오래 잔다(인터럽트로 깨어난다)
            try {
                e.connection().requestCookie("test:never").get();
            } catch (ExecutionException ex) {
                futureError.set(ex.getCause());
            } catch (InterruptedException ex) {
                futureError.set(ex);
            } finally {
                futureHandler.countDown();
            }
        });
        on(AsyncPlayerConfigureEvent.class, e -> {
            seen.add("second");
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ex) {
                sleeperInterrupted.countDown();
            }
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Quitter");
            c.await(0x00);                                // 쿠키 요청이 나갔다 — 기다리는 중
        }                                                 // 클라이언트가 끊는다
        assertTrue(futureHandler.await(10, TimeUnit.SECONDS), "끊기면 기다리던 처리기가 풀려난다");
        assertInstanceOf(ConnectionClosedException.class, futureError.get());
        // 첫 처리기가 끝난 뒤 둘째 처리기가 돌 수도(이미 끊긴 뒤라 건너뛸 수도) 있다 — 돌았다면 인터럽트로 깨어나야 한다
        if (!seen.isEmpty()) assertTrue(sleeperInterrupted.await(10, TimeUnit.SECONDS), "잠자던 처리기가 인터럽트로 깨어난다");
    }

    @Test
    void blockedHandlerWokenByDisconnect() throws Exception {
        CountDownLatch woke = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        on(AsyncPlayerConfigureEvent.class, e -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ex) {
                woke.countDown();
            }
        });
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Sleeper");
            assertTrue(started.await(10, TimeUnit.SECONDS));
        }
        assertTrue(woke.await(10, TimeUnit.SECONDS), "연결이 끊긴 뒤에도 안 돌아오는 구성 처리기는 인터럽트로 풀려난다");
    }

    // ---------------------------------------------------------------- 끊긴 이유

    private final ConcurrentHashMap<String, QuitReason> quits = new ConcurrentHashMap<>();

    private QuitReason awaitQuit(String name) throws InterruptedException {
        for (int i = 0; i < 500 && !quits.containsKey(name); i++) Thread.sleep(20);
        return quits.get(name);
    }

    @Test
    void quitReasonForClientLeaving() throws Exception {
        on(PlayerQuitEvent.class, e -> quits.put(e.player().name(), e.reason()));
        PlayerConnection conn;
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Leaver");
            toPlay(c);
            conn = core.plugins().player(awaitPlayer("Leaver")).connection();
            assertNull(conn.quitReason(), "접속 중에는 null");
        }
        assertEquals(QuitReason.DISCONNECTED, awaitQuit("Leaver"));
        assertEquals(QuitReason.DISCONNECTED, conn.quitReason());
    }

    @Test
    void quitReasonForKick() throws Exception {
        on(PlayerQuitEvent.class, e -> quits.put(e.player().name(), e.reason()));
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Kicked");
            toPlay(c);
            core.plugins().player(awaitPlayer("Kicked")).kick(Text.text("bye"));
            assertEquals(QuitReason.KICKED, awaitQuit("Kicked"));
        }
    }

    @Test
    void quitReasonForProtocolViolation() throws Exception {
        on(PlayerQuitEvent.class, e -> quits.put(e.player().name(), e.reason()));
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Broken");
            toPlay(c);
            awaitPlayer("Broken");
            c.write(0x14, b -> { Types.writeVarInt(b, 0); Types.writeVarInt(b, 500); });   // edit_book 쪽 수가 범위 밖 → 프로토콜 오류
            assertEquals(QuitReason.ERRONEOUS_STATE, awaitQuit("Broken"));
        }
    }

    @Test
    void quitReasonBeforePlayIsOnTheConnection() throws Exception {
        AtomicReference<PlayerConnection> conn = new AtomicReference<>();
        on(AsyncPlayerConfigureEvent.class, e -> conn.set(e.connection()));
        try (TestClient c = TestClient.open(port)) {
            toConfigure(c, "Early");
            for (int i = 0; i < 500 && conn.get() == null; i++) Thread.sleep(20);
            assertNotNull(conn.get());
        }                                                 // 플레이에 들어가기 전에 나감 — PlayerQuitEvent 는 없지만 이유는 남는다
        for (int i = 0; i < 500 && conn.get().isConnected(); i++) Thread.sleep(20);
        assertEquals(QuitReason.DISCONNECTED, conn.get().quitReason());
    }

    // 소켓 없이 처리기 하나만 세워서 끊김 원인별 이유를 확인한다(읽기 타임아웃·서버 종료·잘못된 프레임).
    private static ConnectionHandler handler(io.netty.channel.embedded.EmbeddedChannel[] out) {
        var ch = new org.domi.furnace.server.ConcurrentEmbeddedChannel();
        ch.pipeline().addLast("frameDecoder", new org.domi.furnace.server.net.io.FrameDecoder());
        var h = new ConnectionHandler("t", -1, null, null, null);
        ch.pipeline().addLast("handler", h);
        out[0] = ch;
        return h;
    }

    @Test
    void quitReasonForReadTimeout() {
        var ch = new io.netty.channel.embedded.EmbeddedChannel[1];
        var h = handler(ch);
        ch[0].pipeline().fireExceptionCaught(io.netty.handler.timeout.ReadTimeoutException.INSTANCE);
        ch[0].runPendingTasks();
        assertEquals(QuitReason.TIMED_OUT, h.quitReasonOrNull());
        ch[0].finishAndReleaseAll();
    }

    @Test
    void quitReasonForServerShutdown() {
        var ch = new io.netty.channel.embedded.EmbeddedChannel[1];
        var h = handler(ch);
        ch[0].writeInbound(handshakeFrame());           // ctx 가 잡히려면 패킷이 한 번 지나가야 한다
        h.kickForShutdown();
        ch[0].runPendingTasks();
        assertEquals(QuitReason.SERVER_SHUTDOWN, h.quitReasonOrNull());
        ch[0].finishAndReleaseAll();
    }

    @Test
    void quitReasonForOversizedFrame() {
        var ch = new io.netty.channel.embedded.EmbeddedChannel[1];
        var h = handler(ch);
        ch[0].pipeline().fireExceptionCaught(new IllegalStateException("bad"));
        ch[0].runPendingTasks();
        assertEquals(QuitReason.ERRONEOUS_STATE, h.quitReasonOrNull());
        ch[0].finishAndReleaseAll();
    }

    private static ByteBuf handshakeFrame() {
        ByteBuf body = io.netty.buffer.Unpooled.buffer();
        Types.writeVarInt(body, 0x00);
        Types.writeVarInt(body, 767);
        Types.writeString(body, "localhost");
        body.writeShort(25565);
        Types.writeVarInt(body, 1);
        ByteBuf frame = io.netty.buffer.Unpooled.buffer();
        Types.writeVarInt(frame, body.readableBytes());
        frame.writeBytes(body);
        return frame;
    }
}
