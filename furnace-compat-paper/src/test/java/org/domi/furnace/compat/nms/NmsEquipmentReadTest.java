package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

/** NMS shim 5a-2: 플레이어 장비 칸 읽기(voxy 먼 플레이어 모습). 접속한 플레이어는 그 칸의 살아 있는 보기(고치면 칸에 되쓴다). */
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
    void connectedPlayerSlotsAreLiveViewsFromAnyThread() throws Exception {
        var inv = bukkit.getInventory();
        inv.setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET));
        inv.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.STONE, 5));
        inv.setBoots(null);
        inv.setItemInOffHand(null);
        server.tick(2);   // 틱 스레드가 아닌 곳의 Bukkit 쓰기는 주인이 다음 틱에 넣는다
        ServerPlayer p = bukkit.getHandle();
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        assertSame(Items.DIAMOND_HELMET, head.getItem());
        ItemStack hand = p.getItemBySlot(EquipmentSlot.MAINHAND);
        assertSame(Items.STONE, hand.getItem());
        assertEquals(5, hand.getCount());
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.FEET), "빈 칸은 바닐라처럼 EMPTY");
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.OFFHAND));
        assertSame(ItemStack.EMPTY, p.getItemBySlot(EquipmentSlot.BODY), "플레이어에게는 몸 칸이 없다");
        // 바닐라: 칸에 든 객체 자체 — 고치면 그 칸이 바뀐다(손 칸은 getItemInHand 와 같은 칸)
        hand.shrink(1);
        assertEquals(4, inv.getItemInMainHand().getAmount());
        assertEquals(4, p.getItemInHand(InteractionHand.MAIN_HAND).getCount());
        head.setDamageValue(7);
        assertEquals(7, ((org.bukkit.inventory.meta.Damageable) inv.getHelmet().getItemMeta()).getDamage());
        ItemStack copy = hand.copy();   // 사본은 칸과 떨어진다
        copy.shrink(1);
        assertEquals(4, inv.getItemInMainHand().getAmount());
        // 그새 칸이 다른 스택으로 바뀌면 그 보기는 칸에서 떨어진다(바닐라에서 칸에서 빠진 객체와 같다)
        inv.setHelmet(new org.bukkit.inventory.ItemStack(Material.IRON_HELMET));
        server.tick(2);
        head.setDamageValue(9);
        assertSame(Material.IRON_HELMET, inv.getHelmet().getType());
        assertEquals(0, ((org.bukkit.inventory.meta.Damageable) inv.getHelmet().getItemMeta()).getDamage());
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
        assertSame(Items.IRON_HELMET, seen.get());
    }

    @Test
    void detachedPlayerHandsAreTheHeldObjects() {
        var level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var fake = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes("eq".getBytes()), "[Eq]"),
                new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false));
        ItemStack stone = new ItemStack(Items.STONE, 3);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stone);
        assertSame(stone, fake.getItemBySlot(EquipmentSlot.MAINHAND));
        assertSame(ItemStack.EMPTY, fake.getItemBySlot(EquipmentSlot.HEAD));
    }
}
