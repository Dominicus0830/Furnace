// 블록 파괴 전리품의 block_entity 매개변수: 블록 엔티티의 collectComponents 를 바닐라 표(copy_components)가 아이템으로 옮기고,
// 그 아이템을 다시 놓으면(applyComponentsFromItemStack) 블록 엔티티가 복원되는지.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BannerBlockEntity;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.block.entity.SkullBlockEntity;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityLootTest {

    static LootData data;
    static NetworkComponents net;
    static final Pos POS = new Pos(0, 64, 0);

    @BeforeAll
    static void load() throws Exception {
        data = VanillaChestLootTest.vanillaLoot();
        net = new NetworkComponents(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static int id(String name) { return ItemContext.id("minecraft:item", name); }

    /** 표를 block_entity = be.collectComponents() 로 평가해 서버 아이템으로. */
    static List<ItemStack> drops(String table, String block, BlockEntity be, LootStack tool) {
        RandomSequences seqs = new RandomSequences(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError(); }
            public NoiseRandom randomSequence(ResourceId rid) { return seqs.get(rid); }
        };
        LootParams p = new LootParams().with(LootParams.ORIGIN, new double[]{0.5, 64.5, 0.5})
                .with(LootParams.BLOCK_STATE, new LootBlockState(block, Map.of()))
                .with(LootParams.TOOL, tool).with(LootParams.BLOCK_ENTITY, be.collectComponents());
        LootTable t = data.table(table);
        return t.getRandomItems(t.context(data, world, p, 0L)).stream().filter(s -> !s.isEmpty()).map(net::toItemStack).toList();
    }

    @Test
    void shulkerContentsAndNameSurviveBreakAndPlace() {
        var box = new ShulkerBoxBlockEntity(POS, -1);
        box.items[0] = new ItemStack(id("minecraft:diamond"), 5);
        box.items[13] = new ItemStack(id("minecraft:iron_sword"), 1, ComponentPatch.of(Map.of("minecraft:damage", 7)));
        box.customName = "\"Loot\"";
        box.lock = "key";
        List<ItemStack> out = drops("minecraft:blocks/shulker_box", "minecraft:shulker_box", box, LootStack.empty());
        assertEquals(1, out.size());
        ItemStack item = out.get(0);
        assertEquals(id("minecraft:shulker_box"), item.itemId());
        assertNotNull(item.patch().entries().get("minecraft:container"));
        assertEquals("\"Loot\"", item.patch().entries().get("minecraft:custom_name"));
        assertEquals("key", item.patch().entries().get("minecraft:lock"));

        var placed = new ShulkerBoxBlockEntity(POS, -1);
        placed.applyComponentsFromItemStack(item);
        assertEquals(box.items[0], placed.items[0]);
        assertEquals(box.items[13], placed.items[13]);
        assertTrue(placed.items[1].isEmpty());
        assertEquals("\"Loot\"", placed.customName);
        assertEquals("key", placed.lock);
        assertTrue(placed.components().isEmpty(), "읽은 컴포넌트는 남기지 않는다");
        assertEquals(box.collectComponents(), placed.collectComponents());
    }

    @Test
    void emptyShulkerDropsPlainItem() {
        List<ItemStack> out = drops("minecraft:blocks/shulker_box", "minecraft:shulker_box", new ShulkerBoxBlockEntity(POS, -1), LootStack.empty());
        assertEquals(1, out.size());
        assertTrue(out.get(0).patch().isEmpty(), "빈 용기는 기본값과 같아 패치에 없다: " + out.get(0).patch());
    }

    @Test
    void namedChestKeepsOnlyName() {
        var chest = ChestBlockEntity.empty(0, 64, 0);
        chest.items[0] = new ItemStack(id("minecraft:stone"), 3);
        chest.customName = "\"Box\"";
        List<ItemStack> out = drops("minecraft:blocks/chest", "minecraft:chest", chest, LootStack.empty());
        assertEquals(1, out.size());
        assertEquals(Map.of("minecraft:custom_name", "\"Box\""), out.get(0).patch().entries());
    }

    @Test
    void bannerPatternsRoundTrip() {
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("pattern", "minecraft:stripe_top");
        layer.put("color", "red");
        ItemStack banner = ItemTools.with(new ItemStack(id("minecraft:white_banner"), 1), "minecraft:banner_patterns", List.of(layer));
        var be = new BannerBlockEntity(POS, -1);
        be.applyComponentsFromItemStack(banner);
        List<ItemStack> out = drops("minecraft:blocks/white_banner", "minecraft:white_banner", be, LootStack.empty());
        assertEquals(1, out.size());
        assertEquals(banner.patch(), out.get(0).patch());
        var back = (BannerBlockEntity) BlockEntities.loadStatic(POS, -1, be.saveWithFullMetadata());
        assertEquals(be.patterns(), back.patterns());
    }

    @Test
    void playerHeadProfileRoundTrip() {
        ItemStack head = ItemTools.with(new ItemStack(id("minecraft:player_head"), 1), "minecraft:profile", "Notch");
        var be = new SkullBlockEntity(POS, -1);
        be.applyComponentsFromItemStack(head);
        assertNotNull(be.profile());
        List<ItemStack> out = drops("minecraft:blocks/player_head", "minecraft:player_head", be, LootStack.empty());
        assertEquals(head.patch(), out.get(0).patch());
        var back = (SkullBlockEntity) BlockEntities.loadStatic(POS, -1, be.saveWithFullMetadata());
        assertEquals(be.profile(), back.profile());
    }

    @Test
    void beehiveSilkTouchKeepsBees() {
        var hive = new BeehiveBlockEntity(POS, -1);
        NbtCompound bee = new NbtCompound();
        bee.putString("id", "minecraft:bee");
        hive.storeBee(new BeehiveBlockEntity.Occupant(bee, 10, 600));
        LootStack silk = new LootStack("minecraft:diamond_pickaxe", 1);
        Enchantments.enchant(silk, "minecraft:silk_touch", 1);
        ItemStack item = drops("minecraft:blocks/beehive", "minecraft:beehive", hive, silk).get(0);
        assertTrue(item.patch().entries().get("minecraft:bees") instanceof List<?> l && l.size() == 1, item.patch().toString());
        var placed = new BeehiveBlockEntity(POS, -1);
        placed.applyComponentsFromItemStack(item);
        assertEquals(1, placed.getOccupantCount());
        assertEquals(10, placed.getBees().get(0).ticksInHive());
        assertTrue(drops("minecraft:blocks/beehive", "minecraft:beehive", hive, LootStack.empty()).get(0).patch().isEmpty(), "실크 터치 없으면 벌 없음");
    }

    @Test
    void unreadComponentsAreKeptSavedAndCollected() {
        ItemStack item = ItemTools.with(new ItemStack(id("minecraft:barrel"), 1), "minecraft:custom_model_data", 3);
        var be = new org.domi.furnace.server.block.entity.BarrelBlockEntity(POS, -1);
        be.applyComponentsFromItemStack(item);
        assertEquals(Map.of("minecraft:custom_model_data", 3), be.components());
        var back = BlockEntities.loadStatic(POS, -1, be.saveWithFullMetadata());
        assertEquals(be.components(), back.components());
        assertEquals(3, back.collectComponents().get("minecraft:custom_model_data"));
    }
}
