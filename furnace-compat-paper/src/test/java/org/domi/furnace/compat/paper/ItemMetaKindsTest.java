package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** T-141 E11: 아이템 종류별 메타 하위 형(MetaKind)·ItemFactory·가죽 방어구 메타. 원문 기준은 Paper 1.21.1 CraftItemFactory·CraftMetaLeatherArmor. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemMetaKindsTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    static final ItemFactory FACTORY = CraftItemFactory.instance();

    // ------------------------------------------------------------------ 종류 선택

    @Test
    void factoryIsReachableFromBukkitAndServer() {
        assertSame(FACTORY, Bukkit.getItemFactory());
        assertSame(FACTORY, Bukkit.getServer().getItemFactory());
    }

    @Test
    void leatherArmorItemsGetLeatherMeta() {
        for (Material m : new Material[] {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                Material.LEATHER_HORSE_ARMOR, Material.WOLF_ARMOR}) {
            assertInstanceOf(LeatherArmorMeta.class, new ItemStack(m).getItemMeta(), m + " 의 메타는 LeatherArmorMeta");
            assertInstanceOf(LeatherArmorMeta.class, FACTORY.getItemMeta(m), m + " 공장 메타");
        }
        for (Material m : new Material[] {Material.STONE, Material.DIAMOND_HELMET, Material.LEATHER, Material.DIAMOND_SWORD, Material.WATER}) {
            ItemMeta meta = FACTORY.getItemMeta(m);
            assertNotNull(meta, m.name());
            assertFalse(meta instanceof LeatherArmorMeta, m + " 은 일반 메타");
        }
    }

    @Test
    void airHasNoMeta() {
        assertNull(new ItemStack(Material.AIR).getItemMeta());
        assertNull(FACTORY.getItemMeta(Material.AIR));
        assertNull(FACTORY.asMetaFor(FACTORY.getItemMeta(Material.STONE), Material.AIR));
        assertFalse(FACTORY.isApplicable(FACTORY.getItemMeta(Material.STONE), Material.AIR));
        assertThrows(IllegalArgumentException.class, () -> FACTORY.getItemMeta(null));
    }

    @Test
    void nonItemMaterialGetsAPlainMeta() {
        ItemMeta water = FACTORY.getItemMeta(Material.WATER);
        assertNotNull(water);
        assertFalse(FACTORY.isApplicable(water, Material.WATER), "아이템이 아닌 재료에는 붙일 수 없다");
    }

    // ------------------------------------------------------------------ 가죽 방어구 메타

    @Test
    void leatherColorDefaultsAndRoundTripsThroughTheStack() {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta meta = (LeatherArmorMeta) boots.getItemMeta();
        assertEquals(Color.fromRGB(0xA06540), meta.getColor());
        assertEquals(FACTORY.getDefaultLeatherColor(), meta.getColor());
        assertFalse(meta.isDyed());

        meta.setColor(Color.fromRGB(0x112233));
        assertTrue(meta.isDyed());
        assertTrue(boots.setItemMeta(meta));
        assertEquals(new DyedColor(0x112233, true), boots.asFurnace().get(ItemComponents.DYED_COLOR));

        LeatherArmorMeta back = (LeatherArmorMeta) boots.getItemMeta();
        assertEquals(Color.fromRGB(0x112233), back.getColor());
        assertTrue(back.isDyed());
        assertEquals(meta, back);

        back.setColor(null);
        assertFalse(back.isDyed());
        assertEquals(FACTORY.getDefaultLeatherColor(), back.getColor());
        assertTrue(boots.setItemMeta(back));
        assertNull(boots.asFurnace().get(ItemComponents.DYED_COLOR));
    }

    @Test
    void leatherColorDropsAlphaAndMasksHighBits() {
        LeatherArmorMeta meta = (LeatherArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        meta.setColor(Color.fromARGB(0x40, 0x11, 0x22, 0x33));
        assertEquals(Color.fromRGB(0x112233), meta.getColor());
    }

    @Test
    void hideDyeFlagIsWrittenIntoTheComponent() {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
        meta.setColor(Color.RED);
        meta.addItemFlags(ItemFlag.HIDE_DYE);
        chest.setItemMeta(meta);
        assertEquals(new DyedColor(0xFF0000, false), chest.asFurnace().get(ItemComponents.DYED_COLOR));
        assertTrue(chest.getItemMeta().hasItemFlag(ItemFlag.HIDE_DYE));
    }

    @Test
    void cloneKeepsTheSubtypeAndIsIndependent() {
        LeatherArmorMeta meta = (LeatherArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        meta.setColor(Color.BLUE);
        LeatherArmorMeta copy = meta.clone();
        assertNotSame(meta, copy);
        assertEquals(meta, copy);
        assertEquals(meta.hashCode(), copy.hashCode());
        copy.setColor(Color.GREEN);
        assertEquals(Color.BLUE, meta.getColor());
        assertFalse(meta.equals(copy));
    }

    // ------------------------------------------------------------------ setItemMeta 규칙

    @Test
    void metaOfAnotherKindIsRejectedWithoutChange() {
        LeatherArmorMeta meta = (LeatherArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        meta.setColor(Color.RED);
        ItemStack stone = new ItemStack(Material.STONE);
        assertFalse(stone.setItemMeta(meta), "가죽 메타는 돌에 붙지 않는다");
        assertFalse(stone.hasItemMeta());
        assertTrue(new ItemStack(Material.LEATHER_HORSE_ARMOR).setItemMeta(meta));
    }

    @Test
    void plainMetaIsAdaptedToTheStackKind() {
        ItemMeta plain = FACTORY.getItemMeta(Material.STONE);
        plain.setDisplayName("hello");
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        assertTrue(boots.setItemMeta(plain));
        assertInstanceOf(LeatherArmorMeta.class, boots.getItemMeta());
        assertEquals("hello", boots.getItemMeta().getDisplayName());
    }

    @Test
    void emptyMetaClearsTheStack() {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta meta = (LeatherArmorMeta) boots.getItemMeta();
        meta.setColor(Color.RED);
        boots.setItemMeta(meta);
        assertTrue(boots.hasItemMeta());
        assertTrue(boots.setItemMeta(boots.getItemMeta().getClass().cast(FACTORY.getItemMeta(Material.LEATHER_BOOTS))));
        assertFalse(boots.hasItemMeta(), "빈 메타를 넣으면 컴포넌트가 모두 지워진다");
    }

    @Test
    void foreignMetaIsRejected() {
        ItemMeta foreign = (ItemMeta) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ItemMeta.class}, (p, m, a) -> null);
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(Material.STONE).setItemMeta(foreign));
        assertThrows(IllegalArgumentException.class, () -> FACTORY.isApplicable(foreign, Material.STONE));
        assertThrows(IllegalArgumentException.class, () -> FACTORY.asMetaFor(foreign, Material.STONE));
        assertThrows(IllegalArgumentException.class, () -> FACTORY.equals(foreign, FACTORY.getItemMeta(Material.STONE)));
    }

    // ------------------------------------------------------------------ ItemFactory

    @Test
    void factoryApplicabilityAndEquals() {
        ItemMeta leather = FACTORY.getItemMeta(Material.LEATHER_HELMET);
        ItemMeta plain = FACTORY.getItemMeta(Material.STONE);
        assertTrue(FACTORY.isApplicable(leather, Material.LEATHER_BOOTS));
        assertFalse(FACTORY.isApplicable(leather, Material.STONE));
        assertTrue(FACTORY.isApplicable(plain, Material.LEATHER_BOOTS), "일반 메타는 어떤 아이템에도 붙는다");
        assertFalse(FACTORY.isApplicable(plain, (ItemStack) null));
        assertFalse(FACTORY.isApplicable(null, Material.STONE));
        assertTrue(FACTORY.isApplicable(leather, new ItemStack(Material.LEATHER_HORSE_ARMOR)));

        assertTrue(FACTORY.equals(null, null));
        assertTrue(FACTORY.equals(plain, null), "빈 메타는 null 과 같다");
        assertTrue(FACTORY.equals(null, leather));
        plain.setDisplayName("x");
        assertFalse(FACTORY.equals(plain, null));
        assertFalse(FACTORY.equals(null, plain));
        assertTrue(FACTORY.equals(plain, plain.clone()));
    }

    @Test
    void asMetaForKeepsOrDropsKindSpecificData() {
        LeatherArmorMeta leather = (LeatherArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        leather.setColor(Color.RED);
        leather.setDisplayName("n");
        assertSame(leather, FACTORY.asMetaFor(leather, Material.LEATHER_BOOTS), "같은 종류면 같은 객체");
        ItemMeta plain = FACTORY.asMetaFor(leather, Material.STONE);
        assertFalse(plain instanceof LeatherArmorMeta);
        assertEquals("n", plain.getDisplayName(), "공통 값은 따라온다");
        assertNull(((org.domi.furnace.compat.paper.CompatItemMeta) plain).work().get(ItemComponents.DYED_COLOR), "종류별 값은 버려진다");
        ItemMeta again = FACTORY.asMetaFor(FACTORY.getItemMeta(Material.STONE), Material.STONE);
        assertNotNull(again);
    }

    @Test
    void spawnEggLookup() {
        assertEquals(Material.ZOMBIE_SPAWN_EGG, FACTORY.getSpawnEgg(EntityType.ZOMBIE));
        assertNull(FACTORY.getSpawnEgg(EntityType.UNKNOWN));
        assertNull(FACTORY.getSpawnEgg(EntityType.PLAYER));
        assertNull(FACTORY.getSpawnEgg(null));
    }

    @Test
    void ensureServerConversionsCopies() {
        ItemStack s = new ItemStack(Material.STONE, 3);
        ItemStack c = FACTORY.ensureServerConversions(s);
        assertNotSame(s, c);
        assertEquals(s, c);
    }

    @Test
    void notYetSupportedMethodsSayWhy() {
        assertThrows(UnsupportedFeature.class, () -> FACTORY.createItemStack("minecraft:stone"));
        assertThrows(UnsupportedFeature.class, () -> FACTORY.enchantItem(new ItemStack(Material.DIAMOND_SWORD), 10, false));
    }

    // ------------------------------------------------------------------ 직렬화

    @Test
    void leatherMetaSerializesWithPaperKeys() {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta meta = (LeatherArmorMeta) boots.getItemMeta();
        meta.setColor(Color.fromRGB(0x112233));
        meta.setDisplayName("§bdyed");
        boots.setItemMeta(meta);

        Map<String, Object> map = boots.getItemMeta().serialize();
        assertEquals("LEATHER_ARMOR", map.get("meta-type"));
        assertEquals("meta-type", map.keySet().iterator().next());
        assertEquals(Color.fromRGB(0x112233), map.get("color"));
        assertFalse(map.containsKey("unhandled"), "염색 색은 전용 키로만(unhandled 에 또 넣지 않는다)");
    }

    @Test
    void defaultColoredLeatherIsNotWritten() {
        LeatherArmorMeta meta = (LeatherArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        meta.setColor(FACTORY.getDefaultLeatherColor());
        meta.setDisplayName("n");
        assertFalse(meta.serialize().containsKey("color"), "Paper 는 기본 가죽 색과 같으면 적지 않는다");
        assertFalse(meta.serialize().containsKey("unhandled"));
    }

    @Test
    void leatherMetaReadsAPaperMap() throws Throwable {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("meta-type", "LEATHER_ARMOR");
        paper.put("color", Color.fromRGB(0xAA0011));
        paper.put("ItemFlags", java.util.List.of("HIDE_DYE"));
        ItemMeta meta = SerializableMeta.deserialize(paper);
        LeatherArmorMeta leather = assertInstanceOf(LeatherArmorMeta.class, meta);
        assertEquals(Color.fromRGB(0xAA0011), leather.getColor());
        assertTrue(leather.hasItemFlag(ItemFlag.HIDE_DYE));

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        assertTrue(helmet.setItemMeta(meta));
        assertEquals(new DyedColor(0xAA0011, false), helmet.asFurnace().get(ItemComponents.DYED_COLOR));
        assertEquals(leather, helmet.getItemMeta());
    }

    @Test
    void leatherItemRoundTripsThroughYaml() {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
        meta.setColor(Color.fromRGB(0x336699));
        meta.addItemFlags(ItemFlag.HIDE_DYE);
        chest.setItemMeta(meta);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", chest);
        String text = yaml.saveToString();
        assertTrue(text.contains("meta-type: LEATHER_ARMOR"), text);

        YamlConfiguration read = new YamlConfiguration();
        try {
            read.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        ItemStack back = read.getItemStack("item");
        assertEquals(chest, back);
        assertEquals(new DyedColor(0x336699, false), back.asFurnace().get(ItemComponents.DYED_COLOR));
        assertInstanceOf(LeatherArmorMeta.class, back.getItemMeta());
    }

    @Test
    void metaTypesWeDoNotHaveYetStillReadAsPlainMeta() throws Throwable {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("meta-type", "BOOK");
        m.put("display-name", "\"Book\"");
        ItemMeta meta = SerializableMeta.deserialize(m);
        assertFalse(meta instanceof LeatherArmorMeta);
        assertEquals("Book", meta.getDisplayName());
    }
}
