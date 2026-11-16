// GenericPacket·PacketStruct 값 규칙(서버 없이): 복사, 같은 종류만 바꾸기, 배열 비교, 읽을 수 없는 패킷.
package org.domi.furnace.api.packet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

class GenericPacketApiTest {

    @Test
    void withKeepsKindAndCopiesValues() {
        var p = GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", 3);
        assertEquals(3, p.get("camera_id"));
        assertEquals(3, p.get(PacketField.of("camera_id", Integer.class)));
        assertThrows(IllegalArgumentException.class, () -> p.with("camera_id", 3L), "Integer 자리에 Long");
        assertThrows(IllegalArgumentException.class, () -> p.get(PacketField.of("camera_id", Long.class)));
        assertThrows(IllegalArgumentException.class, () -> p.get("nope"));
        assertThrows(IllegalArgumentException.class, () -> p.with("x", null));
        assertThrows(IllegalArgumentException.class, () -> p.with("x", new Object()), "값 종류가 아니다");

        byte[] raw = {1, 2};
        List<Object> list = new ArrayList<>(List.of("a"));
        var q = GenericPacket.of(PacketTypes.Clientbound.STORE_COOKIE).with("payload", raw).with("list", list);
        raw[0] = 9;
        list.add("b");
        assertArrayEquals(new byte[]{1, 2}, (byte[]) q.get("payload"), "넣을 때 복사");
        ((byte[]) q.get("payload"))[1] = 9;
        assertArrayEquals(new byte[]{1, 2}, (byte[]) q.get("payload"), "꺼낼 때 복사");
        assertEquals(List.of("a"), q.get("list"));
        assertEquals(q, GenericPacket.of(PacketTypes.Clientbound.STORE_COOKIE).with("payload", new byte[]{1, 2}).with("list", List.of("a")), "배열은 내용으로 비교");
        assertEquals(q.hashCode(), GenericPacket.of(PacketTypes.Clientbound.STORE_COOKIE).with("payload", new byte[]{1, 2}).with("list", List.of("a")).hashCode());
        assertEquals(List.of("payload", "list"), q.names());
    }

    @Test
    void mutableValuesAreCopiedEvenInsideOptionalsAndLists() {
        var tag = new org.domi.furnace.api.nbt.NbtCompound();
        tag.putInt("a", 1);
        var p = GenericPacket.of(PacketTypes.Clientbound.TAG_QUERY).with("tag", Optional.of(tag)).with("icons", List.of(new byte[]{1}));
        int h = p.hashCode();
        tag.putInt("a", 2);
        ((org.domi.furnace.api.nbt.NbtCompound) ((Optional<?>) p.get("tag")).orElseThrow()).putInt("a", 3);
        ((byte[]) ((List<?>) p.get("icons")).get(0))[0] = 9;
        assertEquals(h, p.hashCode(), "패킷 값은 밖에서 바뀌지 않는다(검토 R3·R4)");
        assertEquals(1, ((org.domi.furnace.api.nbt.NbtCompound) ((Optional<?>) p.get("tag")).orElseThrow()).getInt("a"));
        assertArrayEquals(new byte[]{1}, (byte[]) ((List<?>) p.get("icons")).get(0));
    }

    @Test
    void nestedStructsAndOptionals() {
        var s = PacketStruct.of(Map.of("text", "hi")).with("tooltip", Optional.of(Text.text("t")));
        var p = GenericPacket.of(PacketTypes.Clientbound.COMMAND_SUGGESTIONS).with("suggestions", List.of(s));
        assertEquals(Optional.of(Text.text("t")), ((PacketStruct) ((List<?>) p.get("suggestions")).get(0)).get("tooltip"));
        assertThrows(IllegalArgumentException.class, () -> PacketStruct.of().with("o", Optional.of(new Object())));
    }

    @Test
    void typedKindsAndUnreadablePackets() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        PacketType<GenericPacket> typed = (PacketType) PacketTypes.Clientbound.SET_TITLE_TEXT;
        assertThrows(IllegalArgumentException.class, () -> GenericPacket.of(typed), "타입 레코드 종류: " + ClientboundSetTitleTextPacket.class);
        var u = GenericPacket.unreadable(PacketTypes.Clientbound.LEVEL_PARTICLES);
        assertFalse(u.readable());
        assertThrows(IllegalStateException.class, u::names);
        assertThrows(IllegalStateException.class, () -> u.with("x", 1));
        assertNotEquals(u, GenericPacket.of(PacketTypes.Clientbound.LEVEL_PARTICLES));
        assertThrows(IllegalArgumentException.class, () -> PacketField.of("x", Object.class));
    }
}
