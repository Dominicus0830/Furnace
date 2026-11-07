package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 칸을 바꾸면 이전에 꺼낸 거울 스택은 떨어져 나가 옛 값을 유지한다(CraftBukkit 과 같다). H3 독립 검토에서 재현된 경우들. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryMirrorDetachTest {
    FurnaceTestServer server; TestPlayer tp; org.bukkit.entity.Player player; BlockPos at;

    @BeforeAll void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CompatServer.INSTANCE);
        tp = server.join("Dana"); server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }
    @AfterAll void stop() { server.close(); }
    <T> T owner(Supplier<T> t) { return server.onRegion(at, t); }
    @BeforeEach void clear() { owner(() -> { player.getInventory().clear(); return null; }); server.tick(1); }

    @Test void swapTwoSlots() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.DIAMOND, 3));
            inv.setItem(1, new ItemStack(Material.STONE, 7));
            ItemStack a = inv.getItem(0), b = inv.getItem(1);
            inv.setItem(0, b);
            inv.setItem(1, a);
            assertEquals(Material.STONE, inv.getItem(0).getType());
            assertEquals(Material.DIAMOND, inv.getItem(1).getType(), "swap: slot 1 should hold the old slot-0 item");
            return null;
        });
    }

    @Test void saveClearRestore() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.DIAMOND, 3));
            ItemStack[] saved = inv.getContents();
            inv.clear();
            inv.setContents(saved);
            assertNotNull(inv.getItem(0), "restore after clear lost the item");
            assertEquals(3, inv.getItem(0).getAmount());
            return null;
        });
    }

    @Test void heldReferenceAfterReplace() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.DIAMOND, 3));
            ItemStack old = inv.getItem(0);
            inv.setItem(0, new ItemStack(Material.STONE, 1));
            assertEquals(Material.DIAMOND, old.getType(), "old reference should keep the old value");
            return null;
        });
    }

    @Test void emptyHandSetTypeThenPutBack() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            ItemStack hand = inv.getItemInMainHand();
            hand.setType(Material.DIAMOND);
            hand.setAmount(2);
            inv.setItemInMainHand(hand);
            assertNotNull(inv.getItem(0));
            assertEquals(Material.DIAMOND, inv.getItem(0).getType());
            return null;
        });
    }

    @Test void nonOwnerSpawnEntityDoesNotLeaveEntity() {
        // spawnEntity on a non-owner thread: must throw and not spawn later
        var w = player.getWorld();
        var loc = player.getLocation();
        int before = owner(() -> w.getNearbyEntities(loc, 8, 8, 8).size());
        try { w.spawnEntity(loc, org.bukkit.entity.EntityType.PIG); } catch (RuntimeException expected) {}
        server.tick(3);
        int after = owner(() -> w.getNearbyEntities(loc, 8, 8, 8).size());
        assertEquals(before, after);
    }
}
