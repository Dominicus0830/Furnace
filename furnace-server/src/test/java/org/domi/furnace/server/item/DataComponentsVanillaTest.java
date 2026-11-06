// 아이템 컴포넌트 코덱이 바닐라 1.21.1 과 같은지 — 기대값은 실제 바닐라 서버 jar 의 코덱으로 인코딩해 얻은 바이트·NBT
// (scratchpad component-spec-a/b 의 ✅ 예시). 네트워크 바이트, 저장 NBT, 패치 정규화 규칙을 본다.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataComponentsVanillaTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    private static String hex(String s) { return HexFormat.of().formatHex(s.getBytes(StandardCharsets.UTF_8)); }

    /** 컴포넌트 값 하나의 네트워크 바이트(종류 id 없이). JSON 꼴 입력을 정규화한 뒤 쓴다. */
    private static String net(String type, Object json) {
        ComponentType t = DataComponents.require(type);
        Object v = t.normalize(json);
        ByteBuf b = Unpooled.buffer();
        t.network().write(b, v);
        String h = HexFormat.of().formatHex(b.array(), b.readerIndex(), b.writerIndex());
        // 되읽으면 같은 정규 값
        Object back = t.network().read(Unpooled.wrappedBuffer(HexFormat.of().parseHex(h)));
        assertTrue(t.same(v, back), type + " 왕복: " + v + " vs " + back);
        return h;
    }

    private static String snbt(String type, Object json) {
        ComponentType t = DataComponents.require(type);
        Object nbt = t.persistent().toNbt(t.normalize(json));
        return nbt instanceof NbtCompound || nbt instanceof org.domi.furnace.server.net.io.nbt.NbtList || nbt instanceof int[] ? Snbt.write(nbt) : String.valueOf(nbt);
    }

    // ------------------------------------------------------------------ 패치

    @Test
    void patchWireOrder() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("minecraft:creative_slot_lock", Map.of());
        raw.put("minecraft:map_post_processing", null);
        raw.put("minecraft:damage", 4);
        raw.put("minecraft:hide_tooltip", Map.of());
        raw.put("minecraft:hide_additional_tooltip", null);
        assertEquals("0302" + "11" + "0304" + "0f" + "1c0e", HexFormat.of().formatHex(ComponentPatch.of(raw).networkBytes()));
        assertEquals("0000", HexFormat.of().formatHex(ComponentPatch.EMPTY.networkBytes()));
    }

    @Test
    void patchNormalization() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("minecraft:damage", 0);
        raw.put("minecraft:max_stack_size", 64);
        raw.put("minecraft:lore", null);
        raw.put("minecraft:food", null);
        raw.put("minecraft:repair_cost", 3);
        raw.put("minecraft:rarity", "common");
        ComponentPatch p = ComponentPatch.of(raw).normalizedFor("minecraft:diamond_sword");
        assertEquals(List.of("minecraft:max_stack_size", "minecraft:lore", "minecraft:repair_cost"), List.copyOf(p.entries().keySet()));
        assertNull(p.entries().get("minecraft:lore"));

        assertTrue(ComponentPatch.of(m("minecraft:fireworks", m("flight_duration", 1))).normalizedFor("minecraft:firework_rocket").isEmpty());
        assertFalse(ComponentPatch.of(m("minecraft:fireworks", m())).normalizedFor("minecraft:firework_rocket").isEmpty(), "비행 0 은 기본값이 아니다");
        assertTrue(ComponentPatch.of(m("minecraft:pot_decorations", List.of("minecraft:brick"))).normalizedFor("minecraft:decorated_pot").isEmpty());
        assertTrue(ComponentPatch.of(m("minecraft:container", List.of())).normalizedFor("minecraft:shulker_box").isEmpty());
        assertTrue(ComponentPatch.of(m("minecraft:potion_contents", m())).normalizedFor("minecraft:potion").isEmpty());
        // 음반: 기본값은 키만 가진 꼴이라 저장 코덱으로 읽은 값과 같지 않다
        assertFalse(ComponentPatch.of(m("minecraft:jukebox_playable", m("song", "minecraft:13"))).normalizedFor("minecraft:music_disc_13").isEmpty());
        // 음식: using_converts_to 가 있으면 기본값과 같아도 남는다(ItemStack 참조 비교)
        assertFalse(ComponentPatch.of(m("minecraft:food", m("nutrition", 6, "saturation", 7.2000003f, "can_always_eat", true,
                "using_converts_to", m("id", "minecraft:bowl")))).normalizedFor("minecraft:suspicious_stew").isEmpty());
    }

    @Test
    void patchCodecRules() {
        assertThrows(CodecException.class, () -> ComponentPatch.fromCodec(m("minecraft:creative_slot_lock", m())));
        assertThrows(CodecException.class, () -> ComponentPatch.fromCodec(m("damage", 1, "minecraft:damage", 2)));
        assertThrows(CodecException.class, () -> ComponentPatch.fromCodec(m("!damage", 5)));
        ComponentPatch p = ComponentPatch.fromCodec(m("damage", 1, "!damage", m()));
        assertEquals(1, p.size());
        assertNull(p.entries().get("minecraft:damage"));
        NbtCompound nbt = ComponentPatch.of(m("minecraft:damage", 5, "minecraft:lore", null)).toNbt();
        assertEquals("{\"!minecraft:lore\":{},\"minecraft:damage\":5}", Snbt.write(nbt));
    }

    // ------------------------------------------------------------------ 부 A

    @Test
    void partA() {
        assertEquals("01200501", net("minecraft:enchantments", m("sharpness", 5)));
        assertEquals("022703200500", net("minecraft:enchantments", m("levels", m("unbreaking", 3, "sharpness", 5), "show_in_tooltip", false)));
        assertEquals("{levels:{\"minecraft:sharpness\":0}}", snbt("minecraft:enchantments", m("sharpness", 0)), "레벨 0 도 남는다");
        assertEquals("01" + "0100" + "0e" + hex("minecraft:logs") + "0000" + "01", net("minecraft:can_break", m("blocks", "#minecraft:logs")));
        assertEquals("{predicates:[{blocks:\"minecraft:stone\"}]}", snbt("minecraft:can_break", m("blocks", "stone")));
        assertEquals("{predicates:[{}]}", snbt("minecraft:can_break", m("predicates", List.of())), "실패하면 빈 술어 하나");
        assertEquals("{predicates:[{nbt:\"{a:1b}\"}]}", snbt("minecraft:can_break", m("predicates", List.of(m("nbt", m("a", 1))))));
        assertEquals("01" + "00" + "01" + "03" + "06" + hex("facing") + "01" + "05" + hex("north")
                        + "03" + hex("age") + "00" + "0101" + hex("1") + "0101" + hex("3")
                        + "03" + hex("lit") + "00" + "0101" + hex("0") + "00" + "00" + "01",
                net("minecraft:can_place_on", m("predicates", List.of(m("state", m("facing", "north", "age", m("min", "1", "max", "3"), "lit", m("min", "0")))))));
        assertEquals("01" + "00" + "03" + hex("a:b") + "4004000000000000" + "01" + "00" + "01",
                net("minecraft:attribute_modifiers", List.of(m("type", "generic.armor", "id", "a:b", "amount", 2.5, "operation", "add_multiplied_base"))));
        assertEquals("fdffffff0f", net("minecraft:custom_model_data", -3));
        assertEquals("0a00", net("minecraft:intangible_projectile", m()));
        assertEquals("", net("minecraft:hide_tooltip", m("a", 1)));
        assertThrows(CodecException.class, () -> DataComponents.require("minecraft:hide_tooltip").normalize(true));
        assertEquals("04" + "4019999a" + "00" + "3fcccccd" + "00" + "00", net("minecraft:food", m("nutrition", 4, "saturation", 2.4)));
        assertEquals("00" + "3f800000" + "01", net("minecraft:tool", m("rules", List.of())));
        assertEquals("00ff000001", net("minecraft:dyed_color", 16711680));
        assertEquals("{rgb:16711680}", snbt("minecraft:dyed_color", 16711680));
        assertEquals("0000007b", net("minecraft:map_color", 123));
        assertEquals("1c", net("minecraft:map_id", 28));
        // 효과(숨은 효과 포함)
        assertEquals("0000" + "01" + "00" + "03" + "64" + "01" + "00" + "01" + "01" + "01" + "c801" + "00" + "01" + "01" + "00",
                net("minecraft:potion_contents", m("custom_effects", List.of(m("id", "speed", "amplifier", 3, "duration", 100, "ambient", true,
                        "show_particles", false, "show_icon", true, "hidden_effect", m("amplifier", 1, "duration", 200))))));
        assertEquals("{custom_effects:[{amplifier:-1b,id:\"minecraft:speed\",show_icon:1b}]}",
                snbt("minecraft:potion_contents", m("custom_effects", List.of(m("id", "speed", "amplifier", 255)))));
        // 음식 효과의 숨은 효과는 다시 쓰이지 않는다
        assertEquals("{effects:[{effect:{id:\"minecraft:speed\",show_icon:1b}}],nutrition:1,saturation:1.0f}",
                snbt("minecraft:food", m("nutrition", 1, "saturation", 1, "effects", List.of(m("effect", m("id", "speed", "hidden_effect", m("duration", 5)))))));
    }

    @Test
    void text() {
        assertEquals("\"abc\"", FlatText.canonical("\"abc\""));
        assertEquals("{\"bold\":true,\"color\":\"red\",\"italic\":false,\"text\":\"a<b>&=é\"}",
                FlatText.canonical("{\"text\":\"a<b>&=é\",\"color\":\"red\",\"bold\":true,\"italic\":false}"));
        assertEquals("{\"extra\":[{\"color\":\"gold\",\"text\":\"b\"}],\"text\":\"a\"}", FlatText.canonical("[\"a\",{\"text\":\"b\",\"color\":\"gold\"}]"));
        assertEquals("\"plain\"", FlatText.canonical("plain"), "Gson 관대 파서");
        assertEquals("0800" + "03" + hex("abc"), net("minecraft:custom_name", "\"abc\""));
        assertEquals("0a" + "08" + "0004" + hex("text") + "0001" + hex("b") + "01" + "0006" + hex("italic") + "00" + "00",
                net("minecraft:item_name", "{\"text\":\"b\",\"italic\":false}"));
    }

    @Test
    void customDataAndSnbt() {
        assertEquals("{a:1b,b:\"x\"}", snbt("minecraft:custom_data", m("a", 1, "b", "x")));
        assertEquals("0a" + "01" + "0001" + hex("a") + "01" + "08" + "0001" + hex("b") + "0001" + hex("x") + "00", net("minecraft:custom_data", m("a", 1, "b", "x")));
        assertEquals("{a:1b,b:[I;1,2]}", snbt("minecraft:custom_data", "{a:1b,b:[I;1,2]}"));
        String s = "{a:\"q'x\",\"b c\":[1L,2L],d:1.5d,e:2.5f,f:3s,g:[B;1B],h:{},i:[],j:'x\"y',z:1}";
        assertEquals(s, Snbt.write(Snbt.parse(s)));
        assertThrows(CodecException.class, () -> DataComponents.require("minecraft:entity_data").normalize(m("NoAI", 1)));
    }

    // ------------------------------------------------------------------ 부 B

    @Test
    void partB() {
        assertEquals("010d0000", net("minecraft:potion_contents", "minecraft:swiftness"));
        assertEquals("{potion:\"minecraft:swiftness\"}", snbt("minecraft:potion_contents", "minecraft:swiftness"));
        assertEquals("000000", net("minecraft:potion_contents", m()));
        assertEquals("0100" + "01ffffffff" + "02" + "00000000010100" + "02ff01ffffffff0f01000000",
                net("minecraft:potion_contents", m("potion", "water", "custom_color", -1, "custom_effects",
                        List.of(m("id", "speed"), m("id", "haste", "amplifier", 255, "duration", -1, "ambient", true, "show_particles", false)))));
        assertEquals("030fa00112c80116a001", net("minecraft:suspicious_stew_effects",
                List.of(m("id", "night_vision"), m("id", "poison", "duration", 200), m("id", "saturation"))));
        assertEquals("[{id:\"minecraft:poison\"}]",
                snbt("minecraft:suspicious_stew_effects", List.of(m("id", "poison", "duration", "bad"))), "duration 은 관대");
        assertEquals("03" + "0361626300" + "0172010166" + "017800",
                net("minecraft:writable_book_content", m("pages", List.of("abc", m("raw", "r", "filtered", "f"), m("raw", "x")))));
        assertEquals("01540001410000" + "00", net("minecraft:written_book_content", m("title", "T", "author", "A")));
        assertEquals("{author:\"A\",title:{raw:\"T\"}}", snbt("minecraft:written_book_content", m("title", "T", "author", "A")));
        assertEquals("{author:\"A\",pages:[{raw:'\"hello\"'},{raw:'{\"bold\":true,\"text\":\"a\"}'}],title:{raw:\"T\"}}",
                snbt("minecraft:written_book_content", m("title", "T", "author", "A", "pages", List.of("\"hello\"", "{\"text\":\"a\",\"bold\":true}"))));
        assertEquals("050201", net("minecraft:trim", m("material", "minecraft:gold", "pattern", "minecraft:coast")));
        assertEquals("01", net("minecraft:instrument", "minecraft:ponder_goat_horn"));
        assertEquals("010201", net("minecraft:jukebox_playable", m("song", "minecraft:13")));
        Object proto = ItemPrototypes.defaultValue("minecraft:music_disc_13", "minecraft:jukebox_playable");
        ByteBuf b = Unpooled.buffer();
        DataComponents.require("minecraft:jukebox_playable").network().write(b, proto);
        assertEquals("00" + "0c" + hex("minecraft:13") + "01", HexFormat.of().formatHex(b.array(), 0, b.writerIndex()));
        assertEquals("01" + "13" + hex("minecraft:overworld") + "0000004000003ffe" + "00",
                net("minecraft:lodestone_tracker", m("target", m("dimension", "overworld", "pos", List.of(1, -2, 3)), "tracked", false)));
        assertEquals("0001", net("minecraft:lodestone_tracker", m()));
        assertEquals("03" + "02" + "00000001" + "00ff0000" + "01" + "ffffffff" + "01" + "01",
                net("minecraft:firework_explosion", m("shape", "creeper", "colors", List.of(1, 16711680), "fade_colors", List.of(-1), "has_trail", true, "has_twinkle", true)));
        assertEquals("{colors:[I;1,16711680],fade_colors:[I;-1],has_trail:1b,has_twinkle:1b,shape:\"creeper\"}",
                snbt("minecraft:firework_explosion", m("shape", "creeper", "colors", List.of(1, 16711680), "fade_colors", List.of(-1), "has_trail", true, "has_twinkle", true)));
        assertEquals("03" + "02" + "040100000005000000" + "0200000000",
                net("minecraft:fireworks", m("explosions", List.of(m("colors", List.of(5), "shape", "burst"), m("shape", "star")), "flight_duration", 3)));
        assertEquals("{flight_duration:-56b}", snbt("minecraft:fireworks", m("flight_duration", 200)));
        assertEquals("0105" + hex("Notch") + "0000", net("minecraft:profile", "Notch"));
        assertEquals("{name:\"Notch\"}", snbt("minecraft:profile", "Notch"));
        assertEquals("02" + "200e" + "00" + "10" + hex("minecraft:custom") + "1d" + hex("block.minecraft.banner.custom") + "0f",
                net("minecraft:banner_patterns", List.of(m("color", "red", "pattern", "minecraft:stripe_bottom"),
                        m("color", "black", "pattern", m("asset_id", "minecraft:custom", "translation_key", "block.minecraft.banner.custom")))));
        assertEquals("04" + "9907".repeat(4), net("minecraft:pot_decorations", List.of()));
        assertEquals("04880a990799079907", net("minecraft:pot_decorations", List.of("minecraft:angler_pottery_sherd")));
        assertEquals("04" + "011c0000" + "00" + "00" + "02010000",
                net("minecraft:container", List.of(m("slot", 3, "item", m("id", "stone", "count", 2)), m("slot", 0, "item", m("id", "dirt")))));
        assertEquals("[{item:{count:1,id:\"minecraft:dirt\"},slot:0},{item:{count:2,id:\"minecraft:stone\"},slot:3}]",
                snbt("minecraft:container", List.of(m("slot", 3, "item", m("id", "stone", "count", 2)), m("slot", 0, "item", m("id", "dirt")))));
        assertEquals("03" + "06" + hex("facing") + "05" + hex("north") + "04" + hex("half") + "03" + hex("top") + "0b" + hex("waterlogged") + "04" + hex("true"),
                net("minecraft:block_state", m("facing", "north", "half", "top", "waterlogged", "true")));
        assertEquals("02" + "0a" + "08" + "0002" + hex("id") + "000d" + hex("minecraft:bee") + "00" + "05" + "d804" + "0a00" + "00" + "00",
                net("minecraft:bees", List.of(m("entity_data", m("id", "minecraft:bee"), "ticks_in_hive", 5, "min_ticks_in_hive", 600),
                        m("ticks_in_hive", 0, "min_ticks_in_hive", 0))));
        assertEquals("[{entity_data:{id:\"minecraft:bee\"},min_ticks_in_hive:600,ticks_in_hive:5},{min_ticks_in_hive:0,ticks_in_hive:0}]",
                snbt("minecraft:bees", List.of(m("entity_data", "{id:\"minecraft:bee\"}", "ticks_in_hive", 5, "min_ticks_in_hive", 600),
                        m("entity_data", m(), "ticks_in_hive", 0, "min_ticks_in_hive", 0))));
        assertEquals("0101a2060000", net("minecraft:charged_projectiles", List.of(m("id", "arrow", "count", 1))));
        assertEquals("[{count:1,id:\"minecraft:arrow\"}]", snbt("minecraft:charged_projectiles", List.of(m("id", "arrow", "count", 100))), "범위 밖 개수는 1");
    }

    @Test
    void itemStackStream() {
        ItemStack sword = new ItemStack(838, 5, ComponentPatch.EMPTY);
        ByteBuf b = Unpooled.buffer();
        ItemStackCodecs.writeStack(b, sword);
        assertEquals("05c6060000", HexFormat.of().formatHex(b.array(), 0, b.writerIndex()));
        assertEquals(sword, ItemStackCodecs.readStack(b));
        assertTrue(ItemStackCodecs.readStack(Unpooled.wrappedBuffer(HexFormat.of().parseHex("ffffffff0f"))).isEmpty());
    }
}
