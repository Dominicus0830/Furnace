package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.Snbt;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * ItemStack·ItemMeta 직렬화(Paper 1.21.1 맵 모양)와 BukkitObjectOutputStream/InputStream, ConfigurationSerialization, 설정 파일 왕복.
 * helper 의 InventorySerialization 이 하는 일(스트림에 쓰고 base64) 그대로다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemSerializationTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    // ------------------------------------------------------------------ 도우미

    /** helper 의 InventorySerialization.encodeItemStack/decodeItemStack 과 같은 코드. */
    static byte[] encode(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object decode(byte[] data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    static ItemStack roundTrip(ItemStack s) throws Exception { return (ItemStack) decode(encode(s)); }

    /** 이름·설명·마법 부여·표시 항목·내구도·영속 데이터를 모두 가진 검. */
    static ItemStack fancySword() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName("§6Excalibur");
        m.setLore(List.of("§7first", "second"));
        m.addEnchant(Enchantment.SHARPNESS, 5, true);
        m.addEnchant(Enchantment.UNBREAKING, 3, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        m.setUnbreakable(true);
        m.setCustomModelData(12);
        ((Damageable) m).setDamage(7);
        ((Repairable) m).setRepairCost(3);
        m.getPersistentDataContainer().set(new NamespacedKey("furnace", "count"), PersistentDataType.INTEGER, 5);
        m.getPersistentDataContainer().set(new NamespacedKey("furnace", "owner"), PersistentDataType.STRING, "dana");
        s.setItemMeta(m);
        return s;
    }

    static void assertFancy(ItemStack s) {
        assertEquals(Material.DIAMOND_SWORD, s.getType());
        ItemMeta m = s.getItemMeta();
        assertEquals("§6Excalibur", m.getDisplayName());
        assertEquals(List.of("§7first", "second"), m.getLore());
        assertEquals(5, m.getEnchantLevel(Enchantment.SHARPNESS));
        assertEquals(3, m.getEnchantLevel(Enchantment.UNBREAKING));
        assertTrue(m.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        assertTrue(m.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE));
        assertTrue(m.isUnbreakable());
        assertEquals(12, m.getCustomModelData());
        assertEquals(7, ((Damageable) m).getDamage());
        assertEquals(3, ((Repairable) m).getRepairCost());
        assertEquals(5, m.getPersistentDataContainer().get(new NamespacedKey("furnace", "count"), PersistentDataType.INTEGER));
        assertEquals("dana", m.getPersistentDataContainer().get(new NamespacedKey("furnace", "owner"), PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------ 맵 모양(Paper 1.21.1)

    @Test
    void plainStackMapShape() {
        Map<String, Object> m = new ItemStack(Material.STONE).serialize();
        assertEquals(List.of("v", "type"), new ArrayList<>(m.keySet()), "amount 1 · 빈 메타는 뺀다");
        assertEquals(3955, m.get("v"));
        assertEquals("STONE", m.get("type"));

        Map<String, Object> m3 = new ItemStack(Material.STONE, 3).serialize();
        assertEquals(List.of("v", "type", "amount"), new ArrayList<>(m3.keySet()));
        assertEquals(3, m3.get("amount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void metaMapShape() {
        Map<String, Object> m = new ItemStack(Material.DIAMOND_SWORD, 2).serialize();
        // meta 는 비어 있으면 없다
        assertFalse(m.containsKey("meta"));

        Map<String, Object> full = fancySword().serialize();
        assertEquals(3955, full.get("v"));
        ItemMeta meta = assertInstanceOf(ItemMeta.class, full.get("meta"), "meta 값은 ItemMeta 객체(스트림·설정이 ==: ItemMeta 로 풀어 쓴다)");
        Map<String, Object> mm = meta.serialize();
        assertEquals("UNSPECIFIC", mm.get("meta-type"));
        assertEquals("meta-type", mm.keySet().iterator().next(), "meta-type 이 맨 앞");
        assertEquals("§6Excalibur", Compat.legacy(JsonTextSerializer.json().deserialize((String) mm.get("display-name"))), "display-name 은 JSON 문자열");
        List<String> lore = (List<String>) mm.get("lore");
        assertEquals(2, lore.size());
        assertEquals("§7first", Compat.legacy(JsonTextSerializer.json().deserialize(lore.get(0))));
        assertEquals(12, mm.get("custom-model-data"));
        assertEquals(Map.of("minecraft:sharpness", 5, "minecraft:unbreaking", 3), mm.get("enchants"));
        assertEquals(3, mm.get("repair-cost"));
        assertEquals(List.of("HIDE_ENCHANTS", "HIDE_UNBREAKABLE"), mm.get("ItemFlags"));
        assertEquals(true, mm.get("Unbreakable"));
        assertEquals(7, mm.get("Damage"));
        assertFalse(mm.containsKey("hide-tool-tip"));
        // 영속 데이터는 SNBT 문자열
        NbtCompound pbv = NbtCompound.parse((String) mm.get("PublicBukkitValues"));
        assertEquals(NbtCompound.parse("{\"furnace:count\":5,\"furnace:owner\":\"dana\"}"), pbv);
        // Paper 가 쓰지 않는 키는 없다
        assertFalse(mm.containsKey("unhandled"));
        assertFalse(mm.containsKey("removed"));
        assertFalse(mm.containsKey("custom"));
    }

    @Test
    void emptyMetaIsOmittedAndNameOnlyMetaIsKept() {
        ItemStack s = new ItemStack(Material.STONE);
        ItemMeta m = s.getItemMeta();
        m.addItemFlags(ItemFlag.HIDE_DESTROYS);
        s.setItemMeta(m);
        assertFalse(s.serialize().containsKey("meta"), "표시 항목만 있고 컴포넌트가 없는 건 스택에 남지 않는다");

        ItemStack named = new ItemStack(Material.STONE);
        ItemMeta nm = named.getItemMeta();
        nm.setItemName("§aStone");
        nm.setEnchantmentGlintOverride(true);
        named.setItemMeta(nm);
        ItemMeta back = ((ItemMeta) named.serialize().get("meta"));
        assertEquals(true, back.serialize().get("enchantment-glint-override"));
        assertNotNull(back.serialize().get("item-name"));
    }

    // ------------------------------------------------------------------ BukkitObject 스트림

    @Test
    void singleItemRoundTripsThroughBukkitObjectStreams() throws Exception {
        ItemStack s = fancySword();
        s.setAmount(1);
        ItemStack back = roundTrip(s);
        assertNotSame(s, back);
        assertFancy(back);
        assertEquals(s, back, "ItemStack.equals — 종류·개수·메타");
        assertEquals(s.getItemMeta(), back.getItemMeta());
    }

    @Test
    void plainAndNullAndAmount() throws Exception {
        assertEquals(new ItemStack(Material.APPLE, 17), roundTrip(new ItemStack(Material.APPLE, 17)));
        assertFalse(roundTrip(new ItemStack(Material.APPLE)).hasItemMeta());
        assertNull(decode(encode(null)));
    }

    @Test
    void itemArraysLikeHelperEncodeThem() throws Exception {
        ItemStack[] items = {fancySword(), null, new ItemStack(Material.STONE, 64), new ItemStack(Material.ENDER_PEARL, 3)};
        // helper: writeInt(length) 뒤에 칸마다 writeObject(null 허용)
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack i : items) out.writeObject(i);
        }
        String base64 = Base64.getEncoder().encodeToString(bytes.toByteArray());
        ItemStack[] back;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            back = new ItemStack[in.readInt()];
            for (int i = 0; i < back.length; i++) back[i] = (ItemStack) in.readObject();
        }
        assertEquals(4, back.length);
        assertFancy(back[0]);
        assertNull(back[1]);
        assertEquals(new ItemStack(Material.STONE, 64), back[2]);
        assertEquals(new ItemStack(Material.ENDER_PEARL, 3), back[3]);
    }

    @Test
    void metaItselfAndMapsOfItemsRoundTrip() throws Exception {
        ItemMeta meta = fancySword().getItemMeta();
        ItemMeta metaBack = (ItemMeta) decode(encode(meta));
        assertEquals(meta, metaBack);

        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("main", fancySword());
        bag.put("list", new ArrayList<>(List.of(new ItemStack(Material.STONE, 2), new ItemStack(Material.DIRT))));
        bag.put("n", 5);
        @SuppressWarnings("unchecked")
        Map<String, Object> bagBack = (Map<String, Object>) decode(encode(bag));
        assertFancy((ItemStack) bagBack.get("main"));
        assertEquals(List.of(new ItemStack(Material.STONE, 2), new ItemStack(Material.DIRT)), bagBack.get("list"));
        assertEquals(5, bagBack.get("n"));
    }

    @Test
    void wrapperIsInterchangeableWithPaperByName() throws Exception {
        Class<?> w = Class.forName("org.bukkit.util.io.Wrapper");
        assertEquals(-986209235411767547L, ObjectStreamClass.lookup(w).getSerialVersionUID(), "Paper 의 Wrapper 와 같은 serialVersionUID");
        var fields = ObjectStreamClass.lookup(w).getFields();
        assertEquals(1, fields.length);
        assertEquals("map", fields[0].getName());
        assertEquals(Map.class, fields[0].getType(), "필드 선언 타입이 Map 이라 Paper 가 우리 스트림을 읽는다");
        // 스트림 안에 Paper 와 같은 클래스 이름이 쓰인다
        assertTrue(new String(encode(new ItemStack(Material.STONE)), java.nio.charset.StandardCharsets.ISO_8859_1).contains("org.bukkit.util.io.Wrapper"));
    }

    @Test
    void unreadableWrapperBecomesAnIOException() throws Exception {
        @SerializableAs("TestTransient")
        class Temp implements ConfigurationSerializable {
            public Map<String, Object> serialize() { return Map.of("x", 1); }
        }
        // 별칭을 등록하지 않은 클래스는 쓸 수는 있어도 되읽을 수 없다(IOException 으로 감싸 알린다)
        byte[] data = encode(new Temp());
        IOException e = assertThrows(IOException.class, () -> decode(data));
        assertEquals("Failed to deserialize object", e.getMessage());
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    // ------------------------------------------------------------------ 컴포넌트 보존(unhandled·removed·custom)

    @Test
    void componentsTheMetaDoesNotModelSurviveInUnhandled() throws Exception {
        var f = new org.domi.furnace.api.item.ItemStack("minecraft:leather_helmet", 2)
                .with(ItemComponents.DYED_COLOR, new DyedColor(0xAA3300, true))
                .with(ItemComponents.MAP_COLOR, 77)
                .withComponent("minecraft:custom_data", "{bar:\"x\",foo:1b}")
                .withoutComponent("minecraft:enchantments");
        ItemStack s = org.domi.furnace.compat.paper.CompatItems.copyOf(f);

        Map<String, Object> mm = ((ItemMeta) s.serialize().get("meta")).serialize();
        assertTrue(mm.containsKey("unhandled"));
        // unhandled 는 Paper 와 같게 base64(gzip NBT) — 우리 NbtBinary 가 아닌 표준 GZIP 으로 풀어 본다
        byte[] raw = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode((String) mm.get("unhandled")))).readAllBytes();
        assertEquals(10, raw[0], "루트는 묶음(태그 10)");
        assertTrue(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1).contains("minecraft:dyed_color"));
        assertEquals(List.of("minecraft:enchantments"), mm.get("removed"));
        // custom_data 의 나머지는 custom
        assertTrue(mm.containsKey("custom"));
        assertFalse(mm.containsKey("PublicBukkitValues"));

        ItemStack back = roundTrip(s);
        var bf = back.asFurnace();
        assertEquals("minecraft:leather_helmet", bf.type());
        assertEquals(2, back.getAmount());
        assertEquals(new DyedColor(0xAA3300, true), bf.get(ItemComponents.DYED_COLOR));
        assertEquals(77, bf.get(ItemComponents.MAP_COLOR));
        assertEquals(NbtCompound.parse("{foo:1b,bar:\"x\"}"), Snbt.parse(bf.components().get("minecraft:custom_data")));
        assertTrue(bf.removedComponents().contains("minecraft:enchantments"));
        assertEquals(s, back);
    }

    @Test
    void explicitValuesEqualToTheItemDefaultAreKept() throws Exception {
        // 곡괭이 기본 max_stack_size 는 1 — 64 를 직접 붙인 값이 되읽을 때 임시 종류(stone, 기본 64)와 같다고 지워지면 안 된다
        var f = new org.domi.furnace.api.item.ItemStack("minecraft:iron_pickaxe", 1).withComponent("minecraft:max_stack_size", "64").withComponent("minecraft:rarity", "\"common\"");
        ItemStack s = org.domi.furnace.compat.paper.CompatItems.copyOf(f);
        Map<String, Object> mm = ((ItemMeta) s.serialize().get("meta")).serialize();
        assertEquals(64, mm.get("max-stack-size"));
        assertEquals("COMMON", mm.get("rarity"));
        ItemStack back = roundTrip(s);
        assertEquals(s, back);
        assertEquals("64", back.asFurnace().components().get("minecraft:max_stack_size"));
        assertEquals(64, back.getMaxStackSize());
    }

    // ------------------------------------------------------------------ Paper 가 쓴 맵 읽기

    @Test
    void readsAMapShapedLikePaperWrote() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("meta-type", "UNSPECIFIC");
        meta.put("display-name", "{\"extra\":[{\"italic\":false,\"color\":\"red\",\"text\":\"Hot\"}],\"text\":\"\"}");
        meta.put("lore", new ArrayList<>(List.of("{\"extra\":[{\"italic\":false,\"text\":\"a\"}],\"text\":\"\"}", "plain legacy")));
        meta.put("enchants", new LinkedHashMap<>(Map.of("minecraft:fire_aspect", 2)));
        meta.put("ItemFlags", new ArrayList<>(List.of("HIDE_ATTRIBUTES", "HIDE_REMOVED_LONG_AGO")));
        meta.put("PublicBukkitValues", "{\"foo:bar\":\"baz\",\"foo:n\":7}");
        meta.put("Damage", 9);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 3955);
        m.put("type", "IRON_SWORD");
        m.put("amount", 4);
        m.put("meta", ConfigurationSerialization.deserializeObject(withAlias(meta)));
        ItemStack s = ItemStack.deserialize(m);
        assertEquals(Material.IRON_SWORD, s.getType());
        assertEquals(4, s.getAmount());
        ItemMeta im = s.getItemMeta();
        assertEquals("§cHot", im.getDisplayName());
        assertEquals(List.of("a", "plain legacy"), im.getLore());
        assertEquals(2, im.getEnchantLevel(Enchantment.FIRE_ASPECT));
        assertTrue(im.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES), "알 수 없는 옛 이름은 무시, 아는 것은 적용");
        assertEquals("baz", im.getPersistentDataContainer().get(new NamespacedKey("foo", "bar"), PersistentDataType.STRING));
        assertEquals(7, im.getPersistentDataContainer().get(new NamespacedKey("foo", "n"), PersistentDataType.INTEGER));
        assertEquals(9, ((Damageable) im).getDamage());
    }

    private static Map<String, Object> withAlias(Map<String, Object> meta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("==", "ItemMeta");
        m.putAll(meta);
        return m;
    }

    @Test
    void legacyKeysAndErrors() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "DIAMOND_PICKAXE");
        m.put("damage", 5);
        m.put("enchantments", new LinkedHashMap<>(Map.of("minecraft:efficiency", 4)));
        ItemStack s = ItemStack.deserialize(m);
        assertEquals(5, s.getDurability());
        assertEquals(4, s.getEnchantmentLevel(Enchantment.EFFICIENCY));

        assertThrows(IllegalArgumentException.class, () -> ItemStack.deserialize(Map.of("v", 3955, "type", "NOT_A_THING")));

        assertThrows(NoSuchElementException.class, () -> org.bukkit.craftbukkit.inventory.SerializableMeta.deserialize(Map.of("display-name", "x")));
        var bad = assertThrows(IllegalArgumentException.class, () -> org.bukkit.craftbukkit.inventory.SerializableMeta.deserialize(Map.of("meta-type", "BOGUS")));
        assertTrue(bad.getMessage().contains("BOGUS"));
        assertThrows(IllegalArgumentException.class, () -> org.bukkit.craftbukkit.inventory.SerializableMeta.deserialize(Map.of("meta-type", "UNSPECIFIC", "repair-cost", "three")));
        // 종류별 meta-type 은 받아들이고(종류별 키는 무시), 값이 나쁜 경고는 위처럼 거절
        assertNotNull(assertDoesNotThrowMeta(Map.of("meta-type", "POTION", "potion-type", "minecraft:water")));
    }

    private static ItemMeta assertDoesNotThrowMeta(Map<String, Object> map) {
        try {
            return org.bukkit.craftbukkit.inventory.SerializableMeta.deserialize(map);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    // ------------------------------------------------------------------ ConfigurationSerialization

    @SerializableAs("FurnaceTestPoint")
    public static final class Point implements ConfigurationSerializable {
        final int x, y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Map<String, Object> serialize() { return new LinkedHashMap<>(Map.of("x", x, "y", y)); }

        public static Point deserialize(Map<String, Object> m) { return new Point((Integer) m.get("x"), (Integer) m.get("y")); }

        @Override
        public boolean equals(Object o) { return o instanceof Point p && p.x == x && p.y == y; }

        @Override
        public int hashCode() { return 31 * x + y; }
    }

    @DelegateDeserialization(Point.class)
    public static final class Point3 implements ConfigurationSerializable {
        @Override
        public Map<String, Object> serialize() { return Map.of("x", 3, "y", 3); }
    }

    public static final class CtorOnly implements ConfigurationSerializable {
        final String v;

        public CtorOnly(Map<String, Object> m) { v = (String) m.get("v"); }

        @Override
        public Map<String, Object> serialize() { return Map.of("v", v); }
    }

    @Test
    void registryAliasesAndDeserialization() {
        assertEquals("org.bukkit.inventory.ItemStack", ConfigurationSerialization.getAlias(ItemStack.class), "ItemStack 별칭은 클래스 이름");
        assertEquals("ItemMeta", ConfigurationSerialization.getAlias(CompatItemMeta.class), "DelegateDeserialization → SerializableMeta 의 별칭");
        assertEquals("ItemMeta", ConfigurationSerialization.getAlias(org.bukkit.craftbukkit.inventory.SerializableMeta.class));
        assertEquals(ItemStack.class, ConfigurationSerialization.getClassByAlias("org.bukkit.inventory.ItemStack"));
        assertEquals(org.bukkit.craftbukkit.inventory.SerializableMeta.class, ConfigurationSerialization.getClassByAlias("ItemMeta"));
        assertEquals("==", ConfigurationSerialization.SERIALIZED_TYPE_KEY);
        assertEquals("org.bukkit.craftbukkit.inventory.CraftItemStack", org.bukkit.craftbukkit.inventory.CraftItemStack.class.getName());
        assertEquals("org.bukkit.inventory.ItemStack", ConfigurationSerialization.getAlias(org.bukkit.craftbukkit.inventory.CraftItemStack.class));

        assertNull(ConfigurationSerialization.getClassByAlias("FurnaceTestPoint"));
        ConfigurationSerialization.registerClass(Point.class);
        try {
            assertEquals(Point.class, ConfigurationSerialization.getClassByAlias("FurnaceTestPoint"));
            assertEquals(Point.class, ConfigurationSerialization.getClassByAlias(Point.class.getName()), "클래스 이름으로도 등록");
            assertEquals(new Point(1, 2), ConfigurationSerialization.deserializeObject(Map.of("==", "FurnaceTestPoint", "x", 1, "y", 2)));
            assertEquals(new Point(4, 5), ConfigurationSerialization.deserializeObject(Map.of("x", 4, "y", 5), Point.class));
            assertEquals("FurnaceTestPoint", ConfigurationSerialization.getAlias(Point3.class), "맡은 클래스의 별칭");
            ConfigurationSerialization.registerClass(Point3.class); // 위임 클래스는 등록하지 않는다
            assertNull(ConfigurationSerialization.getClassByAlias(Point3.class.getName()));

            assertThrows(IllegalArgumentException.class, () -> ConfigurationSerialization.deserializeObject(Map.of("x", 1)), "== 없음");
            assertThrows(IllegalArgumentException.class, () -> ConfigurationSerialization.deserializeObject(Map.of("==", "Nope")), "모르는 별칭");
        } finally {
            ConfigurationSerialization.unregisterClass(Point.class);
        }
        assertNull(ConfigurationSerialization.getClassByAlias("FurnaceTestPoint"));
        assertNull(ConfigurationSerialization.getClassByAlias(Point.class.getName()));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationSerialization.deserializeObject(Map.of("==", "FurnaceTestPoint")));

        ConfigurationSerialization.registerClass(CtorOnly.class, "Ctor");
        try {
            assertEquals("hi", ((CtorOnly) ConfigurationSerialization.deserializeObject(Map.of("==", "Ctor", "v", "hi"))).v, "생성자(Map)로 되읽기");
        } finally {
            ConfigurationSerialization.unregisterClass("Ctor");
        }
        assertNull(ConfigurationSerialization.getClassByAlias("Ctor"));
    }

    @Test
    void customSerializablesWorkInObjectStreamsAndConfigs() throws Exception {
        ConfigurationSerialization.registerClass(Point.class);
        try {
            assertEquals(new Point(7, 8), decode(encode(new Point(7, 8))));
            YamlConfiguration y = new YamlConfiguration();
            y.set("p", new Point(1, 2));
            y.set("pts", List.of(new Point(3, 4), new Point(5, 6)));
            YamlConfiguration back = new YamlConfiguration();
            back.loadFromString(y.saveToString());
            assertEquals(new Point(1, 2), back.get("p"));
            assertEquals(new Point(1, 2), back.getSerializable("p", Point.class));
            assertEquals(List.of(new Point(3, 4), new Point(5, 6)), back.getList("pts"));
        } finally {
            ConfigurationSerialization.unregisterClass(Point.class);
        }
    }

    // ------------------------------------------------------------------ 설정 파일 왕복

    @Test
    void itemStacksRoundTripThroughYamlConfiguration() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        ItemStack sword = fancySword();
        cfg.set("kit.sword", sword);
        cfg.set("kit.apples", new ItemStack(Material.APPLE, 5));
        cfg.set("kit.all", List.of(sword, new ItemStack(Material.STONE, 2)));
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("org.bukkit.inventory.ItemStack"), yaml);
        assertTrue(yaml.contains("ItemMeta"), yaml);
        assertTrue(yaml.contains("meta-type: UNSPECIFIC"), yaml);
        assertTrue(yaml.contains("type: DIAMOND_SWORD"), yaml);
        assertTrue(yaml.contains("v: 3955"), yaml);

        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml);
        ItemStack s = back.getItemStack("kit.sword");
        assertNotNull(s);
        assertFancy(s);
        assertEquals(sword, s);
        assertTrue(back.isItemStack("kit.sword"));
        assertEquals(new ItemStack(Material.APPLE, 5), back.getItemStack("kit.apples"));
        assertNull(back.getItemStack("kit.nothing"));
        assertEquals(new ItemStack(Material.DIRT), back.getItemStack("kit.nothing", new ItemStack(Material.DIRT)));
        assertInstanceOf(ItemStack.class, back.get("kit.sword"));
        assertFalse(back.isConfigurationSection("kit.sword"), "객체로 저장된 값은 구역이 아니다");
        assertNull(back.getConfigurationSection("kit.sword"));
        ConfigurationSection kit = back.getConfigurationSection("kit");
        assertNotNull(kit);
        assertEquals(sword, kit.getItemStack("sword"));
        List<?> all = back.getList("kit.all");
        assertEquals(2, all.size());
        assertEquals(sword, all.get(0));
        assertEquals(new ItemStack(Material.STONE, 2), all.get(1));
        assertEquals(sword, back.getObject("kit.sword", ItemStack.class));
        assertEquals(sword, back.getValues(false).get("kit") instanceof ConfigurationSection c ? c.get("sword") : null);
    }

    @Test
    void memoryConfigurationAlsoRoundTripsItems() {
        MemoryConfiguration cfg = new MemoryConfiguration();
        cfg.set("a.b", fancySword());
        ItemStack s = cfg.getItemStack("a.b");
        assertFancy(s);
        assertEquals(fancySword(), s);
        // 두 번 읽으면 서로 다른 사본(Bukkit 은 같은 객체지만, 사본이 안전하다)
        assertNotSame(cfg.getItemStack("a.b"), s);
        cfg.set("a.b", null);
        assertNull(cfg.getItemStack("a.b"));
    }

    @Test
    void unknownAliasStaysAPlainSection() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("x:\n  ==: not.registered.Thing\n  a: 1\n");
        assertTrue(cfg.isConfigurationSection("x"));
        assertEquals(1, cfg.getInt("x.a"));
        assertNull(cfg.getItemStack("x"));
        // 되읽기 못 한 값이 들어 있어도 파일을 다시 쓸 수 있다
        assertTrue(cfg.saveToString().contains("not.registered.Thing"));
    }

    @Test
    void arrayHelpersMatch() {
        // 설정 맵에서 쓰는 NBT 바이너리: 우리가 쓴 것을 표준 GZIP·바이너리 규칙으로 읽을 수 있다
        NbtCompound c = NbtCompound.parse("{a:1b,b:2s,c:3,d:4L,e:1.5f,f:2.5d,g:\"s\",h:[B;1B,2B],i:[I;1,2],j:[L;1L],k:[1,2],l:{m:\"n\"},o:[{p:1},{p:2}],q:[]}");
        byte[] bin = NbtBinary.writeCompressed(c);
        try {
            assertEquals(c, NbtBinary.readCompressed(bin));
            assertArrayEquals(new byte[] {1, 2}, ((byte[]) NbtBinary.readCompressed(bin).get("h")));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertThrows(IOException.class, () -> NbtBinary.readCompressed(new byte[] {1, 2, 3}));
    }
}
