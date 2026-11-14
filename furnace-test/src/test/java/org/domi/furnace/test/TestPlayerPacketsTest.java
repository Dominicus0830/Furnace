// 가짜 플레이어가 받은 패킷 확인(E3): received(type)·awaitPacket·clearReceived — 실제로 소켓으로 온 것(리스너가 바꾸거나 취소한 뒤)을 버전 중립 레코드로.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.entity.data.EntityDataKeys;
import org.domi.furnace.api.entity.data.EntityFlags;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.RawPacket;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class TestPlayerPacketsTest {

    @Test
    void receivedPacketsAreCollectedAsTypedRecords() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer steve = server.join("Steve");
            var packets = server.api().packets();
            steve.clearReceived();
            packets.send(steve.player(), new ClientboundSetTitleTextPacket(Text.text("안녕")));
            assertEquals(List.of(new ClientboundSetTitleTextPacket(Text.text("안녕"))), steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT),
                    "보낸 바로 다음 줄에서 보인다(받은 패킷 장벽)");
            assertTrue(steve.received().contains(new ClientboundSetTitleTextPacket(Text.text("안녕"))));

            // 번들로 보낸 홀로그램: 생성 + 발광 메타데이터
            int id = packets.nextEntityId();
            packets.sendBundle(steve.player(), List.of(
                    ClientboundAddEntityPacket.of(id, UUID.randomUUID(), "minecraft:text_display", 0, 100, 0, 0, 0),
                    ClientboundSetEntityDataPacket.builder(id)
                            .set(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.of(EntityFlags.GLOWING))
                            .set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점")).build()));
            var data = steve.awaitPacket(PacketTypes.Clientbound.SET_ENTITY_DATA, d -> d.entityId() == id);
            assertEquals(Optional.of(Text.text("상점")), data.get(EntityDataKeys.TextDisplay.TEXT));
            assertTrue(EntityFlags.has(data.get(EntityDataKeys.Entity.SHARED_FLAGS).orElseThrow(), EntityFlags.GLOWING));
            assertEquals(1, steve.received(PacketTypes.Clientbound.ADD_ENTITY).stream().filter(a -> a.entityId() == id).count());

            // 타입 레코드가 없는 종류는 GenericPacket(이름 붙은 필드)으로 모인다
            packets.send(steve.player(), GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", id));
            assertEquals(id, steve.awaitPacket(PacketTypes.Clientbound.SET_CAMERA, c -> true).get("camera_id"));

            steve.clearReceived();
            assertEquals(List.of(), steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT));
            // E5 부터 1.21.1 의 범용 종류는 모두 읽힌다(못 읽는 종류였던 번들 구분자도 이제 모인다)
            assertDoesNotThrow(() -> steve.received(PacketTypes.Clientbound.BUNDLE_DELIMITER));
        }
    }

    @Test
    void listenerChangesAreWhatThePlayerReceives() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).timeout(5_000).start()) {
            TestPlayer steve = server.join("Steve");
            var plugin = server.plugin(CounterPlugin.class);
            var packets = server.api().packets();
            packets.listen(plugin, PacketTypes.Clientbound.SET_TITLE_TEXT, e -> {
                if (e.packet().text().plainText().equals("비밀")) e.setCancelled(true);
                else e.setPacket(new ClientboundSetTitleTextPacket(Text.text("[!] " + e.packet().text().plainText())));
            });
            steve.clearReceived();
            packets.send(steve.player(), new ClientboundSetTitleTextPacket(Text.text("비밀")));
            packets.send(steve.player(), new ClientboundSetTitleTextPacket(Text.text("공지")));
            assertEquals(List.of(new ClientboundSetTitleTextPacket(Text.text("[!] 공지"))), steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT));
            var e = assertThrows(IllegalStateException.class,
                    () -> steve.awaitPacket(PacketTypes.Clientbound.SET_TITLE_TEXT, t -> t.text().plainText().equals("비밀")));
            assertTrue(e.getMessage().contains("[!] 공지"), "실패 메시지에 받은 그 종류 목록: " + e.getMessage());
        }
    }
    @Test
    void afterKickReceivedReturnsEverythingUpToDisconnect() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).timeout(5_000).start()) {
            TestPlayer steve = server.join("Steve");
            steve.clearReceived();
            var handle = steve.player();
            for (int i = 0; i < 200; i++) server.api().packets().send(handle, new ClientboundSetTitleTextPacket(Text.text("k" + i)));
            handle.kick(Text.text("bye"));
            long dl = System.nanoTime() + 5_000_000_000L;
            while (server.internals().playerByName("Steve") != null && System.nanoTime() < dl) Thread.onSpinWait();
            assertEquals(200, steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT).size(), "킥 뒤에는 연결 끝까지 읽은 것 reason=" + steve.disconnectReason());
        }
    }

    @Test
    void pluginPingDoesNotBreakBarrierAndDecodeFailuresAreCounted() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).timeout(2_000).start()) {
            TestPlayer steve = server.join("Steve");
            var core = server.internals();
            var codec = core.plugins().packetsImpl().codec();
            int pingId = codec.ids(PacketTypes.Clientbound.PING)[0];
            // 안티치트류 플러그인이 임의 값으로 ping 을 보내도 장벽은 우리 값만 센다
            core.playerByName("Steve").send(new RawPacket(pingId, java.nio.ByteBuffer.allocate(4).putInt(1_000_000).array(), null, true));
            steve.flushReceived();
            for (int round = 0; round < 50; round++) {
                steve.clearReceived();
                for (int k = 0; k < 20; k++) server.api().packets().send(steve.player(), new ClientboundSetTitleTextPacket(Text.text("k" + k)));
                assertEquals(20, steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT).size());
            }
            // 깨진 본문은 기록되지 않고 수로 남으며 실패 메시지에 보인다
            int titleId = codec.ids(PacketTypes.Clientbound.SET_TITLE_TEXT)[0];
            long before = steve.decodeFailures();
            steve.clearReceived();
            core.playerByName("Steve").send(new RawPacket(titleId, new byte[0], null, true));
            var e = assertThrows(IllegalStateException.class, () -> steve.awaitPacket(PacketTypes.Clientbound.SET_TITLE_TEXT, t -> true));
            assertEquals(before + 1, steve.decodeFailures());
            assertTrue(e.getMessage().contains("풀지 못한 패킷"), e.getMessage());
        }
    }

    /**
     * 실패 수는 받은 패킷 장벽 뒤에 센다: 읽기 스레드가 뒤처져도(바쁜 CI) 서버가 앞서 보낸 패킷의 실패가 빠지지 않는다.
     * 접속 때 player_info_update(채팅 세션 초기화)는 시험 클라이언트가 읽지 않는 패킷이다 — 그 프레임에서 읽기 스레드를 일부러 늦춘다.
     * 장벽 없이 세던 때는 sync() 직후 0 이 나와 "전/후" 비교 시험(SchemaPacketBridgeTest)이 가끔 2 차이로 깨졌다.
     */
    @Test
    void decodeFailuresCountEverythingTheServerSentBefore() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            int infoId = server.internals().plugins().packetsImpl().codec().ids(PacketTypes.Clientbound.PLAYER_INFO_UPDATE)[0];
            TestPlayer.frameTap = (id, body) -> {
                if (id == infoId) java.util.concurrent.locks.LockSupport.parkNanos(300_000_000L);
            };
            try {
                TestPlayer steve = server.join("Steve");
                server.tick(2);
                steve.sync();
                long n = steve.decodeFailures();
                assertTrue(n >= 1, "접속 때 받은 채팅 세션 초기화가 세어져 있다: " + n);
                assertTrue(steve.recentDecodeFailures().stream().allMatch(f -> f.contains("player_info_update")), steve.recentDecodeFailures().toString());
                assertEquals(n, steve.decodeFailures(), "더 보낸 것이 없으면 그대로");
            } finally {
                TestPlayer.frameTap = null;
            }
        }
    }
}
