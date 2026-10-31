// 블록 엔티티 컴포넌트 연결: 놓기(BlockItem.place → applyComponentsFromItemStack), 부수기(getDrops → lootDrops 의 block_entity),
// 종류별 주고받는 컴포넌트, 갱신 태그(getUpdateTag = saveCustomOnly — "components" 없음).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.entity.BannerBlockEntity;
import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.CampfireBlockEntity;
import org.domi.furnace.server.block.entity.DecoratedPotBlockEntity;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.block.entity.SignBlockEntity;
import org.domi.furnace.server.block.entity.SpawnerBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityComponentsTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static int item(String n) { return ItemContext.id("minecraft:item", n); }

    static final Pos P = new Pos(0, 0, 0);
    static final Map<String, Object> LEFTOVER = Map.of("minecraft:custom_model_data", 5);

    static ItemStack shulkerItem() {
        Map<String, Object> slot = new java.util.LinkedHashMap<>();
        slot.put("slot", 3);
        slot.put("item", Map.of("id", "minecraft:diamond", "count", 2));
        ItemStack s = ItemTools.with(new ItemStack(item("minecraft:shulker_box"), 1), "minecraft:container", List.of(slot));
        return ItemTools.with(s, "minecraft:custom_name", "\"Box\"");
    }

    @Test
    void placingAppliesItemComponents() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new BlockPlacementTest.Actor();
        a.x = 3.5; a.z = 3.5;
        a.hands[0] = shulkerItem();
        Pos ground = new Pos(1, -61, 1);
        assertEquals(InteractionResult.CONSUME, BlockPlacement.useItemOn(level, a, 0, new Hit(ground, Dir.UP, 1.5, -60, 1.5, false)));
        var be = (ShulkerBoxBlockEntity) level.getBlockEntity(ground.above());
        assertEquals(new ItemStack(item("minecraft:diamond"), 2), be.items[3]);
        assertEquals("\"Box\"", be.customName);
        assertTrue(be.components().isEmpty());
    }

    @Test
    void getDropsPassesCollectedComponents() {
        Map<String, Object>[] seen = new Map[1];
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            public List<ItemStack> lootDrops(String table, int state, Pos pos, BlockActor e, ItemStack tool, Float r, Map<String, Object> blockEntity) {
                seen[0] = blockEntity;
                return List.of();
            }
        };
        int state = Blocks.get().defaultState("minecraft:shulker_box");
        var be = new ShulkerBoxBlockEntity(P, state);
        be.applyComponentsFromItemStack(shulkerItem());
        BlockBehaviors.of(state).getDrops(state, level, P, be, null, ItemStack.EMPTY);
        assertEquals(be.collectComponents(), seen[0]);
        assertEquals("\"Box\"", seen[0].get("minecraft:custom_name"));
        BlockBehaviors.of(state).getDrops(state, level, P, null, null, ItemStack.EMPTY);
        assertNull(seen[0], "블록 엔티티가 없으면 매개변수도 없다");
    }

    @Test
    void campfireAndPotHaveNoNameOrLock() {
        var fire = new CampfireBlockEntity(P, -1);
        fire.customName = "\"x\"";
        fire.lock = "k";
        assertEquals(java.util.Set.of("minecraft:container"), fire.collectComponents().keySet());
        var pot = new DecoratedPotBlockEntity(P, -1);
        pot.lock = "k";
        assertEquals(java.util.Set.of("minecraft:pot_decorations", "minecraft:container"), pot.collectComponents().keySet());
        ItemStack lockedPot = ItemTools.with(new ItemStack(item("minecraft:decorated_pot"), 1), "minecraft:lock", "k");
        pot.applyComponentsFromItemStack(lockedPot);
        assertEquals(Map.of("minecraft:lock", "k"), pot.components(), "단지는 lock 을 읽지 않아 남는다");
    }

    @Test
    void beaconLockRoundTrip() {
        ItemStack item = ItemTools.with(ItemTools.with(new ItemStack(item("minecraft:beacon"), 1), "minecraft:lock", "key"), "minecraft:custom_name", "\"B\"");
        var be = new BeaconBlockEntity(P, -1);
        be.applyComponentsFromItemStack(item);
        assertEquals("key", be.lock);
        assertEquals("key", be.collectComponents().get("minecraft:lock"));
        var t = be.updateTag();
        assertEquals("key", t.getString("Lock"));
        assertEquals("\"B\"", t.getString("CustomName"));
    }

    @Test
    void updateTagsOmitComponents() {
        List<BlockEntity> list = List.of(new SignBlockEntity(P, -1), new SpawnerBlockEntity(P, -1), new PistonMovingBlockEntity(P, -1), new BeaconBlockEntity(P, -1));
        for (BlockEntity be : list) {
            be.setComponents(LEFTOVER);
            assertTrue(be.saveWithoutMetadata().contains("components"), be.type());
            assertFalse(be.updateTag().contains("components"), be.type() + " 갱신 태그는 saveCustomOnly");
        }
        var banner = new BannerBlockEntity(P, -1);
        banner.setComponents(LEFTOVER);
        assertTrue(banner.updateTag().contains("components"), "깃발만 saveWithoutMetadata(바닐라)");
    }

    private static org.domi.furnace.server.net.io.nbt.NbtCompound campfireData() {
        var t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        t.putString("id", "minecraft:campfire");
        t.putIntArray("CookingTimes", new int[]{7, 0, 0, 0});
        return t;
    }

    /** 가정이 아니라 바닐라 동작: 컨테이너의 CustomName/Items 는 뒤이은 applyComponents 가 컴포넌트 값으로 덮으므로 컴포넌트 없는 모닥불로 시험한다. */
    @Test
    void blockEntityDataMergesIntoPlacedEntity() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new BlockPlacementTest.Actor();
        a.x = 3.5; a.z = 3.5;
        ItemStack placedItem = ItemTools.with(new ItemStack(item("minecraft:campfire"), 1), "minecraft:block_entity_data", campfireData());
        a.hands[0] = placedItem;
        Pos ground = new Pos(1, -61, 1);
        assertEquals(InteractionResult.CONSUME, BlockPlacement.useItemOn(level, a, 0, new Hit(ground, Dir.UP, 1.5, -60, 1.5, false)));
        var be = (CampfireBlockEntity) level.getBlockEntity(ground.above());
        assertEquals(7, be.cookingProgress[0]);
        assertTrue(be.components().isEmpty(), "block_entity_data 는 남은 컴포넌트가 아니다");
        // 설치로 손의 아이템이 소모되므로 따로 든 아이템으로 다시 적용한다. 데이터에 id 가 있고 saveCustomOnly 에는 없어
        // 병합 결과가 늘 달라진다(바닐라 loadInto 와 같은 비교, 가정) — 그래서 true 이고 값은 그대로다.
        assertTrue(BlockPlacement.updateCustomBlockEntityTag(level, new BlockPlacementTest.Actor(), ground.above(), placedItem));
        assertEquals(7, be.cookingProgress[0]);
    }

    @Test
    void blockEntityDataOnlyOpForSpawner() {
        var level = new BlockPlacementTest.TestLevel();
        int state = Blocks.get().defaultState("minecraft:spawner");
        Pos p = new Pos(2, -60, 2);
        level.setBlock(p, state, 3);
        var stack = ItemTools.with(new ItemStack(item("minecraft:spawner"), 1), "minecraft:block_entity_data", spawnerData());
        var a = new BlockPlacementTest.Actor();
        assertFalse(BlockPlacement.updateCustomBlockEntityTag(level, a, p, stack), "권한 없으면 적용하지 않는다");
        var op = new BlockPlacementTest.Actor() {
            @Override public boolean canUseGameMasterBlocks() { return true; }
        };
        assertTrue(BlockPlacement.updateCustomBlockEntityTag(level, op, p, stack));
        assertEquals(77, ((SpawnerBlockEntity) level.getBlockEntity(p)).saveCustomOnly().getShort("Delay"));
    }

    private static org.domi.furnace.server.net.io.nbt.NbtCompound spawnerData() {
        var t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        t.putString("id", "minecraft:mob_spawner");
        t.putShort("Delay", (short) 77);
        t.putShort("RequiredPlayerRange", (short) 5);
        return t;
    }

    @Test
    void creativeBreakUnpacksLootTableBeforeEmptyCheck() {
        List<ItemStack> spawned = new java.util.ArrayList<>();
        var level = new BlockPlacementTest.TestLevel() {
            @Override public void spawnItemEntity(double x, double y, double z, ItemStack stack) { spawned.add(stack); }
            @Override public void unpackContainerLoot(Object c) {
                if (c instanceof ShulkerBoxBlockEntity sb && sb.lootTable != null) {
                    sb.lootTable = null;
                    sb.items[0] = new ItemStack(item("minecraft:emerald"), 3);
                }
            }
        };
        int state = Blocks.get().defaultState("minecraft:shulker_box");
        Pos p = new Pos(2, -60, 2);
        level.setBlock(p, state, 3);
        ((ShulkerBoxBlockEntity) level.getBlockEntity(p)).lootTable = "minecraft:chests/simple_dungeon";
        var creative = new BlockPlacementTest.Actor();
        creative.creative = true;
        BlockBehaviors.of(state).playerWillDestroy(level, p, state, creative);
        assertEquals(1, spawned.size(), "안 열린 전리품 셜커 상자도 풀린 뒤 드롭");
        assertNotNull(spawned.get(0).patch().entries().get("minecraft:container"));
    }

    @Test
    void creativeBreakDropsFilledShulkerWithContents() {
        List<ItemStack> spawned = new java.util.ArrayList<>();
        var level = new BlockPlacementTest.TestLevel() {
            @Override public void spawnItemEntity(double x, double y, double z, ItemStack stack) { spawned.add(stack); }
        };
        int state = Blocks.get().defaultState("minecraft:red_shulker_box");
        Pos p = new Pos(2, -60, 2);
        level.setBlock(p, state, 3);
        var be = (ShulkerBoxBlockEntity) level.getBlockEntity(p);
        be.items[4] = new ItemStack(item("minecraft:diamond"), 9);
        be.customName = "\"Red\"";
        var creative = new BlockPlacementTest.Actor();
        creative.creative = true;
        BlockBehaviors.of(state).playerWillDestroy(level, p, state, creative);
        assertEquals(1, spawned.size());
        assertEquals(item("minecraft:red_shulker_box"), spawned.get(0).itemId());
        assertEquals("\"Red\"", spawned.get(0).patch().entries().get("minecraft:custom_name"));
        assertNotNull(spawned.get(0).patch().entries().get("minecraft:container"));
        // 비었으면 떨구지 않는다, 생존이어도 떨구지 않는다(표가 한다)
        be.items[4] = ItemStack.EMPTY;
        BlockBehaviors.of(state).playerWillDestroy(level, p, state, creative);
        be.items[4] = new ItemStack(item("minecraft:diamond"), 1);
        BlockBehaviors.of(state).playerWillDestroy(level, p, state, new BlockPlacementTest.Actor());
        assertEquals(1, spawned.size());
    }
}
