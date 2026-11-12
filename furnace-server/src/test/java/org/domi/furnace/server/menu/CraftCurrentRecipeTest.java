// PR #238 검토 R1: 격자가 어떤 레시피와도 맞지 않는데 플러그인이 PrepareItemCraftEvent 로 결과를 넣으면(사용자 정의 레시피),
// 예전에는 결과 칸 클릭이 앞서 맞았던 레시피의 id 로 CraftItemEvent 가 됐다. Paper 는 CraftingInventory#getRecipe() 가 격자의 "지금" 레시피(맞는 것 없으면 null)라 보통 InventoryClickEvent 다.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.domi.furnace.server.recipe.RecipeContext;
import org.domi.furnace.server.recipe.RecipeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CraftCurrentRecipeTest {

    @TempDir
    Path tmp;

    static TagRegistries regs;

    static RecipeManager planksOnly(Path root) throws Exception {
        Path dir = root.resolve("data/minecraft/recipe");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("oak_planks.json"),
                "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"building\",\"group\":\"planks\","
                + "\"ingredients\":[{\"item\":\"minecraft:oak_log\"}],\"result\":{\"count\":4,\"id\":\"minecraft:oak_planks\"}}");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        org.domi.furnace.server.item.ItemContext.install(regs);
        try (ResourceManager rm = repo.openResourceManager()) {
            TagSet tags = TagSet.of(regs.loadAll(rm));
            org.domi.furnace.server.item.ItemContext.installTags(tags);
            return RecipeManager.load(rm, new RecipeContext() {
                public Integer itemId(ResourceId item) { return regs.id("minecraft:item", item); }
                public int[] itemTag(ResourceId tag) { return tags.ids("minecraft:item", tag.toString()); }
                public int serializerId(String type) { return RecipeManager.serializerIndex(type); }
            });
        }
    }

    static ItemStack it(String name, int n) { return new ItemStack(regs.id("minecraft:item", ResourceId.parse("minecraft:" + name)), n); }

    @Test
    void customResultAfterGridChangeReportsStaleRecipe() throws Exception {
        RecipeManager rm = planksOnly(tmp);
        PlayerInventory inv = new PlayerInventory();
        java.util.List<String> prepared = new java.util.ArrayList<>();
        var player = new CraftingMenuTest.Player(inv, rm) {
            @Override
            public ItemStack prepareCraft(AbstractContainerMenu menu, String recipe, ItemStack result) {
                prepared.add(String.valueOf(recipe));
                // 사용자 정의 레시피 플러그인: 막대기 하나면 다이아몬드
                if (recipe == null && menu.getSlot(1).getItem().itemId() == it("stick", 1).itemId()) return it("diamond", 1);
                return result;
            }
        };
        InventoryMenu menu = new InventoryMenu(inv, player);
        inv.set(36, it("oak_log", 1));
        inv.set(37, it("stick", 1));
        menu.clicked(36, 0, ClickType.PICKUP, player);   // 커서에 통나무
        menu.clicked(1, 0, ClickType.PICKUP, player);    // 격자 첫 칸 → 판자 레시피
        assertEquals(it("oak_planks", 4).itemId(), menu.getSlot(0).getItem().itemId());
        menu.clicked(1, 0, ClickType.PICKUP, player);    // 통나무를 도로 집는다 → 맞는 레시피 없음
        menu.clicked(36, 0, ClickType.PICKUP, player);   // 통나무를 원래 칸에
        menu.clicked(37, 0, ClickType.PICKUP, player);   // 막대기
        menu.clicked(1, 0, ClickType.PICKUP, player);    // 격자 첫 칸에 막대기 → 플러그인이 다이아몬드
        assertEquals(it("diamond", 1).itemId(), menu.getSlot(0).getItem().itemId());
        assertEquals("null", prepared.get(prepared.size() - 1), "PrepareItemCraftEvent 는 레시피 없음(null)으로 알았다");

        Method m = Class.forName("org.domi.furnace.server.plugin.PluginGameEvents")
                .getDeclaredMethod("craftRecipe", AbstractContainerMenu.class, int.class, ClickType.class);
        m.setAccessible(true);
        String recipe = (String) m.invoke(null, menu, 0, ClickType.PICKUP);
        // Paper: getRecipe()==null → 보통 InventoryClickEvent. Furnace 는 ResultContainer 에 남은 앞선 레시피 id 를 쓴다.
        assertEquals("", recipe == null ? "" : recipe, "결과 칸 클릭의 레시피는 '모름'이어야 한다 — 실제: " + recipe);
    }
}
