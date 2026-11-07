package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Bukkit ItemStack·ItemMeta·Inventory·PlayerInventory 가 실제(헤드리스) 서버의 플레이어 인벤토리 위에서 Bukkit 규칙대로 도는지. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryAdapterTest {

    FurnaceTestServer server;
    TestPlayer tp;
    org.bukkit.entity.Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Dana");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    /** 플레이어를 가진 region 스레드에서 돌린다(이벤트 처리기 안과 같은 조건). */
    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    void owner(Runnable task) {
        server.onRegion(at, () -> {
            task.run();
            return null;
        });
    }

    @BeforeEach
    void clear() {
        owner(() -> {
            player.getInventory().clear();
            player.getInventory().setHeldItemSlot(0);
        });
        server.tick(1);
    }

    // ------------------------------------------------------------------ ItemStack 값

    @Test
    void plainStackBasics() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD, 1);
        assertEquals(Material.DIAMOND_SWORD, s.getType());
        assertEquals(1, s.getAmount());
        assertEquals(1, s.getMaxStackSize());
        assertEquals(64, new ItemStack(Material.STONE).getMaxStackSize());
        assertEquals(16, new ItemStack(Material.ENDER_PEARL).getMaxStackSize());
        assertEquals(0, s.getDurability());
        s.setDurability((short) 12);
        assertEquals(12, s.getDurability());
        assertTrue(s.hasItemMeta(), "damage 컴포넌트가 붙었다");

        // 개수 0·음수도 Bukkit 처럼 그대로 들고 있다
        ItemStack z = new ItemStack(Material.STONE, 0);
        assertEquals(0, z.getAmount());
        assertTrue(z.isEmpty());
        z.setAmount(-3);
        assertEquals(-3, z.getAmount());
        assertEquals(Material.STONE, z.getType());

        // 복사·같음·비슷함
        ItemStack a = new ItemStack(Material.STONE, 5), b = new ItemStack(Material.STONE, 9);
        assertTrue(a.isSimilar(b));
        assertFalse(a.equals(b));
        b.setAmount(5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        ItemStack c = a.clone();
        assertNotSame(a, c);
        assertEquals(a, c);
        c.setAmount(1);
        assertEquals(5, a.getAmount(), "사본은 원본과 따로 논다");
        assertFalse(a.isSimilar(null));
        assertFalse(a.isSimilar(new ItemStack(Material.DIRT)));
        a.setType(Material.DIRT);
        assertEquals(Material.DIRT, a.getType());
        assertEquals(5, a.getAmount());
        assertThrows(NullPointerException.class, () -> new ItemStack(Material.STONE).setType(null));
    }

    @Test
    void enchantmentsOnTheStack() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(s.getEnchantments().isEmpty());
        s.addEnchantment(Enchantment.SHARPNESS, 3);
        s.addUnsafeEnchantment(Enchantment.KNOCKBACK, 200);
        assertEquals(3, s.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertTrue(s.containsEnchantment(Enchantment.KNOCKBACK));
        assertEquals(java.util.Map.of(Enchantment.SHARPNESS, 3, Enchantment.KNOCKBACK, 200), s.getEnchantments());
        assertEquals(3, s.removeEnchantment(Enchantment.SHARPNESS));
        assertEquals(0, s.removeEnchantment(Enchantment.SHARPNESS));
        assertFalse(s.containsEnchantment(Enchantment.SHARPNESS));
        assertThrows(IllegalArgumentException.class, () -> s.addEnchantment(Enchantment.SHARPNESS, 0));
        assertThrows(IllegalArgumentException.class, () -> s.addEnchantment(Enchantment.SHARPNESS, 256));
    }

    // ------------------------------------------------------------------ ItemMeta

    @Test
    void metaIsACopyAppliedBySetItemMeta() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta m = s.getItemMeta();
        assertFalse(m.hasDisplayName());
        assertNull(m.getDisplayName());
        assertFalse(m.hasLore());
        assertNull(m.getLore());
        m.setDisplayName("§6Name");
        m.setLore(List.of("§7a", "b"));
        assertFalse(s.hasItemMeta(), "setItemMeta 전에는 스택이 그대로다");
        assertTrue(s.setItemMeta(m));
        assertTrue(s.hasItemMeta());
        ItemMeta back = s.getItemMeta();
        assertEquals("§6Name", back.getDisplayName());
        assertEquals(List.of("§7a", "b"), back.getLore());
        assertEquals(m, back);

        // 사본이라 꺼낸 메타를 바꿔도 스택은 그대로
        back.setDisplayName("other");
        assertEquals("§6Name", s.getItemMeta().getDisplayName());
        back.setDisplayName(null);
        back.setLore(null);
        s.setItemMeta(back);
        assertFalse(s.getItemMeta().hasDisplayName());
        assertFalse(s.getItemMeta().hasLore());
        s.setItemMeta(null);
        assertFalse(s.hasItemMeta());
        assertThrows(IllegalArgumentException.class, () -> s.setItemMeta(new ItemMeta() {
            public boolean hasDisplayName() { return false; }
            public String getDisplayName() { return null; }
            public void setDisplayName(String n) {}
            public boolean hasItemName() { return false; }
            public String getItemName() { return null; }
            public void setItemName(String n) {}
            public boolean hasLore() { return false; }
            public List<String> getLore() { return null; }
            public void setLore(List<String> l) {}
            public boolean hasCustomModelData() { return false; }
            public int getCustomModelData() { return 0; }
            public void setCustomModelData(Integer d) {}
            public boolean hasEnchants() { return false; }
            public boolean hasEnchant(Enchantment e) { return false; }
            public int getEnchantLevel(Enchantment e) { return 0; }
            public java.util.Map<Enchantment, Integer> getEnchants() { return java.util.Map.of(); }
            public boolean addEnchant(Enchantment e, int l, boolean i) { return false; }
            public boolean removeEnchant(Enchantment e) { return false; }
            public void addItemFlags(ItemFlag... f) {}
            public void removeItemFlags(ItemFlag... f) {}
            public java.util.Set<ItemFlag> getItemFlags() { return java.util.Set.of(); }
            public boolean hasItemFlag(ItemFlag f) { return false; }
            public boolean isUnbreakable() { return false; }
            public void setUnbreakable(boolean u) {}
            public boolean isFireResistant() { return false; }
            public void setFireResistant(boolean u) {}
            public boolean isHideTooltip() { return false; }
            public void setHideTooltip(boolean u) {}
            public boolean hasEnchantmentGlintOverride() { return false; }
            public Boolean getEnchantmentGlintOverride() { return null; }
            public void setEnchantmentGlintOverride(Boolean o) {}
            public PersistentDataContainer getPersistentDataContainer() { return null; }
            public ItemMeta clone() { return this; }
        }));
    }

    @Test
    void metaFieldsMapToComponents() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta m = s.getItemMeta();
        assertTrue(m instanceof Damageable);
        assertTrue(m instanceof Repairable);
        m.setCustomModelData(7);
        assertEquals(7, m.getCustomModelData());
        assertTrue(m.addEnchant(Enchantment.SHARPNESS, 2, false));
        assertFalse(m.addEnchant(Enchantment.SHARPNESS, 2, false), "같은 레벨은 바뀌지 않았다");
        assertTrue(m.addEnchant(Enchantment.SHARPNESS, 5, true));
        assertEquals(5, m.getEnchantLevel(Enchantment.SHARPNESS));
        assertFalse(m.addEnchant(Enchantment.SHARPNESS, 0, true));
        m.setUnbreakable(true);
        m.setItemName("Base");
        ((Damageable) m).setDamage(30);
        ((Repairable) m).setRepairCost(3);
        m.setEnchantmentGlintOverride(true);
        m.setFireResistant(true);
        m.setHideTooltip(true);
        s.setItemMeta(m);

        ItemMeta r = s.getItemMeta();
        assertEquals(7, r.getCustomModelData());
        assertEquals(5, r.getEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(r.hasEnchants());
        assertTrue(r.isUnbreakable());
        assertEquals("Base", r.getItemName());
        assertEquals(30, ((Damageable) r).getDamage());
        assertTrue(((Damageable) r).hasDamage());
        assertEquals(30, s.getDurability(), "ItemStack#getDurability 도 같은 값");
        assertEquals(3, ((Repairable) r).getRepairCost());
        assertTrue(r.hasEnchantmentGlintOverride());
        assertTrue(r.getEnchantmentGlintOverride());
        assertTrue(r.isFireResistant());
        assertTrue(r.isHideTooltip());
        assertTrue(r.removeEnchant(Enchantment.SHARPNESS));
        assertFalse(r.removeEnchant(Enchantment.SHARPNESS));
        assertFalse(r.hasEnchants());

        // 지우기
        r.setCustomModelData(null);
        r.setUnbreakable(false);
        r.setEnchantmentGlintOverride(null);
        assertFalse(r.hasCustomModelData());
        assertThrows(IllegalStateException.class, r::getCustomModelData);
        assertThrows(IllegalStateException.class, r::getEnchantmentGlintOverride);
    }

    @Test
    void itemFlagsRoundTripThroughComponents() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta m = s.getItemMeta();
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.addEnchant(Enchantment.SHARPNESS, 1, true);
        assertTrue(m.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        s.setItemMeta(m);
        ItemMeta r = s.getItemMeta();
        assertEquals(java.util.Set.of(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP), r.getItemFlags());
        assertEquals(1, r.getEnchantLevel(Enchantment.SHARPNESS), "숨겨도 마법 부여는 그대로");
        // 칼의 기본 속성 변경치는 숨기기만 하고 지우지 않는다
        var attrs = s.asFurnace().get(org.domi.furnace.api.item.ItemComponents.ATTRIBUTE_MODIFIERS);
        assertFalse(attrs.showInTooltip());
        assertFalse(attrs.modifiers().isEmpty(), "칼의 공격력 변경치가 남아 있다");

        r.removeItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        s.setItemMeta(r);
        assertEquals(java.util.Set.of(ItemFlag.HIDE_ADDITIONAL_TOOLTIP), s.getItemMeta().getItemFlags());
        assertTrue(s.asFurnace().get(org.domi.furnace.api.item.ItemComponents.ATTRIBUTE_MODIFIERS).showInTooltip());

        // 숨길 대상이 없는 표시 항목은 Bukkit 처럼 받아 주고(효과 없음), 있으면 정직하게 거절한다
        ItemMeta plain = new ItemStack(Material.STONE).getItemMeta();
        plain.addItemFlags(ItemFlag.values());
        assertTrue(plain.hasItemFlag(ItemFlag.HIDE_DESTROYS));
        ItemStack withBreak = new ItemStack(Material.STONE);
        withBreak.setItemMeta(null);
        var rawBreak = new org.domi.furnace.api.item.ItemStack("minecraft:stone", 1).withComponent("minecraft:can_break", "{predicates:[{blocks:\"minecraft:stone\"}],show_in_tooltip:true}");
        ItemStack holder = CompatItems.copyOf(rawBreak);
        assertThrows(UnsupportedFeature.class, () -> holder.getItemMeta().addItemFlags(ItemFlag.HIDE_DESTROYS));
    }

    @Test
    void persistentDataOnTheMeta() {
        var key = new org.bukkit.NamespacedKey("furnace", "owner");
        var counter = new org.bukkit.NamespacedKey("furnace", "count");
        ItemStack s = new ItemStack(Material.PAPER);
        ItemMeta m = s.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        assertTrue(pdc.isEmpty());
        pdc.set(key, PersistentDataType.STRING, "dana");
        pdc.set(counter, PersistentDataType.INTEGER, 7);
        pdc.set(new org.bukkit.NamespacedKey("furnace", "flag"), PersistentDataType.BOOLEAN, true);
        assertEquals("dana", pdc.get(key, PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class, () -> pdc.get(key, PersistentDataType.INTEGER));
        assertNull(pdc.get(new org.bukkit.NamespacedKey("furnace", "none"), PersistentDataType.STRING));
        assertFalse(s.getItemMeta().getPersistentDataContainer().has(key), "setItemMeta 전에는 스택이 그대로");
        s.setItemMeta(m);

        PersistentDataContainer back = s.getItemMeta().getPersistentDataContainer();
        assertEquals("dana", back.get(key, PersistentDataType.STRING));
        assertEquals(7, back.get(counter, PersistentDataType.INTEGER));
        assertTrue(back.get(new org.bukkit.NamespacedKey("furnace", "flag"), PersistentDataType.BOOLEAN));
        assertEquals(3, back.getKeys().size());
        assertEquals(9, back.getOrDefault(new org.bukkit.NamespacedKey("furnace", "x"), PersistentDataType.INTEGER, 9));
        // Paper 와 같은 저장 자리(custom_data 의 PublicBukkitValues) — Furnace 쪽 API 로도 읽힌다
        assertEquals("dana", s.asFurnace().persistentData().get("furnace:owner", org.domi.furnace.api.persistence.PersistentDataType.STRING));

        ItemMeta m2 = s.getItemMeta();
        m2.getPersistentDataContainer().remove(key);
        m2.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("furnace", "nested"), PersistentDataType.TAG_CONTAINER, nested(m2));
        s.setItemMeta(m2);
        var again = s.getItemMeta().getPersistentDataContainer();
        assertFalse(again.has(key));
        assertEquals(7, again.get(counter, PersistentDataType.INTEGER));
        var n = again.get(new org.bukkit.NamespacedKey("furnace", "nested"), PersistentDataType.TAG_CONTAINER);
        assertEquals("inner", n.get(key, PersistentDataType.STRING));
        // 복사
        PersistentDataContainer target = new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
        again.copyTo(target, true);
        assertEquals(7, target.get(counter, PersistentDataType.INTEGER));
        assertEquals(3, target.getKeys().size(), "counter·flag·nested");
        // 사본은 따로 논다
        ItemMeta c = m2.clone();
        c.getPersistentDataContainer().set(counter, PersistentDataType.INTEGER, 99);
        assertEquals(7, m2.getPersistentDataContainer().get(counter, PersistentDataType.INTEGER));
    }

    private static PersistentDataContainer nested(ItemMeta m) {
        PersistentDataContainer c = m.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        c.set(new org.bukkit.NamespacedKey("furnace", "owner"), PersistentDataType.STRING, "inner");
        return c;
    }

    // ------------------------------------------------------------------ 인벤토리

    @Test
    void emptySlotsAreNullAndHandsAreNeverNull() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            assertEquals(41, inv.getSize());
            assertEquals(InventoryType.PLAYER, inv.getType());
            assertSame(player, inv.getHolder());
            assertNull(inv.getItem(0));
            assertNull(inv.getHelmet());
            ItemStack hand = inv.getItemInMainHand();
            assertNotNull(hand);
            assertEquals(Material.AIR, hand.getType());
            assertEquals(0, hand.getAmount());
            assertTrue(inv.isEmpty());
            assertEquals(0, inv.firstEmpty());
            assertEquals(-1, inv.first(Material.STONE));
            assertThrows(IndexOutOfBoundsException.class, () -> inv.getItem(41));
            assertThrows(IndexOutOfBoundsException.class, () -> inv.setItem(-1, null));
        });
    }

    private static void assertSame(Object a, Object b) { org.junit.jupiter.api.Assertions.assertSame(a, b); }

    @Test
    void setItemAndMirrorWriteThrough() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(2, new ItemStack(Material.STONE, 10));
            ItemStack mirror = inv.getItem(2);
            assertEquals(Material.STONE, mirror.getType());
            assertEquals(10, mirror.getAmount());
            // 거울을 바꾸면 칸이 바뀐다
            mirror.setAmount(3);
            assertEquals(3, inv.getItem(2).getAmount());
            mirror.setType(Material.DIRT);
            assertEquals(Material.DIRT, inv.getItem(2).getType());
            ItemMeta meta = mirror.getItemMeta();
            meta.setDisplayName("named");
            mirror.setItemMeta(meta);
            assertEquals("named", inv.getItem(2).getItemMeta().getDisplayName());
            // 0 으로 줄이면 칸이 빈다
            mirror.setAmount(0);
            assertNull(inv.getItem(2));
            // 넣은 스택은 복사된다
            ItemStack mine = new ItemStack(Material.GOLD_INGOT, 4);
            inv.setItem(5, mine);
            mine.setAmount(1);
            assertEquals(4, inv.getItem(5).getAmount());
            // 공기·null·아이템이 아닌 종류는 칸을 비운다
            inv.setItem(5, new ItemStack(Material.AIR));
            assertNull(inv.getItem(5));
            inv.setItem(6, new ItemStack(Material.WATER, 1));
            assertNull(inv.getItem(6));
        });
    }

    @Test
    void addItemFollowsBukkitMerging() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(3, new ItemStack(Material.STONE, 60));
            ItemStack add = new ItemStack(Material.STONE, 10);
            HashMap<Integer, ItemStack> left = inv.addItem(add);
            assertTrue(left.isEmpty());
            assertEquals(64, inv.getItem(3).getAmount(), "먼저 합친다");
            assertEquals(6, inv.getItem(0).getAmount(), "남은 6개는 첫 빈 칸");
            // 못 넣은 것은 순서 번호 → 남은 스택
            for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Material.DIRT, 64));
            ItemStack more = new ItemStack(Material.STONE, 5);
            left = inv.addItem(more, new ItemStack(Material.DIRT, 2));
            assertEquals(2, left.size());
            assertEquals(5, left.get(0).getAmount());
            assertEquals(2, left.get(1).getAmount());
            // 여러 칸에 걸치는 큰 스택
            inv.clear();
            assertTrue(inv.addItem(new ItemStack(Material.ENDER_PEARL, 40)).isEmpty());
            assertEquals(16, inv.getItem(0).getAmount());
            assertEquals(16, inv.getItem(1).getAmount());
            assertEquals(8, inv.getItem(2).getAmount());
            // 방어구 칸·왼손은 addItem 이 채우지 않는다
            assertNull(inv.getHelmet());
            assertNull(inv.getItemInOffHand().isEmpty() ? null : inv.getItemInOffHand());
        });
    }

    @Test
    void removeItemContainsAndSearch() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(1, new ItemStack(Material.STONE, 30));
            inv.setItem(4, new ItemStack(Material.STONE, 40));
            inv.setItem(8, new ItemStack(Material.DIAMOND, 2));
            assertTrue(inv.contains(Material.STONE));
            assertTrue(inv.contains(Material.STONE, 70));
            assertFalse(inv.contains(Material.STONE, 71));
            assertTrue(inv.contains(new ItemStack(Material.DIAMOND, 2)));
            assertFalse(inv.contains(new ItemStack(Material.DIAMOND, 1)), "contains(ItemStack) 은 개수까지 같아야 한다");
            assertTrue(inv.containsAtLeast(new ItemStack(Material.STONE), 70));
            assertEquals(1, inv.first(Material.STONE));
            assertEquals(2, inv.all(Material.STONE).size());
            assertEquals(8, inv.first(new ItemStack(Material.DIAMOND, 2)));
            assertEquals(0, inv.firstEmpty());

            ItemStack take = new ItemStack(Material.STONE, 45);
            assertTrue(inv.removeItem(take).isEmpty());
            assertNull(inv.getItem(1), "첫 칸을 다 비우고");
            assertEquals(25, inv.getItem(4).getAmount(), "다음 칸에서 나머지를 뺀다");
            HashMap<Integer, ItemStack> left = inv.removeItem(new ItemStack(Material.STONE, 40));
            assertEquals(15, left.get(0).getAmount(), "못 뺀 15개");
            assertNull(inv.getItem(4));
            inv.remove(Material.DIAMOND);
            assertTrue(inv.isEmpty());
            inv.setContents(new ItemStack[] {new ItemStack(Material.APPLE), null, new ItemStack(Material.BREAD, 3)});
            assertEquals(Material.APPLE, inv.getItem(0).getType());
            assertEquals(3, inv.getItem(2).getAmount());
            assertThrows(IllegalArgumentException.class, () -> inv.setContents(new ItemStack[42]));
            inv.clear(0);
            assertNull(inv.getItem(0));
            inv.clear();
            assertTrue(inv.isEmpty());
        });
    }

    @Test
    void armorOffHandAndEquipmentSlots() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setHelmet(new ItemStack(Material.IRON_HELMET));
            inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            inv.setBoots(new ItemStack(Material.IRON_BOOTS));
            inv.setItemInOffHand(new ItemStack(Material.SHIELD));
            assertEquals(Material.IRON_BOOTS, inv.getItem(36).getType());
            assertEquals(Material.IRON_HELMET, inv.getItem(39).getType());
            assertEquals(Material.SHIELD, inv.getItem(40).getType());
            ItemStack[] armor = inv.getArmorContents();
            assertEquals(Material.IRON_BOOTS, armor[0].getType());
            assertEquals(Material.IRON_HELMET, armor[3].getType());
            assertEquals(Material.SHIELD, inv.getExtraContents()[0].getType());
            assertEquals(Material.IRON_LEGGINGS, inv.getItem(EquipmentSlot.LEGS).getType());
            assertEquals(Material.SHIELD, inv.getItem(EquipmentSlot.OFF_HAND).getType());
            inv.setItem(EquipmentSlot.CHEST, null);
            assertNull(inv.getChestplate());
            assertEquals(40, inv.getStorageContents().length + 4, "저장 칸은 36");
            inv.setArmorContents(new ItemStack[] {null, null, null, new ItemStack(Material.DIAMOND_HELMET)});
            assertNull(inv.getBoots());
            assertEquals(Material.DIAMOND_HELMET, inv.getHelmet().getType());
            assertThrows(IllegalArgumentException.class, () -> inv.setArmorContents(new ItemStack[5]));
            // 방어구·왼손도 contents 에 들어 있어 removeItem 이 지운다(Bukkit 과 같다)
            assertEquals(Material.DIAMOND_HELMET, inv.getContents()[39].getType());
        });
    }

    @Test
    void heldSlotSelectsTheMainHand() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.APPLE));
            inv.setItem(4, new ItemStack(Material.BREAD));
            assertEquals(0, inv.getHeldItemSlot());
            assertEquals(Material.APPLE, inv.getItemInMainHand().getType());
            inv.setHeldItemSlot(4);
            assertEquals(4, inv.getHeldItemSlot());
            assertEquals(Material.BREAD, inv.getItemInMainHand().getType());
            assertEquals(Material.BREAD, player.getItemInHand().getType());
            inv.setItemInMainHand(new ItemStack(Material.CARROT, 2));
            assertEquals(Material.CARROT, inv.getItem(4).getType());
            assertEquals(Material.CARROT, inv.getItem(EquipmentSlot.HAND).getType());
            assertThrows(IllegalArgumentException.class, () -> inv.setHeldItemSlot(9));
            player.setItemInHand(new ItemStack(Material.MELON_SLICE));
            assertEquals(Material.MELON_SLICE, inv.getItem(4).getType());
        });
        assertEquals(4, tp.player().heldSlot());
        // 단순 읽기는 소유 스레드가 아니어도 된다(플레이어 인벤토리는 서버가 스레드 안전하게 둔다)
        assertEquals(4, player.getInventory().getHeldItemSlot());
        assertEquals(Material.MELON_SLICE, player.getInventory().getItem(4).getType());
    }

    @Test
    void offOwnerWritesAreDeferredButReadBackAtOnce() {
        PlayerInventory inv = player.getInventory();
        // 이 스레드(시험 스레드)는 플레이어의 소유 스레드가 아니다 — 쓰기는 예약되지만 이 객체에서는 바로 읽힌다
        inv.setItem(10, new ItemStack(Material.EMERALD, 3));
        assertEquals(3, inv.getItem(10).getAmount(), "예약된 쓰기가 겹쳐 보인다");
        // 연달아 addItem 해도 서로 덮어쓰지 않는다(접속 처리기의 키트 지급 모양)
        assertTrue(inv.addItem(new ItemStack(Material.STONE, 5)).isEmpty());
        assertTrue(inv.addItem(new ItemStack(Material.DIRT, 7)).isEmpty());
        assertTrue(inv.addItem(new ItemStack(Material.STONE, 70)).isEmpty());
        assertEquals(5 + 70, java.util.Arrays.stream(inv.getContents()).filter(java.util.Objects::nonNull)
                .filter(i -> i.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum());
        server.tick(3);
        // 적용된 뒤(소유 스레드)에도 같은 내용
        owner(() -> {
            assertEquals(3, inv.getItem(10).getAmount());
            assertEquals(75, inv.getContents().length == 0 ? 0 : java.util.Arrays.stream(inv.getContents()).filter(java.util.Objects::nonNull)
                    .filter(i -> i.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum());
            assertEquals(7, inv.getItem(inv.first(Material.DIRT)).getAmount());
        });
        inv.clear();
        assertTrue(inv.isEmpty());
        server.tick(2);
        assertTrue(owner(inv::isEmpty));
        // 단축 칸 바꾸기도 예약되고 바로 읽힌다
        inv.setHeldItemSlot(6);
        assertEquals(6, inv.getHeldItemSlot());
        for (int i = 0; i < 10 && tp.player().heldSlot() != 6; i++) server.tick(1);   // 예약은 플레이어 틱에 적용된다
        assertEquals(6, tp.player().heldSlot());
        assertEquals(6, inv.getHeldItemSlot());
    }

    @Test
    void iteratorWritesThrough() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.STONE));
            var it = inv.iterator();
            it.next();
            it.set(new ItemStack(Material.DIRT, 2));
            assertEquals(Material.DIRT, inv.getItem(0).getType());
        });
    }

    @Test
    void virtualInventoriesAreHonestlyUnsupported() {
        assertThrows(UnsupportedFeature.class, () -> Bukkit.createInventory(null, 27));
        assertThrows(UnsupportedFeature.class, () -> Bukkit.createInventory(null, 27, "Menu"));
        assertThrows(UnsupportedFeature.class, () -> Bukkit.createInventory(null, InventoryType.HOPPER));
    }

    @Test
    void playerSideEffectsOfUpdateInventoryAreNone() {
        player.updateInventory();
        assertArrayEquals(new ItemStack[36], owner(() -> player.getInventory().getStorageContents()));
    }
}
