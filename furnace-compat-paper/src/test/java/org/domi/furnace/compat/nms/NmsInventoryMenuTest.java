package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim "컨테이너 묶음": {@code ServerPlayer.getInventory()}·{@code containerMenu}·{@code inventoryMenu}. 기대값은 Paper 1.21.1 의 칸 번호·동작(Bukkit 쪽 같은
 * 칸과 견준다)이고, 접속한 플레이어의 인벤토리는 서버 인벤토리를 그대로 비춘다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsInventoryMenuTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Packer");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    private void clear() {
        bukkit.getInventory().clear();
        server.tick(2);
    }

    @Test
    void slotNumbersAreTheVanillaOnesAndTheViewIsLive() {
        clear();
        var pi = bukkit.getInventory();
        pi.setItem(0, new org.bukkit.inventory.ItemStack(Material.STONE, 5));          // 핫바 첫 칸
        pi.setItem(20, new org.bukkit.inventory.ItemStack(Material.DIRT, 7));          // 주 칸
        pi.setBoots(new org.bukkit.inventory.ItemStack(Material.IRON_BOOTS));          // 36
        pi.setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET));     // 39
        pi.setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.SHIELD));      // 40
        server.tick(2);
        Inventory inv = bukkit.getHandle().getInventory();
        assertSame(inv, bukkit.getHandle().getInventory(), "플레이어마다 같은 객체");
        assertEquals(41, inv.getContainerSize());
        assertEquals(Items.STONE, inv.getItem(0).getItem());
        assertEquals(5, inv.getItem(0).getCount());
        assertEquals(Items.DIRT, inv.getItem(20).getItem());
        assertEquals(Items.IRON_BOOTS, inv.getItem(36).getItem());
        assertEquals(Items.DIAMOND_HELMET, inv.getItem(39).getItem());
        assertEquals(Items.SHIELD, inv.getItem(Inventory.SLOT_OFFHAND).getItem());
        assertSame(ItemStack.EMPTY, inv.getItem(41), "범위 밖은 EMPTY");
        assertSame(ItemStack.EMPTY, inv.getItem(-1));
        // 필드 목록도 같은 번호를 본다
        assertEquals(36, inv.items.size());
        assertEquals(4, inv.armor.size());
        assertEquals(1, inv.offhand.size());
        assertEquals(Items.STONE, inv.items.get(0).getItem());
        assertEquals(Items.DIAMOND_HELMET, inv.armor.get(3).getItem());
        assertEquals(Items.IRON_BOOTS, inv.getArmor(0).getItem());
        assertEquals(Items.SHIELD, inv.offhand.get(0).getItem());
        // getContents 는 items → armor → offhand 순서(Paper)
        var all = inv.getContents();
        assertEquals(41, all.size());
        assertEquals(Items.IRON_BOOTS, all.get(36).getItem());
        assertFalse(inv.isEmpty());
        // 고치면 그 칸에 되쓴다(바닐라: 칸에 든 객체)
        inv.getItem(0).shrink(2);
        assertEquals(3, bukkit.getInventory().getItem(0).getAmount());
        // 쓰기: setItem 은 칸을 바꾸고 Bukkit 도 본다
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 9));
        assertEquals(Material.COBBLESTONE, bukkit.getInventory().getItem(1).getType());
        assertEquals(9, bukkit.getInventory().getItem(1).getAmount());
        inv.setItem(99, new ItemStack(Items.COBBLESTONE, 1));   // 범위 밖은 조용히 무시(바닐라)
        inv.items.set(2, new ItemStack(Items.GOLD_INGOT, 4));
        assertEquals(Material.GOLD_INGOT, bukkit.getInventory().getItem(2).getType());
    }

    @Test
    void removeAddAndContainsFollowTheVanillaRules() {
        clear();
        Inventory inv = bukkit.getHandle().getInventory();
        assertTrue(inv.isEmpty());
        inv.setItem(3, new ItemStack(Items.DIAMOND, 10));
        ItemStack part = inv.removeItem(3, 4);
        assertEquals(4, part.getCount());
        assertEquals(6, inv.getItem(3).getCount());
        assertEquals(6, inv.removeItem(3, 100).getCount(), "있는 만큼만");
        assertTrue(inv.getItem(3).isEmpty());
        assertSame(ItemStack.EMPTY, inv.removeItem(3, 1), "빈 칸");
        inv.setItem(4, new ItemStack(Items.EMERALD, 2));
        ItemStack whole = inv.removeItemNoUpdate(4);
        assertEquals(2, whole.getCount());
        assertTrue(inv.getItem(4).isEmpty());
        // add: 같은 종류를 먼저 합치고(든 칸 → 왼손 → 0..35), 남은 것은 첫 빈 칸에, 못 넣은 나머지는 스택에 남는다
        inv.setItem(5, new ItemStack(Items.STONE, 60));
        ItemStack more = new ItemStack(Items.STONE, 10);
        assertTrue(inv.add(more));
        assertTrue(more.isEmpty());
        assertEquals(64, inv.getItem(5).getCount());
        assertEquals(6, inv.getItem(0).getCount(), "남은 6개는 첫 빈 칸(0번)에");
        assertEquals(Items.STONE, inv.getItem(0).getItem());
        // 꽉 차면 거짓
        for (int i = 0; i < 36; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
        ItemStack none = new ItemStack(Items.STONE, 3);
        assertFalse(inv.add(none));
        assertEquals(3, none.getCount());
        assertEquals(-1, inv.getFreeSlot());
        assertTrue(inv.contains(new ItemStack(Items.DIRT, 1)));
        assertFalse(inv.contains(new ItemStack(Items.STONE, 1)));
        assertTrue(inv.contains(s -> s.is(Items.DIRT)));
        assertEquals(0, inv.findSlotMatchingItem(new ItemStack(Items.DIRT)));
        inv.clearContent();
        assertTrue(inv.isEmpty());
        assertEquals(0, inv.getFreeSlot());
        server.tick(1);
        assertTrue(bukkit.getInventory().getItem(0) == null || bukkit.getInventory().getItem(0).getType().isAir());
    }

    @Test
    void selectedFollowsTheServerAndWritesGoBack() {
        clear();
        bukkit.getInventory().setItem(2, new org.bukkit.inventory.ItemStack(Material.STICK, 3));
        server.tick(2);
        bukkit.getInventory().setHeldItemSlot(2);
        server.tick(2);
        Inventory inv = bukkit.getHandle().getInventory();
        assertEquals(2, inv.selected);
        assertEquals(Items.STICK, inv.getSelected().getItem());
        // 플러그인이 필드에 쓴 값은 다음 메서드 호출 때 서버에 알린다
        inv.selected = 5;
        assertTrue(inv.getSelected().isEmpty());   // 서버가 바꾸는 동안에도 쓴 값 그대로
        assertEquals(5, inv.selected);
        server.tick(2);
        assertEquals(5, bukkit.getInventory().getHeldItemSlot());
        assertEquals(5, inv.selected);
        // 서버가 바꾼 값은 다음 호출 때 보인다
        bukkit.getInventory().setHeldItemSlot(2);
        server.tick(2);
        assertEquals(Items.STICK, inv.getSelected().getItem());
        assertEquals(2, inv.selected);
        assertTrue(Inventory.isHotbarSlot(8));
        assertFalse(Inventory.isHotbarSlot(9));
    }

    @Test
    void inventoryMenuIsTheInventoryWindowAndContainerMenuStartsAsIt() {
        clear();
        bukkit.getInventory().setItem(7, new org.bukkit.inventory.ItemStack(Material.APPLE, 2));
        bukkit.getInventory().setChestplate(new org.bukkit.inventory.ItemStack(Material.IRON_CHESTPLATE));
        server.tick(2);
        ServerPlayer p = bukkit.getHandle();   // getHandle 이 필드를 서버 창에 맞춘다
        assertSame(p.inventoryMenu, p.containerMenu, "열린 창이 없으면 containerMenu 는 inventoryMenu");
        InventoryMenu menu = p.inventoryMenu;
        assertEquals(0, menu.containerId);
        assertEquals(46, menu.slots.size());
        assertSame(menu, bukkit.getHandle().inventoryMenu, "플레이어마다 같은 객체");
        assertEquals(Items.APPLE, menu.getSlot(36 + 7).getItem().getItem(), "핫바 칸은 창 번호 36..44");
        assertEquals(Items.IRON_CHESTPLATE, menu.getSlot(6).getItem().getItem(), "가슴 갑옷은 창 번호 6");
        assertSame(p.getInventory(), menu.getSlot(36 + 7).container, "플레이어 인벤토리 칸의 컨테이너는 그 플레이어의 Inventory");
        assertEquals(7, menu.getSlot(36 + 7).getContainerSlot());
        assertEquals(38, menu.getSlot(6).getContainerSlot(), "가슴 갑옷(창 6)은 Inventory 38");
    }

    @Test
    void anOpenWindowIsAViewOfTheServerWindow() throws Exception {
        clear();
        bukkit.getInventory().setItem(3, new org.bukkit.inventory.ItemStack(Material.BREAD, 4));
        var chest = Bukkit.createInventory(null, 27, "Box");
        chest.setItem(5, new org.bukkit.inventory.ItemStack(Material.GOLD_BLOCK, 3));
        bukkit.openInventory(chest);
        server.tick(3);
        ServerPlayer p = bukkit.getHandle();
        AbstractContainerMenu m = p.containerMenu;
        assertNotSame(p.inventoryMenu, m);
        assertTrue(m.containerId > 0);
        assertEquals("minecraft:generic_9x3", ((MenuType<?>) m.getType()).furnaceKey());
        assertEquals(27 + 36, m.slots.size());
        // 창의 컨테이너 칸은 마지막으로 알린 값, 플레이어 칸은 지금 값
        assertEquals(Items.GOLD_BLOCK, m.getSlot(5).getItem().getItem());
        assertEquals(3, m.getSlot(5).getItem().getCount());
        Slot mine = m.getSlot(27 + 27 + 3);   // 창의 핫바 칸 3 = Inventory 3
        assertSame(p.getInventory(), mine.container);
        assertEquals(Items.BREAD, mine.getItem().getItem());
        mine.getItem().shrink(1);
        assertEquals(3, bukkit.getInventory().getItem(3).getAmount(), "플레이어 칸은 고치면 되쓴다");
        mine.set(new ItemStack(Items.CARROT, 2));
        assertEquals(Material.CARROT, bukkit.getInventory().getItem(3).getType());
        assertEquals(m.slots.size(), m.getItems().size());
        assertTrue(m.isValidSlotIndex(-999));
        assertTrue(m.isValidSlotIndex(62));
        assertFalse(m.isValidSlotIndex(63));
        // 창이 가진 컨테이너 칸은 주인 스레드가 아니면 못 고친다 — 조용히 틀리지 않는다
        assertThrows(UnsupportedNmsCall.class, () -> m.getSlot(5).getItem().shrink(1));
        assertThrows(UnsupportedNmsCall.class, () -> m.getSlot(5).set(new ItemStack(Items.DIRT)));
        assertThrows(UnsupportedNmsCall.class, () -> m.setCarried(new ItemStack(Items.DIRT)));
        assertSame(ItemStack.EMPTY, m.getCarried().isEmpty() ? ItemStack.EMPTY : m.getCarried());
        assertEquals(m.containerId, bukkit.getHandle().containerMenu.containerId);
        // Bukkit 쪽 같은 창
        assertSame(bukkit.getOpenInventory().getTopInventory(), m.getBukkitView().getTopInventory());
        // 다른 스레드에서도 읽힌다
        var seen = new AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                seen.set(m.getSlot(5).getItem().getItem());
            } catch (Throwable e) {
                seen.set(e);
            }
        });
        t.start();
        t.join(10_000);
        assertSame(Items.GOLD_BLOCK, seen.get());
        // 창을 닫으면 containerMenu 는 다시 inventoryMenu
        bukkit.closeInventory();
        server.tick(3);
        ServerPlayer again = bukkit.getHandle();
        assertSame(again.inventoryMenu, again.containerMenu);
        // 닫힌 창의 칸을 읽으면 조용히 틀린 값을 주지 않는다
        assertThrows(UnsupportedNmsCall.class, () -> m.getSlot(5).getItem());
    }

    @Test
    void aPlayerNotInTheWorldHasItsOwnInventory() {
        var level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var fake = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes("inv".getBytes()), "[Inv]"),
                new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false));
        Inventory inv = fake.getInventory();
        assertTrue(inv.isEmpty());
        ItemStack stone = new ItemStack(Items.STONE, 3);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stone);
        assertSame(stone, inv.getItem(0), "바닐라: 월드에 없는 플레이어의 주 손은 제 인벤토리의 고른 칸");
        assertSame(stone, inv.getSelected());
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        inv.setItem(36, boots);
        assertSame(boots, inv.getArmor(0));
        assertSame(boots, fake.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        // 제 인벤토리는 서버와 이어지지 않는다
        assertTrue(bukkit.getInventory().getItem(0) == null || bukkit.getInventory().getItem(0).getType() != Material.STONE || bukkit.getInventory().getItem(0).getAmount() != 3);
        // 바닐라 생성자의 인벤토리도 같다 + add 규칙
        Inventory own = new Inventory(fake);
        ItemStack dirt = new ItemStack(Items.DIRT, 70);
        assertTrue(own.add(dirt));
        assertTrue(dirt.isEmpty());
        assertEquals(64, own.getItem(0).getCount());
        assertEquals(6, own.getItem(1).getCount());
        // 인벤토리 창도 제 칸으로 만든다
        InventoryMenu menu = new InventoryMenu(own, true, fake);
        assertEquals(46, menu.slots.size());
        assertSame(own.getItem(0), menu.getSlot(36).getItem());
        assertSame(fake.inventoryMenu, fake.containerMenu);
        assertEquals(0, fake.containerMenu.containerId);
    }
}
