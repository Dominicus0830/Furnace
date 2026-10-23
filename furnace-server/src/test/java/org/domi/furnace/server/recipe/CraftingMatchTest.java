// 제작 매칭 시험 — 로컬 바닐라 데이터(tools/vanilla-data/server-1.21.1)의 실제 레시피로: 모양 있는/없는 제작, 좌우 뒤집기, 여분 재료, 남는 아이템.
package org.domi.furnace.server.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingMatchTest {

    static RecipeManager rm;
    static TagRegistries regs;
    static ResourceManager resources;

    @BeforeAll
    static void load() throws Exception {
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(root.resolve("data/minecraft/recipe")), "로컬 바닐라 데이터 없음");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        org.domi.furnace.server.item.ItemContext.install(regs);
        resources = repo.openResourceManager();
        TagSet tags = TagSet.of(regs.loadAll(resources));
        org.domi.furnace.server.item.ItemContext.installTags(tags);
        rm = RecipeManager.load(resources, new RecipeContext() {
            public Integer itemId(ResourceId item) { return regs.id("minecraft:item", item); }
            public int[] itemTag(ResourceId tag) { return tags.ids("minecraft:item", tag.toString()); }
            public int serializerId(String type) { return RecipeManager.serializerIndex(type); }
        });
    }

    @AfterAll
    static void close() throws Exception { if (resources != null) resources.close(); }

    static ItemStack item(String name, int n) { return new ItemStack(regs.id("minecraft:item", ResourceId.parse("minecraft:" + name)), n); }

    static ItemStack item(String name) { return item(name, 1); }

    /** w×h 격자, 이름 배열("" = 빈칸). */
    static CraftingInput grid(int w, int h, String... names) {
        List<ItemStack> l = new ArrayList<>();
        for (String n : names) l.add(n.isEmpty() ? ItemStack.EMPTY : item(n));
        return CraftingInput.of(w, h, l);
    }

    static String resultOf(CraftingInput in) {
        var e = rm.findCrafting(in, null);
        if (e == null) return null;
        ItemStack s = e.recipe().assemble(in, rm.context());
        return org.domi.furnace.server.item.ItemContext.name("minecraft:item", s.itemId()) + "x" + s.count();
    }

    @Test
    void planksFromLog_shapeless() {
        assertEquals("minecraft:oak_planksx4", resultOf(grid(2, 2, "oak_log", "", "", "")));
        // 격자 어디에 놓아도 같다(앞뒤 빈 행·열을 잘라 비교)
        assertEquals("minecraft:oak_planksx4", resultOf(grid(3, 3, "", "", "", "", "", "", "", "", "oak_log")));
    }

    @Test
    void sticksFromTwoPlanks_shaped() {
        assertEquals("minecraft:stickx4", resultOf(grid(2, 2, "oak_planks", "", "oak_planks", "")));
        assertEquals("minecraft:stickx4", resultOf(grid(2, 2, "", "oak_planks", "", "oak_planks")));
        assertEquals("minecraft:oak_pressure_platex1", resultOf(grid(2, 2, "oak_planks", "oak_planks", "", "")), "가로 두 개는 압력판");
        assertNull(resultOf(grid(2, 2, "oak_planks", "", "", "dirt")), "재료가 하나라도 다르면 없다");
    }

    @Test
    void craftingTable_fourPlanks_inTwoByTwo() {
        assertEquals("minecraft:crafting_tablex1", resultOf(grid(2, 2, "oak_planks", "oak_planks", "oak_planks", "oak_planks")));
    }

    @Test
    void shaped_mirrorAndExtraIngredient() {
        // 도끼(비대칭)는 좌우 뒤집어도 만들어진다
        assertEquals("minecraft:wooden_axex1",
            resultOf(grid(3, 3, "oak_planks", "oak_planks", "", "oak_planks", "stick", "", "", "stick", "")));
        assertEquals("minecraft:wooden_axex1",
            resultOf(grid(3, 3, "", "oak_planks", "oak_planks", "", "stick", "oak_planks", "", "stick", "")));
        // 여분 재료가 하나 더 있으면 안 된다
        assertNull(resultOf(grid(3, 3, "oak_planks", "oak_planks", "dirt", "oak_planks", "stick", "", "", "stick", "")));
    }

    @Test
    void shapeless_anyOrder_andWrongCount() {
        // 마른 풀 블록 아님 — 빵: 밀 3개 가로줄은 shaped. 여기선 shapeless 인 '설탕' 대신 '책'(가죽 + 종이 3)을 쓴다
        assertEquals("minecraft:bookx1", resultOf(grid(2, 2, "paper", "leather", "paper", "paper")));
        assertEquals("minecraft:bookx1", resultOf(grid(2, 2, "leather", "paper", "paper", "paper")));
        assertNull(resultOf(grid(2, 2, "leather", "paper", "paper", "")));
    }

    @Test
    void emptyGrid_noRecipe() {
        assertNull(rm.findCrafting(CraftingInput.EMPTY, null));
        assertNull(resultOf(grid(3, 3, "", "", "", "", "", "", "", "", "")));
    }

    @Test
    void remainingItems_bucketsAndBottlesLeaveContainers() {
        // 케이크: 우유 양동이 3 → 빈 양동이 3이 남는다
        CraftingInput cake = grid(3, 3, "milk_bucket", "milk_bucket", "milk_bucket", "sugar", "egg", "sugar", "wheat", "wheat", "wheat");
        assertEquals("minecraft:cakex1", resultOf(cake));
        List<ItemStack> rem = rm.remainingItems(cake);
        long buckets = rem.stream().filter(s -> !s.isEmpty() && s.itemId() == item("bucket").itemId()).count();
        assertEquals(3, buckets);
        assertTrue(rem.get(3).isEmpty() && rem.get(4).isEmpty());
    }

    @Test
    void craftingEntryOrderIsStable() {
        var a = rm.findCrafting(grid(2, 2, "oak_log", "", "", ""), null);
        var b = rm.findCrafting(grid(2, 2, "", "oak_log", "", ""), a);
        assertEquals(a, b);
    }

    @Test
    void ingredientCountsNonEmptyOnly() {
        CraftingInput in = grid(3, 3, "", "", "", "", "stone", "", "", "", "");
        assertEquals(1, in.width());
        assertEquals(1, in.height());
        assertEquals(1, in.ingredientCount());
        assertEquals(Arrays.asList(item("stone")), in.items());
    }
}
