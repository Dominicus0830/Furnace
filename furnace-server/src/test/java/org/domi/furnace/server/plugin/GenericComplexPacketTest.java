// E5 범용 패킷 12종(입자·폭발·청크·빛·명령 트리·레시피 둘·발전 과제·태그·서명 채팅·상인 거래·번들 구분자): 바닐라 1.21.1 jar 로 확인한 바이트,
// 쓰기 검사(바닐라 클라이언트가 끊기는 값), 서버 자신의 패킷 읽기. 넓은 표본 대조(132 프레임)는 docs/feature-parity/context-notes/2026-11-12-easy-nms-e5.md.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes.Clientbound;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.plugin.packet.ProtocolCodec;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericComplexPacketTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private ProtocolCodec codec;

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

    private static PacketStruct s(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return PacketStruct.of(m);
    }

    @SuppressWarnings("unchecked")
    private static GenericPacket g(PacketType<?> t, Object... kv) { return GenericPacket.of((PacketType<GenericPacket>) t, s(kv)); }

    private String hex(GenericPacket p) {
        ByteBuf b = Unpooled.buffer();
        codec.encode(p, b);
        return ByteBufUtil.hexDump(b);
    }

    private GenericPacket decode(PacketType<?> t, String hex) {
        return (GenericPacket) codec.decode(t, codec.ids(t)[0], Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex)));
    }

    /** 바닐라 jar 로 읽고 다시 쓴 바이트와 같은 본문 — 쓰면 그 바이트, 읽으면 같은 값. */
    private void vanilla(GenericPacket p, String hex) {
        assertEquals(hex, hex(p), p.type().toString());
        assertEquals(p, decode(p.type(), hex), p.type() + " 되읽기");
    }

    private String rejected(GenericPacket p) {
        return assertThrows(IllegalArgumentException.class, () -> hex(p)).getMessage();
    }

    private static PacketStruct v3(float x, float y, float z) { return s("x", x, "y", y, "z", z); }

    private static final PacketStruct NO_LIGHT = s("sky_y_mask", new long[0], "block_y_mask", new long[0], "empty_sky_y_mask", new long[0],
            "empty_block_y_mask", new long[0], "sky_updates", List.of(), "block_updates", List.of());

    // ------------------------------------------------------------------ 바닐라 바이트

    @Test
    void particlesExplosionAndLightMatchVanillaBytes() {
        vanilla(g(Clientbound.LEVEL_PARTICLES, "override_limiter", true, "x", 1.5, "y", -2.25, "z", 1e7, "x_dist", 0.1f, "y_dist", 0.2f, "z_dist", 0.3f,
                "max_speed", 1f, "count", 12, "particle", s("type", "minecraft:dust", "color", v3(1f, 0.5f, 0f), "scale", 2.5f)),
                "013ff8000000000000c002000000000000416312d0000000003dcccccd3e4ccccd3e99999a3f8000000000000c0d3f8000003f0000000000000040200000");
        vanilla(g(Clientbound.LEVEL_PARTICLES, "override_limiter", false, "x", 0.0, "y", 0.0, "z", 0.0, "x_dist", 0f, "y_dist", 0f, "z_dist", 0f, "max_speed", 0f,
                "count", 0, "particle", s("type", "minecraft:vibration", "destination", s("type", "minecraft:entity", "entity_id", 42, "y_offset", 1.62f), "arrival_in_ticks", 5)),
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002d012a3fcf5c2905");
        // to_blow 는 폭발 중심 칸(floor)에서 i8 차이 — -138·191 같은 먼 칸도 i8 안이면 그대로
        vanilla(g(Clientbound.EXPLODE, "x", -10.5, "y", 64.0, "z", 3.99, "power", 4f,
                "to_blow", List.of(new BlockPos(-11, 64, 3), new BlockPos(-138, 191, -124), new BlockPos(116, 64, 130)),
                "knockback_x", 0.1f, "knockback_y", 0.2f, "knockback_z", -0.3f, "block_interaction", "DESTROY",
                "small_explosion_particles", s("type", "minecraft:explosion"), "large_explosion_particles", s("type", "minecraft:dust", "color", v3(1f, 0.5f, 0f), "scale", 2.5f),
                "explosion_sound", "minecraft:entity.generic.explode"),
                "c0250000000000004050000000000000400feb851eb851ec4080000003000000817f817f007f3dcccccd3e4ccccdbe99999a01160d3f8000003f0000000000000040200000c104");
        vanilla(g(Clientbound.LIGHT_UPDATE, "x", -5, "z", 100000, "light_data", NO_LIGHT), "fbffffff0fa08d06000000000000");
        vanilla(g(Clientbound.BUNDLE_DELIMITER), "");
    }

    @Test
    void recipeBookTagsMerchantAndChatMatchVanillaBytes() {
        PacketStruct settings = s("crafting_open", true, "crafting_filtering", false, "furnace_open", false, "furnace_filtering", true,
                "blast_furnace_open", true, "blast_furnace_filtering", true, "smoker_open", false, "smoker_filtering", true);
        vanilla(g(Clientbound.RECIPE, "state", "ADD", "book_settings", settings, "recipes", List.of("minecraft:a"), "to_highlight", Optional.empty()),
                "010100000101010001010b6d696e6563726166743a61");
        vanilla(g(Clientbound.UPDATE_TAGS, "tags", List.of(
                s("registry", "minecraft:block", "tags", List.of(s("name", "minecraft:logs", "entries", new int[]{1, 2, 300}), s("name", "x:empty", "entries", new int[0]))),
                s("registry", "minecraft:item", "tags", List.of()))),
                "020f6d696e6563726166743a626c6f636b020e6d696e6563726166743a6c6f6773030102ac0207783a656d707479000e6d696e6563726166743a6974656d00");
        ItemStack named = new ItemStack("minecraft:diamond_sword", 2, Map.of("minecraft:custom_name", "'\"Hi\"'", "minecraft:max_stack_size", "16"), Set.of());
        ItemStack book = new ItemStack("minecraft:book", 2, Map.of("minecraft:custom_name", "'\"Hi\"'", "minecraft:max_stack_size", "16"), Set.of());
        vanilla(g(Clientbound.MERCHANT_OFFERS, "container_id", 5, "offers", List.of(
                        s("base_cost_a", new ItemStack("minecraft:emerald", 3), "result", named, "cost_b", Optional.of(book), "out_of_stock", false,
                                "uses", 1, "max_uses", 12, "xp", 5, "special_price_diff", -2, "price_multiplier", 0.05f, "demand", 3),
                        s("base_cost_a", new ItemStack("minecraft:emerald", 64), "result", new ItemStack("minecraft:bread", 6), "cost_b", Optional.empty(), "out_of_stock", true,
                                "uses", 12, "max_uses", 12, "xp", 0, "special_price_diff", 0, "price_multiplier", 0.2f, "demand", 0)),
                        "villager_level", 3, "villager_xp", 120, "show_progress", true, "can_restock", false),
                "0502a606030002c60602000508000248690110019d070202050800024869011000000000010000000c00000005fffffffe3d4ccccd00000003a606400006d706000000010000000c0000000c00000000000000003e4ccccd0000000003780100");
        // 플레이어 채팅 — 바닐라 jar 가 쓴 프레임(서명·마지막 확인 목록·부분 거름 / 서명 없음·채팅 종류 대상 이름)을 읽고 그대로 다시 쓴다.
        UUID u = new UUID(0x1122334455667788L, 0x99AABBCCDDEEFF00L);
        GenericPacket chat = vanillaRoundTrip(Clientbound.PLAYER_CHAT, CHAT_PARTIALLY_FILTERED);
        assertEquals(u, chat.fields().get("sender"));
        assertEquals("hello", ((PacketStruct) chat.fields().get("body")).get("content"));
        assertEquals("PARTIALLY_FILTERED", ((PacketStruct) chat.fields().get("filter_mask")).get("type"));
        assertTrue(((Optional<?>) chat.fields().get("signature")).isPresent());
        chat = vanillaRoundTrip(Clientbound.PLAYER_CHAT, CHAT_UNSIGNED);
        assertEquals(Optional.empty(), chat.fields().get("signature"));
        assertEquals("PASS_THROUGH", ((PacketStruct) chat.fields().get("filter_mask")).get("type"));
    }

    private static final String CHAT_PARTIALLY_FILTERED = "112233445566778899aabbccddeeff000301000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff0568656c6c6f000001931f7b627bffffffffffffffb3020500000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff0108000268690201000000000000000a01080003426f6200";
    private static final String CHAT_UNSIGNED = "112233445566778899aabbccddeeff0000008002787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878787878780000000000000000000000000000000000000005080001410108000142";

    /** 바닐라 본문을 읽어 다시 쓰면 같은 바이트. */
    private GenericPacket vanillaRoundTrip(PacketType<?> t, String hex) {
        GenericPacket p = decode(t, hex);
        assertEquals(hex, hex(p), t + " 다시 쓰기");
        return p;
    }

    // ------------------------------------------------------------------ 쓰기 검사

    @Test
    void writesThatVanillaClientsCannotTakeAreRefused() {
        PacketStruct ex = s("type", "minecraft:explosion");
        assertTrue(rejected(g(Clientbound.EXPLODE, "x", 0.0, "y", 0.0, "z", 0.0, "power", 1f, "to_blow", List.of(new BlockPos(128, 0, 0)), "knockback_x", 0f,
                "knockback_y", 0f, "knockback_z", 0f, "block_interaction", "KEEP", "small_explosion_particles", ex, "large_explosion_particles", ex,
                "explosion_sound", "minecraft:entity.generic.explode")).contains("i8"));
        PacketStruct light = s("sky_y_mask", new long[]{3}, "block_y_mask", new long[0], "empty_sky_y_mask", new long[0], "empty_block_y_mask", new long[0],
                "sky_updates", List.of(new byte[2048]), "block_updates", List.of());
        assertTrue(rejected(g(Clientbound.LIGHT_UPDATE, "x", 0, "z", 0, "light_data", light)).contains("비트 수"));
        light = s("sky_y_mask", new long[]{1}, "block_y_mask", new long[0], "empty_sky_y_mask", new long[0], "empty_block_y_mask", new long[0],
                "sky_updates", List.of(new byte[10]), "block_updates", List.of());
        assertTrue(rejected(g(Clientbound.LIGHT_UPDATE, "x", 0, "z", 0, "light_data", light)).contains("2048"));
        assertTrue(rejected(g(Clientbound.LEVEL_PARTICLES, "override_limiter", false, "x", 0.0, "y", 0.0, "z", 0.0, "x_dist", 0f, "y_dist", 0f, "z_dist", 0f,
                "max_speed", 0f, "count", 1, "particle", s("type", "minecraft:item", "item_stack", ItemStack.EMPTY))).contains("빈 아이템"));
        rejected(g(Clientbound.LEVEL_PARTICLES, "override_limiter", false, "x", 0.0, "y", 0.0, "z", 0.0, "x_dist", 0f, "y_dist", 0f, "z_dist", 0f,
                "max_speed", 0f, "count", 1, "particle", s("type", "minecraft:flame", "scale", 1f)));   // 모르는 필드
        rejected(g(Clientbound.LEVEL_PARTICLES, "override_limiter", false, "x", 0.0, "y", 0.0, "z", 0.0, "x_dist", 0f, "y_dist", 0f, "z_dist", 0f,
                "max_speed", 0f, "count", 1, "particle", s("type", "minecraft:nope")));
        // 먼지 크기는 바닐라 생성자처럼 0.01~4 로 죈다
        String clamped = hex(g(Clientbound.LEVEL_PARTICLES, "override_limiter", false, "x", 0.0, "y", 0.0, "z", 0.0, "x_dist", 0f, "y_dist", 0f, "z_dist", 0f,
                "max_speed", 0f, "count", 1, "particle", s("type", "dust", "color", v3(0, 0, 0), "scale", 9f)));
        assertTrue(clamped.endsWith("40800000"), clamped);
    }

    private static PacketStruct node(String type, int[] children, Optional<Integer> redirect, String name, PacketStruct arg) {
        return s("type", type, "executable", false, "children", children, "redirect", redirect, "name", Optional.ofNullable(name),
                "argument_type", Optional.ofNullable(arg), "suggestion_id", Optional.empty());
    }

    @Test
    void commandTreesMustResolveLikeTheVanillaClient() {
        PacketStruct root = node("ROOT", new int[]{1, 2}, Optional.empty(), null, null);
        PacketStruct tp = node("LITERAL", new int[]{3}, Optional.empty(), "tp", null);
        PacketStruct alias = node("LITERAL", new int[0], Optional.of(1), "teleport", null);
        PacketStruct arg = node("ARGUMENT", new int[0], Optional.empty(), "n", s("parser", "brigadier:integer", "min", Optional.of(0), "max", Optional.empty()));
        GenericPacket ok = g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, arg), "root_index", 0);
        assertEquals(ok, decode(Clientbound.COMMANDS, hex(ok)));
        // 자식 순환(1 → 3 → 1)·리다이렉트 순환(2 ↔ 4)은 바닐라 validateEntries 가 막는다
        PacketStruct loop = node("ARGUMENT", new int[]{1}, Optional.empty(), "n", s("parser", "brigadier:bool"));
        assertTrue(rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, loop), "root_index", 0)).contains("순환"));
        PacketStruct r2 = node("LITERAL", new int[0], Optional.of(3), "a", null), r3 = node("LITERAL", new int[0], Optional.of(2), "b", null);
        assertTrue(rejected(g(Clientbound.COMMANDS, "entries", List.of(node("ROOT", new int[]{2, 3}, Optional.empty(), null, null), tp, r2, r3), "root_index", 0)).contains("리다이렉트"));
        assertTrue(rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, arg), "root_index", 1)).contains("ROOT"));
        assertTrue(rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, node("LITERAL", new int[]{9}, Optional.empty(), "x", null)), "root_index", 0)).contains("목록 밖"));
        rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, node("LITERAL", new int[0], Optional.empty(), null, null)), "root_index", 0));   // 이름 없는 리터럴
        rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, node("ARGUMENT", new int[0], Optional.empty(), "n", s("parser", "brigadier:string", "type", "WORDS"))), "root_index", 0));
        // resource·resource_or_tag 는 받은 클라이언트가 레지스트리를 찾는다 — 클라이언트에 없는 레지스트리면 끊긴다. *_key 는 찾지 않는다
        for (String parser : List.of("minecraft:resource", "resource_or_tag")) {
            PacketStruct bad = node("ARGUMENT", new int[0], Optional.empty(), "n", s("parser", parser, "registry_key", "x:nope"));
            assertTrue(rejected(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, bad), "root_index", 0)).contains("레지스트리"), parser);
            for (String reg : List.of("minecraft:enchantment", "entity_type", "worldgen/structure_type")) {
                PacketStruct good = node("ARGUMENT", new int[0], Optional.empty(), "n", s("parser", parser, "registry_key", reg));
                hex(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias, good), "root_index", 0));
            }
        }
        hex(g(Clientbound.COMMANDS, "entries", List.of(root, tp, alias,
                node("ARGUMENT", new int[0], Optional.empty(), "n", s("parser", "minecraft:resource_key", "registry_key", "x:nope"))), "root_index", 0));
    }

    @Test
    void recipesAdvancementsAndTagsRefuseDuplicatesAndBadShapes() {
        ItemStack stone = new ItemStack("minecraft:stone", 1);
        PacketStruct shaped = s("id", "x:a", "type", "minecraft:crafting_shaped", "group", "", "category", "MISC", "width", 2, "height", 1,
                "ingredients", List.of(List.of(stone), List.of()), "result", stone, "show_notification", true);
        GenericPacket ok = g(Clientbound.UPDATE_RECIPES, "recipes", List.of(shaped));
        assertEquals(ok, decode(Clientbound.UPDATE_RECIPES, hex(ok)));
        assertTrue(rejected(g(Clientbound.UPDATE_RECIPES, "recipes", List.of(shaped, shaped))).contains("두 번"));
        assertTrue(rejected(g(Clientbound.UPDATE_RECIPES, "recipes", List.of(shaped.with("width", 3)))).contains("width×height"));
        assertTrue(rejected(g(Clientbound.UPDATE_RECIPES, "recipes", List.of(shaped.with("result", ItemStack.EMPTY)))).contains("빈 아이템"));
        rejected(g(Clientbound.UPDATE_RECIPES, "recipes", List.of(s("id", "x:b", "type", "minecraft:stonecutting", "group", "", "ingredient", List.of(ItemStack.EMPTY), "result", stone))));
        assertTrue(rejected(g(Clientbound.RECIPE, "state", "ADD", "book_settings", s("crafting_open", true, "crafting_filtering", false, "furnace_open", false,
                "furnace_filtering", true, "blast_furnace_open", true, "blast_furnace_filtering", true, "smoker_open", false, "smoker_filtering", true),
                "recipes", List.of(), "to_highlight", Optional.of(List.of()))).contains("INIT"));
        PacketStruct crit = s("name", "a", "obtained", Optional.empty());
        assertTrue(rejected(g(Clientbound.UPDATE_ADVANCEMENTS, "reset", false, "added", List.of(), "removed", List.of(),
                "progress", List.of(s("id", "x:a", "criteria", List.of(crit, crit))))).contains("두 번"));
        rejected(g(Clientbound.UPDATE_ADVANCEMENTS, "reset", false, "added", List.of(), "removed", List.of("x:a", "x:a"), "progress", List.of()));
        rejected(g(Clientbound.UPDATE_TAGS, "tags", List.of(s("registry", "minecraft:item", "tags", List.of()), s("registry", "item", "tags", List.of()))));
        // 서명 캐시 번호는 127 까지(클라이언트 MessageSignatureCache 128칸) — delete_chat 과 player_chat 의 last_seen 이 같은 노드
        assertTrue(rejected(g(Clientbound.DELETE_CHAT, "message_signature", s("id", 128, "full_signature", Optional.empty()))).contains("127"));
        hex(g(Clientbound.DELETE_CHAT, "message_signature", s("id", 127, "full_signature", Optional.empty())));
        // 클라이언트에 없는 레지스트리는 빈 태그 목록이라도 TagCollector 가 끊는다
        assertTrue(rejected(g(Clientbound.UPDATE_TAGS, "tags", List.of(s("registry", "x:nope", "tags", List.of())))).contains("레지스트리"));
        hex(g(Clientbound.UPDATE_TAGS, "tags", List.of(s("registry", "worldgen/biome", "tags", List.of()), s("registry", "minecraft:enchantment", "tags", List.of()))));
        assertTrue(rejected(g(Clientbound.MERCHANT_OFFERS, "container_id", 1, "offers", List.of(s("base_cost_a",
                new ItemStack("minecraft:emerald", 1, Map.of(), Set.of("minecraft:max_stack_size")), "result", stone, "cost_b", Optional.empty(), "out_of_stock", false,
                "uses", 0, "max_uses", 1, "xp", 0, "special_price_diff", 0, "price_multiplier", 0f, "demand", 0)),
                "villager_level", 1, "villager_xp", 0, "show_progress", false, "can_restock", false)).contains("지운 컴포넌트"));
    }

    // ------------------------------------------------------------------ 번들 구분자·서버 패킷

    @Test
    void bundleDelimiterIsReadableButOnlyTheServerSendsIt() {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "P", ch);
        core.world().playerOnline(p);
        host.packetsImpl().attach(p);
        var api = host.player(p);
        assertTrue(host.packets().isReadable(Clientbound.BUNDLE_DELIMITER));
        assertEquals(List.of(), host.packets().fields(Clientbound.BUNDLE_DELIMITER));
        var e = assertThrows(IllegalArgumentException.class, () -> host.packets().send(api, GenericPacket.of(Clientbound.BUNDLE_DELIMITER)));
        assertTrue(e.getMessage().contains("sendBundle"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> host.packets().broadcast(GenericPacket.of(Clientbound.BUNDLE_DELIMITER)));
        // 서버 번들의 구분자는 리스너를 거치지 않는다(Player.sendBundle 이 바로 넣는다) — 리스너가 취소·바꾸기로 짝을 깰 수 없다.
        // 혹시 이벤트로 오더라도 PacketEvent 가 취소·바꾸기를 거부한다(UnsupportedOperationException).
        List<String> seen = new ArrayList<>();
        host.packets().listen(new TestPlugins.Gamma(), Clientbound.BUNDLE_DELIMITER, ev -> {
            seen.add(String.valueOf(ev.packet().type()));
            ev.setCancelled(true);
        });
        host.packets().sendBundle(api, List.of(g(Clientbound.SET_CAMERA, "camera_id", 4)));
        ch.runPendingTasks();
        List<byte[]> frames = new ArrayList<>();
        for (Object o; (o = ch.readOutbound()) != null; ) {
            ByteBuf bb = (ByteBuf) o;
            frames.add(ByteBufUtil.getBytes(bb));
            bb.release();
        }
        assertEquals(List.of(), seen);
        int delimiter = codec.ids(Clientbound.BUNDLE_DELIMITER)[0];
        assertEquals(3, frames.size(), "구분자·패킷·구분자");
        assertEquals(delimiter, frames.get(0)[0]);
        assertEquals(delimiter, frames.get(2)[0]);
    }

    @Test
    void serverParticlesAndExplosionsReadAsNamedFields() {
        ByteBuf b = Unpooled.buffer();
        new org.domi.furnace.server.net.protocol.play.LevelParticles(true, 1, 2, 3, 0f, 0f, 0f, 0.5f, 4,
                new org.domi.furnace.server.net.protocol.play.ParticleOptions.Shriek(9)).write(b);
        GenericPacket p = decode(Clientbound.LEVEL_PARTICLES, ByteBufUtil.hexDump(b));
        assertEquals(s("type", "minecraft:shriek", "delay", 9), p.get("particle"));
        assertEquals(4, p.get("count"));
        assertEquals(true, p.get("override_limiter"));
        List<String> names = new ArrayList<>();
        host.packets().fields(Clientbound.EXPLODE).forEach(f -> names.add(f.name()));
        assertEquals(List.of("x", "y", "z", "power", "to_blow", "knockback_x", "knockback_y", "knockback_z", "block_interaction",
                "small_explosion_particles", "large_explosion_particles", "explosion_sound"), names);
    }
}
