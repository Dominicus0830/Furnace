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

    /**
     * 독립 검토 R1: 서버 PlayerInventory.add(줍기·/give 등)는 소유 스레드가 아닌 곳에서도 불려 읽기와 쓰기 사이에 끼어들 수 있다. update 는 비교-교환이라
     * 끼어든 쓰기를 덮지 않고 다시 읽어 fn 을 다시 부른다. fn 안에서 그 끼어들기를 결정적으로 흉내 낸다.
     */
    @Test
    void aWriteThatSlipsBetweenReadAndWriteIsNotLost() throws Exception {
        try (TestPlayer tp = server.join("Slipper")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Slipper");
            Inventory inv = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).furnaceHandle().inventory();
            p.getInventory().setHeldItemSlot(0);
            p.getInventory().setItem(0, new ItemStack(Material.STONE, 1));
            org.domi.furnace.server.entity.Player serverPlayer = ((org.domi.furnace.server.plugin.PlayerHandle) tp.player()).handle();
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

            boolean changed = done(inv.update(0, cur -> {
                if (calls.incrementAndGet() == 1) {   // 첫 호출: 읽은 뒤 쓰기 전에 5개가 같은 칸에 합쳐진다(줍기·/give)
                    var raw = serverPlayer.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(0));
                    serverPlayer.inventory().add(raw.withCount(5), false, 64, false);
                }
                return cur.withComponent("minecraft:custom_name", "\"Marked\"").withAmount(cur.amount());
            }));
            assertTrue(changed);
            assertEquals(2, calls.get(), "칸이 그새 바뀌어 fn 이 다시 불렸다");
            ItemStack now = p.getInventory().getItem(0);
            assertEquals(6, now.getAmount(), "합쳐진 5개를 stale 값으로 덮어 잃지 않는다");
            assertEquals("Marked", now.getItemMeta().getDisplayName());
        }
    }

    @Test
    void anEqualResultIsNotAChangeOnEveryImplementation() throws Exception {
        try (TestPlayer tp = server.join("Equaler")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Equaler");
            Inventory inv = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).furnaceHandle().inventory();
            p.getInventory().setItem(3, new ItemStack(Material.STONE, 2));
            assertFalse(done(inv.update(3, cur -> new org.domi.furnace.api.item.ItemStack(cur.type(), cur.amount(), cur.components(), cur.removedComponents()))),
                    "같은 값의 새 스택은 바꾼 것이 아니다(불필요한 칸 갱신 패킷 없음)");
        }
    }
}
