// E4b 손 코덱: 비트 묶음·조건부·순서 다른 와이어 종류. 고정 바이트는 바닐라 1.21.1 jar 의 패킷 코덱으로 읽고 다시 써서 같은 것을 확인한 값이다
// (방법: docs/feature-parity/context-notes/2026-11-12-easy-nms-e4b.md). 서버가 실제로 보낸 프레임 왕복은 furnace-test GenericPacketRoundTripTest.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketField;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes.Clientbound;
import org.domi.furnace.api.packet.PacketTypes.Serverbound;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.plugin.packet.ProtocolCodec;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericHandPacketTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private ProtocolCodec codec;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        codec = core.plugins().packetsImpl().codec();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private static PacketStruct s(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return PacketStruct.of(m);
    }

    @SuppressWarnings("unchecked")
    private static GenericPacket g(PacketType<?> t, Object... kv) { return GenericPacket.of((PacketType<GenericPacket>) t, s(kv)); }

    private String body(GenericPacket p) {
        ByteBuf b = Unpooled.buffer();
        codec.encode(p, b);
        return ByteBufUtil.hexDump(b);
    }

    /** 바닐라와 같은 바이트로 쓰고, 그 바이트를 읽으면 같은 패킷. */
    private void vanilla(String hex, GenericPacket p) {
        assertEquals(hex, body(p), p.type().toString());
        assertEquals(p, codec.decode(p.type(), codec.ids(p.type())[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex))), p.type().toString());
    }

    @Test
    void flagBytesAndWireOrder() {
        vanilla("0d3d4ccccd3dcccccd", g(Clientbound.PLAYER_ABILITIES, "invulnerable", true, "is_flying", false, "can_fly", true, "instabuild", true,
                "flying_speed", 0.05f, "walking_speed", 0.1f));
        // 와이어 순서(진행도, 레벨, 총량)가 바닐라 필드 순서(진행도, 총량, 레벨)와 달라 필드도 와이어 순서다
        vanilla("3f0000001ef30a", g(Clientbound.SET_EXPERIENCE, "experience_progress", 0.5f, "experience_level", 30, "total_experience", 1395));
        assertEquals(List.of(PacketField.of("experience_progress", Float.class), PacketField.of("experience_level", Integer.class),
                PacketField.of("total_experience", Integer.class)), core.plugins().packets().fields(Clientbound.SET_EXPERIENCE));
        vanilla("02", g(Serverbound.PLAYER_ABILITIES, "is_flying", true));
        vanilla("3f7ae148bf00000003", g(Serverbound.PLAYER_INPUT, "xxa", 0.98f, "zza", -0.5f, "is_jumping", true, "is_shift_key_down", true));
        vanilla("000000c000005004067361792068690205", g(Serverbound.SET_COMMAND_BLOCK, "pos", new BlockPos(3, 4, 5), "command", "say hi", "mode", "REDSTONE",
                "track_output", true, "conditional", false, "automatic", true));
        vanilla("000000c00000500403020f6d696e6563726166743a686f757365fd0030300100020301783f00000094b4e4f4cb0305",
                g(Serverbound.SET_STRUCTURE_BLOCK, "pos", new BlockPos(3, 4, 5), "update_type", "SCAN_AREA", "mode", "CORNER", "name", "minecraft:house",
                        "offset", new BlockPos(-3, 0, 48), "size", new BlockPos(48, 1, 0), "mirror", "FRONT_BACK", "rotation", "COUNTERCLOCKWISE_90", "data", "x",
                        "integrity", 0.5f, "seed", 123456789012L, "ignore_entities", true, "show_air", false, "show_bounding_box", true));
        vanilla("ac020001fffe0003804000", g(Clientbound.MOVE_ENTITY_POS_ROT, "entity_id", 300, "xa", (short) 1, "ya", (short) -2, "za", (short) 3,
                "y_rot", (byte) -128, "x_rot", (byte) 64, "on_ground", false));
        vanilla("3ff00000000000004050000000000000c00c00000000000042b40000c12000001505", g(Clientbound.PLAYER_POSITION, "x", 1.0, "y", 64.0, "z", -3.5,
                "y_rot", 90f, "x_rot", -10f, "relative_arguments", List.of("X", "Z", "X_ROT"), "id", 5));
    }

    @Test
    void conditionalParts() {
        vanilla("03051c6d696e6563726166743a656e746974792e7069672e616d6269656e74",
                g(Clientbound.STOP_SOUND, "source", Optional.of("HOSTILE"), "name", Optional.of("minecraft:entity.pig.ambient")));
        vanilla("020d6d79706c7567696e3a626f6f6d", g(Clientbound.STOP_SOUND, "source", Optional.empty(), "name", Optional.of("myplugin:boom")));
        vanilla("0109", g(Clientbound.STOP_SOUND, "source", Optional.of("VOICE"), "name", Optional.empty()));
        vanilla("00", g(Clientbound.STOP_SOUND, "source", Optional.empty(), "name", Optional.empty()));
        vanilla("013ff000000000000040000000000000004008000000000000014d00", g(Clientbound.PLAYER_LOOK_AT, "from_anchor", "EYES", "x", 1.0, "y", 2.0, "z", 3.0,
                "at_entity", Optional.of(s("entity", 77, "to_anchor", "FEET"))));
        vanilla("00146d696e6563726166743a73746f72792f726f6f74",
                g(Serverbound.SEEN_ADVANCEMENTS, "action", "OPENED_TAB", "tab", Optional.of("minecraft:story/root")));
        vanilla("01", g(Serverbound.SEEN_ADVANCEMENTS, "action", "CLOSED_SCREEN", "tab", Optional.empty()));
        vanilla("036f626a01", g(Clientbound.SET_OBJECTIVE, "objective_name", "obj", "method", 1, "parameters", Optional.empty()));
        vanilla("036f626a020800024869000100", g(Clientbound.SET_OBJECTIVE, "objective_name", "obj", "method", 2,
                "parameters", Optional.of(s("display_name", Text.text("Hi"), "render_type", "INTEGER", "number_format", Optional.of(s("type", "minecraft:blank"))))));
        vanilla("0509000000", g(Clientbound.DAMAGE_EVENT, "entity_id", 5, "source_type", "minecraft:fall", "source_cause_id", -1, "source_direct_id", -1,
                "source_position", Optional.empty()));
        vanilla("05000809013ff8000000000000c000000000000000400a000000000000", g(Clientbound.DAMAGE_EVENT, "entity_id", 5, "source_type", "minecraft:arrow",
                "source_cause_id", 7, "source_direct_id", 8, "source_position", Optional.of(s("x", 1.5, "y", -2.0, "z", 3.25))));
    }

    @Test
    void numberFormatsAndBlockEntityTypes() {
        NbtCompound style = new NbtCompound().put("color", "red").put("bold", (byte) 1);
        vanilla("036f626a0208000248690001010a080005636f6c6f720003726564010004626f6c640100", g(Clientbound.SET_OBJECTIVE, "objective_name", "obj", "method", 2,
                "parameters", Optional.of(s("display_name", Text.text("Hi"), "render_type", "INTEGER",
                        "number_format", Optional.of(s("type", "minecraft:styled", "style", style))))));
        vanilla("055374657665036f626a0701080002486901020800024869", g(Clientbound.SET_SCORE, "owner", "Steve", "objective_name", "obj", "score", 7,
                "display", Optional.of(Text.text("Hi")), "number_format", Optional.of(s("type", "minecraft:fixed", "value", Text.text("Hi")))));
        vanilla("0000004000009ffb2b0a00", g(Clientbound.BLOCK_ENTITY_DATA, "pos", new BlockPos(1, -5, 9), "type", "minecraft:vault", "tag", new NbtCompound()));
        // 이름공간 없는 형식 이름은 minecraft: 로 읽힌다
        var blank = g(Clientbound.SET_SCORE, "owner", "a", "objective_name", "o", "score", 1, "display", Optional.empty(),
                "number_format", Optional.of(s("type", "blank")));
        assertTrue(body(blank).endsWith("00" + "01" + "00"), "표시 없음, 형식 있음, blank = 0");
        var e = assertThrows(IllegalArgumentException.class, () -> body(blank.with("number_format", Optional.of(s("type", "minecraft:fixed")))));
        assertTrue(e.getMessage().contains("[type, value]"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> body(blank.with("number_format", Optional.of(s("type", "minecraft:rainbow")))));
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.BLOCK_ENTITY_DATA, "pos", new BlockPos(0, 0, 0), "type", "minecraft:oven",
                "tag", new NbtCompound())));
    }

    @Test
    void listsWithTheirOwnWire() {
        ItemStack dia = new ItemStack("minecraft:diamond", 3);
        String diaHex = body(g(Clientbound.SET_EQUIPMENT, "entity", 9, "slots", List.of(s("slot", "OFFHAND", "item", dia)))).substring(4);
        vanilla("0901" + diaHex, g(Clientbound.SET_EQUIPMENT, "entity", 9, "slots", List.of(s("slot", "OFFHAND", "item", dia))));
        var three = g(Clientbound.SET_EQUIPMENT, "entity", 9, "slots", List.of(s("slot", "MAINHAND", "item", dia), s("slot", "BODY", "item", ItemStack.EMPTY),
                s("slot", "HEAD", "item", new ItemStack("minecraft:diamond_helmet", 1))));
        String h = body(three);
        assertTrue(h.startsWith("0980" + diaHex + "8600" + "05"), "뒤에 더 있으면 0x80, 마지막은 없음: " + h);
        assertEquals(three, codec.decode(three.type(), codec.ids(three.type())[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(h))));

        var blocks = g(Clientbound.SECTION_BLOCKS_UPDATE, "section_pos", 0x123456789L, "positions", List.of((short) 0, (short) 4095, (short) 0x123),
                "states", List.of(BlockData.of("minecraft:stone"), BlockData.AIR, BlockData.of("minecraft:oak_log").with("axis", "z")));
        vanilla("0000000123456789038020ff1fa38221", blocks);

        vanilla("039003fc19010601000503a506000000", g(Serverbound.CONTAINER_CLICK, "container_id", 3, "state_id", 400, "slot_num", -999,
                "button_num", 1, "click_type", "PICKUP_ALL", "changed_slots", List.of(s("slot", 5, "item", dia)), "carried_item", ItemStack.EMPTY));
        vanilla("0001002400010003a5060000", g(Serverbound.CONTAINER_CLICK, "container_id", 0, "state_id", 1, "slot_num", 36, "button_num", 0,
                "click_type", "QUICK_MOVE", "changed_slots", List.of(), "carried_item", dia));
    }

    @Test
    void shapesVanillaCannotSendAreRefused() {
        ItemStack dia = new ItemStack("minecraft:diamond", 1);
        var eq = g(Clientbound.SET_EQUIPMENT, "entity", 1, "slots", List.of());
        var e = assertThrows(IllegalArgumentException.class, () -> body(eq));
        assertTrue(e.getMessage().contains("slots"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> body(eq.with("slots", List.of(s("slot", "TAIL", "item", dia)))));
        assertThrows(IllegalArgumentException.class, () -> body(eq.with("slots", List.of(s("slot", "HEAD")))));

        var obj = g(Clientbound.SET_OBJECTIVE, "objective_name", "o", "method", 1, "parameters", Optional.of(s("display_name", Text.text("x"),
                "render_type", "INTEGER", "number_format", Optional.empty())));
        e = assertThrows(IllegalArgumentException.class, () -> body(obj));
        assertTrue(e.getMessage().contains("method"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> body(obj.with("method", 0).with("parameters", Optional.empty())));

        assertThrows(IllegalArgumentException.class, () -> body(g(Serverbound.SEEN_ADVANCEMENTS, "action", "OPENED_TAB", "tab", Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> body(g(Serverbound.SEEN_ADVANCEMENTS, "action", "CLOSED_SCREEN", "tab", Optional.of("minecraft:a"))));

        var blocks = g(Clientbound.SECTION_BLOCKS_UPDATE, "section_pos", 0L, "positions", List.of((short) 1), "states", List.of());
        assertThrows(IllegalArgumentException.class, () -> body(blocks), "길이가 다르다");
        assertThrows(IllegalArgumentException.class, () -> body(blocks.with("positions", List.of((short) 4096)).with("states", List.of(BlockData.AIR))));

        var pos = g(Clientbound.PLAYER_POSITION, "x", 0.0, "y", 0.0, "z", 0.0, "y_rot", 0f, "x_rot", 0f, "relative_arguments", List.of("X", "X"), "id", 1);
        assertThrows(IllegalArgumentException.class, () -> body(pos));
        assertThrows(IllegalArgumentException.class, () -> body(pos.with("relative_arguments", List.of("W"))));

        var click = g(Serverbound.CONTAINER_CLICK, "container_id", 0, "state_id", 1, "slot_num", 36, "button_num", 0, "click_type", "PICKUP",
                "changed_slots", List.of(s("slot", 1, "item", dia), s("slot", 1, "item", dia)), "carried_item", dia);
        e = assertThrows(IllegalArgumentException.class, () -> body(click));
        assertTrue(e.getMessage().contains("두 번"), e.getMessage());

        var dmg = g(Clientbound.DAMAGE_EVENT, "entity_id", 1, "source_type", "minecraft:fall", "source_cause_id", -2, "source_direct_id", -1,
                "source_position", Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> body(dmg));
        assertThrows(IllegalArgumentException.class, () -> body(dmg.with("source_cause_id", -1).with("source_position", Optional.of(s("x", 1.0, "y", 2.0)))));

        var structure = g(Serverbound.SET_STRUCTURE_BLOCK, "pos", new BlockPos(0, 0, 0), "update_type", "UPDATE_DATA", "mode", "SAVE", "name", "",
                "offset", new BlockPos(0, 200, 0), "size", new BlockPos(0, 0, 0), "mirror", "NONE", "rotation", "NONE", "data", "", "integrity", 1f, "seed", 0L,
                "ignore_entities", false, "show_air", true, "show_bounding_box", false);
        assertThrows(IllegalArgumentException.class, () -> body(structure), "offset 은 i8");
        // 검토 R1: 바닐라는 data 를 readUtf(128) 로 읽는다
        var ok = structure.with("offset", new BlockPos(0, 0, 0));
        body(ok.with("data", "d".repeat(128)));
        e = assertThrows(IllegalArgumentException.class, () -> body(ok.with("data", "d".repeat(129))));
        assertTrue(e.getMessage().contains("128"), e.getMessage());
    }

    @Test
    void styledNumberFormatRejectsStylesVanillaCannotRead() {
        // 검토 R2: 바닐라 클라이언트가 읽지 못하는 스타일은 접속을 끊는다 — 쓰기에서 막는다
        var p = g(Clientbound.SET_SCORE, "owner", "a", "objective_name", "o", "score", 1, "display", Optional.empty(), "number_format", Optional.empty());
        java.util.function.Function<NbtCompound, GenericPacket> styled = st -> p.with("number_format", Optional.of(s("type", "minecraft:styled", "style", st)));
        body(styled.apply(new NbtCompound().put("color", "dark_red").put("bold", (byte) 1).put("italic", 0)));
        body(styled.apply(new NbtCompound().put("color", "#fff").put("insertion", "x").put("zzz", 1)));   // 바닐라 parseColor 는 짧은 16진수도 받고, 모르는 키는 버린다
        body(styled.apply(new NbtCompound()));
        for (NbtCompound bad : List.of(new NbtCompound().put("color", "notacolor"), new NbtCompound().put("color", "#1000000"), new NbtCompound().put("color", "DarkRed"),
                new NbtCompound().put("bold", "yes"), new NbtCompound().put("font", "Bad Font"), new NbtCompound().put("clickEvent", "x"))) {
            assertThrows(IllegalArgumentException.class, () -> body(styled.apply(bad)), bad.toString());
        }
    }

    @Test
    void duplicateChangedSlotsReadAsTheLastValueAndFailedWritesLeaveNoBytes() {
        // 검토 R3: 바닐라·서버는 Int2ObjectMap — 같은 칸이 다시 오면 마지막 값 하나
        ItemStack dia = new ItemStack("minecraft:diamond", 3);
        String one = body(g(Serverbound.CONTAINER_CLICK, "container_id", 0, "state_id", 1, "slot_num", 36, "button_num", 0, "click_type", "PICKUP",
                "changed_slots", List.of(s("slot", 5, "item", ItemStack.EMPTY)), "carried_item", ItemStack.EMPTY));
        String entry = one.substring(14, one.length() - 2);   // 앞 6바이트(창·상태·칸·버튼·종류) + 개수 다음, 0005 + 빈 아이템
        String diaEntry = body(g(Serverbound.CONTAINER_CLICK, "container_id", 0, "state_id", 1, "slot_num", 36, "button_num", 0, "click_type", "PICKUP",
                "changed_slots", List.of(s("slot", 5, "item", dia)), "carried_item", ItemStack.EMPTY));
        diaEntry = diaEntry.substring(14, diaEntry.length() - 2);
        String twice = one.substring(0, 12) + "02" + entry + diaEntry + "00";
        var read = (GenericPacket) codec.decode(Serverbound.CONTAINER_CLICK, codec.ids(Serverbound.CONTAINER_CLICK)[0],
                Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(twice)));
        assertEquals(List.of(s("slot", 5, "item", dia)), read.get("changed_slots"));
        body(read);   // 다시 쓸 수 있다

        // 검토 R8: 쓰기에 실패하면 버퍼에 아무것도 남기지 않는다
        ByteBuf b = Unpooled.buffer();
        b.writeByte(0x7F);
        var bad = g(Clientbound.SET_EQUIPMENT, "entity", 9, "slots", List.of(s("slot", "HEAD", "item", dia), s("slot", "TAIL", "item", dia)));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(bad, b));
        assertEquals(1, b.writerIndex());
    }

    // ------------------------------------------------------------------ E4b-2 (고정 바이트는 바닐라 jar 코덱으로 확인)

    private static final java.util.UUID BOSS = new java.util.UUID(0x1122334455667788L, 0x99AABBCCDDEEFF00L);

    @Test
    void bossBarOperationsAndTeams() {
        Text hi = Text.text("Hi");
        String id = "112233445566778899aabbccddeeff00";
        vanilla(id + "0008000248693f000000050405", g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "ADD", "name", hi, "progress", 0.5f,
                "color", "PURPLE", "overlay", "NOTCHED_20", "darken_screen", true, "play_music", false, "create_world_fog", true)));
        vanilla(id + "01", g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "REMOVE")));
        vanilla(id + "023e800000", g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "UPDATE_PROGRESS", "progress", 0.25f)));
        vanilla(id + "040600", g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "UPDATE_STYLE", "color", "WHITE", "overlay", "PROGRESS")));
        vanilla(id + "0502", g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "UPDATE_PROPERTIES", "darken_screen", false, "play_music", true,
                "create_world_fog", false)));
        var e = assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "UPDATE_NAME"))));
        assertTrue(e.getMessage().contains("name"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.BOSS_EVENT, "id", BOSS, "operation", s("type", "HIDE"))));

        vanilla("047465616d000800024869031168696465466f724f746865725465616d73056e657665721508000248690800000205537465766504416c6578",
                g(Clientbound.SET_PLAYER_TEAM, "name", "team", "method", 0, "parameters", Optional.of(s("display_name", hi, "options", 3,
                        "nametag_visibility", "hideForOtherTeams", "collision_rule", "never", "color", "RESET", "player_prefix", hi, "player_suffix", Text.empty())),
                        "players", Optional.of(List.of("Steve", "Alex"))));
        vanilla("047465616d01", g(Clientbound.SET_PLAYER_TEAM, "name", "team", "method", 1, "parameters", Optional.empty(), "players", Optional.empty()));
        vanilla("047465616d0401055374657665", g(Clientbound.SET_PLAYER_TEAM, "name", "team", "method", 4, "parameters", Optional.empty(),
                "players", Optional.of(List.of("Steve"))));
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.SET_PLAYER_TEAM, "name", "team", "method", 3, "parameters", Optional.empty(),
                "players", Optional.empty())), "method 3 은 선수 목록이 있어야 한다");
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.SET_PLAYER_TEAM, "name", "team", "method", 2, "parameters", Optional.of(s("display_name", hi,
                "options", 0, "nametag_visibility", "x".repeat(41), "collision_rule", "always", "color", "RED", "player_prefix", hi, "player_suffix", hi)),
                "players", Optional.empty())), "바닐라는 40자까지 읽는다");
    }

    @Test
    void attributesMapsAndStats() {
        vanilla("0502153fb999999999999a02136d696e6563726166743a737072696e74696e673fd3333333333333020a6d79706c7567696e3a78bff00000000000000012403400000000000000",
                g(Clientbound.UPDATE_ATTRIBUTES, "entity_id", 5, "attributes", List.of(
                        s("attribute", "minecraft:generic.movement_speed", "base", 0.1, "modifiers", List.of(
                                s("id", "minecraft:sprinting", "amount", 0.3, "operation", "ADD_MULTIPLIED_TOTAL"),
                                s("id", "myplugin:x", "amount", -1.0, "operation", "ADD_VALUE"))),
                        s("attribute", "minecraft:generic.max_health", "base", 20.0, "modifiers", List.of()))));
        vanilla("070201010200fd640f0108000248691a0000000002010aff0204fb", g(Clientbound.MAP_ITEM_DATA, "map_id", 7, "scale", (byte) 2, "locked", true,
                "decorations", Optional.of(List.of(s("type", "minecraft:player", "x", (byte) -3, "y", (byte) 100, "rot", (byte) 15, "name", Optional.of(Text.text("Hi"))),
                        s("type", "minecraft:red_x", "x", (byte) 0, "y", (byte) 0, "rot", (byte) 0, "name", Optional.empty()))),
                "color_patch", Optional.of(s("width", 2, "height", 1, "start_x", 10, "start_y", 255, "map_colors", new byte[]{4, -5}))));
        var empty = g(Clientbound.MAP_ITEM_DATA, "map_id", 0, "scale", (byte) 0, "locked", false, "decorations", Optional.empty(), "color_patch", Optional.empty());
        vanilla("0000000000", empty);
        assertThrows(IllegalArgumentException.class, () -> body(empty.with("color_patch", Optional.of(s("width", 0, "height", 1, "start_x", 0, "start_y", 0,
                "map_colors", new byte[0])))), "width 0 은 없음과 같은 바이트");
        // 통계 하나씩(바닐라는 해시 맵이라 둘 이상이면 다시 쓴 순서가 다를 수 있다)
        vanilla("01000103", g(Clientbound.AWARD_STATS, "stats", List.of(s("type", "minecraft:mined", "value", "minecraft:stone", "amount", 3))));
        vanilla("010815ac02", g(Clientbound.AWARD_STATS, "stats", List.of(s("type", "minecraft:custom", "value", "minecraft:jump", "amount", 300))));
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.AWARD_STATS, "stats", List.of(
                s("type", "minecraft:mined", "value", "minecraft:stone", "amount", 3), s("type", "mined", "value", "stone", "amount", 4)))), "같은 통계 두 번");
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.AWARD_STATS, "stats", List.of(
                s("type", "minecraft:mined", "value", "minecraft:zombie", "amount", 3)))), "블록 통계에 엔티티 이름");
    }

    @Test
    void chatAndCommonKinds() throws Exception {
        Text hi = Text.text("Hi");
        vanilla("2a", g(Clientbound.DELETE_CHAT, "message_signature", s("id", 41, "full_signature", Optional.empty())));
        byte[] sig = new byte[256];
        for (int i = 0; i < 256; i++) sig[i] = (byte) i;
        var full = g(Clientbound.DELETE_CHAT, "message_signature", s("id", -1, "full_signature", Optional.of(sig)));
        assertEquals("00" + ByteBufUtil.hexDump(sig), body(full));
        assertEquals(full, codec.decode(full.type(), codec.ids(full.type())[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(body(full)))));
        assertThrows(IllegalArgumentException.class, () -> body(full.with("message_signature", s("id", 3, "full_signature", Optional.of(sig)))));
        assertThrows(IllegalArgumentException.class, () -> body(full.with("message_signature", s("id", -1, "full_signature", Optional.of(new byte[255])))));

        vanilla("0800024869050800024869010800024869", g(Clientbound.DISGUISED_CHAT, "message", hi, "chat_type",
                s("chat_type", "minecraft:say_command", "name", hi, "target_name", Optional.of(hi))));
        vanilla("0201090968747470733a2f2f610008000248690968747470733a2f2f62", g(Clientbound.SERVER_LINKS, "links", List.of(
                s("known_type", Optional.of("ANNOUNCEMENTS"), "label", Optional.empty(), "url", "https://a"),
                s("known_type", Optional.empty(), "label", Optional.of(hi), "url", "https://b"))));
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.SERVER_LINKS, "links", List.of(
                s("known_type", Optional.of("NEWS"), "label", Optional.of(hi), "url", "https://a")))), "종류와 이름 둘 다는 안 된다");
        vanilla("0106706c7567696e0178", g(Clientbound.CUSTOM_REPORT_DETAILS, "details", s("plugin", "x")));
        assertThrows(IllegalArgumentException.class, () -> body(g(Clientbound.CUSTOM_REPORT_DETAILS, "details", s("k".repeat(129), "x"))), "키는 128자까지");

        var signed = g(Serverbound.CHAT_COMMAND_SIGNED, "command", "msg Steve hi", "time_stamp", 1700000000123L, "salt", -5L,
                "argument_signatures", List.of(s("name", "message", "signature", sig)), "last_seen_messages", s("offset", 3, "acknowledged", 0xF0F01));
        assertEquals("0c6d73672053746576652068690000018bcfe5687bfffffffffffffffb01076d657373616765" + ByteBufUtil.hexDump(sig) + "03010f0f",
                body(signed));
        assertEquals(signed, codec.decode(signed.type(), codec.ids(signed.type())[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(body(signed)))));
        assertThrows(IllegalArgumentException.class, () -> body(signed.with("last_seen_messages", s("offset", 0, "acknowledged", 1 << 20))), "20비트까지");

        var kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        byte[] key = kpg.generateKeyPair().getPublic().getEncoded();
        var session = g(Serverbound.CHAT_SESSION_UPDATE, "chat_session", s("session_id", BOSS, "profile_public_key",
                s("expires_at", 1800000000000L, "key", key, "key_signature", new byte[]{1, 2, 3})));
        assertEquals(session, codec.decode(session.type(), codec.ids(session.type())[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(body(session)))));
        var e = assertThrows(IllegalArgumentException.class, () -> body(session.with("chat_session", s("session_id", BOSS, "profile_public_key",
                s("expires_at", 0L, "key", new byte[]{1, 2, 3}, "key_signature", new byte[0])))));
        assertTrue(e.getMessage().contains("RSA"), e.getMessage());
    }
}
