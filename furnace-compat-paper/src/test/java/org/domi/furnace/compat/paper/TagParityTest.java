package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Fluid;
import org.bukkit.GameEvent;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.TagSet;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bukkit Tag(Bukkit.getTag/getTags·Tag 상수) — FAWE 가 "Spigot/Paper doesn't support Tags" 를 내던 자리.
 * 기준: Furnace 게임 레지스트리의 태그(바닐라 1.21.1 + 데이터 팩 리로드)와 CraftBukkit 의 CraftBlockTag·CraftItemTag·CraftFluidTag·CraftEntityTag·CraftGameEventTag 동작.
 */
class TagParityTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    private static final Map<String, String> VANILLA_REGISTRY = Map.of(
            Tag.REGISTRY_BLOCKS, "minecraft:block", Tag.REGISTRY_ITEMS, "minecraft:item", Tag.REGISTRY_FLUIDS, "minecraft:fluid",
            Tag.REGISTRY_ENTITY_TYPES, "minecraft:entity_type", Tag.REGISTRY_GAME_EVENTS, "minecraft:game_event");

    @Test
    void everyConstantResolvesAndCoversEveryVanillaTag() throws Exception {
        Map<String, Set<String>> seen = new LinkedHashMap<>();
        int constants = 0;
        for (Field f : Tag.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Tag.class) continue;
            Tag<?> t = (Tag<?>) f.get(null);
            assertNotNull(t, "상수 " + f.getName() + " 가 비었다 — 바닐라 1.21.1 에 있는 태그여야 한다");
            if (f.isAnnotationPresent(Deprecated.class)) continue;   // 옛 이름 별칭은 다른 상수와 같은 객체(legacyTagAliasesPointAtTheSameTag 가 본다)
            constants++;
            String reg = f.getName().startsWith("ITEMS_") ? Tag.REGISTRY_ITEMS : f.getName().startsWith("FLUIDS_") ? Tag.REGISTRY_FLUIDS
                    : f.getName().startsWith("ENTITY_TYPES_") ? Tag.REGISTRY_ENTITY_TYPES : f.getName().startsWith("GAME_EVENT_") ? Tag.REGISTRY_GAME_EVENTS : Tag.REGISTRY_BLOCKS;
            seen.computeIfAbsent(reg, k -> new java.util.HashSet<>()).add(t.getKey().toString());
        }
        int total = 0;
        for (var e : VANILLA_REGISTRY.entrySet()) {
            Set<String> vanilla = Furnace.server().gameRegistries().tags(e.getValue());
            assertEquals(vanilla, seen.get(e.getKey()), e.getKey() + ": 태그 상수가 바닐라 1.21.1 태그와 정확히 같아야 한다");
            total += vanilla.size();
        }
        assertEquals(total, constants);
        assertTrue(constants > 300);
    }

    @Test
    void blockTagAnswersByMaterial() {
        assertTrue(Tag.WOOL.isTagged(Material.WHITE_WOOL));
        assertFalse(Tag.WOOL.isTagged(Material.STONE));
        assertFalse(Tag.WOOL.isTagged(Material.DIAMOND), "블록이 아닌 재료는 블록 태그에 없다");
        assertEquals(new NamespacedKey("minecraft", "wool"), Tag.WOOL.getKey());
        Set<Material> wool = Tag.WOOL.getValues();
        assertEquals(16, wool.size());
        assertTrue(wool.contains(Material.RED_WOOL));
        assertThrows(UnsupportedOperationException.class, () -> wool.add(Material.STONE));
        assertSame(wool, Tag.WOOL.getValues(), "리로드가 없으면 같은 집합을 돌려준다");
        assertFalse(Tag.WOOL.isTagged(null), "Paper 블록 태그는 null 이면 false");
        // 태그 안 태그가 풀려 있다
        assertTrue(Tag.LOGS.isTagged(Material.OAK_LOG));
        assertTrue(Tag.LOGS.getValues().containsAll(Tag.OAK_LOGS.getValues()));
    }

    @Test
    void itemTagIsSeparateFromBlockTag() {
        assertTrue(Tag.ITEMS_WOOL.isTagged(Material.WHITE_WOOL));
        assertTrue(Tag.ITEMS_SWORDS.isTagged(Material.DIAMOND_SWORD));
        assertFalse(Tag.ITEMS_SWORDS.isTagged(Material.DIAMOND));
        assertFalse(Tag.ITEMS_SWORDS.isTagged(Material.WATER), "아이템이 아닌 재료는 아이템 태그에 없다");
        assertTrue(Tag.ITEMS_SWORDS.getValues().contains(Material.NETHERITE_SWORD));
    }

    @Test
    void entityTypeFluidAndGameEventTags() {
        assertTrue(Tag.ENTITY_TYPES_SKELETONS.isTagged(EntityType.SKELETON));
        assertFalse(Tag.ENTITY_TYPES_SKELETONS.isTagged(EntityType.ZOMBIE));
        assertFalse(Tag.ENTITY_TYPES_SKELETONS.isTagged(EntityType.UNKNOWN));
        assertTrue(Tag.ENTITY_TYPES_SKELETONS.getValues().contains(EntityType.WITHER_SKELETON));
        assertEquals(Set.of(Fluid.WATER, Fluid.FLOWING_WATER), Tag.FLUIDS_WATER.getValues());
        assertEquals(Set.of(Fluid.LAVA, Fluid.FLOWING_LAVA), Tag.FLUIDS_LAVA.getValues());
        assertTrue(Tag.FLUIDS_WATER.isTagged(Fluid.FLOWING_WATER));
        assertFalse(Tag.FLUIDS_WATER.isTagged(Fluid.LAVA));
        assertTrue(Tag.GAME_EVENT_VIBRATIONS.isTagged(GameEvent.BLOCK_PLACE));
        assertFalse(Tag.GAME_EVENT_VIBRATIONS.isTagged(GameEvent.SHRIEK));
        assertEquals(60, GameEvent.values().size(), "바닐라 1.21.1 게임 이벤트 60종");
        assertSame(GameEvent.BLOCK_PLACE, GameEvent.getByKey(NamespacedKey.minecraft("block_place")));
        assertNull(GameEvent.getByKey(NamespacedKey.minecraft("nope")));
    }

    @Test
    void bukkitGetTagFollowsCraftServerRules() {
        Tag<Material> logs = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("logs"), Material.class);
        assertEquals(Tag.LOGS, logs);
        assertEquals(Tag.LOGS.hashCode(), logs.hashCode());
        assertNull(Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("no_such_tag"), Material.class), "없는 태그는 null");
        assertNull(Bukkit.getTag(Tag.REGISTRY_ITEMS, new NamespacedKey("pack", "x"), Material.class));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("logs"), EntityType.class));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag(Tag.REGISTRY_FLUIDS, NamespacedKey.minecraft("water"), Material.class));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag("biomes", NamespacedKey.minecraft("logs"), Material.class));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, null, Material.class));
        assertNotEquals(Tag.LOGS, Tag.ITEMS_LOGS, "같은 이름이어도 레지스트리가 다르면 다른 태그");
        assertSame(Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("logs"), Material.class).getKey().getClass(), NamespacedKey.class);

        int blockTags = 0;
        for (Tag<Material> t : Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class)) {
            blockTags++;
            assertNotNull(t.getKey());
        }
        assertEquals(Furnace.server().gameRegistries().tags("minecraft:block").size(), blockTags);
        List<Tag<Fluid>> fluids = new ArrayList<>();
        Bukkit.getTags(Tag.REGISTRY_FLUIDS, Fluid.class).forEach(fluids::add);
        assertEquals(2, fluids.size());
        assertThrows(IllegalArgumentException.class, () -> Bukkit.getTags(Tag.REGISTRY_ITEMS, Fluid.class));
    }

    private static void assertNotEquals(Object a, Object b, String msg) { org.junit.jupiter.api.Assertions.assertNotEquals(a, b, msg); }

    /** 데이터 팩 리로드: 잡고 있던 태그 객체가 다음 물음부터 새 내용을 보이고, 새 태그가 getTag·getTags 에 나온다. */
    @Test
    void dataPackReloadIsVisibleThroughHeldTags() {
        TagSet installed = ItemContext.tagSet();   // 데이터 팩을 안 거는 시험 서버는 null(바닐라 태그를 읽는다)
        TagSet before = installed != null ? installed : TagSet.load("registries/tags-1.21.1.txt");
        try {
            List<String> blocks = Furnace.server().gameRegistries().builtIn("minecraft:block");
            int stone = blocks.indexOf("minecraft:stone"), dirt = blocks.indexOf("minecraft:dirt");
            Map<String, Map<String, int[]>> reg = new LinkedHashMap<>();
            for (String r : before.registries()) reg.put(r, new LinkedHashMap<>(before.tagsOf(r)));
            reg.get("minecraft:block").put("minecraft:wool", new int[]{stone});                // 바닐라 태그를 데이터 팩이 바꿈
            reg.get("minecraft:block").put("pack:mine", new int[]{stone, dirt, 99999});            // 새 태그(범위 밖 번호는 걸러진다)
            ItemContext.installTags(TagSet.of(reg));

            assertEquals(Set.of(Material.STONE), Tag.WOOL.getValues());
            assertFalse(Tag.WOOL.isTagged(Material.WHITE_WOOL));
            assertTrue(Tag.WOOL.isTagged(Material.STONE));
            Tag<Material> mine = Bukkit.getTag(Tag.REGISTRY_BLOCKS, new NamespacedKey("pack", "mine"), Material.class);
            assertNotNull(mine);
            assertEquals(Set.of(Material.STONE, Material.DIRT), mine.getValues());
            int n = 0;
            for (Tag<Material> t : Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class)) if (t.getKey().getNamespace().equals("pack")) n++;
            assertEquals(1, n);

            // 리로드로 태그가 사라지면 잡고 있던 객체는 빈 태그처럼 답한다
            reg.get("minecraft:block").remove("pack:mine");
            ItemContext.installTags(TagSet.of(reg));
            assertEquals(Set.of(), mine.getValues());
            assertFalse(mine.isTagged(Material.STONE));
            assertNull(Bukkit.getTag(Tag.REGISTRY_BLOCKS, new NamespacedKey("pack", "mine"), Material.class));
        } finally {
            ItemContext.installTags(installed);
        }
        assertEquals(16, Tag.WOOL.getValues().size());
    }

    /** R3: Paper 1.21.1 의 Fluid 는 EMPTY 가 맨 앞이다. */
    @Test
    void fluidHasEmptyFirstLikePaper() {
        assertEquals(List.of("EMPTY", "WATER", "FLOWING_WATER", "LAVA", "FLOWING_LAVA"), java.util.Arrays.stream(Fluid.values()).map(Enum::name).toList());
        assertEquals(NamespacedKey.minecraft("empty"), Fluid.EMPTY.getKey());
    }

    /** R4: 1.21.1 Tag 에 있던 별칭 상수는 대상과 같은 객체다. */
    @Test
    void legacyTagAliasesPointAtTheSameTag() {
        assertSame(Tag.WOOL_CARPETS, Tag.CARPETS);
        assertSame(Tag.ITEMS_PIGLIN_FOOD, Tag.PIGLIN_FOOD);
        assertSame(Tag.ITEMS_FOX_FOOD, Tag.FOX_FOOD);
        assertSame(Tag.ITEMS_AXOLOTL_FOOD, Tag.AXOLOTL_TEMPT_ITEMS);
        assertSame(Tag.ENTITY_TYPES_DEFLECTS_PROJECTILES, Tag.ENTITY_TYPES_DEFLECTS_ARROWS);
        assertSame(Tag.ITEMS_BREAKS_DECORATED_POTS, Tag.ITEMS_TOOLS);
        assertSame(Tag.ITEMS_STONE_CRAFTING_MATERIALS, Tag.ITEMS_FURNACE_MATERIALS);
        assertSame(Tag.DRIPSTONE_REPLACEABLE_BLOCKS, Tag.DRIPSTONE_REPLACEABLE);
        assertSame(Tag.ITEMS_NOTEBLOCK_TOP_INSTRUMENTS, Tag.ITEMS_NOTE_BLOCK_TOP_INSTRUMENTS);
        assertTrue(Tag.CARPETS.isTagged(Material.WHITE_CARPET));
    }

    /** R5: GameEvent 옛 이름 별칭은 같은 객체, getRange·getVibrationLevel 은 서버의 바닐라 표와 같다. */
    @Test
    void gameEventAliasesRangeAndVibrationLevel() {
        assertSame(GameEvent.BLOCK_ACTIVATE, GameEvent.BLOCK_PRESS);
        assertSame(GameEvent.BLOCK_ACTIVATE, GameEvent.PISTON_EXTEND);
        assertSame(GameEvent.BLOCK_DEACTIVATE, GameEvent.PISTON_CONTRACT);
        assertSame(GameEvent.ENTITY_ACTION, GameEvent.WOLF_SHAKING);
        assertSame(GameEvent.CONTAINER_OPEN, GameEvent.SHULKER_OPEN);
        assertSame(GameEvent.BLOCK_CHANGE, GameEvent.RING_BELL);
        for (GameEvent e : GameEvent.values()) {
            var vanilla = org.domi.furnace.server.world.gameevent.GameEvent.byName(e.getKey().toString());
            assertNotNull(vanilla, e.toString());
            assertEquals(vanilla.notificationRadius, e.getRange(), e.toString());
            assertEquals(vanilla.frequency(), e.getVibrationLevel(), e.toString());
        }
        assertEquals(32, GameEvent.SHRIEK.getRange());
        assertEquals(15, GameEvent.EXPLODE.getVibrationLevel());
    }

    /** R6: Material 태그에 null 을 물으면 false(Paper EnumMap 조회), 클래스가 null 이면 Paper 문구의 예외. */
    @Test
    void nullHandlingLikePaper() {
        assertFalse(Tag.WOOL.isTagged(null));
        assertFalse(Tag.ITEMS_SWORDS.isTagged(null));
        var e = assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("wool"), null));
        assertEquals("Class clazz cannot be null", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("wool"), EntityType.class));
        assertEquals("Block namespace (blocks) must have material type", e.getMessage());
    }
}
