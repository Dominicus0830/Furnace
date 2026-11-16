// T-141 E11c-2b: Player#profile(ProfileData)(Paper setPlayerProfile)를 실제 소켓으로 본다 — 관찰자 재등록 패킷(엔티티 UUID 로 빼고 새 프로필 id 로
// 올린 뒤 엔티티 UUID 로 스폰), 본인 화면 갱신(refreshPlayer) 순서, 조인 이벤트 안 호출, 다른 region 관찰자(탭만), 숨긴 관찰자, 연속 호출,
// 퇴장 경합, region 이동 중 호출, PlayerShowEntityEvent·PlayerHideEntityEvent.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.domi.furnace.api.entity.ProfileData;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.event.player.PlayerHideEntityEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.event.player.PlayerRespawnEvent;
import org.domi.furnace.api.event.player.PlayerShowEntityEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.PlayerAbilities;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetExperience;
import org.domi.furnace.server.net.protocol.play.SetHealth;
import org.domi.furnace.server.net.protocol.play.SetHeldSlot;
import org.domi.furnace.server.net.protocol.play.SetTime;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.net.protocol.play.TickingState;
import org.domi.furnace.server.net.protocol.play.TickingStep;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileChangeTest {

    private static final List<ProfileProperty> SKIN = List.of(new ProfileProperty("textures", "c2tpbg==", "c2ln"));

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;
    private final FurnacePlugin plugin = new FurnacePlugin() {};
    private final List<String> shown = new CopyOnWriteArrayList<>();
    private final List<String> hidden = new CopyOnWriteArrayList<>();
    private final List<String> respawns = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, 2);   // region 작업 스레드 둘 — 한 region 을 붙잡아도 다른 region 이 돈다(퇴장 경합 시험)
        core.world().startTicking();
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "pchange", tmp);
        plugin.setEnabled(true);
        var bus = core.plugins().eventsImpl();
        bus.subscribe(plugin, PlayerShowEntityEvent.class, e -> shown.add(e.player().name() + ">" + e.entity().uniqueId()));
        bus.subscribe(plugin, PlayerHideEntityEvent.class, e -> hidden.add(e.player().name() + ">" + e.entity().uniqueId()));
        bus.subscribe(plugin, PlayerRespawnEvent.class, e -> respawns.add(e.player().name()));
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        org.domi.furnace.server.entity.view.EntityViewRules.clearForTest();
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    // ------------------------------------------------------------------ 도우미

    private record Frame(int id, byte[] body) {
        ByteBuf buf() { return io.netty.buffer.Unpooled.wrappedBuffer(body); }
    }

    /** add_player 항목(UUID, 이름, 속성 값들). */
    private record Added(UUID uuid, String name, List<String> props) {}

    private Player awaitListed(UUID id) throws InterruptedException {
        for (int i = 0; i < 1500; i++) {
            Player p = core.playerByUuid(id);
            if (p != null && !p.tabListingHeld() && p.ownerRegion != null) return p;
            Thread.sleep(10);
        }
        fail("접속 완료: " + id);
        return null;
    }

    /** done 이 참이 될 때까지(또는 기한까지) 읽은 프레임. */
    private static List<Frame> readWhile(TestClient c, Predicate<List<Frame>> done, long millis) throws Exception {
        List<Frame> out = new ArrayList<>();
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < deadline && !done.test(out)) {
            ByteBuf f = c.poll(50);
            if (f == null) continue;
            int id = Types.readVarInt(f);
            byte[] body = new byte[f.readableBytes()];
            f.readBytes(body);
            out.add(new Frame(id, body));
        }
        return out;
    }

    private static List<Added> added(Frame f) {
        if (f.id() != PlayerInfoUpdate.ID) return List.of();
        ByteBuf b = f.buf();
        int actions = b.readUnsignedByte();
        if ((actions & 0x01) == 0) return List.of();
        int n = Types.readVarInt(b);
        List<Added> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID id = Types.readUuid(b);
            String name = Types.readString(b, 32767);
            int props = Types.readVarInt(b);
            List<String> values = new ArrayList<>();
            for (int k = 0; k < props; k++) {
                Types.readString(b, 32767);
                values.add(Types.readString(b, 32767));
                if (b.readBoolean()) Types.readString(b, 32767);
            }
            out.add(new Added(id, name, values));
            if (b.readBoolean()) return out;   // 채팅 세션(이 시험에서는 없음)
            Types.readVarInt(b);
            b.readBoolean();
            Types.readVarInt(b);
            if (b.readBoolean()) return out;
        }
        return out;
    }

    private static List<UUID> removedUuids(Frame f) {
        if (f.id() != PlayerInfoRemove.ID) return List.of();
        ByteBuf b = f.buf();
        int n = Types.readVarInt(b);
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Types.readUuid(b));
        return out;
    }

    private static List<Integer> removedEntities(Frame f) {
        if (f.id() != RemoveEntities.ID) return List.of();
        ByteBuf b = f.buf();
        int n = Types.readVarInt(b);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Types.readVarInt(b));
        return out;
    }

    /** 스폰 패킷의 (엔티티 번호, UUID). 스폰이 아니면 null. */
    private static Object[] spawn(Frame f) {
        if (f.id() != SpawnEntity.ID) return null;
        ByteBuf b = f.buf();
        return new Object[] {Types.readVarInt(b), Types.readUuid(b)};
    }

    /** 이 프레임들 가운데 그 플레이어(엔티티 번호·UUID 들)에 관한 것만, 사람이 읽을 꼴로. */
    private static List<String> about(List<Frame> frames, int eid, UUID... ids) {
        List<UUID> mine = List.of(ids);
        List<String> out = new ArrayList<>();
        for (Frame f : frames) {
            if (removedEntities(f).contains(eid)) out.add("remove_entity");
            for (UUID u : removedUuids(f)) if (mine.contains(u)) out.add("info_remove " + u);
            for (Added a : added(f)) if (mine.contains(a.uuid())) out.add("info_add " + a.uuid() + " " + a.name() + " " + a.props());
            Object[] s = spawn(f);
            if (s != null && (int) s[0] == eid) out.add("spawn " + s[1]);
        }
        return out;
    }

    private static boolean hasSpawn(List<Frame> frames, int eid) {
        return frames.stream().anyMatch(f -> spawn(f) != null && (int) spawn(f)[0] == eid);
    }

    private void change(Player p, UUID id, String name, List<ProfileProperty> props) {
        core.plugins().player(p).profile(new ProfileData(id, name, props));
    }

    /** 두 사람이 서로 보이게(관찰자가 대상의 엔티티를 스폰할 때까지). */
    private static void awaitSpawned(TestClient observer, int eid) throws Exception {
        List<Frame> f = readWhile(observer, fr -> hasSpawn(fr, eid), 15_000);
        assertTrue(hasSpawn(f, eid), "관찰자가 대상 엔티티를 받았다");
    }

    // ------------------------------------------------------------------ 시험

    /** UUID 그대로(스킨·이름만 바꿈, 가장 흔한 쓰임): Paper 와 같은 패킷 — 관찰자 [엔티티 제거, 탭 제거, 탭 추가(새 이름·스킨), 스폰], 본인 [탭 제거, 탭 추가, 화면 갱신]. */
    @Test
    void sameUuidReregistersLikePaperAndRefreshesTheSelfScreen() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob"); TestClient alex = TestClient.join(port, "Alex")) {
            Player bobP = awaitListed(bob.uuid);
            Player alexP = awaitListed(alex.uuid);
            int eid = alexP.entityId();
            awaitSpawned(bob, eid);
            // 최대 체력에 수정자를 걸어 둔다 — updateScaledHealth 가 그것을 수정자 없는 값으로 바꿔 끝에 붙이는지 가른다. op 4 는 권한 이벤트 값
            var maxHealthAttr = org.domi.furnace.server.attribute.Attribute.MAX_HEALTH;
            java.util.concurrent.CompletableFuture<Void> buffed = new java.util.concurrent.CompletableFuture<>();
            alexP.runOnOwner(() -> {
                alexP.attributes().get(maxHealthAttr).addOrReplaceModifier(new org.domi.furnace.server.attribute.AttributeModifier("furnace:profile_test", 4.0, 0));
                buffed.complete(null);
            });
            buffed.get(5, java.util.concurrent.TimeUnit.SECONDS);
            core.lists().op(new org.domi.furnace.server.players.GameProfile(alex.uuid, "Alex"), 4);
            readWhile(alex, fr -> false, 300);   // 접속 직후 몰린 것을 비운다
            float health = alexP.vitals().health();

            change(alexP, alex.uuid, "Alex2", SKIN);

            List<Frame> bf = readWhile(bob, fr -> hasSpawn(fr, eid), 5_000);
            assertEquals(List.of("remove_entity", "info_remove " + alex.uuid, "info_add " + alex.uuid + " Alex2 [c2tpbg==]", "spawn " + alex.uuid),
                    about(bf, eid, alex.uuid));
            List<Frame> af = readWhile(alex, fr -> fr.stream().anyMatch(f -> f.id() == SetExperience.ID), 5_000);
            assertEquals(List.of("info_remove " + alex.uuid, "info_add " + alex.uuid + " Alex2 [c2tpbg==]"), about(af, eid, alex.uuid),
                    "본인도 탭을 빼고 다시 올린다(엔티티는 자기 자신이라 없음)");
            // refreshPlayer 묶음은 연결 스레드 작업 하나로 이어서 나간다
            int start = -1;
            for (int i = 0; i < af.size(); i++) if (af.get(i).id() == Respawn.ID) { start = i; break; }
            assertTrue(start > 0, "리스폰(같은 차원)이 탭 갱신 뒤에 온다");
            int lastInfo = -1;
            for (int i = 0; i < af.size(); i++) if (af.get(i).id() == PlayerInfoUpdate.ID) lastInfo = i;
            assertTrue(lastInfo < start, "본인 탭 Remove·Update 가 리스폰보다 먼저(설계 §5)");
            List<Integer> ids = af.subList(start, af.size()).stream().map(Frame::id).toList();
            assertEquals(List.of(Respawn.ID, PlayerAbilities.ID, SyncPlayerPosition.ID, EntityEvent.ID,
                    InitializeBorder.ID, SetTime.ID, SetDefaultSpawnPosition.ID, GameEvent.ID, TickingState.ID, TickingStep.ID,
                    ContainerSetContent.ID, UpdateAttributes.ID, SetHealth.ID, SetEntityData.ID, SetHeldSlot.ID, EntityEvent.ID, GameEvent.ID,
                    SetExperience.ID), ids, "Paper refreshPlayer 순서");
            Frame respawn = af.get(start);
            assertEquals(3, respawn.body()[respawn.body().length - 1], "KEEP_ALL_DATA");
            ByteBuf perm = af.get(start + 3).buf();
            assertEquals(eid, perm.readInt());
            assertEquals(24 + 4, perm.readByte(), "권한 레벨 엔티티 이벤트(24+레벨, 프로필 op 4)");
            // updateScaledHealth: syncable 속성 가운데 최대 체력은 한 번, 맨 끝에, 수정자 없이 수정자 반영 값으로
            ByteBuf at = af.get(start + 11).buf();
            assertEquals(eid, Types.readVarInt(at));
            int n = Types.readVarInt(at);
            List<int[]> rows = new ArrayList<>();   // {속성 id, 수정자 수}
            double lastBase = Double.NaN;
            for (int i = 0; i < n; i++) {
                int id = Types.readVarInt(at);
                lastBase = at.readDouble();
                int mods = Types.readVarInt(at);
                for (int k = 0; k < mods; k++) { Types.readString(at, 32767); at.readDouble(); at.readByte(); }
                rows.add(new int[] {id, mods});
            }
            assertEquals(1, rows.stream().filter(r -> r[0] == maxHealthAttr.id()).count(), "최대 체력은 한 번");
            assertEquals(maxHealthAttr.id(), rows.get(n - 1)[0], "최대 체력은 맨 끝");
            assertEquals(0, rows.get(n - 1)[1], "수정자 없이");
            assertEquals(24.0, lastBase, 1e-9, "값은 수정자를 반영한 최대 체력(20+4)");
            Frame data = af.get(start + 13);
            ByteBuf d = data.buf();
            assertEquals(eid, Types.readVarInt(d));
            assertEquals(0, d.readUnsignedByte(), "엔티티 데이터 전부(0번부터)");
            assertEquals(java.util.stream.IntStream.rangeClosed(0, 20).boxed().toList(),
                    alexP.allEntityData().items().stream().map(SetEntityData.Item::index).toList(), "refreshEntityData 는 0..20 의 21 항목(packAll)");
            ByteBuf expected = io.netty.buffer.Unpooled.buffer();
            alexP.allEntityData().write(expected);
            byte[] exp = new byte[expected.readableBytes()];
            expected.readBytes(exp);
            // 값(체력 회복 등)은 그사이 바뀔 수 있어 길이만 대조한다 — 항목이 빠지거나 늘면 길이가 달라진다
            assertEquals(exp.length, data.body().length, "보낸 엔티티 데이터가 그 21 항목");
            assertEquals(alexP.vitals().health(), health, "체력은 그대로(리스폰 초기화 없음)");
            assertTrue(respawns.isEmpty(), "PlayerRespawnEvent 를 부르지 않는다");
            assertEquals("Alex2", alexP.name());
            assertEquals(alex.uuid, alexP.uuid());
            assertTrue(shown.containsAll(List.of("Bob>" + alex.uuid, "Alex2>" + alex.uuid)), "보는 사람마다(본인 포함) PlayerShowEntityEvent: " + shown);
            assertTrue(hidden.isEmpty(), "PlayerHideEntityEvent 는 부르지 않는다");
            assertFalse(bobP.isDisconnected());
        }
    }

    /** UUID 를 바꾸면(설계 R1): 새 프로필 id 는 재등록 탭 패킷 하나에만 실리고 스폰은 엔티티 UUID, 그 뒤 퇴장 Remove 도 엔티티 UUID(Paper 와 같은 유령). */
    @Test
    void changedUuidGoesOnlyIntoTheReregistrationEntry() throws Exception {
        UUID newId = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
        try (TestClient bob = TestClient.join(port, "Bob")) {
            awaitListed(bob.uuid);
            UUID entity;
            int eid;
            try (TestClient alex = TestClient.join(port, "Alex")) {
                entity = alex.uuid;
                Player alexP = awaitListed(alex.uuid);
                eid = alexP.entityId();
                awaitSpawned(bob, eid);
                change(alexP, newId, "Neo", List.of());
                List<Frame> bf = readWhile(bob, fr -> hasSpawn(fr, eid), 5_000);
                assertEquals(List.of("remove_entity", "info_remove " + entity, "info_add " + newId + " Neo []", "spawn " + entity),
                        about(bf, eid, entity, newId));
                assertEquals(entity, alexP.uuid());
                assertEquals(newId, alexP.profile().id());
            }
            List<Frame> quit = readWhile(bob, fr -> fr.stream().anyMatch(f -> removedUuids(f).contains(entity)), 5_000);
            assertTrue(about(quit, eid, entity, newId).contains("info_remove " + entity), "퇴장은 엔티티 UUID 로 뺀다");
            assertFalse(about(quit, eid, entity, newId).contains("info_remove " + newId), "새 id 항목은 Paper 처럼 남는다");
        }
    }

    /** 조인 이벤트 안(접속 탭을 보내기 전, Paper !sentListPacket)에서 부르면 프로필만 바뀐다 — 리스폰 없이 탭이 처음부터 새 프로필. */
    @Test
    void insideTheJoinEventOnlyTheProfileChanges() throws Exception {
        core.plugins().eventsImpl().subscribe(plugin, PlayerJoinEvent.class, e -> {
            if (e.player().name().equals("Alex")) e.player().profile(new ProfileData(e.player().uniqueId(), "Neo", SKIN));
        });
        try (TestClient bob = TestClient.join(port, "Bob")) {
            awaitListed(bob.uuid);
            try (TestClient alex = TestClient.join(port, "Alex")) {
                List<Frame> burst = readWhile(alex, fr -> fr.stream().anyMatch(f -> f.id() == InitializeBorder.ID), 5_000);
                List<Added> self = added(burst.stream().filter(f -> f.id() == PlayerInfoUpdate.ID).findFirst().orElseThrow());
                assertEquals(List.of(new Added(alex.uuid, "Neo", List.of("c2tpbg=="))), self, "본인 탭부터 새 프로필(UUID 칸은 엔티티)");
                Player alexP = awaitListed(alex.uuid);
                List<Frame> later = readWhile(alex, fr -> false, 600);
                assertTrue(java.util.stream.Stream.concat(burst.stream(), later.stream()).noneMatch(f -> f.id() == Respawn.ID), "리스폰 없음");
                List<Frame> bf = readWhile(bob, fr -> hasSpawn(fr, alexP.entityId()), 15_000);
                assertEquals(List.of("info_add " + alex.uuid + " Neo [c2tpbg==]", "spawn " + alex.uuid), about(bf, alexP.entityId(), alex.uuid),
                        "다른 사람에게도 처음부터 새 프로필 한 번");
                assertTrue(shown.isEmpty(), "재등록이 없으니 PlayerShowEntityEvent 도 없다");
            }
        }
    }

    /** 다른 region 관찰자는 엔티티를 보지 않으니 탭만: 엔티티 UUID 로 빼고 새 프로필 id 로 올린다(스폰·엔티티 제거 없음). */
    @Test
    void anObserverInAnotherRegionGetsOnlyTheTab() throws Exception {
        UUID newId = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
        try (TestClient bob = TestClient.join(port, "Bob"); TestClient alex = TestClient.join(port, "Alex")) {
            Player bobP = awaitListed(bob.uuid);
            Player alexP = awaitListed(alex.uuid);
            int eid = alexP.entityId();
            awaitSpawned(bob, eid);
            awaitSpawnGate(bob);
            bobP.runOnOwner(() -> bobP.teleportTo(4000.5, 120, 4000.5));
            awaitApart(bobP, alexP);
            readWhile(bob, fr -> false, 1_000);   // 옮겨 가며 받은 것(청크·엔티티 정리)을 비운다
            change(alexP, newId, "Neo", List.of());
            List<Frame> bf = readWhile(bob, fr -> fr.stream().anyMatch(f -> added(f).stream().anyMatch(a -> a.uuid().equals(newId))), 5_000);
            bf.addAll(readWhile(bob, fr -> false, 300));
            assertEquals(List.of("info_remove " + alex.uuid, "info_add " + newId + " Neo []"), about(bf, eid, alex.uuid, newId));
            for (int i = 0; i < 200 && !shown.contains("Bob>" + alex.uuid); i++) Thread.sleep(10);
            assertTrue(shown.contains("Bob>" + alex.uuid), "다른 region 관찰자에게도 PlayerShowEntityEvent: " + shown);
        }
    }

    /**
     * 접속 직후 스폰 게이트가 풀릴 때까지(시험 클라이언트는 텔레포트를 확인하지 않아 300ms 뒤 대체 해제 — 스폰 자리로 다시 고정하는
     * SyncPlayerPosition id 2). 그 전에 보낸 텔레포트는 이 재고정이 덮으므로 멀리 보내는 시험은 이 뒤에 옮긴다.
     */
    private static void awaitSpawnGate(TestClient c) throws Exception {
        readWhile(c, fr -> fr.stream().anyMatch(f -> f.id() == SyncPlayerPosition.ID && teleportId(f) == 2), 5_000);
    }

    private static int teleportId(Frame f) {
        ByteBuf b = f.buf().duplicate();
        b.skipBytes(8 * 3 + 4 * 2 + 1);
        return Types.readVarInt(b);
    }

    /** 두 플레이어가 서로 다른 region 에 자리 잡을 때까지(연이어 20번 — 1초 — 다른 region 이고 멀리 있음). */
    private static void awaitApart(Player far, Player near) throws InterruptedException {
        int stable = 0;
        for (int i = 0; i < 600 && stable < 20; i++) {
            boolean apart = far.ownerRegion != null && near.ownerRegion != null && far.ownerRegion != near.ownerRegion
                    && Math.abs(far.x() - near.x()) > 1000;
            stable = apart ? stable + 1 : 0;
            Thread.sleep(50);
        }
        assertEquals(20, stable, "서로 다른 region 에 자리 잡았다");
    }

    /** 숨긴 관찰자(canSee 거짓)는 아무것도 받지 않는다(Paper 는 canSee 인 사람만 다시 등록). */
    @Test
    void aHiddenObserverHearsNothing() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob"); TestClient alex = TestClient.join(port, "Alex")) {
            Player bobP = awaitListed(bob.uuid);
            Player alexP = awaitListed(alex.uuid);
            int eid = alexP.entityId();
            awaitSpawned(bob, eid);
            core.plugins().entityViews().hide(plugin, core.plugins().player(bobP), core.plugins().player(alexP));
            assertEquals(List.of("Bob>" + alex.uuid), hidden, "숨기면 PlayerHideEntityEvent");
            readWhile(bob, fr -> fr.stream().anyMatch(f -> removedUuids(f).contains(alex.uuid)), 5_000);
            change(alexP, alex.uuid, "Alex2", List.of());
            readWhile(alex, fr -> fr.stream().anyMatch(f -> f.id() == SetExperience.ID), 5_000);
            List<Frame> bf = readWhile(bob, fr -> false, 600);
            assertEquals(List.of(), about(bf, eid, alex.uuid), "숨긴 관찰자는 재등록 패킷을 받지 않는다");
            assertFalse(shown.contains("Bob>" + alex.uuid));
            core.plugins().entityViews().show(plugin, core.plugins().player(bobP), core.plugins().player(alexP));
            assertTrue(shown.contains("Bob>" + alex.uuid), "다시 보이면 PlayerShowEntityEvent");
            List<Frame> back = readWhile(bob, fr -> hasSpawn(fr, eid), 5_000);
            assertEquals(List.of("info_add " + alex.uuid + " Alex2 []", "spawn " + alex.uuid), about(back, eid, alex.uuid), "보이면 새 이름으로 올라온다");
        }
    }

    /** 연이어 두 번: 둘 다 Paper 순서대로 적용되고 마지막 프로필로 끝난다. */
    @Test
    void twoChangesInARowEndOnTheLastProfile() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob"); TestClient alex = TestClient.join(port, "Alex")) {
            awaitListed(bob.uuid);
            Player alexP = awaitListed(alex.uuid);
            int eid = alexP.entityId();
            awaitSpawned(bob, eid);
            readWhile(alex, fr -> false, 300);   // 접속 때 받은 경험치 패킷 등을 비운다
            change(alexP, alex.uuid, "One", List.of());
            change(alexP, alex.uuid, "Two", List.of());
            List<Frame> bf = readWhile(bob, fr -> fr.stream().anyMatch(f -> added(f).stream().anyMatch(a -> a.name().equals("Two")))
                    && hasSpawn(fr.subList(lastAdd(fr), fr.size()), eid), 5_000);
            List<String> seen = about(bf, eid, alex.uuid);
            assertEquals("spawn " + alex.uuid, seen.get(seen.size() - 1));
            assertEquals("info_add " + alex.uuid + " Two []", seen.get(seen.size() - 2), "마지막 등록은 두 번째 프로필");
            for (int i = 0; i < 200 && !"Two".equals(alexP.name()); i++) Thread.sleep(10);
            assertEquals("Two", alexP.name());
            List<Frame> af = readWhile(alex, fr -> fr.stream().filter(f -> f.id() == Respawn.ID).count() >= 2, 5_000);
            assertEquals(2, af.stream().filter(f -> f.id() == Respawn.ID).count(), "본인 갱신도 두 번(Paper 와 같다)");
        }
    }

    private static int lastAdd(List<Frame> fr) {
        for (int i = fr.size() - 1; i >= 0; i--) if (added(fr.get(i)).stream().anyMatch(a -> a.name().equals("Two"))) return i;
        return fr.size();
    }

    /**
     * 바꾸자마자 나가면: 다른 region 관찰자(Bob)의 region 을 붙잡아 두어 세대 차이를 보기 전에 Alex 가 바꾸고 퇴장까지 끝낸다. 풀린 Bob 은
     * 새 프로필 항목을 한 번도 올리지 않고 엔티티 UUID 로 한 번만 뺀다.
     */
    @Test
    void quittingRightAfterTheChangeLeavesNoEntryBehind() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob")) {
            Player bobP = awaitListed(bob.uuid);
            awaitSpawnGate(bob);
            bobP.runOnOwner(() -> bobP.teleportTo(4000.5, 120, 4000.5));
            UUID entity;
            int eid;
            java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            try {
                try (TestClient alex = TestClient.join(port, "Alex")) {
                    entity = alex.uuid;
                    Player alexP = awaitListed(alex.uuid);
                    awaitApart(bobP, alexP);
                    eid = alexP.entityId();
                    readWhile(bob, fr -> fr.stream().anyMatch(f -> added(f).stream().anyMatch(x -> x.uuid().equals(entity))), 5_000);
                    bobP.runOnOwner(() -> {
                        held.countDown();
                        try {
                            release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS), "Bob 의 region 을 붙잡았다");
                    change(alexP, entity, "Gone", List.of());
                    for (int i = 0; i < 500 && !"Gone".equals(alexP.name()); i++) Thread.sleep(10);
                    assertEquals("Gone", alexP.name(), "Alex 의 region 에서 바뀌었다");
                }
                for (int i = 0; i < 1000 && core.playerByUuid(entity) != null; i++) Thread.sleep(10);
                assertNull(core.playerByUuid(entity), "Bob 이 보기 전에 퇴장까지 끝났다");
            } finally {
                release.countDown();
            }
            List<Frame> bf = readWhile(bob, fr -> fr.stream().anyMatch(f -> removedUuids(f).contains(entity)), 5_000);
            bf.addAll(readWhile(bob, fr -> false, 500));
            List<String> seen = about(bf, eid, entity);
            assertEquals(List.of("info_remove " + entity), seen, "새 프로필로 다시 올리지 않고 엔티티 UUID 로 한 번만 뺀다");
        }
    }

    /**
     * 본인 갱신의 위치(internalTeleport)는 region 이 검증한 위치다: 확인을 마친 상태에서 연결이 받아 두고 region 이 아직 검증하지 않은
     * 이동 패킷이 있어도 그 자리로 확정하지 않는다(Paper getLocation, 네트워크 규칙 3).
     */
    @Test
    void theRefreshTeleportUsesTheValidatedPositionNotAPendingMove() throws Exception {
        try (TestClient alex = TestClient.join(port, "Alex")) {
            Player alexP = awaitListed(alex.uuid);
            awaitSpawnGate(alex);
            readWhile(alex, fr -> false, 300);
            double x0 = alexP.x(), y0 = alexP.y(), z0 = alexP.z();
            java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch moveSent = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CompletableFuture<Boolean> sawPending = new java.util.concurrent.CompletableFuture<>();
            alexP.runOnOwner(() -> {   // 이번 틱의 이동 처리는 끝났다 — 이 안에서 받은 이동은 다음 틱까지 검증 대기
                held.countDown();
                try {
                    moveSent.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    boolean pending = false;
                    for (int i = 0; i < 200 && !(pending = alexP.movesPending()); i++) Thread.sleep(10);
                    sawPending.complete(pending);
                    change(alexP, alex.uuid, "Alex2", List.of());   // 소유 스레드 — 그 자리에서 적용
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS));
            alex.write(0x1A, b -> { b.writeDouble(x0 + 0.3); b.writeDouble(y0); b.writeDouble(z0); b.writeBoolean(true); });   // move_player_pos
            moveSent.countDown();
            assertTrue(sawPending.get(5, java.util.concurrent.TimeUnit.SECONDS), "연결이 받은 이동이 검증 대기 중이었다");
            List<Frame> af = readWhile(alex, fr -> fr.stream().anyMatch(f -> f.id() == SetExperience.ID)
                    && fr.stream().anyMatch(f -> f.id() == Respawn.ID), 5_000);
            int respawn = -1;
            for (int i = 0; i < af.size(); i++) if (af.get(i).id() == Respawn.ID) respawn = i;
            assertTrue(respawn >= 0, "본인 갱신을 받았다");
            ByteBuf pos = af.stream().skip(respawn).filter(f -> f.id() == SyncPlayerPosition.ID).findFirst().orElseThrow().buf();
            assertEquals(x0, pos.readDouble(), 1e-9, "검증 전 이동(x0+0.3)이 아니라 region 위치");
            assertEquals(y0, pos.readDouble(), 1e-9);
            assertEquals(z0, pos.readDouble(), 1e-9);
        }
    }

    /**
     * 막 보낸 텔레포트(확인 전)가 있을 때 바꾸면: 본인 갱신 위치는 그 목적지(되돌리지 않는다). 프로필 작업은 옛 region 에서 돌고, 플레이어는
     * 그 뒤 새 region 으로 옮겨 간다.
     */
    @Test
    void aChangeRightAfterATeleportKeepsTheTeleportDestination() throws Exception {
        try (TestClient alex = TestClient.join(port, "Alex")) {
            Player alexP = awaitListed(alex.uuid);
            var before = alexP.ownerRegion;
            awaitSpawnGate(alex);
            readWhile(alex, fr -> false, 300);
            alexP.runOnOwner(() -> alexP.teleportTo(4000.5, 120, 4000.5));
            change(alexP, alex.uuid, "Moved", List.of());
            List<Frame> af = readWhile(alex, fr -> fr.stream().anyMatch(f -> f.id() == Respawn.ID), 10_000);
            af.addAll(readWhile(alex, fr -> false, 500));
            int respawn = -1;
            for (int i = 0; i < af.size(); i++) if (af.get(i).id() == Respawn.ID) respawn = i;
            ByteBuf pos = af.stream().skip(respawn).filter(f -> f.id() == SyncPlayerPosition.ID).findFirst().orElseThrow().buf();
            assertEquals(4000.5, pos.readDouble(), 1e-9, "갱신의 위치는 막 보낸 텔레포트 목적지(되돌리지 않는다)");
            for (int i = 0; i < 1500 && alexP.ownerRegion == before; i++) Thread.sleep(10);
            assertEquals("Moved", alexP.name());
            assertEquals(1, af.stream().filter(f -> f.id() == Respawn.ID).count(), "본인 갱신 한 번");
            assertNotSame(before, alexP.ownerRegion, "옮겨 갔다");
        }
    }
}
