// 양조대 시험: 물병 + 네더 사마귀 → 어색한 물약(400틱, 연료 1회 소모), 화약으로 투척용, 재료가 안 맞으면 시작하지 않음, 병 상태 속성.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.domi.furnace.server.block.entity.BrewingStandBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.PotionBrewing;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.Test;

class BrewingStandBlockEntityTest {

    static ItemStack item(String name, int n) { return TestRecipes.item(name, n); }

    static ItemStack potion(String item, String potion) { return PotionBrewing.createItemStack("minecraft:" + item, "minecraft:" + potion); }

    @Test
    void brewsAwkwardPotionThenSplashWithGunpowder() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(1, -60, 1);
        int state = B.defaultState("minecraft:brewing_stand");
        level.setBlock(pos, state, 3);
        BrewingStandBlockEntity be = new BrewingStandBlockEntity(pos, state);
        be.items[0] = potion("potion", "water");
        be.items[1] = potion("potion", "water");
        be.items[3] = item("nether_wart", 1);
        be.items[4] = item("blaze_powder", 1);
        be.tick(level);   // 연료 채우고 시작
        assertEquals(399 + 1, be.brewTime);
        assertEquals(19, be.fuel);
        assertTrue(be.items[4].isEmpty());
        for (int i = 0; i < 400; i++) be.tick(level);
        assertEquals("minecraft:awkward", PotionBrewing.potionOf(be.items[0]));
        assertEquals("minecraft:awkward", PotionBrewing.potionOf(be.items[1]));
        assertTrue(be.items[3].isEmpty());
        assertTrue(be.items[2].isEmpty());
        assertEquals(0, be.brewTime);
        // 화약 → 투척용
        be.items[3] = item("gunpowder", 1);
        be.tick(level);
        for (int i = 0; i < 400; i++) be.tick(level);
        assertEquals("minecraft:splash_potion", org.domi.furnace.server.item.ItemTools.itemName(be.items[0]));
        assertEquals("minecraft:awkward", PotionBrewing.potionOf(be.items[0]));
        assertEquals(18, be.fuel);
    }

    @Test
    void wrongIngredientDoesNotStart() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(1, -60, 1);
        BrewingStandBlockEntity be = new BrewingStandBlockEntity(pos, B.defaultState("minecraft:brewing_stand"));
        be.items[0] = potion("potion", "water");
        be.items[3] = item("dirt", 1);
        be.fuel = 5;
        be.tick(level);
        assertEquals(0, be.brewTime);
        assertEquals(5, be.fuel);
    }

    @Test
    void mixTableLookups() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        assertTrue(PotionBrewing.isIngredient(item("redstone", 1)));
        assertFalse(PotionBrewing.isIngredient(item("dirt", 1)));
        ItemStack awkward = potion("potion", "awkward");
        assertTrue(PotionBrewing.hasMix(awkward, item("golden_carrot", 1)));
        assertEquals("minecraft:night_vision", PotionBrewing.potionOf(PotionBrewing.mix(item("golden_carrot", 1), awkward)));
        assertEquals("minecraft:mundane", PotionBrewing.potionOf(PotionBrewing.mix(item("sugar", 1), potion("potion", "water"))));
        assertEquals("minecraft:swiftness", PotionBrewing.potionOf(PotionBrewing.mix(item("sugar", 1), awkward)));
        assertFalse(PotionBrewing.hasMix(item("glass_bottle", 1), item("redstone", 1)));
    }
}
