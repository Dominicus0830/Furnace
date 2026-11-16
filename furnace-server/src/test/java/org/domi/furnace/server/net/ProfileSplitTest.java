// T-141 E11c-2a: 엔티티 UUID 와 게임 프로필을 나눈 호출처 분류(설계 §3 표)를 줄마다 확인한다. 로그인 시험 훅으로
// 엔티티 UUID·접속 이름과 다른 프로필(id·이름)을 넣고, 각 쓰임이 표대로 엔티티 쪽/프로필 쪽을 보는지 본다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.chat.SignedMessageBody;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileSplitTest {

    /** 넣을 프로필: 접속 이름 "Alex" 와 다른 id·이름. */
    private static final UUID PROFILE_ID = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
    private static final String PROFILE_NAME = "Bobby";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().startTicking();
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core)
                .profileForTest(g -> g.name().equals("Alex") ? new GameProfile(PROFILE_ID, PROFILE_NAME, g.properties()) : g);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player awaitListed(UUID entity) throws InterruptedException {
        for (int i = 0; i < 1500; i++) {
            Player p = core.playerByUuid(entity);
            if (p != null && !p.tabListingHeld()) return p;
            Thread.sleep(10);
        }
        fail("접속 완료: " + entity);
        return null;
    }

    /** 첫 add_player 항목의 (UUID, 이름). */
    private static Object[] firstEntry(ByteBuf b) {
        b.readUnsignedByte();
        Types.readVarInt(b);
        return new Object[]{Types.readUuid(b), Types.readString(b, 16)};
    }

    @Test
    void entityIdentityAndProfileGoWhereTheTableSays() throws Exception {
        try (TestClient alex = TestClient.join(port, "Alex")) {
            UUID entity = alex.uuid;   // 오프라인 접속 UUID = 엔티티 UUID
            assertNotEquals(PROFILE_ID, entity);
            Player p = awaitListed(entity);
            assertEquals(entity, p.uuid(), "엔티티 UUID 는 접속 UUID 그대로");
            assertEquals(PROFILE_ID, p.profile().id());
            assertEquals(PROFILE_NAME, p.name(), "getName = 프로필 이름");
            assertEquals("Alex", p.joinName());

            // 탭: UUID 칸은 엔티티 UUID, 이름은 프로필(설계 §1-4)
            ByteBuf tab = alex.await(PlayerInfoUpdate.ID);
            Object[] self = firstEntry(tab);
            assertEquals(entity, self[0]);
            assertEquals(PROFILE_NAME, self[1]);
            try (TestClient obs = TestClient.join(port, "Obs")) {
                ByteBuf seen = obs.await(PlayerInfoUpdate.ID, b -> {
                    b.readUnsignedByte();
                    int n = Types.readVarInt(b);
                    for (int i = 0; i < n; i++) if (Types.readUuid(b).equals(entity)) return true; else return false;
                    return false;
                });
                assertEquals(PROFILE_NAME, firstEntry(seen)[1], "다른 사람 탭에도 엔티티 UUID + 프로필 이름");
            }

            // 접속자 조회·선택자: 프로필 이름(의도한 차이 §8 — 옛 접속 이름으로는 못 찾는다), UUID 조회는 엔티티 UUID
            assertSame(p, core.playerByName(PROFILE_NAME));
            assertNull(core.playerByName("Alex"));
            assertSame(p, core.playerByUuid(entity));
            assertNull(core.playerByUuid(PROFILE_ID));
            assertEquals(PROFILE_NAME, p.selectorName());

            // op·화이트리스트·밴: 프로필(Paper getGameProfile)
            core.lists().op(new GameProfile(entity, "Alex"), 4);
            assertFalse(core.lists().isOp(ServerCore.profileOf(p)), "엔티티 UUID 로 op 한 것은 이 플레이어가 아니다");
            core.lists().op(new GameProfile(PROFILE_ID, PROFILE_NAME), 4);
            assertTrue(core.lists().isOp(ServerCore.profileOf(p)));
            assertEquals(4, core.profilePermissions(ServerCore.profileOf(p)));
            core.lists().getWhiteList().add(new org.domi.furnace.server.players.UserWhiteListEntry(new GameProfile(PROFILE_ID, PROFILE_NAME)));
            assertTrue(core.lists().isWhiteListed(ServerCore.profileOf(p)));
            core.lists().getBans().add(new org.domi.furnace.server.players.UserBanListEntry(new GameProfile(entity, "Alex")));
            assertFalse(core.lists().getBans().isListed(ServerCore.profileOf(p)), "엔티티 UUID 로 건 밴은 이 프로필이 아니다");
            core.lists().getBans().add(new org.domi.furnace.server.players.UserBanListEntry(new GameProfile(PROFILE_ID, PROFILE_NAME)));
            assertTrue(core.lists().getBans().isListed(ServerCore.profileOf(p)));

            // 스코어보드: 이름 = 프로필 이름(getScoreboardName)
            var sb = core.scoreboard();
            var team = sb.addPlayerTeam("t1");
            sb.addPlayerToTeam(PROFILE_NAME, team);
            assertSame(team, p.team());

            // 표시 이름: 바닐라 getDisplayName 은 프로필 이름, 채팅 기본 표시 이름은 접속 때의 이름(R4)
            assertEquals(PROFILE_NAME, p.displayName().getString());
            assertEquals("Alex", p.chatDisplayName().getString());

            // 서명 채팅 디코더·sender: 엔티티 UUID(R6)
            assertEquals(entity, p.chat().decoder().unpack(null, SignedMessageBody.unsigned("hi")).sender());

            // 파일: 엔티티 UUID(통계는 접속 때 만들고, 플레이어 데이터는 퇴장 때 저장)
            assertNotNull(p.stats());
        }
        // 퇴장 저장: 엔티티 UUID 이름의 파일, 프로필 id 이름의 파일은 없다
        UUID entity = GameProfile.offlineUuid("Alex");
        Path data = core.world().worldDir().resolve("playerdata");
        for (int i = 0; i < 1000 && !Files.exists(data.resolve(entity + ".dat")); i++) Thread.sleep(10);
        assertTrue(Files.exists(data.resolve(entity + ".dat")), "엔티티 UUID 로 저장");
        try (var files = Files.list(data)) {
            var names = files.map(f -> f.getFileName().toString()).toList();
            assertTrue(names.stream().noneMatch(n -> n.startsWith(PROFILE_ID.toString())), "프로필 id 로 파일을 쓰지 않는다: " + names);
        }
    }

    /** 클라이언트 흉내: 서비스 키로 서명한 프로필 공개키(서명 대상 = 프로필 UUID + 만료 + 공개키 DER). */
    private static org.domi.furnace.server.chat.ProfilePublicKey.Data profileKey(java.security.KeyPair services, java.security.KeyPair player,
                                                                              UUID signedFor) throws Exception {
        java.time.Instant expires = java.time.Instant.now().plusSeconds(3600);
        byte[] k = player.getPublic().getEncoded();
        java.nio.ByteBuffer payload = java.nio.ByteBuffer.allocate(24 + k.length);
        payload.putLong(signedFor.getMostSignificantBits()).putLong(signedFor.getLeastSignificantBits()).putLong(expires.toEpochMilli()).put(k);
        java.security.Signature s = java.security.Signature.getInstance("SHA1withRSA");
        s.initSign(services.getPrivate());
        s.update(payload.array());
        return new org.domi.furnace.server.chat.ProfilePublicKey.Data(expires, player.getPublic(), s.sign());
    }

    private static java.security.KeyPair rsa() throws Exception {
        var g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(1024);
        return g.generateKeyPair();
    }

    /** 채팅 세션 키 검증은 프로필 id 로(Paper handleChatSessionUpdate 의 data.validate(getGameProfile())) — 엔티티 UUID 로 서명한 키는 거절. */
    @Test
    void chatSessionKeyIsValidatedAgainstTheProfileId() throws Exception {
        var services = rsa();
        core.servicesKeys().setProfileKeys(java.util.List.of(services.getPublic()));
        var player = rsa();
        try (TestClient alex = TestClient.join(port, "Alex")) {
            Player p = awaitListed(alex.uuid);
            var data = new org.domi.furnace.server.chat.RemoteChatSession.Data(UUID.randomUUID(), profileKey(services, player, PROFILE_ID));
            alex.write(0x07, data::write);
            for (int i = 0; i < 500 && p.chat().session() == null; i++) Thread.sleep(10);
            assertNotNull(p.chat().session(), "프로필 id 로 서명한 키는 받아들인다");
            assertFalse(p.isDisconnected());
        }
        try (TestClient alex = TestClient.join(port, "Alex")) {
            awaitListed(alex.uuid);
            var data = new org.domi.furnace.server.chat.RemoteChatSession.Data(UUID.randomUUID(), profileKey(services, player, alex.uuid));
            alex.write(0x07, data::write);
            ByteBuf d = alex.await(org.domi.furnace.server.net.protocol.play.Disconnect.ID);
            byte[] raw = new byte[d.readableBytes()];
            d.readBytes(raw);
            String text = new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(text.contains("invalid_public_key_signature") || text.contains("Invalid signature for profile public key"),
                    "엔티티 UUID 로 서명한 키는 프로필 id 와 맞지 않아 거절");
        }
    }
}
