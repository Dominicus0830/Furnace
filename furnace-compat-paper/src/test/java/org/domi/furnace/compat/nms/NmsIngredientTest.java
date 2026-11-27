package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 재료(Ingredient) — 몹 목표(TemptGoal 등)가 먹이 확인에 쓴다. 규칙은 바닐라 1.21.1 test·of·EMPTY 와 같다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsIngredientTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        org.bukkit.Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void itemsMatchByKindNotCount() {
        Ingredient wheat = Ingredient.of(Items.WHEAT, Items.CARROT);
        assertTrue(wheat.test(new ItemStack(Items.WHEAT, 3)));
        assertTrue(wheat.test(new ItemStack(Items.CARROT)));
        assertFalse(wheat.test(new ItemStack(Items.APPLE)));
        assertFalse(wheat.test(ItemStack.EMPTY));
        assertFalse(wheat.test(null));
        assertEquals(2, wheat.getItems().length);
        assertFalse(wheat.isEmpty());
    }

    @Test
    void emptyIngredientMatchesOnlyEmptyStacks() {
        assertSame(Ingredient.EMPTY, Ingredient.of());
        assertTrue(Ingredient.EMPTY.isEmpty());
        assertTrue(Ingredient.EMPTY.test(ItemStack.EMPTY));
        assertFalse(Ingredient.EMPTY.test(new ItemStack(Items.WHEAT)));
        assertFalse(Ingredient.EMPTY.test(null));
        // 바닐라: 빈 스택만 든 재료도 EMPTY
        assertSame(Ingredient.EMPTY, Ingredient.of(ItemStack.EMPTY));
    }

    @Test
    void tagIngredientReadsTheTag() {
        Ingredient planks = Ingredient.of(ItemTags.PLANKS);
        assertTrue(planks.test(new ItemStack(Items.OAK_PLANKS)));
        assertFalse(planks.test(new ItemStack(Items.STONE)));
        assertTrue(planks.getItems().length > 5);
    }

    @Test
    void equalsFollowsTheValues() {
        assertEquals(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WHEAT));
        assertNotEquals(Ingredient.of(Items.WHEAT), Ingredient.of(Items.CARROT));
        assertEquals(Ingredient.of(ItemTags.PLANKS), Ingredient.of(ItemTags.PLANKS));
    }
}
