// 아이템 컴포넌트 타입 API·아이템 영속 데이터: 코덱 규칙(범위·기본값), 바닐라식 기본값 처리, 저장 왕복, 서버 스택 변환.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.api.item.AttributeModifiers;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponent;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemEnchantments;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.item.ItemSupport;
import org.domi.furnace.api.item.Marker;
import org.domi.furnace.api.item.Rarity;
import org.domi.furnace.api.item.Unbreakable;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemComponentsApiTest {

    private static ItemRegistry items;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        items = ItemRegistry.load("items/1.21.1.txt");
        ItemSupport.install(ItemSupportImpl.INSTANCE);
    }

    private static ItemStack sword() { return ItemStack.of("diamond_sword"); }

    // ------------------------------------------------------------------ 타입 있는 값

    @Test
    void textNameAndLoreRoundTrip() {
        Text name = Text.text("검").color(org.domi.furnace.api.text.NamedTextColor.GOLD);
        ItemStack s = sword().with(ItemComponents.CUSTOM_NAME, name).with(ItemComponents.LORE, List.of(Text.text("첫 줄"), Text.text("둘째 줄")))
                .with(ItemComponents.ITEM_NAME, Text.text("기본 이름"));
        assertEquals(name, s.get(ItemComponents.CUSTOM_NAME));
        assertEquals(List.of(Text.text("첫 줄"), Text.text("둘째 줄")), s.get(ItemComponents.LORE));
        assertEquals(Text.text("기본 이름"), s.get(ItemComponents.ITEM_NAME));
        // 저장 꼴은 바닐라 flatCodec 의 안정 JSON 문자열
        assertTrue(s.components().get("minecraft:custom_name").contains("gold"), s.components().get("minecraft:custom_name"));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.LORE, java.util.Collections.nCopies(257, Text.text("x"))), "로어는 256줄까지");
    }

    @Test
    void numbersEnforceVanillaRanges() {
        ItemStack s = sword().with(ItemComponents.DAMAGE, 7).with(ItemComponents.CUSTOM_MODEL_DATA, 12).with(ItemComponents.REPAIR_COST, 3);
        assertEquals(7, s.get(ItemComponents.DAMAGE));
        assertEquals(12, s.get(ItemComponents.CUSTOM_MODEL_DATA));
        assertEquals(3, s.get(ItemComponents.REPAIR_COST));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.DAMAGE, -1));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.MAX_STACK_SIZE, 100));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.MAX_STACK_SIZE, 0));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.MAX_DAMAGE, 0));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.OMINOUS_BOTTLE_AMPLIFIER, 5));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.DAMAGE, null));
    }

    @Test
    void rarityUnbreakableGlintAndMarkers() {
        ItemStack s = sword().with(ItemComponents.RARITY, Rarity.EPIC).with(ItemComponents.UNBREAKABLE, new Unbreakable(false))
                .with(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE, true).with(ItemComponents.HIDE_TOOLTIP);
        assertEquals(Rarity.EPIC, s.get(ItemComponents.RARITY));
        assertEquals(new Unbreakable(false), s.get(ItemComponents.UNBREAKABLE));
        assertEquals(Boolean.TRUE, s.get(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertEquals(Marker.PRESENT, s.get(ItemComponents.HIDE_TOOLTIP));
        assertTrue(s.has(ItemComponents.HIDE_TOOLTIP));
        assertFalse(s.has(ItemComponents.FIRE_RESISTANT));
        assertNull(s.get(ItemComponents.FIRE_RESISTANT));
    }

    @Test
    void enchantmentsAndAttributeModifiers() {
        ItemEnchantments ench = ItemEnchantments.EMPTY.with("sharpness", 5).with("minecraft:unbreaking", 3).withShowInTooltip(false);
        ItemStack s = sword().with(ItemComponents.ENCHANTMENTS, ench);
        assertEquals(ench, s.get(ItemComponents.ENCHANTMENTS));
        assertEquals(5, s.get(ItemComponents.ENCHANTMENTS).level("minecraft:sharpness"));
        assertEquals(0, ItemEnchantments.EMPTY.with("sharpness", 5).without("sharpness").levels().size(), "레벨 0 은 넣지 않는다");
        assertThrows(IllegalArgumentException.class, () -> ItemEnchantments.EMPTY.with("sharpness", 256));
        assertThrows(IllegalArgumentException.class, () -> sword().with(ItemComponents.ENCHANTMENTS, ItemEnchantments.EMPTY.with("no_such_enchant", 1)), "레지스트리에 없는 마법 부여");
        ItemStack book = ItemStack.of("enchanted_book").with(ItemComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY.with("mending", 1));
        assertEquals(1, book.get(ItemComponents.STORED_ENCHANTMENTS).level("mending"));

        var mod = new AttributeModifiers.Entry("generic.attack_damage", "furnace:bonus", 2.5, AttributeModifiers.Operation.ADD_VALUE, "mainhand");
        ItemStack m = sword().with(ItemComponents.ATTRIBUTE_MODIFIERS, AttributeModifiers.EMPTY.with(mod));
        var got = m.get(ItemComponents.ATTRIBUTE_MODIFIERS);
        assertEquals(1, got.modifiers().size());
        assertEquals("minecraft:generic.attack_damage", got.modifiers().get(0).attribute());
        assertEquals(2.5, got.modifiers().get(0).amount());
        assertEquals("mainhand", got.modifiers().get(0).slot());
    }

    @Test
    void dyedColorStringsAndMaps() {
        ItemStack s = ItemStack.of("leather_chestplate").with(ItemComponents.DYED_COLOR, new DyedColor(0xFF8800, false));
        assertEquals(new DyedColor(0xFF8800, false), s.get(ItemComponents.DYED_COLOR));
        ItemStack st = ItemStack.of("oak_log").with(ItemComponents.BLOCK_STATE, Map.of("axis", "x")).with(ItemComponents.LOCK, "열쇠");
        assertEquals(Map.of("axis", "x"), st.get(ItemComponents.BLOCK_STATE));
        assertEquals("열쇠", st.get(ItemComponents.LOCK));
        ItemStack r = ItemStack.of("knowledge_book").with(ItemComponents.RECIPES, List.of("minecraft:stick"));
        assertEquals(List.of("minecraft:stick"), r.get(ItemComponents.RECIPES));
    }

    // ------------------------------------------------------------------ 바닐라식 기본값

    @Test
    void defaultsComeFromPrototypeAndRedundantPatchIsDropped() {
        assertEquals(1, sword().get(ItemComponents.MAX_STACK_SIZE), "검은 쌓이지 않는다");
        assertEquals(64, ItemStack.of("stone").get(ItemComponents.MAX_STACK_SIZE));
        assertEquals(1561, sword().get(ItemComponents.MAX_DAMAGE));
        assertEquals(0, sword().getOrDefault(ItemComponents.DAMAGE, 0));

        // 기본값과 같은 값은 붙이지 않는다
        ItemStack same = sword().with(ItemComponents.MAX_STACK_SIZE, 1);
        assertTrue(same.components().isEmpty(), same.components().toString());
        assertEquals(sword(), same);
        // 다른 값은 붙고, 다시 기본값으로 되돌리면 떨어진다
        ItemStack big = sword().with(ItemComponents.MAX_STACK_SIZE, 16);
        assertEquals(16, big.get(ItemComponents.MAX_STACK_SIZE));
        assertEquals(sword(), big.with(ItemComponents.MAX_STACK_SIZE, 1));
    }

    @Test
    void removeMarksDefaultsOnlyWhenTheItemHasOne() {
        ItemStack noMaxDamage = sword().without(ItemComponents.MAX_DAMAGE);
        assertNull(noMaxDamage.get(ItemComponents.MAX_DAMAGE));
        assertTrue(noMaxDamage.removedComponents().contains("minecraft:max_damage"), "기본값이 있던 것은 지움 표시");
        // 기본값이 없는 컴포넌트는 그냥 뗀다
        ItemStack named = sword().with(ItemComponents.CUSTOM_NAME, Text.text("x"));
        ItemStack unnamed = named.without(ItemComponents.CUSTOM_NAME);
        assertEquals(sword(), unnamed);
        assertTrue(unnamed.removedComponents().isEmpty());
        // 지움 표시 뒤에 값을 넣으면 표시가 사라진다
        assertTrue(noMaxDamage.with(ItemComponents.MAX_DAMAGE, 100).removedComponents().isEmpty());
        assertEquals(100, noMaxDamage.with(ItemComponents.MAX_DAMAGE, 100).get(ItemComponents.MAX_DAMAGE));
    }

    // ------------------------------------------------------------------ raw

    @Test
    void rawAccessesAnyComponentAsSnbt() {
        ItemComponent<String> food = ItemComponent.raw("minecraft:food");
        ItemStack s = ItemStack.of("stick").with(food, "{nutrition:4,saturation:0.5f,can_always_eat:1b}");
        String back = s.get(food);
        assertNotNull(back);
        assertTrue(back.contains("nutrition:4"), back);
        assertThrows(IllegalArgumentException.class, () -> ItemStack.of("stick").with(food, "{nutrition:-3,saturation:0.5f}"), "코덱 규칙 위반");
        assertThrows(IllegalArgumentException.class, () -> ItemStack.of("stick").with(ItemComponent.raw("minecraft:nope"), "1"));
        // raw 와 타입 키는 다르다 — lock 은 raw 면 따옴표 있는 SNBT 문자열
        assertEquals("\"k\"", ItemStack.of("stick").with(ItemComponent.raw("minecraft:lock"), "\"k\"").get(ItemComponent.raw("minecraft:lock")));
        assertNotEquals(ItemComponent.raw("minecraft:lock"), ItemComponents.LOCK);
    }

    // ------------------------------------------------------------------ 서버 스택·저장 왕복

    @Test
    void typedValuesSurviveServerConversionAndSave() {
        ItemStack s = sword().with(ItemComponents.CUSTOM_NAME, Text.text("이름")).with(ItemComponents.ENCHANTMENTS, ItemEnchantments.EMPTY.with("sharpness", 2))
                .with(ItemComponents.DAMAGE, 9).withPersistentData(c -> c.set("myplugin:level", PersistentDataType.INTEGER, 4));
        var server = ApiConvert.item(s, items);
        var saved = (NbtCompound) ItemStackCodecs.ITEM_STACK.toNbt(ItemStackCodecs.toCanonical(server));
        var loaded = ItemStackCodecs.fromCanonical(Map.of("id", "minecraft:diamond_sword", "count", 1,
                "components", ItemStackCodecs.ITEM_STACK.normalize(saved) instanceof Map<?, ?> m ? m.get("components") : Map.of()));
        ItemStack back = ApiConvert.item(loaded, items);
        assertEquals(Text.text("이름"), back.get(ItemComponents.CUSTOM_NAME));
        assertEquals(2, back.get(ItemComponents.ENCHANTMENTS).level("sharpness"));
        assertEquals(9, back.get(ItemComponents.DAMAGE));
        assertEquals(4, back.persistentData().get("myplugin:level", PersistentDataType.INTEGER));
    }

    // ------------------------------------------------------------------ 영속 데이터

    @Test
    void persistentDataSetGetRemove() {
        ItemStack base = sword();
        assertTrue(base.persistentData().isEmpty());
        ItemStack s = base.withPersistentData(c -> {
            c.set("myplugin:owner", PersistentDataType.STRING, "도미니코");
            c.set("myplugin:kills", PersistentDataType.LONG, 12L);
            c.set("myplugin:flag", PersistentDataType.BOOLEAN, true);
            c.set("myplugin:bytes", PersistentDataType.BYTE_ARRAY, new byte[] {1, 2, 3});
        });
        assertNotSame(base, s);
        assertTrue(base.persistentData().isEmpty(), "원래 스택은 그대로");
        var view = s.persistentData();
        assertEquals("도미니코", view.get("myplugin:owner", PersistentDataType.STRING));
        assertEquals(12L, view.get("myplugin:kills", PersistentDataType.LONG));
        assertEquals(Boolean.TRUE, view.get("myplugin:flag", PersistentDataType.BOOLEAN));
        assertArrayEquals(new byte[] {1, 2, 3}, view.get("myplugin:bytes", PersistentDataType.BYTE_ARRAY));
        assertTrue(view.has("myplugin:kills"));
        assertFalse(view.has("myplugin:kills", PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class, () -> view.get("myplugin:kills", PersistentDataType.STRING));
        assertEquals(4, view.keys().size());
        assertThrows(UnsupportedOperationException.class, () -> ((org.domi.furnace.api.persistence.PersistentDataContainer) view).remove("myplugin:owner"), "보기는 읽기 전용");

        ItemStack removed = s.withPersistentData(c -> {
            c.remove("myplugin:owner");
            c.remove("myplugin:kills");
            c.remove("myplugin:flag");
            c.remove("myplugin:bytes");
        });
        assertEquals(base, removed, "다 지우면 custom_data 도 사라져 처음과 같다");
        assertFalse(removed.components().containsKey("minecraft:custom_data"));
    }

    @Test
    void persistentDataStoredLikePaperAndKeepsOtherCustomData() {
        ItemStack s = sword().withComponent("minecraft:custom_data", "{other:1b,PublicBukkitValues:{\"keep:me\":5}}")
                .withPersistentData(c -> c.set("myplugin:k", PersistentDataType.INTEGER, 7));
        NbtCompound custom = (NbtCompound) Snbt.parse(s.components().get("minecraft:custom_data"));
        assertEquals((byte) 1, custom.tags().get("other"), "다른 custom_data 는 그대로");
        NbtCompound pbv = (NbtCompound) custom.tags().get("PublicBukkitValues");
        assertEquals(5, pbv.tags().get("keep:me"), "다른 서버/플러그인이 쓴 값도 보존");
        assertEquals(7, pbv.tags().get("myplugin:k"));
        // 영속 데이터를 다 지워도 다른 custom_data 가 있으면 컴포넌트는 남는다
        ItemStack cleared = s.withPersistentData(c -> {
            c.remove("myplugin:k");
            c.remove("keep:me");
        });
        NbtCompound after = (NbtCompound) Snbt.parse(cleared.components().get("minecraft:custom_data"));
        assertEquals(1, after.tags().size());
        assertTrue(after.tags().containsKey("other"));
    }

    @Test
    void persistentDataNoChangeReturnsSameStackAndNestedContainers() {
        ItemStack base = sword();
        assertSame(base, base.withPersistentData(c -> {}));
        assertSame(base, base.withPersistentData(c -> c.remove("myplugin:none")));
        ItemStack s = base.withPersistentData(c -> {
            var inner = c.newContainer();
            inner.set("a:b", PersistentDataType.DOUBLE, 1.5);
            c.set("myplugin:inner", PersistentDataType.TAG_CONTAINER, inner);
        });
        assertEquals(1.5, s.persistentData().get("myplugin:inner", PersistentDataType.TAG_CONTAINER).get("a:b", PersistentDataType.DOUBLE));
        assertSame(s, s.withPersistentData(c -> c.set("myplugin:inner", PersistentDataType.TAG_CONTAINER, c.get("myplugin:inner", PersistentDataType.TAG_CONTAINER))),
                "같은 값을 다시 넣으면 바뀐 것이 없다");
        assertThrows(IllegalArgumentException.class, () -> base.withPersistentData(c -> c.set("Bad Key", PersistentDataType.INTEGER, 1)), "키 규칙");
        assertThrows(IllegalArgumentException.class, () -> base.withPersistentData(c -> c.set("a:b", PersistentDataType.INTEGER, null)));
    }

    @Test
    void persistentDataAffectsSimilarityAndAmountKeepsData() {
        ItemStack a = ItemStack.of("stick").withPersistentData(c -> c.set("p:x", PersistentDataType.INTEGER, 1));
        ItemStack b = ItemStack.of("stick").withPersistentData(c -> c.set("p:x", PersistentDataType.INTEGER, 2));
        ItemStack a2 = ItemStack.of("stick").withPersistentData(c -> c.set("p:x", PersistentDataType.INTEGER, 1));
        assertFalse(a.isSimilar(b));
        assertTrue(a.isSimilar(a2), "같은 데이터면 쌓인다");
        assertEquals(1, a.withAmount(5).persistentData().get("p:x", PersistentDataType.INTEGER));
    }

    @Test
    void persistentDataLists() {
        var strings = PersistentDataType.list(PersistentDataType.STRING);
        var ints = PersistentDataType.list(PersistentDataType.INTEGER);
        ItemStack s = sword().withPersistentData(c -> {
            c.set("p:names", strings, List.of("a", "b"));
            c.set("p:nums", ints, List.of(1, 2, 3));
            c.set("p:empty", ints, List.of());
            c.set("p:boxes", PersistentDataType.list(PersistentDataType.TAG_CONTAINER), List.of(c.newContainer()));
        });
        var view = s.persistentData();
        assertEquals(List.of("a", "b"), view.get("p:names", strings));
        assertEquals(List.of(1, 2, 3), view.get("p:nums", ints));
        assertEquals(List.of(), view.get("p:empty", ints));
        assertEquals(1, view.get("p:boxes", PersistentDataType.list(PersistentDataType.TAG_CONTAINER)).size());
        assertTrue(view.has("p:names", strings));
        assertFalse(view.has("p:names", ints), "원소 종류가 다르면 읽을 수 없다");
        assertThrows(IllegalArgumentException.class, () -> view.get("p:names", ints));
        assertThrows(UnsupportedOperationException.class, () -> view.get("p:nums", ints).add(4), "읽은 목록은 고칠 수 없다");
        assertEquals(strings, PersistentDataType.list(PersistentDataType.STRING));
        assertNotEquals(strings, ints);
        assertThrows(IllegalArgumentException.class, () -> PersistentDataType.list(strings), "목록의 목록");
        assertThrows(IllegalArgumentException.class, () -> sword().withPersistentData(c -> c.set("p:x", strings, (List) List.of("a", 1))), "섞인 종류");
        assertThrows(IllegalArgumentException.class, () -> sword().withPersistentData(c -> c.set("p:x", ints, (List) List.of("a"))));
    }
}
