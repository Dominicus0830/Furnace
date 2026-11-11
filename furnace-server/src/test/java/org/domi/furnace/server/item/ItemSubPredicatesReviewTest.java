// PR #224 독립 검토 재현(R1 전리품 쪽 없는 키, R2 SNBT 스타일 불리언, R3 감싸개 예외, R4 중복 키)을 회귀 시험으로 남긴다.
// 기대값은 바닐라 1.21.1 바이트코드 기준.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.Map;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
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

class ItemSubPredicatesReviewTest {
    static LootRegistries regs;

    @BeforeAll
    static void load() throws Exception {
        PackRepository repo = new PackRepository(null, null);
        repo.reload();
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (var rm = repo.openResourceManager()) { regs = new LootRegistries(tags, tags.loadAll(rm)); }
    }

    @BeforeEach
    void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        ItemContext.installTags(TagSet.load("registries/tags-1.21.1.txt"));
    }

    static ItemStack stack(String s) throws Exception { return ItemArgument.item().parse(new StringReader(s)).createItemStack(1, false); }
    static boolean cmd(String p, ItemStack s) throws Exception { return ItemPredicateArgument.itemPredicate().parse(new StringReader(p)).test(s); }
    static ItemPredicate loot(String json) { return ItemPredicate.parse(JsonParser.parseString(json), regs); }

    // 쪽 비교는 Component.equals: Style(bold=false) 는 Style.EMPTY 와 다르다
    @Test void writtenPageStyleFalseIsNotPlain() throws Exception {
        ItemStack b = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['\"hi\"']}]");
        assertFalse(cmd("*[written_book_content~{pages:{contains:[{text:\"hi\",bold:false}]}}]", b));
    }

    @Test void writtenPageStyleFalseOnPage() throws Exception {
        ItemStack b = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['{\"text\":\"hi\",\"bold\":false}']}]");
        assertFalse(cmd("*[written_book_content~{pages:{contains:[\"hi\"]}}]", b));
        assertTrue(cmd("*[written_book_content~{pages:{contains:[{text:\"hi\",bold:false}]}}]", b));
    }

    @Test void writtenPageExtraVsSibling() throws Exception {
        ItemStack b = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['[\"a\",\"b\"]']}]");
        assertTrue(cmd("*[written_book_content~{pages:{contains:[[\"a\",\"b\"]]}}]", b));
        assertFalse(cmd("*[written_book_content~{pages:{contains:[\"ab\"]}}]", b));
    }

    // 전리품 쪽: 동기화 안 하는 레지스트리(물약·속성)는 없는 키를 검사하지 않는다 — 바닐라는 로드 오류
    @Test void lootUnknownPotionIsLoadError() {
        assertThrows(LootParseException.class, () -> loot("{\"predicates\":{\"minecraft:potion_contents\":\"minecraft:no_such_potion\"}}"));
    }

    @Test void lootUnknownAttributeIsLoadError() {
        assertThrows(LootParseException.class,
                () -> loot("{\"predicates\":{\"minecraft:attribute_modifiers\":{\"modifiers\":{\"contains\":[{\"attribute\":\"minecraft:no_such_attr\"}]}}}}"));
    }

    // 같은 종류를 두 번(네임스페이스 생략/포함) — DFU 8.0.16 DispatchedMapCodec: Duplicate entry for key
    @Test void lootDuplicateSubPredicateKey() {
        assertThrows(LootParseException.class,
                () -> loot("{\"predicates\":{\"damage\":{\"damage\":1},\"minecraft:damage\":{\"damage\":2}}}"));
    }

    // 감싸개: 정규화 실패 시 원래 값을 돌려 시험 때 CodecException 이 새는지
    @Test void lootRawInvalidComponentDoesNotThrowAtTestTime() {
        LootStack s = LootStack.of("minecraft:stick", 1, Map.of("minecraft:enchantments", 5));
        ItemPredicate p = loot("{\"predicates\":{\"minecraft:enchantments\":[{}]}}");
        assertDoesNotThrow(() -> p.test(s, regs));
    }

    // 감싸개: 줄임 꼴(물약 문자열) 원값도 정규화돼 맞는가
    @Test void lootRawPotionShorthand() {
        LootStack s = LootStack.of("minecraft:potion", 1, Map.of("minecraft:potion_contents", "minecraft:swiftness"));
        assertTrue(loot("{\"predicates\":{\"minecraft:potion_contents\":\"minecraft:swiftness\"}}").test(s, regs));
    }

    // 기본값 인챈트(빈 집합)·속성 — 전리품 쪽도 명령과 같게
    @Test void lootDefaultsLikeCommand() {
        LootStack s = LootStack.of("minecraft:stone", 1, Map.of());
        assertTrue(loot("{\"predicates\":{\"minecraft:enchantments\":[]}}").test(s, regs));
        assertTrue(loot("{\"predicates\":{\"minecraft:attribute_modifiers\":{}}}").test(s, regs));
        assertFalse(loot("{\"predicates\":{\"minecraft:enchantments\":[{}]}}").test(s, regs));
        LootStack sw = LootStack.of("minecraft:diamond_sword", 1, Map.of());
        assertTrue(loot("{\"predicates\":{\"minecraft:attribute_modifiers\":{\"modifiers\":{\"size\":2}}}}").test(sw, regs));
    }

    // SNBT 불리언은 바이트(1b): 바닐라 NbtOps Codec.BOOL 은 1b=true 로 읽는다 — bold:true 술어는 굵지 않은 쪽과 달라야
    @Test void writtenPageBoldTrueSnbt() throws Exception {
        ItemStack plain = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['\"hi\"']}]");
        assertFalse(cmd("*[written_book_content~{pages:{contains:[{text:\"hi\",bold:true}]}}]", plain));
        ItemStack bold = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['{\"text\":\"hi\",\"bold\":true}']}]");
        assertTrue(cmd("*[written_book_content~{pages:{contains:[{text:\"hi\",bold:true}]}}]", bold));
    }

    // 전리품(JSON) 쪽은 진짜 불리언
    @Test void writtenPageStyleLootJson() throws Exception {
        ItemStack plain = stack("written_book[written_book_content={title:\"T\",author:\"A\",pages:['\"hi\"']}]");
        LootStack ls = LootStack.of("minecraft:written_book", 1, plain.patch().entries());
        assertFalse(loot("{\"predicates\":{\"minecraft:written_book_content\":{\"pages\":{\"contains\":[{\"text\":\"hi\",\"bold\":false}]}}}}").test(ls, regs));
        assertFalse(loot("{\"predicates\":{\"minecraft:written_book_content\":{\"pages\":{\"contains\":[{\"text\":\"hi\",\"bold\":true}]}}}}").test(ls, regs));
        assertTrue(loot("{\"predicates\":{\"minecraft:written_book_content\":{\"pages\":{\"contains\":[\"hi\"]}}}}").test(ls, regs));
    }

    @Test void lootUnknownTrimMaterialIsLoadError() {
        assertThrows(LootParseException.class, () -> loot("{\"predicates\":{\"minecraft:trim\":{\"material\":\"minecraft:no_such_material\"}}}"));
    }

    @Test void lootUnknownJukeboxSongIsLoadError() {
        assertThrows(LootParseException.class, () -> loot("{\"predicates\":{\"minecraft:jukebox_playable\":{\"song\":\"minecraft:no_such_song\"}}}"));
    }

    @Test void commandUnknownAttributeAndTrimRejected() throws Exception {
        assertThrows(Exception.class, () -> ItemPredicateArgument.itemPredicate().parse(new StringReader("*[attribute_modifiers~{modifiers:{contains:[{attribute:\"no_such_attr\"}]}}]")));
        assertThrows(Exception.class, () -> ItemPredicateArgument.itemPredicate().parse(new StringReader("*[trim~{material:\"no_such_material\"}]")));
    }
}
