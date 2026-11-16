package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** T-141 E11c-1: 공개 API {@code Inventory#update} — 플레이어 인벤토리에서 읽고-바꾸기가 한 소유 스레드 작업이다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryUpdateApiTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    /** 쓰기·갱신은 소유 스레드 작업이라 서버를 돌려야 끝난다. */
    <T> T done(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        for (int i = 0; i < 200 && !f.isDone(); i++) server.tick(1);
        if (!f.isDone()) throw new AssertionError("future did not complete");
        return f.get(1, TimeUnit.SECONDS);
    }

    @Test
    void updatesAPlayerSlotAtomically() throws Exception {
        try (TestPlayer tp = server.join("Updater")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Updater");
            Inventory inv = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).furnaceHandle().inventory();
            p.getInventory().setItem(4, new ItemStack(Material.STONE, 5));

            assertTrue(done(inv.update(4, cur -> cur.withAmount(cur.amount() + 2))));
            assertEquals(7, p.getInventory().getItem(4).getAmount());

            assertFalse(done(inv.update(4, cur -> cur)), "그대로 돌려주면 바꾸지 않는다");
            assertFalse(done(inv.update(4, cur -> null)));
            assertEquals(7, p.getInventory().getItem(4).getAmount());

            assertTrue(done(inv.update(4, cur -> org.domi.furnace.api.item.ItemStack.EMPTY)));
            assertTrue(p.getInventory().getItem(4) == null || p.getInventory().getItem(4).getType() == Material.AIR);
        }
    }

    @Test
    void outOfRangeFailsLikeGetAndSet() throws Exception {
        try (TestPlayer tp = server.join("Ranger")) {
            server.tick(2);
            Inventory inv = ((org.bukkit.craftbukkit.entity.CraftPlayer) Bukkit.getPlayerExact("Ranger")).furnaceHandle().inventory();
            var f = inv.update(99, cur -> cur);
            try {
                done(f);
                throw new AssertionError("expected IndexOutOfBoundsException");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IndexOutOfBoundsException, String.valueOf(e.getCause()));
            }
        }
    }
}
