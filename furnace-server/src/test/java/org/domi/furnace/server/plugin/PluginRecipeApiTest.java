// 플러그인 API 레시피 레지스트리: 등록·검증·제거·복원, 제작/굽기/석재 절단/대장장이 공통 표, /reload 뒤 유지, 플러그인 꺼짐 정리, 접속자 재전송.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.recipe.Recipe;
import org.domi.furnace.api.recipe.RecipeCategory;
import org.domi.furnace.api.recipe.RecipeChoice;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.RecipeBookUpdate;
import org.domi.furnace.server.net.protocol.play.UpdateRecipes;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.recipe.CraftingInput;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginRecipeApiTest {

    private static final Path VANILLA = Path.of("../tools/vanilla-data/server-1.21.1");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private DataPackManager packs;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();
    private final TestPlugins.Beta other = new TestPlugins.Beta();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Files.isDirectory(VANILLA.resolve("data/minecraft/recipe")), "로컬 바닐라 데이터 없음");
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        packs = DataPackManager.start(VANILLA, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (host != null) host.close();
        if (sched != null) sched.close();
        if (packs != null) packs.close();
        TestServers.drainIoPools();
    }

    private RecipeManager rm() { return core.recipes(); }

    private org.domi.furnace.server.entity.ItemStack stack(String name) {
        return new org.domi.furnace.server.entity.ItemStack(core.world().itemRegistry().idOf("minecraft:" + name), 1);
    }

    private CraftingInput grid(int w, int h, String... names) {
        List<org.domi.furnace.server.entity.ItemStack> l = new ArrayList<>();
        for (String n : names) l.add(n.isEmpty() ? org.domi.furnace.server.entity.ItemStack.EMPTY : stack(n));
        return CraftingInput.of(w, h, l);
    }

    private String craft(CraftingInput in) {
        var e = rm().findCrafting(in, null);
        if (e == null) return null;
        var s = e.recipe().assemble(in, rm().context());
        return core.world().itemRegistry().nameOf(s.itemId()) + "x" + s.count();
    }

    private static Recipe.Shaped sample() {
        return new Recipe.Shaped(List.of("G", "G"), Map.of('G', RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 3));
    }

    // ------------------------------------------------------------------ 제작대

    @Test
    void shapedRecipeCraftsAndMirrorsLikeVanilla() {
        var reg = host.recipes();
        assertNull(craft(grid(2, 2, "gravel", "", "gravel", "")));
        reg.register(plugin, "myplugin:bones", sample());
        assertEquals("minecraft:bonex3", craft(grid(2, 2, "gravel", "", "gravel", "")));
        assertEquals("minecraft:bonex3", craft(grid(3, 3, "", "", "", "", "gravel", "", "", "gravel", "")), "격자 어디에 놓아도");
        assertNull(craft(grid(2, 2, "gravel", "", "", "")), "한 개만으로는 안 된다");
        // 모양이 비대칭이면 좌우 뒤집은 것도 맞는다
        reg.register(plugin, "myplugin:axe", new Recipe.Shaped(List.of("DD", "DS", " S"),
                Map.of('D', RecipeChoice.item("dirt"), 'S', RecipeChoice.item("stick")), new ItemStack("minecraft:iron_axe", 1)));
        assertEquals("minecraft:iron_axex1", craft(grid(3, 3, "dirt", "dirt", "", "dirt", "stick", "", "", "stick", "")));
        assertEquals("minecraft:iron_axex1", craft(grid(3, 3, "", "dirt", "dirt", "", "stick", "dirt", "", "stick", "")));
    }

    @Test
    void shapelessAndTagIngredients() {
        host.recipes().register(plugin, "myplugin:mix", new Recipe.Shapeless("mixes", RecipeCategory.MISC,
                List.of(RecipeChoice.tag("minecraft:logs"), RecipeChoice.item("gravel", "sand")), new ItemStack("minecraft:diamond", 2)));
        assertEquals("minecraft:diamondx2", craft(grid(2, 2, "oak_log", "gravel", "", "")));
        assertEquals("minecraft:diamondx2", craft(grid(2, 2, "sand", "birch_log", "", "")));
        assertNull(craft(grid(2, 2, "oak_log", "dirt", "", "")));
        assertEquals("minecraft:diamondx2", craft(grid(2, 2, "", "spruce_log", "sand", "")), "순서와 위치는 상관없다");
    }

    @Test
    void resultKeepsComponents() {
        var named = new ItemStack("minecraft:stick", 1).withComponent("minecraft:damage", "0").withComponent("minecraft:max_stack_size", "16");
        host.recipes().register(plugin, "myplugin:special_stick", new Recipe.Shaped(List.of("G"), Map.of('G', RecipeChoice.item("gravel")), named));
        var in = grid(1, 1, "gravel");
        var assembled = rm().findCrafting(in, null).recipe().assemble(in, rm().context());
        var api = ApiConvert.item(assembled, core.world().itemRegistry());
        assertEquals("16", api.components().get("minecraft:max_stack_size"));
    }

    // ------------------------------------------------------------------ 굽기·석재·대장장이

    @Test
    void cookingStonecuttingSmithingShareTheSameTable() {
        var reg = host.recipes();
        reg.register(plugin, "myplugin:bake", new Recipe.Cooking(Recipe.CookingKind.SMELTING, "", RecipeCategory.FOOD,
                RecipeChoice.item("gravel"), new ItemStack("minecraft:bread", 1), 1.5f, 40));
        reg.register(plugin, "myplugin:grill", new Recipe.Cooking(Recipe.CookingKind.CAMPFIRE, RecipeChoice.item("gravel"), new ItemStack("minecraft:cooked_beef", 1), 0f));
        reg.register(plugin, "myplugin:cut", new Recipe.Stonecutting(RecipeChoice.item("gravel"), new ItemStack("minecraft:cobblestone", 2)));
        reg.register(plugin, "myplugin:smith", new Recipe.SmithingTransform(RecipeChoice.item("stick"), RecipeChoice.item("dirt"),
                RecipeChoice.item("gravel"), new ItemStack("minecraft:diamond_block", 1)));

        var smelt = rm().findCooking("minecraft:smelting", stack("gravel"));
        assertNotNull(smelt);
        assertEquals(40, smelt.recipe().cookingTime());
        assertEquals(1.5f, smelt.recipe().experience());
        assertEquals("myplugin:bake", smelt.id().toString());
        assertNull(rm().findCooking("minecraft:blasting", stack("gravel")), "종류가 다르면 맞지 않는다");
        assertEquals(600, rm().findCooking("minecraft:campfire_cooking", stack("gravel")).recipe().cookingTime(), "모닥불 기본 시간");

        var cuts = rm().findStonecutting(stack("gravel"));
        assertEquals(1, cuts.size());
        assertEquals(2, cuts.get(0).recipe().result().count());

        assertEquals(1, rm().findSmithing(stack("stick"), stack("dirt"), stack("gravel")).size());
        assertTrue(rm().smithingAccepts(1, stack("dirt")));
        assertFalse(rm().smithingAccepts(1, stack("sand")));
    }

    // ------------------------------------------------------------------ 검증

    @Test
    void invalidRecipesAreRejectedAndTableUnchanged() {
        var reg = host.recipes();
        int before = rm().size();
        byte[] body = rm().networkBody();
        reg.register(plugin, "myplugin:ok", sample());
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:ok", sample()), "같은 키");
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "minecraft:stick", sample()), "데이터 팩 키와 겹침");
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "nocolon", sample()), "네임스페이스 없는 키");
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "Upper:Case", sample()), "대문자 키");
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:badres",
                new Recipe.Shaped(List.of("G"), Map.of('G', RecipeChoice.item("gravel")), new ItemStack("minecraft:no_such_item", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:badingredient",
                new Recipe.Shaped(List.of("G"), Map.of('G', RecipeChoice.item("minecraft:no_such_item")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:badtag",
                new Recipe.Shapeless(List.of(RecipeChoice.tag("minecraft:no_such_tag")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:undefinedkey",
                new Recipe.Shaped(List.of("GX"), Map.of('G', RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:unusedkey",
                new Recipe.Shaped(List.of("G"), Map.of('G', RecipeChoice.item("gravel"), 'H', RecipeChoice.item("sand")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:wide",
                new Recipe.Shaped(List.of("GGGG"), Map.of('G', RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:toomany",
                new Recipe.Shapeless(java.util.Collections.nCopies(10, RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:bigstack",
                new Recipe.Stonecutting(RecipeChoice.item("gravel"), new ItemStack("minecraft:bone", 100))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:wrongcat",
                new Recipe.Shapeless("", RecipeCategory.FOOD, List.of(RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 1))));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:special", new Recipe.Special("minecraft:crafting_special_armordye")));
        assertThrows(IllegalArgumentException.class, () -> reg.register(plugin, "myplugin:emptyall",
                new Recipe.Stonecutting(RecipeChoice.NONE, new ItemStack("minecraft:bone", 1))), "빈 재료는 대장장이 칸에서만");
        assertEquals(before + 1, rm().size(), "성공한 하나만 늘었다");
        assertNotSame(body, rm().networkBody());
    }

    // ------------------------------------------------------------------ 제거·복원

    @Test
    void removeVanillaRecipeAndRestore() {
        var reg = host.recipes();
        String sticks = "minecraft:stick";
        assertEquals("minecraft:stickx4", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")));
        assertTrue(reg.keys().contains(sticks));
        assertTrue(reg.remove(sticks));
        assertFalse(reg.keys().contains(sticks));
        assertNotEquals("minecraft:stickx4", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")));
        assertFalse(reg.remove(sticks), "두 번째는 없음");
        assertFalse(reg.isPluginRecipe(sticks));
        assertTrue(reg.restore(sticks));
        assertEquals("minecraft:stickx4", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")));
        assertFalse(reg.restore(sticks), "내린 적 없으면 false");
    }

    @Test
    void replaceVanillaRecipeByRemoveThenRegister() {
        var reg = host.recipes();
        reg.remove("minecraft:stick");
        reg.register(plugin, "minecraft:stick", new Recipe.Shaped(List.of("P", "P"), Map.of('P', RecipeChoice.item("oak_planks")), new ItemStack("minecraft:stick", 8)));
        assertEquals("minecraft:stickx8", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")));
        assertFalse(reg.restore("minecraft:stick"), "자리가 차 있으면 되돌리지 않는다");
    }

    @Test
    void removeAndGetRoundTripPluginRecipe() {
        var reg = host.recipes();
        var recipe = new Recipe.Shaped("bones", RecipeCategory.EQUIPMENT, List.of("GS", "G "),
                Map.of('G', RecipeChoice.item("gravel"), 'S', RecipeChoice.item("stick")), new ItemStack("minecraft:bone", 3), false);
        reg.register(plugin, "myplugin:rt", recipe);
        var got = (Recipe.Shaped) reg.get("myplugin:rt").orElseThrow();
        assertEquals("bones", got.group());
        assertEquals(RecipeCategory.EQUIPMENT, got.category());
        assertFalse(got.showNotification());
        assertEquals(List.of("AB", "A "), got.pattern(), "글자는 처음 나온 순서대로 A, B …");
        assertEquals(RecipeChoice.item("gravel"), got.ingredients().get('A'));
        assertEquals(RecipeChoice.item("stick"), got.ingredients().get('B'));
        assertEquals("minecraft:bone", got.result().type());
        assertEquals(3, got.result().amount());
        assertTrue(reg.isPluginRecipe("myplugin:rt"));
        assertTrue(reg.remove("myplugin:rt"));
        assertTrue(reg.get("myplugin:rt").isEmpty());
        assertFalse(reg.remove("myplugin:rt"));
        // 다시 같은 키로 올릴 수 있다
        reg.register(plugin, "myplugin:rt", recipe);
    }

    @Test
    void vanillaRecipesReadBackAsApiValues() {
        var reg = host.recipes();
        assertInstanceOf(Recipe.Shaped.class, reg.get("minecraft:stick").orElseThrow());
        assertInstanceOf(Recipe.Shapeless.class, reg.get("minecraft:oak_planks").orElseThrow());
        var smelt = (Recipe.Cooking) reg.get("minecraft:iron_ingot_from_smelting_iron_ore").orElseThrow();
        assertEquals(Recipe.CookingKind.SMELTING, smelt.kind());
        assertEquals(200, smelt.cookingTime());
        assertEquals("minecraft:iron_ingot", smelt.result().type());
        assertInstanceOf(Recipe.Stonecutting.class, reg.get("minecraft:stone_bricks_from_stone_stonecutting").orElseThrow());
        assertInstanceOf(Recipe.SmithingTransform.class, reg.get("minecraft:netherite_sword_smithing").orElseThrow());
        assertEquals("minecraft:crafting_special_armordye", ((Recipe.Special) reg.get("minecraft:armor_dye").orElseThrow()).type());
        assertTrue(reg.get("minecraft:no_such_recipe").isEmpty());
        assertTrue(reg.get("garbage key").isEmpty());
    }

    // ------------------------------------------------------------------ 소유·리로드

    @Test
    void recipesGoWhenOwnerPluginDisables() {
        var reg = host.recipes();
        reg.register(plugin, "myplugin:a", sample());
        reg.register(plugin, "myplugin:b", new Recipe.Stonecutting(RecipeChoice.item("gravel"), new ItemStack("minecraft:cobblestone", 1)));
        reg.register(other, "other:c", new Recipe.Shapeless(List.of(RecipeChoice.item("sand")), new ItemStack("minecraft:bone", 1)));
        assertEquals(List.of("myplugin:a", "myplugin:b"), List.copyOf(reg.keys(plugin)));
        assertEquals(List.of("other:c"), List.copyOf(reg.keys(other)));
        host.recipes().removeAll(plugin);
        assertTrue(reg.get("myplugin:a").isEmpty());
        assertTrue(reg.get("myplugin:b").isEmpty());
        assertTrue(reg.get("other:c").isPresent(), "다른 플러그인 것은 남는다");
        assertTrue(reg.keys(plugin).isEmpty());
        assertNull(craft(grid(2, 2, "gravel", "", "gravel", "")));
        assertTrue(rm().findStonecutting(stack("gravel")).isEmpty());
    }

    @Test
    void pluginRecipesAndRemovalsSurviveReload() throws Exception {
        var reg = host.recipes();
        reg.register(plugin, "myplugin:bones", sample());
        reg.remove("minecraft:stick");
        RecipeManager before = rm();
        packs.reload(List.of("vanilla"), Runnable::run).get(60, TimeUnit.SECONDS);
        assertNotSame(before, rm(), "/reload 는 매니저를 새로 만든다");
        assertEquals("minecraft:bonex3", craft(grid(2, 2, "gravel", "", "gravel", "")));
        assertNotEquals("minecraft:stickx4", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")), "제거도 유지");
        assertTrue(reg.isPluginRecipe("myplugin:bones"));
        assertTrue(reg.restore("minecraft:stick"), "리로드 뒤에도 되돌릴 수 있다");
        assertEquals("minecraft:stickx4", craft(grid(2, 2, "oak_planks", "", "oak_planks", "")));
    }

    // ------------------------------------------------------------------ 접속자

    private record Sent(int id, ByteBuf body) {}

    private List<Sent> sent(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Sent> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(new Sent(Types.readVarInt(bb), bb));
        }
        return out;
    }

    @Test
    void connectedPlayersGetRecipeListAndForgetRemovedRecipes() {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Crafty", ch);
        core.world().playerOnline(p);
        var reg = host.recipes();
        p.awardRecipes(List.of(ResourceId.parse("minecraft:stick")), rm());
        sent(ch);   // 비운다

        reg.register(plugin, "myplugin:bones", sample());
        var sent = sent(ch);
        assertEquals(1, sent.size());
        assertEquals(UpdateRecipes.ID, sent.get(0).id());
        byte[] body = new byte[sent.get(0).body().readableBytes()];
        sent.get(0).body().readBytes(body);
        assertArrayEquals(rm().networkBody(), body, "보낸 목록은 지금 표와 같다");
        assertTrue(new String(body, java.nio.charset.StandardCharsets.UTF_8).contains("myplugin:bones"));

        reg.remove("minecraft:stick");
        var after = sent(ch);
        assertEquals(List.of(RecipeBookUpdate.ID, UpdateRecipes.ID), after.stream().map(Sent::id).toList(), "잊게 한 뒤 목록을 보낸다");
        assertFalse(p.recipeBook().contains(ResourceId.parse("minecraft:stick")));
        reg.remove("minecraft:stone_pickaxe");   // 모르던 레시피는 레시피 북 패킷 없이 목록만
        assertEquals(List.of(UpdateRecipes.ID), sent(ch).stream().map(Sent::id).toList());
    }

    @Test
    void discoverRecipesAddsToRecipeBook() throws Exception {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Learner", new EmbeddedChannel());
        core.world().playerOnline(p);
        var handle = host.player(p);
        host.recipes().register(plugin, "myplugin:bones", sample());
        assertFalse(handle.hasDiscoveredRecipe("myplugin:bones"));
        // 지금 스레드에서 직접 돌려 주인 스레드 홉을 거치지 않는다
        assertEquals(1, p.awardRecipes(List.of(ResourceId.parse("myplugin:bones"), ResourceId.parse("myplugin:missing")), rm()));
        assertTrue(handle.hasDiscoveredRecipe("myplugin:bones"));
        assertEquals(1, p.resetRecipes(List.of(ResourceId.parse("myplugin:bones"))));
        assertFalse(handle.hasDiscoveredRecipe("myplugin:bones"));
    }
}
