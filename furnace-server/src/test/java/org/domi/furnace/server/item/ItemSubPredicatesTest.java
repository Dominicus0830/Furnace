// 아이템 하위 술어 14종(바닐라 1.21.1 ItemSubPredicates)과 전체 ItemPredicate: 명령 인자(item_predicate 의 "종류~값")와
// 전리품·발전 과제(loot/ItemPredicate)가 같은 공용 코덱(ItemPredicates)으로 같은 판정을 내는지.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.loot.ItemPredicate;
import org.domi.furnace.server.loot.LootParseException;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemSubPredicatesTest {

    static LootRegistries regs;

    @BeforeAll
    static void loadLootRegistries() throws Exception {
        PackRepository repo = new PackRepository(null, null);
        repo.reload();
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (var rm = repo.openResourceManager()) {
            regs = new LootRegistries(tags, tags.loadAll(rm));
        }
    }

    @BeforeEach
    void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        ItemContext.installTags(TagSet.load("registries/tags-1.21.1.txt"));
    }

    private static ItemStack stack(String s) throws CommandSyntaxException {
        return ItemArgument.item().parse(new StringReader(s)).createItemStack(1, false);
    }

    private static ItemStack stack(String s, int count) throws CommandSyntaxException {
        return ItemArgument.item().parse(new StringReader(s)).createItemStack(count, false);
    }

    private static boolean matches(String predicate, ItemStack s) throws CommandSyntaxException {
        return ItemPredicateArgument.itemPredicate().parse(new StringReader(predicate)).test(s);
    }

    private static String errorKey(String predicate) {
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class,
                () -> ItemPredicateArgument.itemPredicate().parse(new StringReader(predicate)));
        return e.getRawMessage().translationKey();
    }

    @Test
    void damage() throws Exception {
        ItemStack sword = stack("diamond_sword[damage=10]");   // 최대 1561
        assertTrue(matches("*[damage~{damage:10}]", sword));
        assertTrue(matches("*[damage~{durability:1551}]", sword));
        assertFalse(matches("*[damage~{durability:{min:1552}}]", sword));
        assertFalse(matches("*[damage~{}]", stack("stone")), "damage 컴포넌트가 없으면 거짓");
        // 바닐라는 isDamageableItem 을 보지 않는다: 부서지지 않아도 damage 값으로 판정
        assertTrue(matches("*[damage~{damage:10}]", stack("diamond_sword[damage=10,unbreakable={}]")));
    }

    @Test
    void enchantmentsAndStoredEnchantments() throws Exception {
        ItemStack sword = stack("diamond_sword[enchantments={levels:{sharpness:3,unbreaking:1}}]");
        assertTrue(matches("*[enchantments~[{enchantments:\"sharpness\",levels:{min:3}}]]", sword));
        assertTrue(matches("*[enchantments~[{enchantments:[\"looting\",\"unbreaking\"]}]]", sword));
        assertFalse(matches("*[enchantments~[{enchantments:\"looting\"}]]", sword));
        assertTrue(matches("*[enchantments~[{levels:3}]]", sword), "인챈트 없이 레벨만: 아무 인챈트나 그 레벨");
        assertFalse(matches("*[enchantments~[{levels:{min:4}}]]", sword));
        assertTrue(matches("*[enchantments~[{enchantments:\"#minecraft:non_treasure\",levels:3}]]", sword));
        assertTrue(matches("*[enchantments~[{}]]", sword), "빈 술어: 인챈트가 하나라도 있으면");
        assertFalse(matches("*[enchantments~[{}]]", stack("stone")), "기본 빈 인챈트");
        assertTrue(matches("*[enchantments~[]]", stack("stone")), "술어 목록이 비면 컴포넌트만 있으면 참");
        assertFalse(matches("*[enchantments~[{levels:{min:5,max:1}}]]", sword), "1.21.1 은 뒤집힌 범위를 거절하지 않고 아무것도 맞지 않는다");

        ItemStack book = stack("enchanted_book[stored_enchantments={levels:{mending:1}}]");
        assertTrue(matches("*[stored_enchantments~[{enchantments:\"mending\"}]]", book));
        assertFalse(matches("*[enchantments~[{enchantments:\"mending\"}]]", book));
        assertEquals("arguments.item.predicate.malformed", errorKey("*[enchantments~[{enchantments:\"no_such_enchantment\"}]]"));
        assertEquals("arguments.item.predicate.malformed", errorKey("*[enchantments~[{enchantments:\"#minecraft:no_such_tag\"}]]"));
    }

    @Test
    void potionContents() throws Exception {
        ItemStack potion = stack("potion[potion_contents={potion:\"minecraft:swiftness\"}]");
        assertTrue(matches("*[potion_contents~\"swiftness\"]", potion));
        assertTrue(matches("*[potion_contents~[\"strength\",\"minecraft:swiftness\"]]", potion));
        assertFalse(matches("*[potion_contents~\"strength\"]", potion));
        assertFalse(matches("*[potion_contents~\"swiftness\"]", stack("potion[potion_contents={custom_color:1}]")), "물약 없이 색만");
    }

    @Test
    void customData() throws Exception {
        ItemStack s = stack("stick[custom_data={a:1,b:{c:[1,2,3]},s:\"x\"}]");
        assertTrue(matches("*[custom_data~{a:1}]", s));
        assertTrue(matches("*[custom_data~{b:{c:[3,1]}}]", s), "리스트는 부분 일치(순서 무관)");
        assertTrue(matches("*[custom_data~\"{s:'x'}\"]", s), "SNBT 문자열도 받는다(TagParser.LENIENT_CODEC)");
        assertFalse(matches("*[custom_data~{a:1b}]", s), "수 타입이 다르면 다르다");
        assertFalse(matches("*[custom_data~{b:{c:[4]}}]", s));
        assertFalse(matches("*[custom_data~{z:1}]", s));
        assertTrue(matches("*[custom_data~{}]", stack("stone")), "custom_data 가 없어도 빈 태그로 본다");
        assertFalse(matches("*[custom_data~{a:1}]", stack("stone")));
    }

    @Test
    void containerAndBundle() throws Exception {
        ItemStack chest = stack("chest[container=[{slot:0,item:{id:\"diamond\",count:2}},{slot:3,item:{id:\"stone\",count:1}}]]");
        assertTrue(matches("*[container~{}]", chest));
        assertTrue(matches("*[container~{items:{contains:[{items:\"diamond\"}]}}]", chest));
        assertTrue(matches("*[container~{items:{contains:[{items:\"diamond\"},{items:\"stone\"}]}}]", chest));
        assertFalse(matches("*[container~{items:{contains:[{items:\"diamond\"},{items:\"dirt\"}]}}]", chest));
        assertTrue(matches("*[container~{items:{contains:[{count:{min:2}}]}}]", chest));
        assertTrue(matches("*[container~{items:{count:[{test:{items:\"diamond\"},count:1}]}}]", chest), "칸 수(개수 아님)");
        assertTrue(matches("*[container~{items:{size:2}}]", chest), "빈 칸은 세지 않는다");
        assertFalse(matches("*[container~{items:{size:3}}]", chest));
        assertTrue(matches("*[container~{items:{contains:[{items:\"diamond\",predicates:{custom_data:{}}}]}}]", chest), "안쪽 술어에도 하위 술어");
        assertFalse(matches("*[container~{}]", stack("stone")), "컴포넌트가 없으면 거짓");
        assertEquals("arguments.item.predicate.malformed", errorKey("*[container~{items:{count:[{test:{}}]}}]"));

        ItemStack bundle = stack("bundle[bundle_contents=[{id:\"diamond\",count:3},{id:\"stick\",count:1}]]");
        assertTrue(matches("*[bundle_contents~{items:{contains:[{items:\"diamond\",count:3}]}}]", bundle));
        assertFalse(matches("*[bundle_contents~{items:{contains:[{items:\"diamond\",count:4}]}}]", bundle));
        assertTrue(matches("*[bundle_contents~{items:{size:{min:2}}}]", bundle));
    }

    @Test
    void fireworks() throws Exception {
        ItemStack star = stack("firework_star[firework_explosion={shape:\"star\",has_trail:true}]");
        assertTrue(matches("*[firework_explosion~{shape:\"star\",has_trail:true}]", star));
        assertTrue(matches("*[firework_explosion~{has_twinkle:false}]", star));
        assertFalse(matches("*[firework_explosion~{has_twinkle:true}]", star));
        assertFalse(matches("*[firework_explosion~{shape:\"burst\"}]", star));
        assertEquals("arguments.item.predicate.malformed", errorKey("*[firework_explosion~{shape:\"square\"}]"));

        ItemStack rocket = stack("firework_rocket[fireworks={flight_duration:2,explosions:[{shape:\"burst\",has_twinkle:true}]}]");
        assertTrue(matches("*[fireworks~{flight_duration:2,explosions:{contains:[{shape:\"burst\",has_twinkle:true}]}}]", rocket));
        assertFalse(matches("*[fireworks~{flight_duration:3}]", rocket));
        assertFalse(matches("*[fireworks~{explosions:{contains:[{shape:\"star\"}]}}]", rocket));
        assertTrue(matches("*[fireworks~{explosions:{size:1}}]", rocket));
    }

    @Test
    void books() throws Exception {
        ItemStack writable = stack("writable_book[writable_book_content={pages:[\"hello\",\"world\"]}]");
        assertTrue(matches("*[writable_book_content~{pages:{contains:[\"world\"]}}]", writable));
        assertFalse(matches("*[writable_book_content~{pages:{contains:[\"nope\"]}}]", writable));
        assertTrue(matches("*[writable_book_content~{pages:{size:2}}]", writable));

        ItemStack written = stack("written_book[written_book_content={title:\"T\",author:\"A\",generation:1,pages:['\"hi\"']}]");
        assertTrue(matches("*[written_book_content~{author:\"A\",title:\"T\",generation:1,resolved:false}]", written));
        assertFalse(matches("*[written_book_content~{author:\"B\"}]", written));
        assertFalse(matches("*[written_book_content~{generation:0}]", written));
        // 쪽 술어 값은 텍스트 컴포넌트 코덱: 문자열은 그 글자 그대로의 텍스트
        assertTrue(matches("*[written_book_content~{pages:{contains:[\"hi\"]}}]", written));
        assertTrue(matches("*[written_book_content~{pages:{contains:[{text:\"hi\"}]}}]", written));
        assertFalse(matches("*[written_book_content~{pages:{contains:['\"hi\"']}}]", written));
    }

    @Test
    void attributeModifiers() throws Exception {
        ItemStack sword = stack("diamond_sword");   // 기본값: attack_damage +6(base_attack_damage), attack_speed -2.4, 주손
        assertTrue(matches("*[attribute_modifiers~{modifiers:{contains:[{attribute:\"generic.attack_damage\",amount:{min:6}}]}}]", sword));
        assertTrue(matches("*[attribute_modifiers~{modifiers:{contains:[{id:\"base_attack_damage\",operation:\"add_value\",slot:\"mainhand\"}]}}]", sword));
        assertFalse(matches("*[attribute_modifiers~{modifiers:{contains:[{slot:\"offhand\"}]}}]", sword));
        assertTrue(matches("*[attribute_modifiers~{modifiers:{size:2}}]", sword));
        assertTrue(matches("*[attribute_modifiers~{modifiers:{size:0}}]", stack("stone")), "기본 빈 변경치");
    }

    @Test
    void trim() throws Exception {
        ItemStack chest = stack("diamond_chestplate[trim={material:\"minecraft:gold\",pattern:\"minecraft:coast\"}]");
        assertTrue(matches("*[trim~{material:\"gold\"}]", chest));
        assertTrue(matches("*[trim~{material:[\"iron\",\"gold\"],pattern:\"coast\"}]", chest));
        assertFalse(matches("*[trim~{pattern:\"wild\"}]", chest));
        assertFalse(matches("*[trim~{}]", stack("diamond_chestplate")));
    }

    @Test
    void jukeboxPlayable() throws Exception {
        ItemStack disc = stack("music_disc_13");
        assertTrue(matches("*[jukebox_playable~{}]", disc));
        assertTrue(matches("*[jukebox_playable~{song:\"13\"}]", disc));
        assertTrue(matches("*[jukebox_playable~{song:[\"cat\",\"13\"]}]", disc));
        assertFalse(matches("*[jukebox_playable~{song:\"cat\"}]", disc));
        assertFalse(matches("*[jukebox_playable~{}]", stack("stone")));
    }

    @Test
    void commandErrors() throws Exception {
        assertEquals("arguments.item.predicate.unknown", errorKey("*[foo~{}]"));
        assertEquals("arguments.item.predicate.malformed", errorKey("*[damage~\"x\"]"));
        assertEquals("arguments.item.predicate.malformed", errorKey("*[potion_contents~\"no_such_potion\"]"));
        // 1.21.1 MinMaxBounds 코덱은 뒤집힌 범위를 받는다(아무것도 맞지 않음)
        assertFalse(matches("*[count~{min:5,max:1}]", stack("stone", 3)));
    }

    // ------------------------------------------------------------------ 전리품·발전 과제 쪽

    private static ItemPredicate loot(String json) { return ItemPredicate.parse(JsonParser.parseString(json), regs); }

    private static LootStack lootStack(String s) throws CommandSyntaxException {
        ItemStack st = stack(s);
        return LootStack.of(ItemContext.name("minecraft:item", st.itemId()), st.count(), st.patch().entries());
    }

    @Test
    void lootSharesTheSameCodec() throws Exception {
        LootStack sword = lootStack("diamond_sword[custom_data={a:1},enchantments={levels:{sharpness:3}}]");
        // JSON 객체로 쓴 custom_data 는 JsonOps → NbtOps 변환에서 수가 좁혀진다(1 → 1b) — 바닐라도 그래서 int 1 과 다르다
        assertFalse(loot("{\"predicates\":{\"minecraft:custom_data\":{\"a\":1}}}").test(sword, regs));
        assertTrue(loot("{\"predicates\":{\"minecraft:custom_data\":\"{a:1}\"}}").test(sword, regs));
        assertTrue(loot("{\"items\":\"minecraft:diamond_sword\",\"predicates\":{\"enchantments\":[{\"enchantments\":\"minecraft:sharpness\",\"levels\":{\"min\":3}}]}}")
                .test(sword, regs));
        LootStack chest = lootStack("chest[container=[{slot:1,item:{id:\"diamond\",count:5}}]]");
        assertTrue(loot("{\"predicates\":{\"minecraft:container\":{\"items\":{\"contains\":[{\"items\":\"minecraft:diamond\",\"count\":{\"min\":5}}]}}}}")
                .test(chest, regs));
        assertTrue(loot("{\"predicates\":{\"minecraft:jukebox_playable\":{\"song\":\"minecraft:13\"}}}").test(lootStack("music_disc_13"), regs));
        assertTrue(loot("{\"components\":{\"minecraft:custom_data\":{\"a\":1}}}").test(lootStack("stick[custom_data={a:1b}]"), regs),
                "components(정확히 같음)도 같은 변환");
    }

    @Test
    void lootRejectsUnknownAndMalformed() {
        // 14종을 모두 구현했으므로 바닐라처럼 모르는 종류는 그 항목의 로드 오류(예전: 경고 후 항상 거짓)
        assertThrows(LootParseException.class, () -> loot("{\"predicates\":{\"minecraft:no_such_predicate\":{}}}"));
        assertThrows(LootParseException.class, () -> loot("{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:nope\"}]}}"));
        assertThrows(LootParseException.class, () -> loot("{\"components\":{\"minecraft:no_such_component\":1}}"));
        assertThrows(LootParseException.class, () -> loot("{\"items\":\"minecraft:no_such_item\"}"));
    }
}
