package org.domi.furnace.api.packet.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.api.packet.PacketDirection;
import org.junit.jupiter.api.Test;

class ProtocolIdsTest {

    private static final ProtocolIds IDS = ProtocolIds.protocol767();
    private static final PacketSchema SCHEMA = PacketSchema.protocol767();

    @Test
    void entryCountsMatchVanilla1211() {
        // 바닐라 1.21.1 GameProtocols·ConfigurationProtocols 등의 등록 수(tools/compat/protocol-ids-extract.py 로 센 값)
        assertEquals(124, IDS.entries("game", PacketDirection.CLIENTBOUND).size());
        assertEquals(58, IDS.entries("game", PacketDirection.SERVERBOUND).size());
        assertEquals(17, IDS.entries("configuration", PacketDirection.CLIENTBOUND).size());
        assertEquals(8, IDS.entries("configuration", PacketDirection.SERVERBOUND).size());
        assertEquals(6, IDS.entries("login", PacketDirection.CLIENTBOUND).size());
        assertEquals(5, IDS.entries("login", PacketDirection.SERVERBOUND).size());
        assertEquals(2, IDS.entries("status", PacketDirection.CLIENTBOUND).size());
        assertEquals(2, IDS.entries("status", PacketDirection.SERVERBOUND).size());
        assertEquals(0, IDS.entries("handshake", PacketDirection.CLIENTBOUND).size());
        assertEquals(1, IDS.entries("handshake", PacketDirection.SERVERBOUND).size());
        assertEquals(List.of("configuration", "game", "handshake", "login", "status"), IDS.protocols().stream().sorted().toList());
    }

    @Test
    void wellKnownIdsMatchTheProtocol() {
        assertEquals("bundle_delimiter", IDS.entries("game", PacketDirection.CLIENTBOUND).get(0).wireName());
        assertEquals("add_entity", IDS.entries("game", PacketDirection.CLIENTBOUND).get(1).wireName());
        assertEquals("set_time", IDS.entries("game", PacketDirection.CLIENTBOUND).get(100).wireName());
        assertEquals("accept_teleportation", IDS.entries("game", PacketDirection.SERVERBOUND).get(0).wireName());
        assertEquals("hello", IDS.entries("login", PacketDirection.SERVERBOUND).get(0).wireName());
        // 구성 단계는 common·cookie 종류를 섞어 쓴다
        ProtocolIds.Entry customPayload = IDS.entries("configuration", PacketDirection.CLIENTBOUND).get(1);
        assertEquals("common", customPayload.holder());
        assertEquals("custom_payload", customPayload.wireName());
        assertEquals("cookie", IDS.entries("configuration", PacketDirection.CLIENTBOUND).get(0).holder());
    }

    @Test
    void typesAndSchemaPacketsAreTheSameSet() {
        Set<String> types = new HashSet<>();
        for (ProtocolIds.Type t : IDS.types()) assertTrue(types.add(t.holder() + "/" + t.direction() + "/" + t.wireName()), "중복 종류: " + t);
        Set<String> schema = new HashSet<>();
        for (PacketDef p : SCHEMA.packets()) schema.add(p.state() + "/" + p.direction() + "/" + p.wireName());
        assertEquals(schema, types);
        assertEquals(202, types.size());
    }

    @Test
    void everyEntryNamesADeclaredTypeOfItsOwnDirection() {
        Set<String> declared = new HashSet<>();
        for (ProtocolIds.Type t : IDS.types()) declared.add(t.holder() + "/" + t.direction() + "/" + t.wireName());
        Set<String> registered = new HashSet<>();
        for (String protocol : IDS.protocols()) {
            for (PacketDirection d : PacketDirection.values()) {
                Set<String> seen = new HashSet<>();
                List<ProtocolIds.Entry> list = IDS.entries(protocol, d);
                for (int i = 0; i < list.size(); i++) {
                    ProtocolIds.Entry e = list.get(i);
                    assertEquals(i, e.id(), protocol + " " + d);
                    String key = e.holder() + "/" + d + "/" + e.wireName();
                    assertTrue(declared.contains(key), "선언되지 않은 종류: " + key);
                    assertTrue(seen.add(key), protocol + " " + d + " 에서 중복 등록: " + key);
                    registered.add(key);
                }
            }
        }
        // 번호를 받지 못한 종류는 번들 종류 하나뿐(구분자가 번호를 받는다)
        Set<String> unregistered = new HashSet<>(declared);
        unregistered.removeAll(registered);
        assertEquals(Set.of("game/CLIENTBOUND/bundle"), unregistered);
    }

    @Test
    void rejectsGapsAndUnknownLines() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolIds.parse("id game clientbound 1 game:add_entity\n"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolIds.parse("nope x\n"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolIds.parse("type game sideways add_entity\n"));
    }
}
