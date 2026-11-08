package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.fixtures.DeathProbe;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 진짜 Bukkit 플러그인 jar 의 PlayerDeathEvent 리스너가 getDrops()·getDroppedExp()·setKeepInventory·setKeepLevel·getNewLevel 을
 * Paper 1.21.1 처럼 쓸 수 있다(무덤·keep-inventory 류가 죽을 때마다 오류를 내던 자리). 실제 드롭·인벤토리·리스폰 경험치로 확인한다.
 */
class BukkitDeathDropsTest {

    private static final String YML = """
            name: DeathUser
            version: '1'
            main: du.DeathUser
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package du;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.event.*;
            import org.bukkit.event.entity.PlayerDeathEvent;
            import org.bukkit.inventory.ItemStack;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.DeathProbe;
            public final class DeathUser extends JavaPlugin implements Listener {
                @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
                @EventHandler public void death(PlayerDeathEvent e) {
                    try {
                        StringBuilder sb = new StringBuilder("drops=");
                        for (ItemStack d : e.getDrops()) sb.append(d.getType().name()).append('x').append(d.getAmount()).append(';');
                        DeathProbe.LOG.add(sb + " exp=" + e.getDroppedExp() + " keep=" + e.getKeepInventory() + "," + e.getKeepLevel()
                                + " new=" + e.getNewExp() + "," + e.getNewLevel() + "," + e.getNewTotalExp());
                        switch (DeathProbe.mode) {
                            case "grave" -> {   // 무덤·keep-inventory 류
                                e.setKeepInventory(true);
                                e.getDrops().clear();
                                e.setDroppedExp(0);
                                e.setKeepLevel(true);
                            }
                            case "edit" -> {
                                Iterator<ItemStack> it = e.getDrops().iterator();
                                while (it.hasNext()) { ItemStack d = it.next(); if (d.getType() == Material.SHIELD) it.remove(); }
                                for (ItemStack d : e.getDrops()) if (d.getType() == Material.DIRT) d.setAmount(9);
                                e.getDrops().add(new ItemStack(Material.GOLD_INGOT, 2));
                                e.setDroppedExp(11);
                                e.setNewLevel(2);
                                e.setNewTotalExp(10);
                                e.setNewExp(3);
                            }
                            case "cancel" -> {
                                try { e.setCancelled(true); DeathProbe.LOG.add("cancel=ok"); }
                                catch (Throwable t) { DeathProbe.LOG.add("cancel=" + t.getClass().getSimpleName()); }
                            }
                            default -> { }
                        }
                    } catch (Throwable t) { DeathProbe.failure = t; }
                }
            }
            """;

    private static final ItemStack CURSED = new ItemStack("minecraft:iron_sword", 1,
            Map.of("minecraft:enchantments", "{levels:{\"minecraft:vanishing_curse\":1}}"), java.util.Set.of());

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { DeathProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("du.jar"), YML, Map.of("du.DeathUser", PLUGIN));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static TestPlayer joinWithItems(FurnaceTestServer server) {
        TestPlayer tp = server.join("Dana");
        server.tick(2);
        tp.sync();
        var inv = tp.player().inventory();
        var writes = List.of(inv.set(0, new ItemStack("minecraft:dirt", 4)), inv.set(1, new ItemStack("minecraft:stone", 2)), inv.set(2, CURSED),
                inv.set(36, new ItemStack("minecraft:iron_helmet", 1)), inv.set(40, new ItemStack("minecraft:shield", 1)));
        server.tick(3);
        for (int i = 0; i < 40 && !writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone); i++) { server.tick(1); tp.sync(); }
        assertTrue(writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone), "쓰기가 끝나지 않았다: " + writes);
        return tp;
    }

    private static void die(FurnaceTestServer server, TestPlayer tp) {
        tp.player().setHealth(0f);
        server.tick(3);
        tp.sync();
        server.tick(3);
    }

    private static Map<String, Integer> floor(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            Map<String, Integer> out = new TreeMap<>();
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var st = ((ItemEntity) e).getItem();
                out.merge(server.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
            }
            return out;
        }));
    }

    private static int orbs(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            int sum = 0;
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ExperienceOrb)) sum += ((ExperienceOrb) e).value() * Math.max(1, ((ExperienceOrb) e).count());
            return sum;
        }));
    }

    private static int inventoryKinds(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        int n = 0;
        for (int i = 0; i <= 40; i++) if (!p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i)).isEmpty()) n++;
        return n;
    }

    @Test
    void listenerReadsRealDropsExpAndDefaults() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "리스너가 던졌다: " + DeathProbe.failure);
            assertEquals(List.of("drops=DIRTx4;STONEx2;IRON_HELMETx1;SHIELDx1; exp=35 keep=false,false new=0,0,0"), DeathProbe.LOG);
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(server));
            assertEquals(0, inventoryKinds(server));
            assertEquals(35, orbs(server));
        }
    }

    @Test
    void graveStylePluginKeepsItemsWithoutErrors() throws Exception {
        DeathProbe.mode = "grave";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "getDrops().clear() 등이 던지면 안 된다: " + DeathProbe.failure);
            assertEquals(Map.of(), floor(server), "드롭 목록을 비웠으므로 아무것도 안 떨어진다");
            assertEquals(5, inventoryKinds(server), "keepInventory → 인벤토리 그대로");
            assertEquals(0, orbs(server), "setDroppedExp(0)");
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(5, p.vitals().xpLevel(), "setKeepLevel(true) → 레벨 유지");
        }
    }

    @Test
    void editedListAndNewExpFlowBackToTheServer() throws Exception {
        DeathProbe.mode = "edit";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "리스너가 던졌다: " + DeathProbe.failure);
            assertEquals(Map.of("minecraft:dirt", 9, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:gold_ingot", 2), floor(server),
                    "방패는 뺐고 흙은 제자리 수정, 금 추가");
            assertEquals(0, inventoryKinds(server), "keepInventory 가 false 라 인벤토리는 비운다");
            assertEquals(11, orbs(server));
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(2, p.vitals().xpLevel());
            assertEquals(13, p.vitals().xpTotal(), "newTotalExp 10 + newExp 3");
        }
    }

    @Test
    void cancellingTheDeathStillSaysUnsupported() throws Exception {
        DeathProbe.mode = "cancel";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            die(server, tp);
            assertTrue(DeathProbe.LOG.contains("cancel=UnsupportedFeature"), DeathProbe.LOG.toString());
        }
    }
}
