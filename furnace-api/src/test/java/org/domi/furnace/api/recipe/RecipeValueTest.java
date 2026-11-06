// 레시피 값 기록(검증·기본값)과 재료 선택자 정규화 시험.
package org.domi.furnace.api.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.api.item.ItemStack;
import org.junit.jupiter.api.Test;

class RecipeValueTest {

    private static final ItemStack DIAMOND = ItemStack.of("diamond");

    @Test
    void choiceNormalizesNamespacesAndTagHash() {
        assertEquals(List.of("minecraft:stone", "foo:bar"), ((RecipeChoice.Items) RecipeChoice.item("stone", "foo:bar")).items());
        assertEquals("minecraft:planks", ((RecipeChoice.Tag) RecipeChoice.tag("#planks")).tag());
        assertThrows(IllegalArgumentException.class, () -> new RecipeChoice.Items(List.of()));
        assertTrue(((RecipeChoice.Any) RecipeChoice.NONE).choices().isEmpty());
    }

    @Test
    void shapedDefaultsAndRequiredResult() {
        var r = new Recipe.Shaped(List.of("AA", "AA"), Map.of('A', RecipeChoice.item("stone")), DIAMOND);
        assertEquals("minecraft:crafting_shaped", r.type());
        assertEquals("", r.group());
        assertEquals(RecipeCategory.MISC, r.category());
        assertTrue(r.showNotification());
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Shaped(List.of("A"), Map.of('A', RecipeChoice.item("stone")), ItemStack.EMPTY));
    }

    @Test
    void cookingDefaultsFollowVanillaTimes() {
        var smelt = new Recipe.Cooking(Recipe.CookingKind.SMELTING, RecipeChoice.item("iron_ore"), DIAMOND, 0.7f);
        assertEquals(200, smelt.cookingTime());
        assertEquals("minecraft:smelting", smelt.type());
        assertEquals(100, new Recipe.Cooking(Recipe.CookingKind.BLASTING, RecipeChoice.item("iron_ore"), DIAMOND, 0f).cookingTime());
        assertEquals(600, new Recipe.Cooking(Recipe.CookingKind.CAMPFIRE, RecipeChoice.item("beef"), DIAMOND, 0f).cookingTime());
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Cooking(Recipe.CookingKind.SMELTING, "", RecipeCategory.MISC,
                RecipeChoice.item("iron_ore"), DIAMOND, -1f, 10));
        assertThrows(IllegalArgumentException.class, () -> new Recipe.Cooking(Recipe.CookingKind.SMELTING, "", RecipeCategory.MISC,
                RecipeChoice.item("iron_ore"), DIAMOND, 1f, 0));
    }

    @Test
    void specialIsReadOnlyMarker() {
        var s = new Recipe.Special("minecraft:crafting_special_armordye");
        assertTrue(s.result().isEmpty());
        assertEquals("", s.group());
    }

    @Test
    void shapelessAndStonecuttingTypes() {
        assertEquals("minecraft:crafting_shapeless", new Recipe.Shapeless(List.of(RecipeChoice.item("stone")), DIAMOND).type());
        assertEquals("minecraft:stonecutting", new Recipe.Stonecutting(RecipeChoice.item("stone"), DIAMOND).type());
        assertEquals("minecraft:smithing_transform",
                new Recipe.SmithingTransform(RecipeChoice.NONE, RecipeChoice.item("stone"), RecipeChoice.NONE, DIAMOND).type());
    }
}
