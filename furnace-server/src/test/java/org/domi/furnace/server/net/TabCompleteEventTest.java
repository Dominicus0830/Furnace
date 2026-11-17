// AsyncTabCompleteEvent 의 서버 쪽 계약: 구독자가 없으면 서버가 후보를 만들고, handled 면 처리기 후보만 보내고, 취소·빈 handled 면 응답이 없다
// (Paper handleCustomCommandSuggestions0). 취소 계약 점검(CancelContractTest)이 가리키는 증명 시험.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.server.AsyncTabCompleteEvent;
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

class TabCompleteEventTest {

    private static final int SUGGEST_REQUEST = 0x0B, SUGGEST_RESPONSE = 0x10;

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
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "tce", tmp);
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

    private static void suggest(TestClient c, int id, String text) throws Exception {
        c.write(SUGGEST_REQUEST, b -> { Types.writeVarInt(b, id); Types.writeString(b, text); });
    }

    /** 응답 패킷 하나의 (요청 번호, 후보 글자들). */
    private static List<Object> reply(ByteBuf body) {
        int id = Types.readVarInt(body);
        Types.readVarInt(body);   // start
        Types.readVarInt(body);   // length
        int n = Types.readVarInt(body);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            texts.add(Types.readString(body, 32767));
            if (body.readBoolean()) throw new AssertionError("설명 글은 쓰지 않는다");
        }
        return List.of(id, texts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handledCancelledAndFallbackFollowPaper() throws Exception {
        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(AsyncTabCompleteEvent.class, e -> {
            seen.add(e.buffer() + "|" + e.isCommand());
            switch (e.buffer()) {
                case "/cancel" -> e.setCancelled(true);
                case "/both" -> { e.completions().add(new AsyncTabCompleteEvent.Completion("x")); e.setCancelled(true); }
                case "/empty" -> e.setHandled(true);
                case "/mine" -> { e.completions().add(new AsyncTabCompleteEvent.Completion("beta")); e.completions().add(new AsyncTabCompleteEvent.Completion("12")); }
                default -> { }
            }
        });
        try (TestClient c = TestClient.join(port, "Dana")) {
            suggest(c, 1, "/cancel");
            suggest(c, 2, "/both");
            suggest(c, 3, "/empty");
            suggest(c, 4, "/mine");
            suggest(c, 5, "/lis");
            // 응답은 요청 순서가 아닐 수 있다(전용 스레드) — 4 와 5 만 와야 한다
            List<Object> a = reply(c.await(SUGGEST_RESPONSE));
            List<Object> b = reply(c.await(SUGGEST_RESPONSE));
            var byId = new java.util.TreeMap<Integer, List<String>>();
            byId.put((Integer) a.get(0), (List<String>) a.get(1));
            byId.put((Integer) b.get(0), (List<String>) b.get(1));
            assertEquals(java.util.Set.of(4, 5), byId.keySet(), "취소·빈 handled 요청에는 응답이 없다");
            assertTrue(byId.get(4).containsAll(List.of("beta", "12")) && byId.get(4).size() == 2, byId.get(4).toString());
            assertTrue(byId.get(5).contains("list"), "처리기가 건드리지 않으면 서버가 평소처럼 후보를 만든다: " + byId.get(5));
            c.socketTimeout(400);
            assertThrows(java.net.SocketTimeoutException.class, () -> c.await(SUGGEST_RESPONSE), "그 밖의 응답은 없다");
        }
        assertEquals(5, seen.size());
    }
}
