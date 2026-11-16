// 접속 순서(Paper placeNewPlayer): PlayerJoinEvent 가 접속 탭 전송보다 먼저 불리고, 이벤트 안의 변경(숨김·내보내기·게임 모드)이
// 탭에 처음부터 반영되는지 실제 소켓으로 확인한다. 본인 탭 항목이 먼저, 기존 접속자는 한 패킷으로(없으면 보내지 않음).
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.SystemChat;
import org.domi.furnace.server.plugin.PlayerHandle;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinTabOrderTest {

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
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "jto", tmp);
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

    private void onJoin(Consumer<PlayerJoinEvent> h) {
        core.plugins().eventsImpl().subscribe(plugin, PlayerJoinEvent.class, h);
    }

    private static Player server(PlayerJoinEvent e) { return ((PlayerHandle) e.player()).handle(); }

    /** 접속 탭을 보내고 보류를 푼 뒤까지 기다린다(다른 접속자의 기준 상태). */
    private Player awaitListed(String name) throws InterruptedException {
        for (int i = 0; i < 1500; i++) {
            Player p = core.playerByName(name);
            if (p != null && !p.tabListingHeld()) return p;
            Thread.sleep(10);
        }
        fail("접속 탭 전송 완료: " + name);
        return null;
    }

    private record Frame(int id, byte[] body) {
        ByteBuf buf() { return io.netty.buffer.Unpooled.wrappedBuffer(body); }
    }

    /** stopId 패킷을 받을 때까지(그것 포함) 모든 프레임. */
    private static List<Frame> readUntil(TestClient c, int stopId) throws Exception {
        List<Frame> out = new ArrayList<>();
        while (true) {
            ByteBuf f = c.read();
            int id = Types.readVarInt(f);
            byte[] body = new byte[f.readableBytes()];
            f.readBytes(body);
            out.add(new Frame(id, body));
            if (id == stopId) return out;
        }
    }

    /** add_player 가 든 탭 갱신의 UUID 목록(add_player 가 없으면 빈 목록). */
    private static List<UUID> addedUuids(Frame f) {
        if (f.id() != PlayerInfoUpdate.ID) return List.of();
        ByteBuf b = f.buf();
        int actions = b.readUnsignedByte();
        if ((actions & 0x01) == 0) return List.of();
        int n = Types.readVarInt(b);
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(Types.readUuid(b));
            Types.readString(b, 32767);   // 이름
            int props = Types.readVarInt(b);
            for (int k = 0; k < props; k++) {
                Types.readString(b, 32767);
                Types.readString(b, 32767);
                if (b.readBoolean()) Types.readString(b, 32767);
            }
            if (b.readBoolean()) return out;   // 채팅 세션은 이 시험에서 없다 — 있으면 뒤 항목은 건너뛴다
            Types.readVarInt(b);   // 게임 모드
            b.readBoolean();       // listed
            Types.readVarInt(b);   // 지연
            if (b.readBoolean()) return out;   // 표시 이름(없음)
        }
        return out;
    }

    /** 첫 add_player 항목의 게임 모드. */
    private static int firstGameMode(Frame f) {
        ByteBuf b = f.buf();
        b.readUnsignedByte();
        Types.readVarInt(b);
        Types.readUuid(b);
        Types.readString(b, 32767);
        int props = Types.readVarInt(b);
        for (int k = 0; k < props; k++) {
            Types.readString(b, 32767);
            Types.readString(b, 32767);
            if (b.readBoolean()) Types.readString(b, 32767);
        }
        assertFalse(b.readBoolean(), "채팅 세션 없음");
        return Types.readVarInt(b);
    }

    /** 관찰자가 who 를 올리는 탭 갱신을 받을 때까지 읽는다. 그 앞의 프레임을 모두 돌려준다. */
    private static List<Frame> awaitTabEntry(TestClient c, UUID who) throws Exception {
        List<Frame> out = new ArrayList<>();
        while (true) {
            List<Frame> part = readUntil(c, PlayerInfoUpdate.ID);
            out.addAll(part);
            if (addedUuids(part.get(part.size() - 1)).contains(who)) return out;
        }
    }

    /** 시스템 채팅 본문(NBT 텍스트)의 글자 — 이름이 들었는지만 본다. */
    private static String chatText(Frame f) { return new String(f.body(), java.nio.charset.StandardCharsets.UTF_8); }

    private static boolean mentions(List<Frame> frames, UUID who) {
        for (Frame f : frames) {
            if (addedUuids(f).contains(who)) return true;
            if (f.id() == SpawnEntity.ID) {
                ByteBuf b = f.buf();
                Types.readVarInt(b);
                if (Types.readUuid(b).equals(who)) return true;
            }
        }
        return false;
    }

    @Test
    void joinEventRunsBeforeTheJoinTabAndSeesThePlayerOnline() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        onJoin(e -> {
            Player p = server(e);
            seen.add("held=" + p.tabListingHeld() + ",listed=" + p.listed().size()
                    + ",online=" + core.plugins().onlinePlayers().contains(e.player()));
        });
        try (TestClient c = TestClient.join(port, "Alex")) {
            List<Frame> burst = readUntil(c, InitializeBorder.ID);
            assertEquals(List.of("held=true,listed=0,online=true"), seen, "이벤트 때 아직 탭을 안 보냈고 접속자 목록에는 있다");
            List<Frame> tabs = burst.stream().filter(f -> f.id() == PlayerInfoUpdate.ID).toList();
            assertEquals(1, tabs.size(), "혼자면 본인 항목 한 패킷뿐(빈 기존 접속자 패킷 없음 — Paper)");
            assertEquals(List.of(c.uuid), addedUuids(tabs.get(0)));
            assertFalse(awaitListed("Alex").tabListingHeld());
        }
    }

    @Test
    void selfEntryComesFirstThenEveryoneElseInOnePacket() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            awaitListed("Bob");
            try (TestClient carl = TestClient.join(port, "Carl")) {
                awaitListed("Carl");
                try (TestClient alex = TestClient.join(port, "Alex")) {
                    List<Frame> tabs = readUntil(alex, InitializeBorder.ID).stream().filter(f -> f.id() == PlayerInfoUpdate.ID).toList();
                    assertEquals(2, tabs.size());
                    assertEquals(List.of(alex.uuid), addedUuids(tabs.get(0)), "본인 먼저");
                    assertEquals(java.util.Set.of(bob.uuid, carl.uuid), java.util.Set.copyOf(addedUuids(tabs.get(1))), "기존 접속자는 한 패킷");
                    assertEquals(2, addedUuids(tabs.get(1)).size());
                }
            }
        }
    }

    @Test
    void hidingInTheJoinEventNeverLeaksATabEntry() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            Player bobP = awaitListed("Bob");
            var bobH = core.plugins().player(bobP);
            onJoin(e -> {
                if (!e.player().name().equals("Alex")) return;
                core.plugins().entityViews().hide(plugin, bobH, e.player());   // 밥에게서 알렉스를 숨긴다(투명화 플러그인 꼴)
                core.plugins().entityViews().hide(plugin, e.player(), bobH);   // 알렉스에게서 밥을 숨긴다
            });
            try (TestClient alex = TestClient.join(port, "Alex")) {
                List<Frame> tabs = readUntil(alex, InitializeBorder.ID).stream().filter(f -> f.id() == PlayerInfoUpdate.ID).toList();
                assertEquals(1, tabs.size(), "숨긴 밥은 알렉스의 접속 탭에 없다");
                assertEquals(List.of(alex.uuid), addedUuids(tabs.get(0)));
                awaitListed("Alex");
                // 대조군: 숨기지 않은 댄은 밥에게 올라간다 — 그때까지 밥이 받은 것에 알렉스가 한 번도 없어야 한다
                try (TestClient dan = TestClient.join(port, "Dan")) {
                    List<Frame> bobFrames = awaitTabEntry(bob, dan.uuid);
                    assertFalse(mentions(bobFrames, alex.uuid), "조인 이벤트에서 숨긴 알렉스가 밥의 탭·엔티티에 잠깐도 오르지 않는다");
                }
            }
        }
    }

    /**
     * 관찰자 쪽 보류(Region.syncTabList·핑 방송이 보류 중인 접속자를 건너뜀): 조인 이벤트 처리기를 붙잡아 두는 동안 관찰자 region 이 여러 틱을 돌아도
     * 관찰자는 새 접속자의 탭·엔티티를 받지 않고, 처리기를 놓으면 곧 받는다.
     */
    @Test
    void observerRegionSkipsAPlayerWhileItsJoinEventRuns() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            Player bobP = awaitListed("Bob");
            for (int i = 0; i < 500 && bobP.ownerRegion == null; i++) Thread.sleep(10);   // 밥의 region 이 밥을 받아들일 때까지
            java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            onJoin(e -> {
                if (!e.player().name().equals("Alex")) return;
                inside.countDown();
                try {
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            UUID alexId = org.domi.furnace.server.players.GameProfile.offlineUuid("Alex");
            // 접속 처리가 끝나야 로그인 패킷이 비워지므로(flush) 처리기를 붙잡는 동안 join 은 다른 스레드에서 기다린다
            var joining = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return TestClient.join(port, "Alex");
                } catch (Exception ex) {
                    throw new java.util.concurrent.CompletionException(ex);
                }
            });
            try {
                assertTrue(inside.await(10, java.util.concurrent.TimeUnit.SECONDS));
                Player alexP = core.playerByName("Alex");
                assertNotNull(alexP, "조인 이벤트 중에도 접속자 목록에 있다");
                assertTrue(alexP.tabListingHeld());
                var bobRegion = bobP.ownerRegion;
                assertNotNull(bobRegion);
                long tick0 = bobRegion.tickCount();
                // 처리기를 붙잡은 채 밥이 받는 것을 읽는다 — 그동안 밥의 region 이 여러 번 탭을 맞춘다
                List<Frame> during = new ArrayList<>();
                long deadline = System.nanoTime() + 800_000_000L;
                bob.socketTimeout(100);
                while (System.nanoTime() < deadline) {
                    try {
                        ByteBuf f = bob.read();
                        int id = Types.readVarInt(f);
                        byte[] body = new byte[f.readableBytes()];
                        f.readBytes(body);
                        during.add(new Frame(id, body));
                    } catch (java.net.SocketTimeoutException ignored) {
                        // 계속 읽는다
                    }
                }
                bob.socketTimeout(0);
                assertTrue(bobRegion.tickCount() - tick0 >= 5, "보류 동안 관찰자 region 이 여러 틱 돌았다: " + (bobRegion.tickCount() - tick0));
                assertFalse(mentions(during, alexId), "보류 중에는 관찰자 탭·엔티티에 오르지 않는다");
                release.countDown();
                awaitTabEntry(bob, alexId);   // 놓으면 다음 틱에 올라간다
            } finally {
                release.countDown();
                joining.get(20, java.util.concurrent.TimeUnit.SECONDS).close();
            }
        }
    }

    @Test
    void kickedInTheJoinEventGetsNoTabAndNobodyListsIt() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            awaitListed("Bob");
            onJoin(e -> {
                if (e.player().name().equals("Alex")) e.player().kick(Text.text("no"));
            });
            UUID alexId;
            try (TestClient alex = TestClient.join(port, "Alex")) {
                alexId = alex.uuid;
                List<Frame> burst = readUntil(alex, Disconnect.ID);
                assertTrue(burst.stream().noneMatch(f -> f.id() == PlayerInfoUpdate.ID || f.id() == InitializeBorder.ID),
                        "CraftBukkit: 조인 이벤트에서 내보내면 탭·레벨 정보를 보내지 않는다");
            }
            for (int i = 0; i < 500 && core.playerByName("Alex") != null; i++) Thread.sleep(10);
            assertNull(core.playerByName("Alex"));
            try (TestClient dan = TestClient.join(port, "Dan")) {
                List<Frame> bobFrames = awaitTabEntry(bob, dan.uuid);
                assertFalse(mentions(bobFrames, alexId), "내보낸 알렉스는 밥의 탭에 오르지 않는다");
                // 댄의 접속 알림까지 읽고, 그 앞에 알렉스의 접속 알림이 없었는지 본다(Paper: 내보내기를 확인한 뒤에 방송)
                List<Frame> chats = new ArrayList<>(bobFrames);
                while (chats.stream().noneMatch(f -> f.id() == SystemChat.ID && chatText(f).contains("Dan"))) {
                    chats.addAll(readUntil(bob, SystemChat.ID));
                }
                // 퇴장 알림(multiplayer.player.left)은 Paper 도 보낸다(PlayerList.remove 의 PlayerQuitEvent) — 접속 알림만 본다
                assertTrue(chats.stream().filter(f -> f.id() == SystemChat.ID)
                                .noneMatch(f -> chatText(f).contains("multiplayer.player.joined") && chatText(f).contains("Alex")),
                        "조인 이벤트에서 내보낸 알렉스의 접속 알림은 방송하지 않는다");
            }
        }
    }

    @Test
    void observersGetTheGameModeSetInTheJoinEvent() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            awaitListed("Bob");
            onJoin(e -> {
                if (e.player().name().equals("Alex")) e.player().setGameMode(GameMode.SPECTATOR);
            });
            try (TestClient alex = TestClient.join(port, "Alex")) {
                List<Frame> tabs = readUntil(alex, InitializeBorder.ID).stream().filter(f -> f.id() == PlayerInfoUpdate.ID).toList();
                assertEquals(GameMode.SPECTATOR.id(), firstGameMode(tabs.get(0)), "본인 항목도 이벤트 뒤의 게임 모드");
                List<Frame> bobFrames = awaitTabEntry(bob, alex.uuid);
                Frame entry = bobFrames.get(bobFrames.size() - 1);
                assertEquals(List.of(alex.uuid), addedUuids(entry));
                assertEquals(GameMode.SPECTATOR.id(), firstGameMode(entry), "밥이 처음 받은 알렉스 항목이 이미 관전자다");
            }
        }
    }
}
