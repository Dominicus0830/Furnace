// 프로토콜 767 중립 스키마: 읽기·이름 풀이·play 패킷 목록(PacketTypes)과 맞물림·문법 오류 보고.
package org.domi.furnace.api.packet.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.junit.jupiter.api.Test;

class PacketSchemaTest {

    /** play 단계에서 오가는 패킷이 속한 스키마 상태(게임 + 설정과 같이 쓰는 공통·쿠키·핑). */
    private static final Set<String> PLAY_STATES = Set.of("game", "common", "cookie", "ping");

    @Test
    void loadsEveryPacketOfProtocol767() {
        PacketSchema s = PacketSchema.protocol767();
        assertEquals(767, s.protocol());
        assertEquals(201, s.packets().size(), "1.21.1 패킷 종류 수(핸드셰이크 1 + 상태 2 + 핑 2 + 쿠키 2 + 로그인 9 + 설정 7 + 공통 16 + 게임 162)");
        assertEquals(162, s.packets().stream().filter(p -> p.state().equals("game")).count());
        assertSame(s, PacketSchema.protocol767());
        Set<String> classes = new HashSet<>();
        for (PacketDef p : s.packets()) {
            assertTrue(classes.add(p.binaryName()), "클래스 겹침: " + p.binaryName());
            assertTrue(p.binaryName().startsWith("net.minecraft.network.protocol." + p.state() + "."), p.binaryName());
            for (FieldDef f : p.fields()) assertNotNull(s.wireOf(f, p.javaPackage()));
        }
    }

    @Test
    void everyPlayPacketTypeHasASchemaPacketAndBack() {
        PacketSchema s = PacketSchema.protocol767();
        for (PacketDirection d : PacketDirection.values()) {
            Set<String> api = new HashSet<>();
            for (PacketType<?> t : PacketTypes.all(d)) {
                String wire = t.name().substring("minecraft:".length());
                api.add(wire);
                if (wire.equals("move_player")) continue;   // API 는 이동 넷을 하나로 묶었다
                assertTrue(PLAY_STATES.stream().anyMatch(st -> s.packet(st, d, wire).isPresent()), "스키마에 없다: " + d + " " + wire);
            }
            for (PacketDef p : s.packets()) {
                if (p.direction() != d || !PLAY_STATES.contains(p.state())) continue;
                String wire = p.wireName().startsWith("move_player_") ? "move_player" : p.wireName();
                assertTrue(api.contains(wire), "PacketTypes 에 없다: " + d + " " + p.wireName());
            }
        }
    }

    @Test
    void fieldsKeepMojangNamesTypesAndWires() {
        PacketSchema s = PacketSchema.protocol767();
        PacketDef add = s.packet("game", PacketDirection.CLIENTBOUND, "add_entity").orElseThrow();
        assertEquals("ClientboundAddEntityPacket", add.className());
        assertEquals(PacketDef.Kind.CLASS, add.kind());
        assertTrue(add.custom());
        assertEquals(List.of("id", "uuid", "type", "x", "y", "z", "xa", "ya", "za", "xRot", "yRot", "yHeadRot", "data"),
                add.fields().stream().map(FieldDef::name).toList());
        assertEquals("net.minecraft.world.entity.EntityType", s.resolve(add.fields().get(2).type().name(), add.javaPackage()));
        assertEquals("i16", s.wireOf(add.fields().get(6), add.javaPackage()));
        assertEquals("getUUID", add.fields().get(1).accessor());

        PacketDef team = s.packet("game", PacketDirection.CLIENTBOUND, "set_player_team").orElseThrow();
        assertEquals("optional<struct:ClientboundSetPlayerTeamPacket$Parameters>", s.wireOf(team.fields().get(3), team.javaPackage()));
        PacketDef tags = s.packet("common", PacketDirection.CLIENTBOUND, "update_tags").orElseThrow();
        assertEquals("Map<ResourceKey<? extends Registry<?>>,TagNetworkSerialization.NetworkPayload>", tags.fields().get(0).type().toString());
        assertEquals("map<identifier,tag_payload>", s.wireOf(tags.fields().get(0), tags.javaPackage()));

        PacketDef pos = s.packetByClass("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos").orElseThrow();
        assertEquals("ClientboundMoveEntityPacket", pos.outerName());
        assertEquals("ClientboundMoveEntityPacket", pos.superType().name());
        StructDef base = s.struct("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket").orElseThrow();
        assertEquals(StructDef.Kind.ABSTRACT, base.kind());
        assertEquals(9, base.fields().size());

        assertEquals(PacketDef.Kind.SINGLETON, s.packet("game", PacketDirection.CLIENTBOUND, "chunk_batch_start").orElseThrow().kind());
        TypeDecl slot = s.type("net.minecraft.world.entity.EquipmentSlot").orElseThrow();
        assertEquals(List.of("MAINHAND", "OFFHAND", "FEET", "LEGS", "CHEST", "HEAD", "BODY"), slot.constants());
        assertEquals(TypeDecl.Kind.LIB, s.type("com.mojang.authlib.GameProfile").orElseThrow().kind());
        assertEquals("BossEvent.BossBarColor", s.type("net.minecraft.world.BossEvent$BossBarColor").orElseThrow().simpleName());
    }

    @Test
    void typeRefParsesNestedGenericsAndArrays() {
        TypeRef t = TypeRef.parse("Map<ResourceKey<? extends Registry<?>>, List<byte[]>>");
        assertEquals("Map", t.name());
        assertEquals(TypeRef.Bound.EXTENDS, t.arguments().get(0).arguments().get(0).bound());
        assertEquals(TypeRef.Bound.WILDCARD, t.arguments().get(0).arguments().get(0).arguments().get(0).bound());
        assertEquals(1, t.arguments().get(1).arguments().get(0).arrayDimensions());
        assertEquals("Map<ResourceKey<? extends Registry<?>>,List<byte[]>>", t.toString());
        assertThrows(IllegalArgumentException.class, () -> TypeRef.parse("List<int"));
    }

    @Test
    void reportsErrorsWithLineNumbers() {
        String unknownType = "state game\npacket clientbound x ClientboundXPacket record\n  Frobnicator f\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PacketSchema.parse(1, unknownType));
        assertTrue(e.getMessage().contains("Frobnicator"), e.getMessage());
        assertTrue(e.getMessage().contains("2줄"), e.getMessage());

        String dup = "state game\npacket clientbound x A record\npacket clientbound x B record\n";
        assertThrows(IllegalArgumentException.class, () -> PacketSchema.parse(1, dup));
        String getterOnRecord = "state game\npacket clientbound x A record\n  int a get=getA\n";
        assertThrows(IllegalArgumentException.class, () -> PacketSchema.parse(1, getterOnRecord));
        String noState = "packet clientbound x A record\n";
        assertThrows(IllegalArgumentException.class, () -> PacketSchema.parse(1, noState));
        String badWire = "state game\npacket clientbound x A record\n  int a zigzag\n";
        assertThrows(IllegalArgumentException.class, () -> PacketSchema.parse(1, badWire));
    }
}
