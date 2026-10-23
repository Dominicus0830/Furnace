// 특수 제작 시험: 바닐라 레시피 파일의 특수 종류를 실제 격자로 — 염색 평균색, 불꽃놀이, 깃발 복제, 책·지도 복제, 수리, 방패 장식, 셜커 염색, 스튜, 물약 화살, 장식 단지.
package org.domi.furnace.server.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpecialRecipesTest {

    static RecipeManager rm;

    @BeforeAll
    static void load() throws Exception {
        rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
    }

    static ItemStack item(String name) { return TestRecipes.item(name, 1); }

    static CraftingInput grid(int w, int h, ItemStack... stacks) {
        List<ItemStack> l = new ArrayList<>(List.of(stacks));
        return CraftingInput.of(w, h, l);
    }

    static CraftingInput row(ItemStack... stacks) { return grid(stacks.length, 1, stacks); }

    static ItemStack craft(CraftingInput in) {
        var e = rm.findCrafting(in, null);
        assertNotNull(e, "레시피가 있어야 한다");
        return e.recipe().assemble(in, rm.context());
    }

    static String name(ItemStack s) { return ItemContext.name("minecraft:item", s.itemId()); }

    @Test
    void armorDye_singleDyeAndAverage() {
        ItemStack out = craft(row(item("leather_chestplate"), item("red_dye")));
        assertEquals("minecraft:leather_chestplate", name(out));
        assertEquals(Map.of("rgb", 11546150), ItemTools.component(out, "minecraft:dyed_color"), "show_in_tooltip 기본값(참)은 정규 값에서 빠진다");
        ItemStack two = craft(row(item("leather_chestplate"), item("red_dye"), item("blue_dye")));
        assertEquals(11359128, ((Map<?, ?>) ItemTools.component(two, "minecraft:dyed_color")).get("rgb"));
    }

    @Test
    void armorDye_needsDyeAndOneGarment() {
        assertNull(rm.findCrafting(row(item("leather_chestplate"), item("stone")), null));
        assertNull(rm.findCrafting(row(item("leather_chestplate"), item("leather_boots"), item("red_dye")), null));
    }

    @Test
    void fireworkStar_thenFade_thenRocket() {
        ItemStack star = craft(grid(3, 1, item("gunpowder"), item("red_dye"), item("feather")));
        assertEquals("minecraft:firework_star", name(star));
        Map<?, ?> ex = (Map<?, ?>) ItemTools.component(star, "minecraft:firework_explosion");
        assertEquals("burst", ex.get("shape"));
        assertEquals(List.of(11743532), ex.get("colors"));
        ItemStack full = craft(grid(5, 1, item("gunpowder"), item("blue_dye"), item("diamond"), item("glowstone_dust"), item("fire_charge")));
        Map<?, ?> ex2 = (Map<?, ?>) ItemTools.component(full, "minecraft:firework_explosion");
        assertEquals("large_ball", ex2.get("shape"));
        assertEquals(true, ex2.get("has_trail"));
        assertEquals(true, ex2.get("has_twinkle"));
        ItemStack faded = craft(row(star, item("green_dye")));
        assertEquals(List.of(3887386), ((Map<?, ?>) ItemTools.component(faded, "minecraft:firework_explosion")).get("fade_colors"));
        ItemStack rocket = craft(grid(4, 1, item("paper"), item("gunpowder"), item("gunpowder"), faded));
        assertEquals("minecraft:firework_rocket", name(rocket));
        assertEquals(3, rocket.count());
        Map<?, ?> fw = (Map<?, ?>) ItemTools.component(rocket, "minecraft:fireworks");
        assertEquals(2, fw.get("flight_duration"));
        assertEquals(1, ((List<?>) fw.get("explosions")).size());
        assertNull(rm.findCrafting(row(item("paper"), item("gunpowder"), item("gunpowder"), item("gunpowder"), item("gunpowder")), null), "화약은 3개까지");
    }

    @Test
    void mapCloning_makesNPlusOne() {
        ItemStack filled = new ItemStack(item("filled_map").itemId(), 1);
        ItemStack out = craft(grid(3, 1, filled, item("map"), item("map")));
        assertEquals("minecraft:filled_map", name(out));
        assertEquals(3, out.count());
    }

    @Test
    void bookCloning_incrementsGeneration_andKeepsOriginalInGrid() {
        Map<String, Object> content = Map.of("title", "T", "author", "A", "generation", 0);
        ItemStack book = ItemTools.with(item("written_book"), "minecraft:written_book_content", content);
        CraftingInput in = grid(3, 1, book, item("writable_book"), item("writable_book"));
        ItemStack out = craft(in);
        assertEquals(2, out.count());
        assertEquals(1, ((Map<?, ?>) ItemTools.component(out, "minecraft:written_book_content")).get("generation"));
        List<ItemStack> rem = rm.remainingItems(in);
        assertEquals(1, rem.stream().filter(s -> !s.isEmpty() && name(s).equals("minecraft:written_book")).count(), "원본 책은 격자에 남는다");
        // 사본의 사본(generation 2)은 못 만든다
        ItemStack copyOfCopy = ItemTools.with(item("written_book"), "minecraft:written_book_content", Map.of("title", "T", "author", "A", "generation", 2));
        assertTrue(rm.findCrafting(grid(2, 1, copyOfCopy, item("writable_book")), null) == null || craft(grid(2, 1, copyOfCopy, item("writable_book"))).isEmpty());
    }

    @Test
    void repair_combinesDurabilityPlusFivePercent() {
        ItemStack a = ItemTools.withDamage(item("iron_pickaxe"), 100);
        ItemStack b = ItemTools.withDamage(item("iron_pickaxe"), 200);
        ItemStack out = craft(row(a, b));
        assertEquals("minecraft:iron_pickaxe", name(out));
        int max = ItemTools.maxDamage(a);
        int expected = Math.max(max - ((max - 100) + (max - 200) + max * 5 / 100), 0);
        assertEquals(expected, ItemTools.damage(out));
        assertNull(rm.findCrafting(row(a, item("iron_axe")), null), "같은 아이템끼리만");
    }

    @Test
    void bannerDuplicate_needsPatternedAndBlankOfSameColor() {
        ItemStack patterned = ItemTools.with(item("white_banner"), "minecraft:banner_patterns",
            List.of(Map.of("pattern", "minecraft:border", "color", "red")));
        ItemStack out = craft(row(patterned, item("white_banner")));
        assertEquals(1, out.count());
        assertEquals(1, ((List<?>) ItemTools.component(out, "minecraft:banner_patterns")).size());
        List<ItemStack> rem = rm.remainingItems(row(patterned, item("white_banner")));
        assertEquals(1, rem.stream().filter(s -> !s.isEmpty()).count(), "무늬 있는 깃발은 격자에 남는다");
        assertNull(rm.findCrafting(row(patterned, item("red_banner")), null), "색이 다르면 안 된다");
    }

    @Test
    void shieldDecoration_copiesPatternsAndBaseColor() {
        ItemStack banner = ItemTools.with(item("red_banner"), "minecraft:banner_patterns",
            List.of(Map.of("pattern", "minecraft:border", "color", "white")));
        ItemStack out = craft(row(item("shield"), banner));
        assertEquals("minecraft:shield", name(out));
        assertEquals("red", ItemTools.component(out, "minecraft:base_color"));
        assertEquals(1, ((List<?>) ItemTools.component(out, "minecraft:banner_patterns")).size());
    }

    @Test
    void shulkerColoring_keepsComponents() {
        ItemStack box = ItemTools.with(item("shulker_box"), "minecraft:custom_name", "\"My Box\"");
        ItemStack out = craft(row(box, item("lime_dye")));
        assertEquals("minecraft:lime_shulker_box", name(out));
        assertEquals(box.patch().entries().get("minecraft:custom_name"), out.patch().entries().get("minecraft:custom_name"));
        ItemStack again = craft(row(out, item("pink_dye")));
        assertEquals("minecraft:pink_shulker_box", name(again));
    }

    @Test
    void suspiciousStew_takesFlowerEffect() {
        ItemStack out = craft(grid(2, 2, item("red_mushroom"), item("brown_mushroom"), item("bowl"), item("poppy")));
        assertEquals("minecraft:suspicious_stew", name(out));
        List<?> effects = (List<?>) ItemTools.component(out, "minecraft:suspicious_stew_effects");
        assertEquals(1, effects.size());
        assertEquals("minecraft:night_vision", ((Map<?, ?>) effects.get(0)).get("id"));
        assertEquals(100, ((Map<?, ?>) effects.get(0)).get("duration"));
    }

    @Test
    void tippedArrow_eightAroundLingeringPotion() {
        ItemStack potion = ItemTools.with(item("lingering_potion"), "minecraft:potion_contents", Map.of("potion", "minecraft:swiftness"));
        ItemStack a = item("arrow");
        ItemStack out = craft(grid(3, 3, a, a, a, a, potion, a, a, a, a));
        assertEquals("minecraft:tipped_arrow", name(out));
        assertEquals(8, out.count());
        assertEquals("minecraft:swiftness", ((Map<?, ?>) ItemTools.component(out, "minecraft:potion_contents")).get("potion"));
    }

    @Test
    void decoratedPot_fourSherdsAndBricks() {
        ItemStack e = ItemStack.EMPTY, brick = item("brick");
        ItemStack out = craft(grid(3, 3, e, brick, e, item("angler_pottery_sherd"), e, brick, e, brick, e));
        assertEquals("minecraft:decorated_pot", name(out));
        assertEquals(List.of("minecraft:brick", "minecraft:angler_pottery_sherd", "minecraft:brick", "minecraft:brick"),
            ItemTools.component(out, "minecraft:pot_decorations"));
        assertNull(rm.findCrafting(grid(3, 3, brick, brick, e, brick, e, brick, e, brick, e), null), "모서리에 벽돌이 있으면 장식 단지가 아니다");
    }

    @Test
    void specialRecipesDoNotUnlockInRecipeBook() {
        var e = rm.findCrafting(row(item("leather_chestplate"), item("red_dye")), null);
        assertTrue(e.isSpecial());
        var planks = rm.findCrafting(row(item("oak_log")), null);
        assertFalse(planks.isSpecial());
    }
}
