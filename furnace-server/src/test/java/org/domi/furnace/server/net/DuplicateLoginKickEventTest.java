// 같은 계정 두 번 접속(실제 소켓): 옛 세션을 쫓아낼 때 PlayerKickEvent(DUPLICATE_LOGIN)가 나고(전역 스레드),
// 플러그인이 취소하면 새 접속은 로그인을 마치지 않고 끊겨 같은 계정이 이중 접속되지 않는다. 취소하지 않으면 옛 세션이 끊기고 새 접속이 들어온다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.event.player.PlayerKickEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuplicateLoginKickEventTest {

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
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "dup", tmp);
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

    private static void startLogin(TestClient c, String name) throws Exception {
        c.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
        c.write(0x00, b -> { Types.writeString(b, name); Types.writeUuid(b, new UUID(0, 0)); });
    }

    @Test
    void duplicateLoginFiresKickEventAndReplacesTheOldSession() throws Exception {
        List<PlayerKickEvent.Cause> causes = new CopyOnWriteArrayList<>();
        List<String> threads = new CopyOnWriteArrayList<>();
        core.plugins().eventsImpl().subscribe(plugin, PlayerKickEvent.class, e -> { causes.add(e.cause()); threads.add(Thread.currentThread().getName()); });
        try (TestClient first = TestClient.join(port, "Twin"); TestClient second = TestClient.open(port)) {
            startLogin(second, "Twin");
            second.await(0x02);                                  // 새 접속은 Login Success 까지 간다
            first.await(0x1D);                                   // 옛 세션은 Disconnect 를 받는다
            assertEquals(List.of(PlayerKickEvent.Cause.DUPLICATE_LOGIN), causes);
            assertFalse(threads.get(0).startsWith("nioEventLoop") || threads.get(0).contains("epoll"), "이벤트는 네트워크 스레드가 아니라 전역 스레드에서: " + threads.get(0));
        }
    }

    /** 바닐라 WAITING_FOR_DUPE_DISCONNECT: 옛 플레이어의 퇴장 처리(PlayerQuitEvent)가 끝난 뒤에야 새 접속이 Login Success 를 받는다. */
    @Test
    void newLoginWaitsForTheOldPlayerToFinishLeaving() throws Exception {
        java.util.concurrent.atomic.AtomicLong quitFinished = new java.util.concurrent.atomic.AtomicLong();
        core.plugins().eventsImpl().subscribe(plugin, org.domi.furnace.api.event.player.PlayerQuitEvent.class, e -> {
            try { Thread.sleep(400); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            quitFinished.set(System.nanoTime());
        });
        try (TestClient first = TestClient.join(port, "Twin"); TestClient second = TestClient.open(port)) {
            startLogin(second, "Twin");
            second.await(0x02);
            long gotSuccess = System.nanoTime();
            assertNotEquals(0L, quitFinished.get(), "옛 플레이어 퇴장 처리가 끝나기 전에 새 접속이 로그인을 마쳤다");
            assertTrue(gotSuccess >= quitFinished.get());
        }
    }

    @Test
    void cancelledDuplicateKickKeepsOldSessionAndRejectsTheNewLogin() throws Exception {
        core.plugins().eventsImpl().subscribe(plugin, PlayerKickEvent.class, e -> {
            if (e.cause() == PlayerKickEvent.Cause.DUPLICATE_LOGIN) e.setCancelled(true);
        });
        try (TestClient first = TestClient.join(port, "Twin"); TestClient second = TestClient.open(port)) {
            startLogin(second, "Twin");
            // 새 접속은 Login Success(0x02)가 아니라 Login Disconnect(0x00)를 받는다
            int id;
            do {
                ByteBuf f = second.read();
                id = Types.readVarInt(f);
                assertNotEquals(0x02, id, "취소했는데 새 접속이 로그인을 마쳤다 — 같은 계정 이중 접속");
            } while (id != 0x00);
        }
    }
}
