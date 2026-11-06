// 패킷 종류 목록·이름 조회·값 검사(서버 없이).
package org.domi.furnace.api.packet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSoundPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSignUpdatePacket;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.SoundCategory;
import org.junit.jupiter.api.Test;

class PacketTypesTest {

    @Test
    void namesAreUniquePerDirectionAndLookupWorks() {
        for (PacketDirection d : PacketDirection.values()) {
            Set<String> names = new HashSet<>();
            for (PacketType<?> t : PacketTypes.all(d)) {
                assertEquals(d, t.direction());
                assertTrue(t.name().startsWith("minecraft:"), t.name());
                assertTrue(names.add(t.name()), "중복: " + t);
                assertSame(t, PacketTypes.byName(d, t.name()));
            }
        }
        assertSame(PacketTypes.Serverbound.MOVE_PLAYER, PacketTypes.byName(PacketDirection.SERVERBOUND, "move_player"));
        assertNull(PacketTypes.byName(PacketDirection.SERVERBOUND, "move_player_pos"), "이동 넷은 하나로 묶였다");
        assertNotSame(PacketTypes.Clientbound.CUSTOM_PAYLOAD, PacketTypes.Serverbound.CUSTOM_PAYLOAD);
        assertTrue(PacketTypes.Clientbound.SYSTEM_CHAT.typed());
        assertFalse(PacketTypes.Clientbound.GAME_EVENT.typed());
        assertEquals("clientbound/minecraft:system_chat", PacketTypes.Clientbound.SYSTEM_CHAT.toString());
    }

    @Test
    void valuesValidateAndCopy() {
        var spawn = ClientboundAddEntityPacket.of(1, java.util.UUID.randomUUID(), "zombie", 0, 0, 0, 0, 0);
        assertEquals("minecraft:zombie", spawn.entityType());
        assertEquals("minecraft:block.note_block.pling", ClientboundSoundPacket.of("block.note_block.pling", SoundCategory.RECORDS, 0, 0, 0, 1, 1).sound());
        byte[] data = {1, 2};
        var payload = new ClientboundCustomPayloadPacket("a:b", data);
        data[0] = 9;
        assertEquals(1, payload.data()[0], "만들 때 복사");
        payload.data()[1] = 9;
        assertEquals(2, payload.data()[1], "꺼낼 때 복사");
        assertEquals(payload, new ClientboundCustomPayloadPacket("a:b", new byte[]{1, 2}));
        assertNull(ServerboundInteractPacket.attack(1, false).hand());
        assertThrows(NullPointerException.class, () -> new ServerboundInteractPacket(1, ServerboundInteractPacket.Action.INTERACT, null, 0, 0, 0, false));
        assertEquals(Hand.OFF_HAND, ServerboundInteractPacket.interact(1, Hand.OFF_HAND, false).hand());
        assertThrows(IllegalArgumentException.class, () -> new ServerboundSignUpdatePacket(new BlockPos(0, 0, 0), true, List.of("a")));
    }
}
