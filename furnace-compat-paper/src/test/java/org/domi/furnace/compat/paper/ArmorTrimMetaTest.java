package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** T-141 E12: 방어구 장식 — ArmorMeta·ColorableArmorMeta·ArmorTrim·TrimMaterial·TrimPattern. 원문 기준은 Paper 1.21.1 CraftMetaArmor·CraftMetaColorableArmor·CraftItemMetas. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArmorTrimMetaTest {

    FurnaceTestServer server;
    static final ItemFactory FACTORY = CraftItemFactory.instance();
    static final String TRIM = "minecraft:trim";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    private static ArmorTrim trim() { return new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.SENTRY); }

    private static String component(ItemStack s) { return s.asFurnace().components().get(TRIM); }

    // ------------------------------------------------------------------ 종류

    @Test
    void metalArmorGetsArmorMetaAndLeatherGetsColorable() {
        String[] metals = {"chainmail", "diamond", "golden", "iron", "netherite"};
        for (String metal : metals) {
            for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
                Material m = Material.valueOf(metal.toUpperCase() + "_" + piece.toUpperCase());
                ItemMeta meta = new ItemStack(m).getItemMeta();
                assertInstanceOf(ArmorMeta.class, meta, m.name());
                assertFalse(meta instanceof LeatherArmorMeta, m.name());
            }
        }
        assertInstanceOf(ArmorMeta.class, FACTORY.getItemMeta(Material.TURTLE_HELMET));
        for (Material m : ItemMetaKindsTest.COLORABLE) {
            assertInstanceOf(ColorableArmorMeta.class, FACTORY.getItemMeta(m), m.name());
        }
        ItemMeta horse = FACTORY.getItemMeta(Material.LEATHER_HORSE_ARMOR);
        assertInstanceOf(LeatherArmorMeta.class, horse);
        assertFalse(horse instanceof ArmorMeta, "가죽 말 갑옷은 장식이 없다");
        for (Material m : new Material[] {Material.STONE, Material.ELYTRA, Material.IRON_HORSE_ARMOR, Material.SHIELD}) {
            assertFalse(FACTORY.getItemMeta(m) instanceof ArmorMeta, m.name());
        }
        assertEquals("ARMOR", FACTORY.getItemMeta(Material.IRON_BOOTS).serialize().get("meta-type"));
    }

    // ------------------------------------------------------------------ 레지스트리·상수

    @Test
    void registriesHoldTheVanillaValuesAsOneObjectPerKey() {
        assertEquals(10, Registry.TRIM_MATERIAL.stream().count());
        assertEquals(18, Registry.TRIM_PATTERN.stream().count());
        assertSame(TrimMaterial.QUARTZ, Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft("quartz")));
        assertSame(TrimPattern.SENTRY, Registry.TRIM_PATTERN.getOrThrow(NamespacedKey.minecraft("sentry")));
        assertNull(Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft("sentry")));
        assertEquals(NamespacedKey.minecraft("amethyst"), TrimMaterial.AMETHYST.getKey());
        assertEquals(NamespacedKey.minecraft("bolt"), TrimPattern.BOLT.getKey());
        assertSame(TrimPattern.FLOW, Registry.TRIM_PATTERN.match("Flow"));
        for (TrimMaterial m : new TrimMaterial[] {TrimMaterial.QUARTZ, TrimMaterial.IRON, TrimMaterial.NETHERITE, TrimMaterial.REDSTONE, TrimMaterial.COPPER,
                TrimMaterial.GOLD, TrimMaterial.EMERALD, TrimMaterial.DIAMOND, TrimMaterial.LAPIS, TrimMaterial.AMETHYST}) {
            assertNotNull(m);
            assertSame(m, Registry.TRIM_MATERIAL.get(m.getKey()));
        }
        assertEquals("trim_material.minecraft.quartz", TrimMaterial.QUARTZ.getTranslationKey());
        assertEquals("trim_pattern.minecraft.sentry", TrimPattern.SENTRY.getTranslationKey());
    }

    @Test
    void armorTrimEqualityIsObjectIdentityOfBothParts() {
        assertEquals(trim(), trim());
        assertEquals(trim().hashCode(), trim().hashCode());
        assertNotEquals(trim(), new ArmorTrim(TrimMaterial.IRON, TrimPattern.SENTRY));
        assertThrows(IllegalArgumentException.class, () -> new ArmorTrim(null, TrimPattern.SENTRY));
        assertThrows(IllegalArgumentException.class, () -> new ArmorTrim(TrimMaterial.IRON, null));
    }

    // ------------------------------------------------------------------ 메타 읽기·쓰기

    @Test
    void trimRoundTripsThroughTheStack() {
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        ArmorMeta meta = (ArmorMeta) boots.getItemMeta();
        assertFalse(meta.hasTrim());
        assertNull(meta.getTrim());
        meta.setTrim(trim());
        assertTrue(meta.hasTrim());
        assertTrue(boots.setItemMeta(meta));
        assertEquals("{material:\"minecraft:quartz\",pattern:\"minecraft:sentry\"}", component(boots), "툴팁에 보이는 장식은 show_in_tooltip 을 적지 않는다");
        ArmorMeta back = (ArmorMeta) boots.getItemMeta();
        assertEquals(trim(), back.getTrim());
        assertEquals(meta, back);

        back.setTrim(null);
        assertFalse(back.hasTrim());
        assertTrue(boots.setItemMeta(back));
        assertNull(component(boots));
    }

    @Test
    void hideArmorTrimFlagIsWrittenAndReadBack() {
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        ArmorMeta meta = (ArmorMeta) helmet.getItemMeta();
        meta.setTrim(trim());
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        helmet.setItemMeta(meta);
        assertTrue(component(helmet).contains("show_in_tooltip:0b"), component(helmet));

        ArmorMeta back = (ArmorMeta) helmet.getItemMeta();
        assertTrue(back.hasItemFlag(ItemFlag.HIDE_ARMOR_TRIM));
        assertEquals(trim(), back.getTrim());
        back.removeItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        helmet.setItemMeta(back);
        assertEquals("{material:\"minecraft:quartz\",pattern:\"minecraft:sentry\"}", component(helmet));

        // 표시 항목을 뺀 메타를 복제해도 다시 켜지지 않는다
        helmet.setItemMeta(meta);
        ArmorMeta hidden = (ArmorMeta) helmet.getItemMeta();
        hidden.removeItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        assertFalse(hidden.clone().hasItemFlag(ItemFlag.HIDE_ARMOR_TRIM));
    }

    @Test
    void hideFlagWithoutATrimIsLostOnTheStack() {
        // 표시 항목은 장식 컴포넌트(show_in_tooltip)에만 실리므로, 장식이 없으면 스택에 남지 않는다(Paper 도 같다)
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);
        ArmorMeta meta = (ArmorMeta) helmet.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        assertTrue(helmet.setItemMeta(meta));
        assertNull(component(helmet));
        assertFalse(helmet.getItemMeta().hasItemFlag(ItemFlag.HIDE_ARMOR_TRIM));
        meta.setTrim(trim());
        helmet.setItemMeta(meta);
        assertTrue(component(helmet).contains("show_in_tooltip:0b"), "같은 메타에 장식을 더하면 표시 항목이 따른다");
    }

    @Test
    void unknownReferencesAreDroppedLikePaper() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        // 레지스트리에 없는 재료(서버 코덱을 거치지 않는 날 컴포넌트): Paper 는 장식을 버린다
        ItemStack bad = org.domi.furnace.compat.paper.CompatItems.copyOf(
                boots.asFurnace().withComponent(TRIM, "{material:\"furnace:nope\",pattern:\"minecraft:sentry\"}"));
        ArmorMeta meta = (ArmorMeta) bad.getItemMeta();
        assertFalse(meta.hasTrim());
        bad.setItemMeta(meta);
        assertNull(component(bad), "되쓰면 장식이 사라진다");
        // 인라인 재료(레지스트리 키가 아닌 값)도 마찬가지
        ItemStack inline = org.domi.furnace.compat.paper.CompatItems.copyOf(boots.asFurnace().withComponent(TRIM,
                "{material:{asset_name:\"x\",ingredient:\"minecraft:iron_ingot\",item_model_index:0.5f,description:\"x\"},pattern:\"minecraft:sentry\"}"));
        assertFalse(((ArmorMeta) inline.getItemMeta()).hasTrim());
    }

    @Test
    void aTrimValueNotFromTheRegistryIsRejected() {
        TrimMaterial foreign = new TrimMaterial() {
            @Override
            public NamespacedKey getKey() { return new NamespacedKey("plugin", "own"); }

            @Override
            public String getTranslationKey() { return "own"; }
        };
        ArmorMeta meta = (ArmorMeta) FACTORY.getItemMeta(Material.IRON_HELMET);
        assertThrows(IllegalArgumentException.class, () -> meta.setTrim(new ArmorTrim(foreign, TrimPattern.SENTRY)));
        assertFalse(meta.hasTrim());
    }

    // ------------------------------------------------------------------ 염색 가능 방어구

    @Test
    void colorableArmorCarriesTrimAndColor() {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ColorableArmorMeta meta = (ColorableArmorMeta) chest.getItemMeta();
        meta.setColor(Color.RED);
        meta.setTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.WILD));
        chest.setItemMeta(meta);
        ColorableArmorMeta back = (ColorableArmorMeta) chest.getItemMeta();
        assertEquals(Color.RED, back.getColor());
        assertTrue(back.isDyed());
        assertEquals(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.WILD), back.getTrim());
        ColorableArmorMeta copy = back.clone();
        assertEquals(back, copy);
        copy.setTrim(null);
        assertTrue(back.hasTrim(), "복제는 따로 산다");
        assertEquals("COLORABLE_ARMOR", back.serialize().get("meta-type"));
    }

    @Test
    void trimStaysOnColorableButNotOnHorseArmor() {
        ColorableArmorMeta colorable = (ColorableArmorMeta) FACTORY.getItemMeta(Material.LEATHER_HELMET);
        colorable.setColor(Color.BLUE);
        colorable.setTrim(trim());
        assertSame(colorable, FACTORY.asMetaFor(colorable, Material.LEATHER_BOOTS));
        ItemMeta horse = FACTORY.asMetaFor(colorable, Material.LEATHER_HORSE_ARMOR);
        assertInstanceOf(LeatherArmorMeta.class, horse);
        assertFalse(horse instanceof ArmorMeta);
        assertNull(((CompatItemMeta) horse).work().components().get(TRIM), "Paper CraftMetaLeatherArmor 는 장식을 옮기지 않는다");
        ItemMeta plain = FACTORY.asMetaFor(colorable, Material.STONE);
        assertFalse(plain instanceof ArmorMeta);
        // 장식 메타는 자기 아이템 종류에만 붙는다(Paper: 메타 클래스의 첫 인터페이스가 아이템의 메타 클래스와 같아야 한다)
        ArmorMeta metal = (ArmorMeta) FACTORY.getItemMeta(Material.DIAMOND_HELMET);
        metal.setTrim(trim());   // 비어 있는 메타는 어느 아이템에서나 "지우기"로 받아들여지므로 값을 넣는다
        assertTrue(FACTORY.isApplicable(metal, Material.IRON_BOOTS));
        assertFalse(FACTORY.isApplicable(metal, Material.LEATHER_HELMET));
        assertFalse(FACTORY.isApplicable(colorable, Material.DIAMOND_HELMET));
        assertFalse(new ItemStack(Material.LEATHER_HELMET).setItemMeta(metal));
    }

    // ------------------------------------------------------------------ 직렬화

    @Test
    void serializesWithPaperKeysAndReadsPaperMaps() throws Throwable {
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        ArmorMeta meta = (ArmorMeta) boots.getItemMeta();
        meta.setTrim(new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.DUNE));
        Map<String, Object> map = meta.serialize();
        assertEquals("ARMOR", map.get("meta-type"));
        assertEquals(Map.of("material", "minecraft:emerald", "pattern", "minecraft:dune"), map.get("trim"));
        assertFalse(map.containsKey("unhandled"), "장식은 전용 키로 적고 unhandled 에 또 넣지 않는다");

        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("meta-type", "ARMOR");
        paper.put("trim", Map.of("material", "minecraft:redstone", "pattern", "minecraft:vex"));
        ItemMeta read = SerializableMeta.deserialize(paper);
        ArmorMeta armor = assertInstanceOf(ArmorMeta.class, read);
        assertEquals(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.VEX), armor.getTrim());
        assertTrue(new ItemStack(Material.IRON_CHESTPLATE).setItemMeta(read));

        // 모르는 키의 장식은 Paper 처럼 조용히 무시한다
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("meta-type", "ARMOR");
        unknown.put("trim", Map.of("material", "furnace:nope", "pattern", "minecraft:vex"));
        assertFalse(assertInstanceOf(ArmorMeta.class, SerializableMeta.deserialize(unknown)).hasTrim());
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("meta-type", "ARMOR");
        bad.put("trim", "text");
        assertThrows(IllegalArgumentException.class, () -> SerializableMeta.deserialize(bad));
    }

    @Test
    void colorableMapKeepsTrimAndColor() throws Throwable {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("meta-type", "COLORABLE_ARMOR");
        paper.put("color", Color.fromRGB(0x112233));
        paper.put("trim", Map.of("material", "minecraft:lapis", "pattern", "minecraft:tide"));
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        assertTrue(helmet.setItemMeta(SerializableMeta.deserialize(paper)));
        ColorableArmorMeta meta = (ColorableArmorMeta) helmet.getItemMeta();
        assertEquals(Color.fromRGB(0x112233), meta.getColor());
        assertEquals(new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.TIDE), meta.getTrim());
        Map<String, Object> out = meta.serialize();
        assertTrue(out.containsKey("trim") && out.containsKey("color"), out.toString());
    }

    @Test
    void armorItemRoundTripsThroughYaml() throws Exception {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ArmorMeta meta = (ArmorMeta) leggings.getItemMeta();
        meta.setTrim(new ArmorTrim(TrimMaterial.COPPER, TrimPattern.SPIRE));
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        leggings.setItemMeta(meta);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", leggings);
        String text = yaml.saveToString();
        assertTrue(text.contains("meta-type: ARMOR"), text);
        assertTrue(text.contains("minecraft:copper") && text.contains("minecraft:spire"), text);
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(text);
        ItemStack back = read.getItemStack("item");
        assertEquals(leggings, back);
        ArmorMeta backMeta = (ArmorMeta) back.getItemMeta();
        assertEquals(new ArmorTrim(TrimMaterial.COPPER, TrimPattern.SPIRE), backMeta.getTrim());
        assertTrue(backMeta.hasItemFlag(ItemFlag.HIDE_ARMOR_TRIM));
    }

    @Test
    void metaEqualityAndHashFollowTheTrim() {
        ArmorMeta a = (ArmorMeta) FACTORY.getItemMeta(Material.IRON_HELMET);
        ArmorMeta b = (ArmorMeta) FACTORY.getItemMeta(Material.IRON_HELMET);
        assertEquals(a, b);
        a.setTrim(trim());
        assertNotEquals(a, b);
        b.setTrim(trim());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.setTrim(new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.COAST));
        assertNotEquals(a, b);
    }
}
