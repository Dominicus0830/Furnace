package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
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

/** NMS shim 5a-2: 플레이어 장비 칸 읽기(voxy 먼 플레이어 모습). 접속한 플레이어는 지금 칸의 읽기 전용 사본. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEquipmentReadTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Wearer");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void connectedPlayerSlotsAreReadOnlyCopiesFromAnyThread() throws Exception {
        var inv = bukkit.getInventory();
        inv.setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET));
        inv.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.STONE, 5));
        server.tick(2);
        ServerPlayer p = bukkit.getHandle();
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        assertSame(Items.DIAMOND_HELMET, head.getItem());
        ItemStack hand = p.getItemBySlot(EquipmentSlot.MAINHAND);
        assertSame(Items.STONE, hand.getItem());
        assertEquals(5, hand.getCount());
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.FEET), "빈 칸은 바닐라처럼 EMPTY");
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.OFFHAND));
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.BODY), "플레이어에게는 몸 칸이 없다");
        // 고치면 칸이 따라가지 않으므로 막는다 — 사본은 고칠 수 있다
        assertThrows(UnsupportedNmsCall.class, () -> hand.shrink(1));
        assertThrows(UnsupportedNmsCall.class, () -> hand.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1));
        ItemStack copy = hand.copy();
        copy.shrink(1);
        assertEquals(4, copy.getCount());
        assertEquals(5, inv.getItemInMainHand().getAmount());
        // 다른 스레드(틱 스레드 아님)에서도 지금 칸
        var seen = new AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                seen.set(p.getItemBySlot(EquipmentSlot.HEAD).getItem());
            } catch (Throwable e) {
                seen.set(e);
            }
        });
        t.start();
        t.join(10_000);
        assertSame(Items.DIAMOND_HELMET, seen.get());
        assertThrows(UnsupportedNmsCall.class, () -> p.getItemInHand(InteractionHand.MAIN_HAND), "손 객체를 고치는 쓰기 문맥용 길은 그대로 막는다");
    }

    @Test
    void detachedPlayerHandsAreTheHeldObjects() {
        var level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var fake = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes("eq".getBytes()), "[Eq]"),
                new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false));
        ItemStack stone = new ItemStack(Items.STONE, 3);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stone);
        assertSame(stone, fake.getItemBySlot(EquipmentSlot.MAINHAND));
        assertFalse(stone.furnaceIsReadOnly());
        assertSame(ItemStack.EMPTY, fake.getItemBySlot(EquipmentSlot.HEAD));
        assertTrue(new ItemStack(Items.STONE).furnaceReadOnly().furnaceIsReadOnly());
        assertFalse(ItemStack.EMPTY.furnaceReadOnly().furnaceIsReadOnly(), "공유 EMPTY 는 표시하지 않는다");
    }
}
