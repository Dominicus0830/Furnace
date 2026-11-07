// 바닐라 1.21.1 전리품 함수 14개(set_custom_data … set_writable_book_pages)와 EntityPredicate·LocationPredicate 의 새 필드 시험.
// 바닐라 데이터 없이 도는 단위 시험이다(빈 리소스 매니저).
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.command.arguments.TagParser;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemFunctionsTest {

    static LootData data;
    static LootRegistries regs;

    @BeforeAll
    static void load() throws Exception {
        PackRepository repo = new PackRepository(null, null);
        repo.reload();
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (var rm = repo.openResourceManager()) {
            regs = new LootRegistries(tags, tags.loadAll(rm));
            data = LootData.load(rm, regs);
        }
    }

    static final LootWorld WORLD = new LootWorld() {
        public NoiseRandom random() { return new LegacyRandom(1L); }
        public NoiseRandom randomSequence(ResourceId id) { return new LegacyRandom(1L); }
    };

    static LootStack apply(String json, LootStack s, LootParams params, long seed) {
        LootFunction f = LootFunction.parse(JsonParser.parseString(json), regs);
        return f.apply(s, new LootContext(data, WORLD, params, new LegacyRandom(seed)));
    }

    static LootStack apply(String json, LootStack s) { return apply(json, s, new LootParams(), 1L); }

    static NbtCompound snbt(String s) {
        try {
            return TagParser.parseTag(s);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return (List<Object>) o; }

    // ------------------------------------------------------------------ custom_data

    @Test
    void setCustomDataMergesIntoExistingTag() {
        LootStack s = new LootStack("minecraft:stick", 1);
        s = apply("{\"function\":\"minecraft:set_custom_data\",\"tag\":\"{a:1b,b:{c:\\\"x\\\"}}\"}", s);
        s = apply("{\"function\":\"minecraft:set_custom_data\",\"tag\":\"{b:{d:2},e:[1,2]}\"}", s);
        NbtCompound c = ItemFunctions.customData(s);
        assertEquals(1, c.getByte("a"));
        assertEquals("x", c.getCompound("b").getString("c"));
        assertEquals(2, c.getCompound("b").getInt("d"));
        assertEquals(2, c.getList("e").size());
    }

    @Test
    void copyCustomDataReplaceAppendMerge() {
        NbtCompound nbt = snbt("{Health:20.0f,tags:[\"a\",\"b\"],Brain:{x:1b,y:{z:2}}}");
        LootEntity self = new LootEntity() {
            public String type() { return "minecraft:zombie"; }
            public NbtCompound nbt() { return nbt; }
        };
        LootParams p = new LootParams().with(LootParams.THIS_ENTITY, self);
        String fn = "{\"function\":\"minecraft:copy_custom_data\",\"source\":\"this\",\"ops\":["
                + "{\"source\":\"Health\",\"target\":\"hp\",\"op\":\"replace\"},"
                + "{\"source\":\"tags[]\",\"target\":\"log\",\"op\":\"append\"},"
                + "{\"source\":\"Brain\",\"target\":\"brain\",\"op\":\"merge\"},"
                + "{\"source\":\"Missing\",\"target\":\"nope\",\"op\":\"replace\"}]}";
        LootStack s = apply(fn, new LootStack("minecraft:stick", 1), p, 1L);
        NbtCompound c = ItemFunctions.customData(s);
        assertEquals(20.0f, c.getFloat("hp"));
        assertEquals(2, c.getList("log").size());
        assertEquals(1, c.getCompound("brain").getByte("x"));
        assertEquals(2, c.getCompound("brain").getCompound("y").getInt("z"));
        assertFalse(c.contains("nope"));
        // 원본 NBT 는 건드리지 않는다
        assertFalse(nbt.contains("hp"));
    }

    @Test
    void copyCustomDataWithoutSourceLeavesStackAlone() {
        LootStack s = apply("{\"function\":\"minecraft:copy_custom_data\",\"source\":\"this\",\"ops\":["
                + "{\"source\":\"a\",\"target\":\"b\",\"op\":\"replace\"}]}", new LootStack("minecraft:stick", 1));
        assertFalse(s.has("minecraft:custom_data"));
    }

    @Test
    void copyCustomDataFromStorage() {
        NbtCompound st = snbt("{value:5}");
        LootWorld w = new LootWorld() {
            public NoiseRandom random() { return new LegacyRandom(1L); }
            public NoiseRandom randomSequence(ResourceId id) { return new LegacyRandom(1L); }
            public NbtCompound storageNbt(String storage) { return storage.equals("minecraft:test") ? st : null; }
        };
        LootFunction f = LootFunction.parse(JsonParser.parseString("{\"function\":\"minecraft:copy_custom_data\","
                + "\"source\":{\"type\":\"minecraft:storage\",\"source\":\"test\"},\"ops\":[{\"source\":\"value\",\"target\":\"v\",\"op\":\"replace\"}]}"), regs);
        LootStack s = f.apply(new LootStack("minecraft:stick", 1), new LootContext(data, w, new LootParams(), new LegacyRandom(1L)));
        assertEquals(5, ItemFunctions.customData(s).getInt("v"));
    }

    // ------------------------------------------------------------------ 속성

    @Test
    void setAttributesReplacesAndConsumesSlotRandomFirst() {
        String fn = "{\"function\":\"minecraft:set_attributes\",\"modifiers\":[{\"id\":\"test:a\",\"attribute\":\"minecraft:generic.attack_damage\","
                + "\"operation\":\"add_value\",\"amount\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3},\"slot\":[\"mainhand\",\"offhand\",\"head\"]}]}";
        LootStack s = apply(fn, new LootStack("minecraft:iron_sword", 1), new LootParams(), 42L);
        Map<String, Object> v = map(s.get("minecraft:attribute_modifiers"));
        Map<String, Object> e = map(list(v.get("modifiers")).get(0));
        LegacyRandom r = new LegacyRandom(42L);
        String slot = List.of("mainhand", "offhand", "head").get(r.nextInt(3));
        float amount = 1 + r.nextFloat() * 2;   // uniform(1,3): 슬롯 뽑기 다음 난수
        assertEquals(slot, e.get("slot"));
        assertEquals((double) amount, (Double) e.get("amount"), 1e-9);
        assertEquals("minecraft:generic.attack_damage", e.get("type"));
        assertEquals("test:a", e.get("id"));
        assertEquals(1, list(v.get("modifiers")).size(), "replace 기본값 true — 칼의 기본 속성은 없어진다");
    }

    @Test
    void setAttributesWithoutReplaceKeepsItemDefaults() {
        String fn = "{\"function\":\"minecraft:set_attributes\",\"replace\":false,\"modifiers\":[{\"id\":\"test:a\",\"attribute\":\"minecraft:generic.max_health\","
                + "\"operation\":\"add_value\",\"amount\":4,\"slot\":\"head\"}]}";
        LootStack s = apply(fn, new LootStack("minecraft:iron_sword", 1));
        List<Object> mods = list(map(s.get("minecraft:attribute_modifiers")).get("modifiers"));
        assertTrue(mods.size() >= 2, "기본 공격력·공격 속도 뒤에 붙는다: " + mods);
        assertEquals("test:a", map(mods.get(mods.size() - 1)).get("id"));
        assertEquals("minecraft:generic.attack_damage", map(mods.get(0)).get("type"));
    }

    // ------------------------------------------------------------------ 용기

    @Test
    void setContentsBuildsContainerFromEntries() {
        String fn = "{\"function\":\"minecraft:set_contents\",\"component\":\"minecraft:container\",\"entries\":["
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"},"
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\",\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":3}]}]}";
        LootStack s = apply(fn, new LootStack("minecraft:shulker_box", 1));
        List<Object> slots = list(s.get("minecraft:container"));
        assertEquals(2, slots.size());
        assertEquals(0, map(slots.get(0)).get("slot"));
        assertEquals("minecraft:diamond", map(map(slots.get(0)).get("item")).get("id"));
        assertEquals(3, map(map(slots.get(1)).get("item")).get("count"));
    }

    @Test
    void setContentsOnEmptyStackDoesNothing() {
        LootStack s = apply("{\"function\":\"minecraft:set_contents\",\"component\":\"minecraft:bundle_contents\",\"entries\":["
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}", LootStack.empty());
        assertTrue(s.isEmpty());
    }

    @Test
    void modifyContentsAppliesModifierToEachItemAndLimitsStackSize() {
        LootStack s = new LootStack("minecraft:bundle", 1);
        s = apply("{\"function\":\"minecraft:set_contents\",\"component\":\"minecraft:bundle_contents\",\"entries\":["
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"},{\"type\":\"minecraft:item\",\"name\":\"minecraft:ender_pearl\"}]}", s);
        s = apply("{\"function\":\"minecraft:modify_contents\",\"component\":\"minecraft:bundle_contents\","
                + "\"modifier\":{\"function\":\"minecraft:set_count\",\"count\":50}}", s);
        List<Object> items = list(s.get("minecraft:bundle_contents"));
        assertEquals(2, items.size());
        assertEquals(50, map(items.get(0)).get("count"));
        assertEquals(16, map(items.get(1)).get("count"), "엔더 진주는 한 칸 16개까지");
    }

    @Test
    void modifyContentsKeepsEmptySlotsInContainer() {
        LootStack s = new LootStack("minecraft:shulker_box", 1);
        s.set("minecraft:container", List.of(
                Map.of("slot", 2, "item", Map.of("id", "minecraft:diamond", "count", 1))));
        s = apply("{\"function\":\"minecraft:modify_contents\",\"component\":\"minecraft:container\","
                + "\"modifier\":{\"function\":\"minecraft:set_count\",\"count\":9}}", s);
        List<Object> slots = list(s.get("minecraft:container"));
        assertEquals(1, slots.size());
        assertEquals(2, map(slots.get(0)).get("slot"));
        assertEquals(9, map(map(slots.get(0)).get("item")).get("count"));
    }

    @Test
    void setLootTableWritesSeededContainerLoot() {
        LootStack s = apply("{\"function\":\"minecraft:set_loot_table\",\"name\":\"minecraft:chests/simple_dungeon\",\"seed\":7,"
                + "\"type\":\"minecraft:chest\"}", new LootStack("minecraft:chest", 1));
        Map<String, Object> v = map(s.get("minecraft:container_loot"));
        assertEquals("minecraft:chests/simple_dungeon", v.get("loot_table"));
        assertEquals(7L, v.get("seed"));
        assertTrue(apply("{\"function\":\"minecraft:set_loot_table\",\"name\":\"a:b\",\"type\":\"minecraft:chest\"}", LootStack.empty()).isEmpty());
    }

    // ------------------------------------------------------------------ 이름·머리·깃발·폭죽·책

    @Test
    void setLoreModes() {
        String base = "{\"function\":\"minecraft:set_lore\",\"lore\":[\"B\",\"C\"],";
        LootStack s = new LootStack("minecraft:stick", 1);
        s = apply(base + "\"mode\":\"replace_all\"}", s);
        assertEquals(List.of("\"B\"", "\"C\""), list(s.get("minecraft:lore")));
        s = apply("{\"function\":\"minecraft:set_lore\",\"lore\":[\"A\"],\"mode\":\"append\"}", s);
        assertEquals(List.of("\"B\"", "\"C\"", "\"A\""), list(s.get("minecraft:lore")));
        s = apply("{\"function\":\"minecraft:set_lore\",\"lore\":[\"X\"],\"mode\":\"insert\",\"offset\":1}", s);
        assertEquals(List.of("\"B\"", "\"X\"", "\"C\"", "\"A\""), list(s.get("minecraft:lore")));
        s = apply("{\"function\":\"minecraft:set_lore\",\"lore\":[\"Y\"],\"mode\":\"replace_section\",\"offset\":1,\"size\":2}", s);
        assertEquals(List.of("\"B\"", "\"Y\"", "\"A\""), list(s.get("minecraft:lore")));
        // 범위를 넘는 insert 는 기존 값을 그대로 둔다
        s = apply("{\"function\":\"minecraft:set_lore\",\"lore\":[\"Z\"],\"mode\":\"insert\",\"offset\":9}", s);
        assertEquals(3, list(s.get("minecraft:lore")).size());
    }

    @Test
    void listOperationRequiresKnownMode() {
        assertThrows(LootParseException.class, () -> apply("{\"function\":\"minecraft:set_lore\",\"lore\":[],\"mode\":\"splice\"}", LootStack.empty()));
        assertThrows(LootParseException.class, () -> apply("{\"function\":\"minecraft:set_lore\",\"lore\":[]}", LootStack.empty()));
    }

    @Test
    void fillPlayerHeadCopiesGameProfileOfTarget() {
        LootEntity victim = new LootEntity() {
            public String type() { return "minecraft:player"; }
            public Map<String, Object> gameProfile() { return Map.of("name", "Steve"); }
        };
        LootParams p = new LootParams().with(LootParams.THIS_ENTITY, victim);
        String fn = "{\"function\":\"minecraft:fill_player_head\",\"entity\":\"this\"}";
        assertEquals("Steve", map(apply(fn, new LootStack("minecraft:player_head", 1), p, 1L).get("minecraft:profile")).get("name"));
        assertNull(apply(fn, new LootStack("minecraft:stick", 1), p, 1L).get("minecraft:profile"));
        assertNull(apply(fn, new LootStack("minecraft:player_head", 1), new LootParams(), 1L).get("minecraft:profile"));
    }

    @Test
    void setBannerPatternAppendsOrReplaces() {
        String pat = "{\"pattern\":\"minecraft:border\",\"color\":\"red\"}";
        LootStack s = apply("{\"function\":\"minecraft:set_banner_pattern\",\"append\":false,\"patterns\":[" + pat + "]}", new LootStack("minecraft:white_banner", 1));
        assertEquals(1, list(s.get("minecraft:banner_patterns")).size());
        s = apply("{\"function\":\"minecraft:set_banner_pattern\",\"append\":true,\"patterns\":[{\"pattern\":\"minecraft:circle\",\"color\":\"blue\"}]}", s);
        List<Object> l = list(s.get("minecraft:banner_patterns"));
        assertEquals(2, l.size());
        assertEquals("minecraft:circle", map(l.get(1)).get("pattern"));
        s = apply("{\"function\":\"minecraft:set_banner_pattern\",\"append\":false,\"patterns\":[" + pat + "]}", s);
        assertEquals(1, list(s.get("minecraft:banner_patterns")).size());
    }

    @Test
    void setFireworksAndExplosion() {
        LootStack s = new LootStack("minecraft:firework_rocket", 1);
        s = apply("{\"function\":\"minecraft:set_fireworks\",\"flight_duration\":3,\"explosions\":{\"mode\":\"append\",\"values\":["
                + "{\"shape\":\"star\",\"colors\":[255],\"has_trail\":true}]}}", s);
        Map<String, Object> fw = map(s.get("minecraft:fireworks"));
        assertEquals(3, fw.get("flight_duration"));
        assertEquals(1, list(fw.get("explosions")).size());
        s = apply("{\"function\":\"minecraft:set_fireworks\",\"explosions\":{\"mode\":\"append\",\"values\":[{\"shape\":\"burst\"}]}}", s);
        fw = map(s.get("minecraft:fireworks"));
        assertEquals(3, fw.get("flight_duration"), "flight_duration 을 안 주면 기존 값 유지");
        assertEquals(2, list(fw.get("explosions")).size());

        LootStack star = apply("{\"function\":\"minecraft:set_firework_explosion\",\"shape\":\"creeper\",\"trail\":true}", new LootStack("minecraft:firework_star", 1));
        Map<String, Object> ex = map(star.get("minecraft:firework_explosion"));
        assertEquals("creeper", ex.get("shape"));
        assertEquals(true, ex.get("has_trail"));
        assertEquals(false, ex.get("has_twinkle"));
        star = apply("{\"function\":\"minecraft:set_firework_explosion\",\"twinkle\":true}", star);
        ex = map(star.get("minecraft:firework_explosion"));
        assertEquals("creeper", ex.get("shape"));
        assertEquals(true, ex.get("has_trail"));
        assertEquals(true, ex.get("has_twinkle"));
    }

    @Test
    void bookFunctions() {
        LootStack s = apply("{\"function\":\"minecraft:set_book_cover\",\"title\":\"Hi\",\"author\":\"Me\",\"generation\":2}", new LootStack("minecraft:written_book", 1));
        Map<String, Object> c = map(s.get("minecraft:written_book_content"));
        assertEquals("Hi", c.get("title"));
        assertEquals("Me", c.get("author"));
        assertEquals(2, c.get("generation"));
        s = apply("{\"function\":\"minecraft:set_written_book_pages\",\"mode\":\"append\",\"pages\":[\"one\",{\"raw\":\"two\"}]}", s);
        c = map(s.get("minecraft:written_book_content"));
        assertEquals("Hi", c.get("title"), "쪽을 바꿔도 표지는 유지");
        assertEquals(2, list(c.get("pages")).size());
        assertEquals("\"one\"", list(c.get("pages")).get(0));
        assertEquals("\"two\"", map(list(c.get("pages")).get(1)).get("raw"));

        LootStack w = apply("{\"function\":\"minecraft:set_writable_book_pages\",\"mode\":\"replace_all\",\"pages\":[\"a\",\"b\"]}", new LootStack("minecraft:writable_book", 1));
        assertEquals(List.of("a", "b"), list(map(w.get("minecraft:writable_book_content")).get("pages")));
        w = apply("{\"function\":\"minecraft:set_writable_book_pages\",\"mode\":\"insert\",\"offset\":1,\"pages\":[\"x\"]}", w);
        assertEquals(List.of("a", "x", "b"), list(map(w.get("minecraft:writable_book_content")).get("pages")));
        assertThrows(LootParseException.class, () -> apply("{\"function\":\"minecraft:set_book_cover\",\"generation\":4}", LootStack.empty()));
    }

    @Test
    void everyVanillaFunctionTypeIsKnown() {
        // 바닐라 1.21.1 LootItemFunctions 의 40종 — 모르는 함수로 던지는 일이 없어야 한다(파싱이 필수 필드 때문에 실패하는 것은 정상).
        List<String> types = List.of("set_count", "set_item", "enchant_with_levels", "enchant_randomly", "set_enchantments", "set_custom_data",
                "set_components", "furnace_smelt", "enchanted_count_increase", "set_damage", "set_attributes", "set_name", "exploration_map",
                "set_stew_effect", "copy_name", "set_contents", "modify_contents", "filtered", "limit_count", "apply_bonus", "set_loot_table",
                "explosion_decay", "set_lore", "fill_player_head", "copy_custom_data", "copy_state", "set_banner_pattern", "set_potion",
                "set_instrument", "reference", "sequence", "copy_components", "set_fireworks", "set_firework_explosion", "set_book_cover",
                "set_written_book_pages", "set_writable_book_pages", "toggle_tooltips", "set_ominous_bottle_amplifier", "set_custom_model_data");
        assertEquals(40, types.size());
        for (String t : types) {
            try {
                LootFunction.parse(JsonParser.parseString("{\"function\":\"minecraft:" + t + "\"}"), regs);
            } catch (LootParseException e) {
                assertFalse(e.getMessage().startsWith("아직 지원하지 않는 전리품 함수"), t + " 미지원: " + e.getMessage());
            } catch (RuntimeException ignored) {
                // 필수 필드 누락 — 함수 종류는 알고 있다
            }
        }
    }

    @Test
    void resultsConvertToServerItemsWithoutLoss() {
        NetworkComponents net = new NetworkComponents(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        LootEntity victim = new LootEntity() {
            public String type() { return "minecraft:player"; }
            public Map<String, Object> gameProfile() { return Map.of("name", "Steve", "id", List.of(1, 2, 3, 4)); }
        };
        LootParams p = new LootParams().with(LootParams.THIS_ENTITY, victim);
        String[][] cases = {
            {"minecraft:stick", "{\"function\":\"minecraft:set_custom_data\",\"tag\":\"{a:1b}\"}", "minecraft:custom_data"},
            {"minecraft:iron_sword", "{\"function\":\"minecraft:set_attributes\",\"modifiers\":[{\"id\":\"t:a\",\"attribute\":\"minecraft:generic.max_health\","
                    + "\"operation\":\"add_value\",\"amount\":2,\"slot\":\"mainhand\"}]}", "minecraft:attribute_modifiers"},
            {"minecraft:shulker_box", "{\"function\":\"minecraft:set_contents\",\"component\":\"minecraft:container\",\"entries\":["
                    + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}", "minecraft:container"},
            {"minecraft:chest", "{\"function\":\"minecraft:set_loot_table\",\"name\":\"minecraft:chests/simple_dungeon\",\"seed\":5,\"type\":\"minecraft:chest\"}", "minecraft:container_loot"},
            {"minecraft:stick", "{\"function\":\"minecraft:set_lore\",\"lore\":[\"A\",{\"text\":\"B\",\"color\":\"red\"}],\"mode\":\"replace_all\"}", "minecraft:lore"},
            {"minecraft:player_head", "{\"function\":\"minecraft:fill_player_head\",\"entity\":\"this\"}", "minecraft:profile"},
            {"minecraft:white_banner", "{\"function\":\"minecraft:set_banner_pattern\",\"append\":false,\"patterns\":[{\"pattern\":\"minecraft:border\",\"color\":\"red\"}]}", "minecraft:banner_patterns"},
            {"minecraft:firework_rocket", "{\"function\":\"minecraft:set_fireworks\",\"flight_duration\":2,\"explosions\":{\"mode\":\"replace_all\",\"values\":[{\"shape\":\"star\",\"colors\":[255]}]}}", "minecraft:fireworks"},
            {"minecraft:firework_star", "{\"function\":\"minecraft:set_firework_explosion\",\"shape\":\"burst\"}", "minecraft:firework_explosion"},
            {"minecraft:written_book", "{\"function\":\"minecraft:set_book_cover\",\"title\":\"T\",\"author\":\"A\"}", "minecraft:written_book_content"},
            {"minecraft:writable_book", "{\"function\":\"minecraft:set_writable_book_pages\",\"mode\":\"replace_all\",\"pages\":[\"x\"]}", "minecraft:writable_book_content"},
        };
        for (String[] c : cases) {
            LootStack out = apply(c[1], new LootStack(c[0], 1), p, 1L);
            var item = net.toItemStack(out);
            assertNotNull(item, c[2] + " 변환 실패");
            assertFalse(item.isEmpty(), c[2]);
            assertTrue(item.patch().entries().get(c[2]) != null, c[2] + " 이 서버 아이템에 없다: " + item.patch().entries());
        }
    }

    @Test
    void setAttributesReplacesSameAttributeAndIdLikeVanilla() {
        String mod = "{\"id\":\"t:a\",\"attribute\":\"minecraft:generic.max_health\",\"operation\":\"add_value\",\"amount\":%d,\"slot\":\"head\"}";
        String fn = "{\"function\":\"minecraft:set_attributes\",\"replace\":false,\"modifiers\":[" + String.format(mod, 1) + "," + String.format(mod, 5) + "]}";
        LootStack s = apply(fn, new LootStack("minecraft:stick", 1));
        List<Object> mods = list(map(s.get("minecraft:attribute_modifiers")).get("modifiers"));
        assertEquals(1, mods.size(), "같은 (속성, id) 는 앞의 것을 지우고 넣는다");
        assertEquals(5.0, (Double) map(mods.get(0)).get("amount"), 1e-9);
        s = apply(fn, s);
        assertEquals(1, list(map(s.get("minecraft:attribute_modifiers")).get("modifiers")).size(), "기존 항목과 id 가 겹쳐도 중복 안 됨");
    }

    @Test
    void listOperationValidationMatchesVanilla() {
        // offset 이 커도 읽힌다(실행 때 로그만 찍고 그대로 둔다)
        LootStack s = apply("{\"function\":\"minecraft:set_lore\",\"lore\":[\"x\"],\"mode\":\"insert\",\"offset\":999}", new LootStack("minecraft:stick", 1));
        assertEquals(0, list(s.get("minecraft:lore")).size());
        // replace_section 의 size 가 최대보다 크면 거부
        assertThrows(LootParseException.class, () -> apply(
                "{\"function\":\"minecraft:set_lore\",\"lore\":[],\"mode\":\"replace_section\",\"size\":257}", LootStack.empty()));
    }

    @Test
    void bookResolvedFlagFollowsVanillaDefaults() {
        LootStack s = apply("{\"function\":\"minecraft:set_book_cover\",\"title\":\"T\"}", new LootStack("minecraft:written_book", 1));
        assertEquals(true, map(s.get("minecraft:written_book_content")).get("resolved"), "WrittenBookContent.EMPTY 는 resolved=true");
        s = apply("{\"function\":\"minecraft:set_written_book_pages\",\"mode\":\"append\",\"pages\":[\"p\"]}", s);
        assertEquals(false, map(s.get("minecraft:written_book_content")).get("resolved"), "withReplacedPages 는 resolved=false");
    }

    @Test
    void copyCustomDataLeavesExistingEmptyComponentAloneWhenNothingMatches() {
        NbtCompound nbt = snbt("{a:1}");
        LootEntity self = new LootEntity() {
            public String type() { return "minecraft:zombie"; }
            public NbtCompound nbt() { return nbt; }
        };
        LootStack st = new LootStack("minecraft:stick", 1);
        st.set("minecraft:custom_data", new NbtCompound());
        LootStack out = apply("{\"function\":\"minecraft:copy_custom_data\",\"source\":\"this\",\"ops\":["
                + "{\"source\":\"missing\",\"target\":\"x\",\"op\":\"replace\"}]}", st, new LootParams().with(LootParams.THIS_ENTITY, self), 1L);
        assertNotNull(out.get("minecraft:custom_data"), "맞는 연산이 없으면 컴포넌트를 건드리지 않는다");
    }
}
