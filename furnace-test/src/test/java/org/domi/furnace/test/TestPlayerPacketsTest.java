// 가짜 플레이어가 받은 패킷 확인(E3): received(type)·awaitPacket·clearReceived — 실제로 소켓으로 온 것(리스너가 바꾸거나 취소한 뒤)을 버전 중립 레코드로.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.entity.data.EntityDataKeys;
import org.domi.furnace.api.entity.data.EntityFlags;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.text.Text;
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

            steve.clearReceived();
            assertEquals(List.of(), steve.received(PacketTypes.Clientbound.SET_TITLE_TEXT));
            assertThrows(IllegalArgumentException.class, () -> steve.received(PacketTypes.Clientbound.BUNDLE_DELIMITER), "타입 래퍼 없는 종류");
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
}
