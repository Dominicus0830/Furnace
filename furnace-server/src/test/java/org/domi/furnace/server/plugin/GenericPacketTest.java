// E4a 범용 패킷: 어떤 종류를 읽는지, 바닐라 jar 로 확인한 와이어(고정 바이트), 쓰기 검사, 리스너에서 바꾸기.
// 서버가 실제로 보내는 종류의 바이트 왕복은 furnace-test GenericPacketRoundTripTest.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketField;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.PacketTypes.Clientbound;
import org.domi.furnace.api.packet.PacketTypes.Serverbound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.plugin.packet.Protocol767;
import org.domi.furnace.server.plugin.packet.ProtocolCodec;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericPacketTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private ProtocolCodec codec;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();
    private final Map<Player, EmbeddedChannel> channels = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        codec = host.packetsImpl().codec();
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player online(String name) {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, ch);
        channels.put(p, ch);
        core.world().playerOnline(p);
        host.packetsImpl().attach(p);
        return p;
    }

    private List<byte[]> sent(Player p) {
        EmbeddedChannel ch = channels.get(p);
        ch.runPendingTasks();
        List<byte[]> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(ByteBufUtil.getBytes(bb));
            bb.release();
        }
        return out;
    }

    private byte[] body(GenericPacket p) {
        ByteBuf b = Unpooled.buffer();
        codec.encode(p, b);
        return ByteBufUtil.getBytes(b);
    }

    private GenericPacket decode(PacketType<?> t, byte[] body) {
        return (GenericPacket) codec.decode(t, codec.ids(t)[0], Unpooled.wrappedBuffer(body));
    }

    private static byte[] bytes(int... v) {
        byte[] a = new byte[v.length];
        for (int i = 0; i < v.length; i++) a[i] = (byte) v[i];
        return a;
    }

    // ------------------------------------------------------------------ 표

    @Test
    void everyGenericKindIsReadableOrSaysWhy() {
        var proto = (Protocol767) codec;
        int readable = 0;
        for (PacketDirection d : PacketDirection.values()) {
            for (PacketType<?> t : PacketTypes.all(d)) {
                if (t.typed()) {
                    assertTrue(codec.readable(t), t + " 타입 레코드는 늘 읽힌다");
                    assertEquals(List.of(), host.packets().fields(t));
                } else if (codec.readable(t)) {
                    readable++;
                    assertNull(proto.whyUnreadable(t));
                    assertTrue(host.packets().isReadable(t));
                } else {
                    assertNotNull(proto.whyUnreadable(t), t + " 못 읽는 까닭");
                    assertFalse(host.packets().isReadable(t));
                    assertEquals(List.of(), host.packets().fields(t));
                }
            }
        }
        assertEquals(102, readable, "E4a 에서 범용 코덱이 읽는 종류 수(스키마에서 custom 이 아니고 와이어를 아는 것)");
        assertFalse(codec.readable(Clientbound.BUNDLE_DELIMITER), "custom 은 E4b");
    }

    @Test
    void fieldNamesAreSnakeCaseInVanillaOrder() {
        assertEquals(List.of(PacketField.of("camera_id", Integer.class)), host.packets().fields(Clientbound.SET_CAMERA));
        assertEquals(List.of("id", "x", "y", "z", "value"),
                host.packets().fields(Clientbound.ADD_EXPERIENCE_ORB).stream().map(PacketField::name).toList());
        assertEquals(List.of(PacketField.of("entity_id", Integer.class), PacketField.of("event_id", Byte.class)),
                host.packets().fields(Clientbound.ENTITY_EVENT));
        assertEquals(PacketField.of("is_front_text", Boolean.class), host.packets().fields(Clientbound.OPEN_SIGN_EDITOR).get(1));
    }

    // ------------------------------------------------------------------ 바닐라 jar 로 확인한 와이어

    @Test
    void simpleKindsWriteVanillaBytes() {
        assertArrayEquals(bytes(0xAC, 0x02), body(GenericPacket.of(Clientbound.SET_CAMERA).with("camera_id", 300)));
        // horse_screen_open: u8 containerId, varint 열 수, i32 엔티티(바닐라 vw.k·vw.c·vw.p)
        assertArrayEquals(bytes(0xFF, 0x05, 0, 0, 0x01, 0x00), body(GenericPacket.of(Clientbound.HORSE_SCREEN_OPEN)
                .with("container_id", 255).with("inventory_columns", 5).with("entity_id", 256)));
        // container_set_data: u8, i16, i16
        assertArrayEquals(bytes(0x02, 0xFF, 0xFF, 0x00, 0x07), body(GenericPacket.of(Clientbound.CONTAINER_SET_DATA)
                .with("container_id", 2).with("id", -1).with("value", 7)));
        assertEquals(GenericPacket.of(Clientbound.SET_CAMERA).with("camera_id", 300), decode(Clientbound.SET_CAMERA, bytes(0xAC, 0x02)));
    }

    @Test
    void nullableTagIsEndTagNotBoolHeader() {
        // 바닐라 ClientboundTagQueryPacket 은 writeNbt(tag) — 없으면 END(0) 한 바이트, 있으면 이름 없는 compound(bool 머리 없음)
        var empty = GenericPacket.of(Clientbound.TAG_QUERY).with("transaction_id", 5).with("tag", Optional.empty());
        assertArrayEquals(bytes(0x05, 0x00), body(empty));
        assertEquals(empty, decode(Clientbound.TAG_QUERY, bytes(0x05, 0x00)));
        var some = decode(Clientbound.TAG_QUERY, bytes(0x05, 0x0A, 0x00));   // 빈 compound
        assertEquals(Optional.of(new NbtCompound()), some.get("tag"));
        assertArrayEquals(bytes(0x05, 0x0A, 0x00), body(some));
    }

    @Test
    void soundHolderIsIdPlusOneOrDirectName() {
        int pig = ItemContext.id("minecraft:sound_event", "minecraft:entity.pig.ambient");
        var p = GenericPacket.of(Clientbound.SOUND_ENTITY).with("sound", "minecraft:entity.pig.ambient").with("source", "MASTER").with("id", 9)
                .with("volume", 1f).with("pitch", 1f).with("seed", 0L);
        ByteBuf b = Unpooled.wrappedBuffer(body(p));
        assertEquals(pig + 1, Types.readVarInt(b), "등록된 소리 = 번호 + 1(바닐라 holder(SOUND_EVENT, 직접 코덱))");
        assertEquals(0, Types.readVarInt(b), "SoundSource.MASTER 서수");
        assertEquals(p, decode(Clientbound.SOUND_ENTITY, body(p)));

        var custom = p.with("sound", "myplugin:boom");
        ByteBuf c = Unpooled.wrappedBuffer(body(custom));
        assertEquals(0, Types.readVarInt(c), "등록 안 된 소리는 직접 정의");
        assertEquals("myplugin:boom", Types.readString(c, 100));
        assertFalse(c.readBoolean(), "고정 범위 없음");
        assertEquals(custom, decode(Clientbound.SOUND_ENTITY, body(custom)));
    }

    @Test
    void respawnSpawnInfoStructAndNullableGameType() {
        var info = PacketStruct.of(new LinkedHashMap<>(Map.of())).with("dimension_type", "minecraft:overworld").with("dimension", "minecraft:overworld")
                .with("seed", 7L).with("game_type", "CREATIVE").with("previous_game_type", Optional.empty()).with("is_debug", false)
                .with("is_flat", true).with("last_death_location", Optional.empty()).with("portal_cooldown", 0);
        var p = GenericPacket.of(Clientbound.RESPAWN).with("common_player_spawn_info", info).with("data_to_keep", (byte) 3);
        byte[] b = body(p);
        // dimension_type 번호(holderRegistry, 0) · "minecraft:overworld"(1+19) · seed 8 · game_type u8 · previous i8
        assertEquals(0, b[0]);
        assertEquals(1, b[29], "CREATIVE = 1");
        assertEquals(-1, b[30], "이전 게임 모드 없음 = -1(바닐라 GameType.getNullableId)");
        assertEquals(p, decode(Clientbound.RESPAWN, b));
        var withPrev = p.with("common_player_spawn_info", info.with("previous_game_type", Optional.of("SURVIVAL")));
        assertEquals(0, body(withPrev)[30]);
    }

    @Test
    void stringEnumAndServerboundKinds() {
        var jig = GenericPacket.of(Serverbound.SET_JIGSAW_BLOCK).with("pos", new BlockPos(1, 2, 3)).with("name", "minecraft:a").with("target", "minecraft:b")
                .with("pool", "minecraft:c").with("final_state", "minecraft:air").with("joint", "ROLLABLE").with("selection_priority", 0).with("placement_priority", 0);
        byte[] b = body(jig);
        assertTrue(new String(b, StandardCharsets.ISO_8859_1).contains("rollable"), "바닐라 getSerializedName(소문자)");
        assertEquals(jig, decode(Serverbound.SET_JIGSAW_BLOCK, b));
        var book = GenericPacket.of(Serverbound.EDIT_BOOK).with("slot", 3).with("pages", List.of("a", "b")).with("title", Optional.of("t"));
        assertEquals(book, decode(Serverbound.EDIT_BOOK, body(book)));
    }

    // ------------------------------------------------------------------ 쓰기 검사

    @Test
    void encodingChecksFieldsKindsAndRanges() {
        var e = assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.SET_CAMERA)));
        assertTrue(e.getMessage().contains("빠진 필드 [camera_id]"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.SET_CAMERA).with("camera_id", 1).with("cameraId", 1)));
        assertTrue(e.getMessage().contains("모르는 필드 [cameraId]"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.SET_CAMERA).with("camera_id", 1L)));
        assertTrue(e.getMessage().contains("Integer"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.CONTAINER_CLOSE).with("container_id", 256)));
        assertTrue(e.getMessage().contains("u8"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.OPEN_BOOK).with("hand", "LEFT_HAND")));
        assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.unreadable(Clientbound.SET_CAMERA)));
        assertThrows(IllegalArgumentException.class, () -> body(GenericPacket.of(Clientbound.BUNDLE_DELIMITER)), "못 읽는 종류는 못 보낸다");
        assertThrows(IllegalArgumentException.class, () -> decode(Clientbound.SET_CAMERA, bytes(0x01, 0x02)), "본문이 남으면 실패");
    }

    // ------------------------------------------------------------------ 보내기·리스너

    @Test
    void listenerReadsAndChangesGenericPacketsAndUnreadableOnesAreObservable() {
        Player p = online("P");
        var api = host.player(p);
        List<GenericPacket> seen = new ArrayList<>();
        host.packets().listen(plugin, Clientbound.GAME_EVENT, e -> {
            seen.add(e.packet());
            if (e.packet().get("event").equals(1)) e.setPacket(e.packet().with("param", 0.5f));
        });
        host.packets().listen(plugin, Clientbound.SET_CAMERA, EventPriority.NORMAL, e -> e.setCancelled(true));
        List<GenericPacket> particles = new ArrayList<>();
        host.packets().listen(plugin, Clientbound.LEVEL_PARTICLES, e -> {   // 아직 못 읽는 종류(custom): 내용 없이 관찰·취소만
            particles.add(e.packet());
            assertThrows(UnsupportedOperationException.class, () -> e.setPacket(GenericPacket.of(Clientbound.LEVEL_PARTICLES)));
            e.setCancelled(true);
        });
        // 서버 자신의 패킷(바이트로 나간 것)도 리스너가 이름 붙은 필드로 본다
        p.send(new org.domi.furnace.server.net.protocol.play.GameEvent(1, 0f));
        host.packets().send(api, GenericPacket.of(Clientbound.SET_CAMERA).with("camera_id", 4));
        p.send(new org.domi.furnace.server.net.protocol.RawPacket(codec.ids(Clientbound.LEVEL_PARTICLES)[0], new byte[]{1, 2, 3}, null, false));
        List<byte[]> out = sent(p);
        assertEquals(1, out.size(), "set_camera·입자는 취소됐다");
        ByteBuf b = Unpooled.wrappedBuffer(out.get(0));
        assertEquals(codec.ids(Clientbound.GAME_EVENT)[0], Types.readVarInt(b));
        assertEquals(1, b.readUnsignedByte());
        assertEquals(0.5f, b.readFloat(), "리스너가 바꾼 값");
        assertEquals(List.of(GenericPacket.of(Clientbound.GAME_EVENT).with("event", 1).with("param", 0f)), seen);
        assertEquals(List.of(GenericPacket.unreadable(Clientbound.LEVEL_PARTICLES)), particles);
    }
}
